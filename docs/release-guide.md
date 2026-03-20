# Release Guide

This is the step-by-step process for publishing a firmware update that the Android app can detect and flash.

## 1. Make your firmware changes

Edit:

- `firmware/eeg_test/eeg_test.ino`
- `firmware/eeg_test/version.h` only for non-tag-controlled fields

## 2. Compile locally

```powershell
arduino-cli compile --fqbn Seeeduino:nrf52:xiaonRF52840 firmware/eeg_test
```

## 3. Commit to `main`

```powershell
git add .
git commit -m "feat: describe the firmware change"
git push origin main
```

## 4. Create the release tag

Choose the channel in the tag name.

Examples:

```powershell
git tag v0.1.8-dev
git push origin v0.1.8-dev
```

```powershell
git tag v0.2.2-beta
git push origin v0.2.2-beta
```

```powershell
git tag v1.0.2-stable
git push origin v1.0.2-stable
```

## 5. What GitHub Actions does

`build-firmware.yml` will:

- parse the tag
- rewrite `version.h`
- compile the firmware
- build a legacy CRC DFU ZIP
- sign the ZIP for the Android app gate
- create a draft GitHub Release

## 6. Review the draft release

Open GitHub Releases and edit the draft.

Replace the placeholder notes with:

- summary of changes
- known limitations
- caution notes if any

## 7. Publish the release

Once you click publish:

- `publish-pages.yml` updates `catalog.json`
- it also updates `releases.json`
- GitHub Pages serves the new metadata

## 8. Verify the metadata

```powershell
curl https://hiibrarahmad.github.io/PRJ-2026-dfu-updtate-0017-xaio.github.io/catalog.json
curl https://hiibrarahmad.github.io/PRJ-2026-dfu-updtate-0017-xaio.github.io/releases.json
```

Confirm:

- the target channel now points to the new tag
- the new release appears in history
- `dfu_package_format` is `legacy-crc`

## 9. Verify from the app

In the Android app:

1. connect to the device
2. select the channel
3. confirm the new release appears
4. start DFU
5. reconnect after reboot and verify the installed version

## 10. Downgrade testing

Because `releases.json` stores history, the app can show older published builds.

To test downgrade:

1. install a newer `dev`, `beta`, or `stable` release
2. reconnect
3. choose an older release from the history list
4. confirm the warning and flash it

## 11. When not to reuse an old release

Do not replace a published tag just to change behavior.

Instead:

- create a new tag
- publish a new release

Good:

- `v0.1.7-dev` -> `v0.1.8-dev`

Avoid:

- silently rebuilding `v0.1.7-dev`
