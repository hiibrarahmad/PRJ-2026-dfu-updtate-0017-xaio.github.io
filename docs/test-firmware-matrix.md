# Test Firmware Matrix

These OTA demo builds all use `firmware/eeg_test/eeg_test.ino`, but the visible LED behavior changes with the version and release channel.

## Visible behavior

| Channel / Version | LED behavior |
| --- | --- |
| `dev` `0.1.0` | Solid red |
| `dev` `0.1.7` and newer dev builds | Solid green |
| `beta` | Blue blink |
| `stable` | RGB cycle: red -> green -> blue |

## Recommended test path

1. Start with `0.1.0-dev` on the board
2. Upgrade to `0.1.7-dev` and confirm green
3. Switch to `0.2.1-beta` and confirm blue blink
4. Switch to `1.0.1-stable` and confirm RGB cycle
5. Downgrade back to an older release from history if needed

## Suggested tags

- `v0.1.0-dev`
- `v0.1.7-dev`
- `v0.2.1-beta`
- `v1.0.1-stable`

## Notes

- The XIAO nRF52840 RGB LEDs are active-low in the Seeed nRF52 core, so `LOW` turns a color on.
- The app compares version code, channel, hardware revision, and package format before starting DFU.
