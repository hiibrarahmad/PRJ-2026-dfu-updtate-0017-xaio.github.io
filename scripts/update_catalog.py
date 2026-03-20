#!/usr/bin/env python3
import argparse
import json
import os
import pathlib
from datetime import datetime, timezone
from typing import Any, Dict, List


def load_json(path: pathlib.Path, fallback: Any) -> Any:
    if not path.exists():
        return fallback
    return json.loads(path.read_text(encoding="utf-8"))


def summarize_release_notes(body: str) -> str:
    for line in body.splitlines():
        cleaned = line.strip().lstrip("-*").strip()
        if cleaned and not cleaned.startswith("#"):
            return cleaned[:240]
    return "Release published."


def sort_records(records: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    return sorted(
        records,
        key=lambda item: (int(item["version_code"]), item.get("published_at", "")),
        reverse=True,
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--site-dir", required=True)
    parser.add_argument("--release-metadata", required=True)
    parser.add_argument("--sha256-file", required=True)
    args = parser.parse_args()

    release_url = os.environ["RELEASE_URL"]
    release_body = os.environ.get("RELEASE_BODY", "").strip()
    published_at = os.environ.get("PUBLISHED_AT") or datetime.now(timezone.utc).isoformat()
    repository = os.environ["REPOSITORY"]
    tag_name = os.environ["RELEASE_TAG"]

    site_dir = pathlib.Path(args.site_dir)
    catalog_path = site_dir / "catalog.json"
    releases_path = site_dir / "releases.json"

    metadata = json.loads(pathlib.Path(args.release_metadata).read_text(encoding="utf-8"))
    sha256 = pathlib.Path(args.sha256_file).read_text(encoding="utf-8").strip()

    base_download = f"https://github.com/{repository}/releases/download/{tag_name}"
    record = {
        "tag": tag_name,
        "version": metadata["version"],
        "version_code": int(metadata["version_code"]),
        "channel": metadata["channel"],
        "dfu_package_format": metadata.get("dfu_package_format", "legacy-crc"),
        "security_epoch": int(metadata["security_epoch"]),
        "forced_update": False,
        "hw_allow": metadata["hw_allow"],
        "min_bootloader": metadata["min_bootloader"],
        "stack_req": metadata["stack_req"],
        "url": f"{base_download}/firmware.zip",
        "sha256": sha256,
        "sig_url": f"{base_download}/firmware.zip.sig",
        "release_notes_url": release_url,
        "release_notes_summary": summarize_release_notes(release_body),
        "release_notes_markdown": release_body,
        "published_at": published_at,
    }

    catalog = load_json(catalog_path, {"stable": None, "beta": None, "dev": None})
    history = load_json(releases_path, {"stable": [], "beta": [], "dev": []})

    channel = record["channel"]
    existing_channel_records = history.get(channel, [])
    deduped = [item for item in existing_channel_records if item.get("tag") != tag_name]
    deduped.append(record)
    history[channel] = sort_records(deduped)
    catalog[channel] = history[channel][0]

    catalog_path.write_text(json.dumps(catalog, indent=2), encoding="utf-8")
    releases_path.write_text(json.dumps(history, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
