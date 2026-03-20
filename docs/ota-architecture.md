# OTA Architecture

## Firmware layer

- Board: Seeed XIAO nRF52840
- Sketch path: `firmware/eeg_test`
- BLE exposes:
  - firmware version
  - channel
  - hardware revision
  - structured version payload
- `BLEDfu` switches the board into the bootloader for OTA updates

## Bootloader layer

- Bootloader family: XIAO OTAFIX legacy DFU bootloader
- Accepted package style: legacy CRC-based DFU ZIP
- Rejected package style: signed legacy init packet generated with `--key-file`

This is why the release workflow builds `legacy-crc` packages for the board.

## Release metadata layer

GitHub Pages serves:

- `catalog.json`
  - latest published release per channel
- `releases.json`
  - full release history per channel

Each record contains:

- version
- version code
- channel
- package format
- SHA-256
- signature URL
- release notes

## Android app layer

The app lives in a separate repository and reads this repo’s Pages metadata.

The app:

- reads installed version data over BLE
- fetches `catalog.json` and `releases.json`
- compares installed and available versions
- allows upgrade, reinstall, and controlled downgrade
- verifies SHA-256 and app-side ZIP signature
- starts Nordic DFU with XIAO-safe settings

## GitHub automation

- `build-firmware.yml`
  - runs on release tags
  - compiles firmware
  - builds `firmware.zip`
  - signs the ZIP for app verification
  - creates a draft release

- `publish-pages.yml`
  - runs when a release is published
  - updates `catalog.json`
  - updates `releases.json`
  - deploys the metadata to GitHub Pages

