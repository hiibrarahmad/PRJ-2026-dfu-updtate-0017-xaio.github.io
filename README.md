# XAIO OTA DFU Phase 1

This repository scaffolds a professional Phase 1 OTA update system for a Seeed XIAO nRF52840 test device and an Android companion app.

What is included:

- `firmware/eeg_test`: Arduino firmware with BLE Device Information Service, buttonless DFU service, and a custom version characteristic.
- `app`: Android app scaffold that reads device version data, fetches OTA metadata from GitHub Pages, compares versions, shows release notes, logs upgrade and downgrade actions, and starts Nordic Secure DFU.
- `.github/workflows`: GitHub Actions for tag-driven firmware builds, draft releases, and GitHub Pages deployment of `catalog.json` and `releases.json`.
- `catalog/site`: GitHub Pages output for the app to read published firmware metadata.

Current XIAO bootloader note:

- The XIAO OTAFIX bootloader used in testing accepts legacy CRC-based DFU ZIPs.
- It rejects the signed legacy init packet format generated with `adafruit-nrfutil --key-file`.
- This repository therefore signs the release ZIP for the Android app gate, but generates an unsigned legacy DFU package for the bootloader.

## Repository Assumptions

The Android app is wired to a dedicated public firmware release repository.

- GitHub owner: `hiibrarahmad`
- Release repo: set with Gradle property `releaseRepo`
- Default release repo value: `PRJ-2026-dfu-updtate-0017-xaio.github.io`

By default the app will look for:

- `https://hiibrarahmad.github.io/PRJ-2026-dfu-updtate-0017-xaio.github.io/catalog.json`
- `https://hiibrarahmad.github.io/PRJ-2026-dfu-updtate-0017-xaio.github.io/releases.json`

If you want the GitHub Pages site at the root path `https://YOUR_GITHUB_OWNER.github.io/`, GitHub requires the repository name to be exactly `YOUR_GITHUB_OWNER.github.io`.

## Release Flow

1. Update the non-tag fields in [`firmware/eeg_test/version.h`](firmware/eeg_test/version.h) when hardware policy changes.
2. Tag a release, for example `v1.2.0-stable`.
3. GitHub Actions compiles the firmware, builds a legacy CRC DFU package that the XIAO bootloader accepts, signs the ZIP for the Android app gate, and creates a draft release.
4. A human reviews and publishes the release.
5. A second workflow regenerates `catalog.json` and `releases.json` and deploys them to GitHub Pages.
6. The Android app reads the latest metadata, shows `No update` / `Update available`, and also lets the user pick an older published build for downgrade with logging.

## Key Files

- [`firmware/eeg_test/version.h`](firmware/eeg_test/version.h)
- [`firmware/eeg_test/eeg_test.ino`](firmware/eeg_test/eeg_test.ino)
- [`.github/workflows/build-firmware.yml`](.github/workflows/build-firmware.yml)
- [`.github/workflows/publish-pages.yml`](.github/workflows/publish-pages.yml)
- [`scripts/update_catalog.py`](scripts/update_catalog.py)
- [`catalog/site/catalog.json`](catalog/site/catalog.json)
- [`catalog/site/releases.json`](catalog/site/releases.json)

## Secrets You Will Need On GitHub

- `APP_SIGNATURE_PRIVATE_KEY_PEM_BASE64`

The public key for the Android app-side ZIP signature must also replace the placeholder file in:

- `app/src/main/assets/ota_app_signature_public.pem`

## Local Validation

This workspace did not start with an Android SDK or Gradle installed, so the Android app is scaffolded but not fully built locally here.

Firmware can be validated with:

```powershell
arduino-cli compile --fqbn Seeeduino:nrf52:xiaonRF52840 firmware/eeg_test
```
