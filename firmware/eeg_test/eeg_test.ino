#include <bluefruit.h>
#include <Adafruit_TinyUSB.h>
#include <string.h>
#include "version.h"

namespace {
constexpr char DEVICE_NAME[] = "XAIO-EEG-Test";
constexpr char MODEL_NAME[] = "XIAO-nRF52840-Test";
constexpr char MANUFACTURER_NAME[] = "XAIO";
constexpr char VERSION_SERVICE_UUID[] = "12345678-1234-1234-1234-123456789abc";
constexpr char VERSION_CHAR_UUID[] = "12345678-1234-1234-1234-123456789abd";
constexpr size_t VERSION_JSON_CAPACITY = 160;

enum class DemoPattern : uint8_t {
  kLegacyDevRed,
  kUpdatedDevGreen,
  kBetaBlueBlink,
  kStableRgbCycle,
};
}

BLEDfu bleDfu;
BLEDis bleDis;
BLEService versionService(VERSION_SERVICE_UUID);
BLECharacteristic versionChar(VERSION_CHAR_UUID);

char versionJson[VERSION_JSON_CAPACITY];
DemoPattern activePattern = DemoPattern::kLegacyDevRed;

void writeLed(uint8_t pin, bool enabled) {
  digitalWrite(pin, enabled ? LOW : HIGH);
}

void showColor(bool red, bool green, bool blue) {
  writeLed(LED_RED, red);
  writeLed(LED_GREEN, green);
  writeLed(LED_BLUE, blue);
}

DemoPattern resolvePattern() {
  if (strcmp(FW_CHANNEL, "stable") == 0) {
    return DemoPattern::kStableRgbCycle;
  }
  if (strcmp(FW_CHANNEL, "beta") == 0) {
    return DemoPattern::kBetaBlueBlink;
  }
  if (FW_VERSION_CODE >= 105) {
    return DemoPattern::kUpdatedDevGreen;
  }
  return DemoPattern::kLegacyDevRed;
}

void updateVersionPayload() {
  snprintf(
      versionJson,
      sizeof(versionJson),
      "{\"fw\":\"%s\",\"code\":%d,\"epoch\":%d,\"channel\":\"%s\",\"hw\":\"%s\"}",
      FW_SEMVER,
      FW_VERSION_CODE,
      SECURITY_EPOCH,
      FW_CHANNEL,
      HW_REV);
}

void setupDeviceInformationService() {
  bleDis.setManufacturer(MANUFACTURER_NAME);
  bleDis.setModel(MODEL_NAME);
  bleDis.setFirmwareRev(FW_SEMVER);
  bleDis.setSoftwareRev(FW_CHANNEL);
  bleDis.setHardwareRev(HW_REV);
  bleDis.begin();
}

void setupVersionService() {
  updateVersionPayload();

  versionService.begin();
  versionChar.setProperties(CHR_PROPS_READ);
  versionChar.setPermission(SECMODE_OPEN, SECMODE_NO_ACCESS);
  versionChar.setMaxLen(sizeof(versionJson) - 1);
  versionChar.begin();
  versionChar.write(versionJson, strlen(versionJson));
}

void setupUsbConsole() {
  TinyUSBDevice.setManufacturerDescriptor(MANUFACTURER_NAME);
  TinyUSBDevice.setProductDescriptor(MODEL_NAME);
  TinyUSBDevice.begin();

  Serial.begin(115200);
  uint32_t startedAt = millis();
  while (!TinyUSBDevice.mounted() && millis() - startedAt < 1500) {
    delay(10);
  }

  Serial.println();
  Serial.print("Booting firmware ");
  Serial.print(FW_SEMVER);
  Serial.print(" [");
  Serial.print(FW_CHANNEL);
  Serial.print("] code=");
  Serial.print(FW_VERSION_CODE);
  Serial.print(" epoch=");
  Serial.println(SECURITY_EPOCH);
}

void setupLedPattern() {
  pinMode(LED_RED, OUTPUT);
  pinMode(LED_GREEN, OUTPUT);
  pinMode(LED_BLUE, OUTPUT);
  showColor(false, false, false);
  activePattern = resolvePattern();
  switch (activePattern) {
    case DemoPattern::kLegacyDevRed:
      showColor(true, false, false);
      break;
    case DemoPattern::kUpdatedDevGreen:
      showColor(false, true, false);
      break;
    case DemoPattern::kBetaBlueBlink:
      showColor(false, false, true);
      break;
    case DemoPattern::kStableRgbCycle:
      showColor(true, false, false);
      break;
  }
}

void startAdvertising() {
  Bluefruit.Advertising.stop();
  Bluefruit.ScanResponse.clearData();
  Bluefruit.Advertising.clearData();

  Bluefruit.Advertising.addFlags(BLE_GAP_ADV_FLAGS_LE_ONLY_GENERAL_DISC_MODE);
  Bluefruit.Advertising.addTxPower();
  Bluefruit.Advertising.addName();
  Bluefruit.Advertising.addService(bleDis);
  Bluefruit.Advertising.addService(versionService);

  Bluefruit.ScanResponse.addName();

  Bluefruit.Advertising.restartOnDisconnect(true);
  Bluefruit.Advertising.setInterval(32, 244);
  Bluefruit.Advertising.setFastTimeout(30);
  Bluefruit.Advertising.start(0);
}

void setup() {
  setupUsbConsole();

  Bluefruit.begin();
  Bluefruit.setName(DEVICE_NAME);
  Bluefruit.setTxPower(4);

  bleDfu.begin();
  setupDeviceInformationService();
  setupVersionService();
  setupLedPattern();
  startAdvertising();
}

void loop() {
  static uint32_t lastStepAt = 0;
  static bool betaLedOn = false;
  static uint8_t stableStep = 0;

  switch (activePattern) {
    case DemoPattern::kLegacyDevRed:
      showColor(true, false, false);
      break;

    case DemoPattern::kUpdatedDevGreen:
      showColor(false, true, false);
      break;

    case DemoPattern::kBetaBlueBlink:
      if (millis() - lastStepAt >= 350) {
        lastStepAt = millis();
        betaLedOn = !betaLedOn;
        showColor(false, false, betaLedOn);
      }
      break;

    case DemoPattern::kStableRgbCycle:
      if (millis() - lastStepAt >= 450) {
        lastStepAt = millis();
        stableStep = (stableStep + 1) % 3;
        if (stableStep == 0) {
          showColor(true, false, false);
        } else if (stableStep == 1) {
          showColor(false, true, false);
        } else {
          showColor(false, false, true);
        }
      }
      break;
  }
}
