# Key Setup

The current XIAO OTAFIX bootloader path in this project uses legacy CRC-based DFU packages.
That means the bootloader does not accept the signed legacy init packet format generated with
`adafruit-nrfutil --key-file`.

For the working Phase 1 flow in this repository, use the Android app-side ZIP signature only.

## 1. Android App ZIP Signature Key

Purpose:

- Lets the Android app verify that the downloaded ZIP came from your release pipeline before it starts DFU.

Generate locally:

```bash
openssl genrsa -out app_signature_private.pem 2048
openssl rsa -in app_signature_private.pem -pubout -out app_signature_public.pem
```

Store on GitHub:

- Secret: `APP_SIGNATURE_PRIVATE_KEY_PEM_BASE64`

Distribute publicly:

- Copy the public key into `app/src/main/assets/ota_app_signature_public.pem`

Do not commit private keys.

## Future upgrade path

If you later move to a bootloader that enforces signed DFU packages, add a separate bootloader
signing key and switch the build pipeline back to signed DFU ZIP generation. That is not compatible
with the current XIAO OTAFIX legacy CRC bootloader flow.
