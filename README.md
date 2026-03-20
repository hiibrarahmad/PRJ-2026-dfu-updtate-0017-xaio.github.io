# XAIO Firmware Release Repo

This repository is the dedicated firmware and OTA release source for the XIAO nRF52840 update flow.

What lives here:

- Arduino firmware in `firmware/eeg_test`
- GitHub Actions that build DFU ZIPs from tags
- GitHub Releases for `dev`, `beta`, and `stable`
- GitHub Pages metadata in `catalog/site`
- release documentation for versioning and publishing

The Android app lives in a separate repository:

- `PRJ-2026-dfu-ota-0016-xaio.github.io`

## Current OTA model

- Bootloader: XIAO OTAFIX legacy DFU bootloader
- DFU package type: legacy CRC package
- App-side trust check: RSA signature on the release ZIP
- Metadata endpoints:
  - `catalog.json` for latest per channel
  - `releases.json` for full history and downgrade selection

Important:

- This bootloader rejects signed legacy init packets from `adafruit-nrfutil --key-file`.
- The workflow intentionally builds unsigned legacy CRC DFU ZIPs for the board.
- The Android app still verifies the downloaded ZIP using the app signature before flashing.

## Release channels

- `dev`: fastest iteration and internal testing
- `beta`: broader testing before promotion
- `stable`: approved release for normal device use

Tag format controls the channel:

- `v0.1.7-dev`
- `v0.2.1-beta`
- `v1.0.1-stable`

## Main files

- `firmware/eeg_test/version.h`
- `firmware/eeg_test/eeg_test.ino`
- `.github/workflows/build-firmware.yml`
- `.github/workflows/publish-pages.yml`
- `scripts/write_version_header.py`
- `scripts/generate_release_metadata.py`
- `scripts/update_catalog.py`

## Step-by-step docs

- [Firmware Customization](docs/firmware-customization.md)
- [Release Guide](docs/release-guide.md)
- [OTA Architecture](docs/ota-architecture.md)
- [Test Firmware Matrix](docs/test-firmware-matrix.md)

## GitHub setup

Required GitHub Actions secret:

- `APP_SIGNATURE_PRIVATE_KEY_PEM_BASE64`

Public key files:

- `keys/app_signature_public.pem` is the public key that matches the ZIP-signing private key.
- The Android app repo must carry the matching public key in `app/src/main/assets/ota_app_signature_public.pem`.

## Local validation

Compile firmware:

```powershell
arduino-cli compile --fqbn Seeeduino:nrf52:xiaonRF52840 firmware/eeg_test
```

Inspect a generated ZIP:

```powershell
tar -tf firmware.zip
```

Check live metadata:

```powershell
curl https://hiibrarahmad.github.io/PRJ-2026-dfu-updtate-0017-xaio.github.io/catalog.json
curl https://hiibrarahmad.github.io/PRJ-2026-dfu-updtate-0017-xaio.github.io/releases.json
```
