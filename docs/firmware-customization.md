# Firmware Customization

This document explains how to change the firmware behavior and publish it through the OTA pipeline.

## 1. Understand the two firmware files

`firmware/eeg_test/version.h`

- Holds the firmware identity fields.
- `FW_SEMVER` is the human version.
- `FW_VERSION_CODE` is the numeric version the app compares.
- `FW_CHANNEL` is `dev`, `beta`, or `stable`.
- `HW_REV` must match the allowed hardware in release metadata.
- `SECURITY_EPOCH` is the rollback floor.

`firmware/eeg_test/eeg_test.ino`

- Holds the actual board behavior.
- Exposes BLE version data.
- Starts the DFU service.
- Controls the RGB LED demo behavior.

## 2. Change device behavior

Open `firmware/eeg_test/eeg_test.ino`.

Typical changes:

- Change LED behavior in `setupLedPattern()`
- Change runtime behavior in `loop()`
- Add new version-dependent behavior in `resolvePattern()`
- Add sensors, BLE data, or product logic

Example idea:

- `dev` could be red
- `beta` could blink blue
- `stable` could cycle RGB
- a later `dev` release could switch to green

## 3. Keep the version header sane

The workflow overwrites these fields on release tags:

- `FW_SEMVER`
- `FW_VERSION_CODE`
- `FW_CHANNEL`

These fields are normally stable unless your hardware policy changes:

- `HW_REV`
- `SECURITY_EPOCH`
- `MIN_BOOTLOADER`
- `STACK_REQ`

## 4. Versioning rules

Use this format:

- `major.minor.patch`

Examples:

- `0.1.7`
- `0.2.1`
- `1.0.1`

`FW_VERSION_CODE` follows:

- `major * 10000 + minor * 100 + patch`

Examples:

- `0.1.7` -> `107`
- `0.2.1` -> `201`
- `1.0.1` -> `10001`

## 5. Channel strategy

Use `dev` when:

- you are testing quickly
- you want frequent internal builds

Use `beta` when:

- the behavior is ready for broader testing
- you want to validate upgrade and downgrade behavior

Use `stable` when:

- the firmware is approved for normal use
- release notes are finalized

## 6. Security epoch

Only increment `SECURITY_EPOCH` for a real security floor change.

Effects:

- higher epoch blocks older lower-epoch packages
- the mobile app will hard-block a lower epoch

Do not increment it for ordinary feature releases.

## 7. Build locally before tagging

```powershell
arduino-cli compile --fqbn Seeeduino:nrf52:xiaonRF52840 firmware/eeg_test
```

If the compile fails, fix the sketch before tagging.

## 8. Release notes matter

The mobile app shows release summaries and history from GitHub Releases.

Before publishing a draft release, edit the notes to include:

- what changed
- what users should expect
- any upgrade or downgrade caution

## 9. What the app needs from this repo

The Android app reads:

- `catalog.json`
- `releases.json`
- `firmware.zip`
- `firmware.zip.sig`

The app compares:

- hardware revision
- installed firmware version
- selected release version
- package format
- SHA-256
- ZIP signature

So every published release must keep those artifacts valid.
