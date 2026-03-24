from __future__ import annotations

import argparse

from flask import Flask, jsonify, render_template, request

from service import (
    DashboardError,
    ReleaseRequest,
    cleanup_artifacts,
    create_release,
    dashboard_state,
    launch_github_login,
    open_github_signup,
)


app = Flask(
    __name__,
    template_folder="templates",
    static_folder="static",
)


def parse_bool(value: str | None) -> bool:
    return (value or "").strip().lower() in {"1", "true", "yes", "on"}


@app.get("/")
def index():
    return render_template("index.html")


@app.get("/api/status")
def api_status():
    return jsonify({"ok": True, "state": dashboard_state()})


@app.post("/api/github/login")
def api_github_login():
    try:
        return jsonify({"ok": True, "message": launch_github_login()})
    except DashboardError as error:
        return jsonify({"ok": False, "error": str(error)}), 400


@app.post("/api/github/signup")
def api_github_signup():
    return jsonify({"ok": True, "message": open_github_signup()})


@app.post("/api/cleanup")
def api_cleanup():
    try:
        result = cleanup_artifacts()
        return jsonify({"ok": True, "result": result})
    except DashboardError as error:
        return jsonify({"ok": False, "error": str(error)}), 400


@app.post("/api/releases")
def api_create_release():
    try:
        firmware_zip = request.files.get("firmware_zip")
        if firmware_zip is None or not firmware_zip.filename:
            raise DashboardError("Select a firmware ZIP file first.")

        release_request = ReleaseRequest(
            firmware_name=firmware_zip.filename,
            firmware_bytes=firmware_zip.read(),
            version=request.form.get("version", ""),
            channel=request.form.get("channel", "dev"),
            release_notes=request.form.get("release_notes", ""),
            publish_immediately=parse_bool(request.form.get("publish_immediately")),
            clean_after=parse_bool(request.form.get("clean_after")),
            signing_key_path=request.form.get("signing_key_path", ""),
            hardware_rev=request.form.get("hardware_rev", "xiao-nrf52840-r1"),
            security_epoch=int(request.form.get("security_epoch", "1")),
            min_bootloader=request.form.get("min_bootloader", "0.9.0"),
            stack_req=request.form.get("stack_req", "s140_7.3.0"),
        )
        result = create_release(release_request)
        return jsonify({"ok": True, "result": result})
    except (DashboardError, ValueError) as error:
        return jsonify({"ok": False, "error": str(error)}), 400


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", default=8123, type=int)
    args = parser.parse_args()

    app.run(host=args.host, port=args.port, debug=False)


if __name__ == "__main__":
    main()
