// Read-only camera bus diagnostic, run only after esp_camera_init() fails.
// The precompiled esp32-camera driver in core 3.3.12 is built with
// CONFIG_LOG_MAXIMUM_LEVEL=1, so its own "Detected camera at address / PID"
// debug output is unavailable. This reproduces the probe step by hand:
// power the sensor (PWDN low), drive XCLK, scan SCCB and read the ID registers.
#include <Arduino.h>
#include <Wire.h>
#include "board_config.h"

static bool sccbRead8(uint8_t addr, uint8_t reg, uint8_t &val) {
  Wire.beginTransmission(addr);
  Wire.write(reg);
  if (Wire.endTransmission(true) != 0) return false;  // SCCB has no repeated start
  if (Wire.requestFrom(addr, (uint8_t)1) != 1) return false;
  val = Wire.read();
  return true;
}

static bool sccbWrite8(uint8_t addr, uint8_t reg, uint8_t val) {
  Wire.beginTransmission(addr);
  Wire.write(reg);
  Wire.write(val);
  return Wire.endTransmission(true) == 0;
}

static bool sccbRead16(uint8_t addr, uint16_t reg, uint8_t &val) {
  Wire.beginTransmission(addr);
  Wire.write((uint8_t)(reg >> 8));
  Wire.write((uint8_t)(reg & 0xFF));
  if (Wire.endTransmission(true) != 0) return false;
  if (Wire.requestFrom(addr, (uint8_t)1) != 1) return false;
  val = Wire.read();
  return true;
}

static void printReg(const char *name, bool ok, uint8_t v) {
  if (ok) Serial.printf(" %s=0x%02X", name, v);
  else Serial.printf(" %s=NACK", name);
}

void diagnoseCameraBus() {
  Serial.printf("[diag] chip %s rev %u, %u cores, flash %u B @ %u Hz, PSRAM found=%d size=%u B\n",
                ESP.getChipModel(), (unsigned)ESP.getChipRevision(), (unsigned)ESP.getChipCores(),
                (unsigned)ESP.getFlashChipSize(), (unsigned)ESP.getFlashChipSpeed(),
                (int)psramFound(), (unsigned)ESP.getPsramSize());

#if PWDN_GPIO_NUM >= 0
  pinMode(PWDN_GPIO_NUM, OUTPUT);
  digitalWrite(PWDN_GPIO_NUM, HIGH);  // power down, then up: clean sensor start
  delay(10);
  digitalWrite(PWDN_GPIO_NUM, LOW);
  delay(20);
#endif
  bool clk = ledcAttach(XCLK_GPIO_NUM, 20000000, 1);
  if (clk) ledcWrite(XCLK_GPIO_NUM, 1);  // 50% duty at 1-bit resolution
  delay(20);
  Serial.printf("[diag] PWDN=GPIO%d low, XCLK GPIO%d 20 MHz %s, SCCB SDA=GPIO%d SCL=GPIO%d\n",
                PWDN_GPIO_NUM, XCLK_GPIO_NUM, clk ? "on" : "FAILED", SIOD_GPIO_NUM, SIOC_GPIO_NUM);

  Wire.begin(SIOD_GPIO_NUM, SIOC_GPIO_NUM, 100000);
  int found = 0;
  Serial.print("[diag] SCCB ACK at:");
  for (uint8_t a = 0x08; a <= 0x77; a++) {
    Wire.beginTransmission(a);
    if (Wire.endTransmission(true) == 0) { Serial.printf(" 0x%02X", a); found++; }
  }
  Serial.println(found ? "" : " none");

  uint8_t v;
  // OV2640 (0x30): select sensor bank, PIDH/PIDL expect 0x26/0x41|0x42, MIDH/MIDL expect 0x7F/0xA2.
  Serial.print("[diag] 0x30 OV2640:");
  printReg("bank", sccbWrite8(0x30, 0xFF, 0x01), 0x01);
  bool ok = sccbRead8(0x30, 0x0A, v); printReg("PIDH", ok, v);
  ok = sccbRead8(0x30, 0x0B, v); printReg("PIDL", ok, v);
  ok = sccbRead8(0x30, 0x1C, v); printReg("MIDH", ok, v);
  ok = sccbRead8(0x30, 0x1D, v); printReg("MIDL", ok, v);
  Serial.println();
  // OV3660/OV5640 (0x3C): 16-bit registers 0x300A/0x300B expect 0x36 0x60 / 0x56 0x40.
  Serial.print("[diag] 0x3C OV3660/OV5640:");
  ok = sccbRead16(0x3C, 0x300A, v); printReg("PIDH", ok, v);
  ok = sccbRead16(0x3C, 0x300B, v); printReg("PIDL", ok, v);
  Serial.println();

  Wire.end();
  if (clk) ledcDetach(XCLK_GPIO_NUM);
}
