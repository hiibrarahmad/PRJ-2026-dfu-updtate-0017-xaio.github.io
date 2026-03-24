from __future__ import annotations

import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import webbrowser
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding


CHANNELS = ("dev", "beta", "stable")
DEFINE_RE = re.compile(r'^#define\s+(\w+)\s+(.+)$')
SEMVER_RE = re.compile(r"^(\d+)\.(\d+)\.(\d+)$")


class DashboardError(RuntimeError):
    pass


@dataclass
class ReleaseRequest:
    firmware_name: str
    firmware_bytes: bytes
    version: str
    channel: str
    release_notes: str
    publish_immediately: bool
    clean_after: bool
    signing_key_path: str
    hardware_rev: str
    security_epoch: int
    min_bootloader: str
    stack_req: str


def repo_root() -> pathlib.Path:
    return pathlib.Path(__file__).resolve().parents[2]


def runtime_dir() -> pathlib.Path:
    path = repo_root() / "tools" / "release_dashboard" / "runtime"
    path.mkdir(parents=True, exist_ok=True)
    return path


def preferences_path() -> pathlib.Path:
    return runtime_dir() / "preferences.json"


def default_signing_key_path() -> str:
    return os.environ.get("APP_SIGNATURE_PRIVATE_KEY_PATH", "").strip()


def load_preferences() -> dict[str, Any]:
    path = preferences_path()
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def save_preferences(values: dict[str, Any]) -> None:
    preferences_path().write_text(json.dumps(values, indent=2), encoding="utf-8")


def run_command(command: list[str], cwd: pathlib.Path | None = None, check: bool = True) -> subprocess.CompletedProcess[str]:
    completed = subprocess.run(
        command,
        cwd=cwd or repo_root(),
        text=True,
        capture_output=True,
        check=False,
    )
    if check and completed.returncode != 0:
        message = completed.stderr.strip() or completed.stdout.strip() or "Command failed."
        raise DashboardError(message)
    return completed


def parse_git_remote(remote: str) -> str:
    value = remote.strip()
    if value.startswith("git@github.com:"):
        return value.split(":", 1)[1].removesuffix(".git")
    if value.startswith("https://github.com/"):
        return value.split("https://github.com/", 1)[1].removesuffix(".git")
    raise DashboardError(f"Unsupported origin remote: {value}")


def detected_repo_slug() -> str:
    remote = run_command(["git", "remote", "get-url", "origin"]).stdout.strip()
    return parse_git_remote(remote)


def current_branch() -> str:
    return run_command(["git", "branch", "--show-current"]).stdout.strip()


def parse_header_defaults() -> dict[str, str]:
    header_path = repo_root() / "firmware" / "eeg_test" / "version.h"
    values: dict[str, str] = {}
    for line in header_path.read_text(encoding="utf-8").splitlines():
        match = DEFINE_RE.match(line.strip())
        if not match:
            continue
        key, value = match.groups()
        values[key] = value.strip().strip('"')
    return values


def github_auth_status() -> dict[str, Any]:
    if shutil.which("gh") is None:
        return {
            "available": False,
            "authenticated": False,
            "summary": "`gh` CLI is not installed or not on PATH.",
            "account": "",
        }

    completed = run_command(["gh", "auth", "status"], check=False)
    combined = "\n".join(part for part in (completed.stdout.strip(), completed.stderr.strip()) if part).strip()
    account_match = re.search(r"account\s+([A-Za-z0-9_.-]+)", combined)
    return {
        "available": True,
        "authenticated": completed.returncode == 0,
        "summary": combined or "GitHub auth status unavailable.",
        "account": account_match.group(1) if account_match else "",
    }


def tool_status() -> dict[str, bool]:
    return {
        "git": shutil.which("git") is not None,
        "gh": shutil.which("gh") is not None,
        "python": shutil.which("python") is not None,
    }


def list_cleanup_targets() -> list[str]:
    candidates = [
        repo_root() / "build",
        repo_root() / "dist",
        repo_root() / "tmp",
        repo_root() / "out",
        repo_root() / "firmware.zip",
        repo_root() / "firmware.zip.sig",
        repo_root() / "firmware.zip.sha256",
        repo_root() / "release-notes.md",
        runtime_dir() / "releases",
        runtime_dir() / "uploads",
    ]
    return [str(path.relative_to(repo_root())) for path in candidates if path.exists()]


def dashboard_state() -> dict[str, Any]:
    defaults = parse_header_defaults()
    preferences = load_preferences()
    auth = github_auth_status()
    return {
        "repo_root": str(repo_root()),
        "repo_slug": detected_repo_slug(),
        "branch": current_branch(),
        "tools": tool_status(),
        "github": auth,
        "defaults": {
            "hardware_rev": defaults.get("HW_REV", "xiao-nrf52840-r1"),
            "security_epoch": defaults.get("SECURITY_EPOCH", "1"),
            "min_bootloader": defaults.get("MIN_BOOTLOADER", "0.9.0"),
            "stack_req": defaults.get("STACK_REQ", "s140_7.3.0"),
            "channel": defaults.get("FW_CHANNEL", "dev"),
            "signing_key_path": preferences.get("signing_key_path", default_signing_key_path()),
        },
        "cleanup_targets": list_cleanup_targets(),
    }


def compute_version_code(version: str) -> int:
    match = SEMVER_RE.fullmatch(version.strip())
    if not match:
        raise DashboardError("Version must be semantic version format like 0.1.8")
    major, minor, patch = (int(group) for group in match.groups())
    return major * 10000 + minor * 100 + patch


def normalize_channel(channel: str) -> str:
    value = channel.strip().lower()
    if value not in CHANNELS:
        raise DashboardError("Channel must be one of: dev, beta, stable.")
    return value


def ensure_private_key(path_text: str) -> pathlib.Path:
    path = pathlib.Path(path_text.strip()).expanduser()
    if not path.exists():
        raise DashboardError("Private signing key file was not found.")
    return path


def sign_zip(zip_path: pathlib.Path, key_path: pathlib.Path, output_path: pathlib.Path) -> None:
    private_key = serialization.load_pem_private_key(
        key_path.read_bytes(),
        password=None,
    )
    signature = private_key.sign(
        zip_path.read_bytes(),
        padding.PKCS1v15(),
        hashes.SHA256(),
    )
    output_path.write_bytes(signature)


def sha256_file(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while True:
            block = handle.read(8192)
            if not block:
                break
            digest.update(block)
    return digest.hexdigest()


def build_release_record(request: ReleaseRequest, tag: str, version_code: int) -> dict[str, Any]:
    return {
        "tag": tag,
        "version": request.version,
        "version_code": version_code,
        "channel": request.channel,
        "dfu_package_format": "legacy-crc",
        "security_epoch": request.security_epoch,
        "hw_allow": [request.hardware_rev],
        "min_bootloader": request.min_bootloader,
        "stack_req": request.stack_req,
    }


def save_uploaded_zip(request: ReleaseRequest, target_dir: pathlib.Path) -> pathlib.Path:
    target_dir.mkdir(parents=True, exist_ok=True)
    zip_path = target_dir / "firmware.zip"
    zip_path.write_bytes(request.firmware_bytes)
    return zip_path


def create_release_assets(request: ReleaseRequest, tag: str, version_code: int) -> tuple[pathlib.Path, pathlib.Path, pathlib.Path, pathlib.Path]:
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    output_dir = runtime_dir() / "releases" / f"{tag}-{timestamp}"
    zip_path = save_uploaded_zip(request, output_dir)

    sha_path = output_dir / "firmware.zip.sha256"
    sig_path = output_dir / "firmware.zip.sig"
    metadata_path = output_dir / "release-metadata.json"
    notes_path = output_dir / "release-notes.md"

    sha_path.write_text(f"{sha256_file(zip_path)}\n", encoding="utf-8")
    sign_zip(zip_path, ensure_private_key(request.signing_key_path), sig_path)
    metadata_path.write_text(
        json.dumps(build_release_record(request, tag, version_code), indent=2),
        encoding="utf-8",
    )
    notes_path.write_text(request.release_notes.strip() + "\n", encoding="utf-8")
    return zip_path, sha_path, sig_path, metadata_path


def ensure_release_tag_is_free(tag: str, repo_slug: str) -> None:
    existing = run_command(["gh", "release", "view", tag, "--repo", repo_slug], check=False)
    if existing.returncode == 0:
        raise DashboardError(f"The release tag {tag} already exists on {repo_slug}.")


def create_release(request: ReleaseRequest) -> dict[str, Any]:
    repo_slug = detected_repo_slug()
    auth = github_auth_status()
    if not auth["authenticated"]:
        raise DashboardError("GitHub CLI is not authenticated. Use the dashboard sign-in action first.")

    request.channel = normalize_channel(request.channel)
    version_code = compute_version_code(request.version)
    tag = f"v{request.version}-{request.channel}"
    ensure_release_tag_is_free(tag, repo_slug)

    zip_path, sha_path, sig_path, metadata_path = create_release_assets(request, tag, version_code)
    title = f"Firmware {request.version} ({request.channel})"

    command = [
        "gh",
        "release",
        "create",
        tag,
        str(zip_path),
        str(sha_path),
        str(sig_path),
        str(metadata_path),
        "--repo",
        repo_slug,
        "--title",
        title,
        "--notes-file",
        str(zip_path.parent / "release-notes.md"),
    ]
    if not request.publish_immediately:
        command.append("--draft")
    if request.channel != "stable":
        command.append("--prerelease")

    run_command(command)
    release_view = run_command(
        ["gh", "release", "view", tag, "--repo", repo_slug, "--json", "url,isDraft"],
    )
    release_info = json.loads(release_view.stdout)

    save_preferences(
        {
            "signing_key_path": request.signing_key_path,
            "hardware_rev": request.hardware_rev,
            "security_epoch": request.security_epoch,
            "min_bootloader": request.min_bootloader,
            "stack_req": request.stack_req,
            "channel": request.channel,
        }
    )

    cleaned = None
    if request.clean_after:
        cleaned = cleanup_artifacts(preserve_preferences=True)

    return {
        "tag": tag,
        "release_url": release_info["url"],
        "draft": release_info.get("isDraft", False),
        "cleaned": cleaned,
    }


def cleanup_artifacts(preserve_preferences: bool = True) -> dict[str, Any]:
    removed: list[str] = []
    targets = [
        repo_root() / "build",
        repo_root() / "dist",
        repo_root() / "tmp",
        repo_root() / "out",
        repo_root() / "firmware.zip",
        repo_root() / "firmware.zip.sig",
        repo_root() / "firmware.zip.sha256",
        repo_root() / "release-notes.md",
        runtime_dir() / "releases",
        runtime_dir() / "uploads",
    ]

    for target in targets:
        if not target.exists():
            continue
        if target.is_dir():
            shutil.rmtree(target)
        else:
            target.unlink()
        removed.append(str(target.relative_to(repo_root())))

    if not preserve_preferences and preferences_path().exists():
        preferences_path().unlink()
        removed.append(str(preferences_path().relative_to(repo_root())))

    return {"removed": removed}


def launch_github_login() -> str:
    if shutil.which("gh") is None:
        raise DashboardError("GitHub CLI is not installed.")

    if sys.platform.startswith("win"):
        subprocess.Popen(
            [
                "powershell",
                "-NoExit",
                "-Command",
                "gh auth login --web --git-protocol https",
            ]
        )
    else:
        subprocess.Popen(["gh", "auth", "login", "--web", "--git-protocol", "https"])
    return "Opened GitHub CLI login in a new terminal window."


def open_github_signup() -> str:
    webbrowser.open("https://github.com/signup", new=2)
    return "Opened GitHub sign up page in your browser."
