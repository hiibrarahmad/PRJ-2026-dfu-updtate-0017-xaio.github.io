# Release Dashboard

This dashboard is a local web tool inside the firmware repo that helps you create GitHub Releases from an existing DFU ZIP.

It is useful when:

- you already have a finished `firmware.zip`
- you want to define the version and channel manually
- you want to write the update notes in one screen
- you want the tool to sign assets locally and upload them to GitHub

## What it does

- detects the current repo from `origin`
- checks GitHub CLI authentication
- offers a sign-in action for a new PC
- offers a GitHub sign-up shortcut
- uploads a local DFU ZIP
- computes `version_code` from semantic version
- creates `firmware.zip.sha256`
- creates `firmware.zip.sig` using your local private key
- creates `release-metadata.json`
- creates a GitHub Release with all required assets
- optionally publishes immediately
- optionally cleans temp artifacts after completion

## Requirements

- Python 3.11+
- `gh` CLI installed
- GitHub CLI authenticated to the repo account
- local private key file that matches `keys/app_signature_public.pem`

## Start the dashboard

From the repo root:

```powershell
.\run_release_dashboard.ps1
```

If PowerShell execution policy blocks `.ps1` files on your PC, use the Windows launcher instead:

```cmd
run_release_dashboard.cmd
```

Or run PowerShell once with a one-time bypass:

```powershell
powershell -ExecutionPolicy Bypass -File .\run_release_dashboard.ps1
```

Then open:

```text
http://127.0.0.1:8123
```

## New PC setup

On a new PC:

1. install Python
2. install GitHub CLI
3. clone this repo
4. place your private signing key somewhere safe on the PC
5. run `run_release_dashboard.cmd` or `.\run_release_dashboard.ps1`
6. click `Sign In To GitHub` in the dashboard
7. confirm the repo and auth status turn healthy

## Important note

The dashboard does not modify the contents of your `firmware.zip`.

You are responsible for making sure:

- the ZIP really matches the version you enter
- the ZIP is a valid legacy CRC DFU package for the XIAO bootloader
- the metadata you enter matches the actual firmware behavior
