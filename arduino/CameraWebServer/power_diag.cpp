// Power / RF diagnosis on the board itself (no multimeter needed for a relative comparison).
//   VDD33  : ESP32 PHY's own measurement of the 3.3 V rail (rom_phy_get_vdd33, needs the radio on).
//            The raw unit is not calibrated mV, so only compare readings of the same board.
//   RSSI   : averaged over the same window, to tell "weak Wi-Fi" from "camera interference".
// Printed as one line per phase, e.g. "[power] camera OFF: vdd33 min/avg ... rssi avg ...".
#include <Arduino.h>
#include <WiFi.h>
#include <esp_system.h>
#include <esp_task_wdt.h>
#include "esp_camera.h"

extern "C" int rom_phy_get_vdd33();

const char *resetReasonName(int reason) {
  switch (reason) {
    case ESP_RST_POWERON: return "POWERON";
    case ESP_RST_EXT: return "EXTERNAL";
    case ESP_RST_SW: return "SOFTWARE";
    case ESP_RST_PANIC: return "PANIC";
    case ESP_RST_INT_WDT: return "INT_WATCHDOG";
    case ESP_RST_TASK_WDT: return "TASK_WATCHDOG";
    case ESP_RST_WDT: return "WATCHDOG";
    case ESP_RST_DEEPSLEEP: return "DEEPSLEEP";
    case ESP_RST_BROWNOUT: return "BROWNOUT (nguồn sụt dưới ngưỡng)";
    case ESP_RST_SDIO: return "SDIO";
    default: return "UNKNOWN";
  }
}

// grabFrames: also pull JPEG frames during the window (camera at full load).
void powerRfProbe(const char *phase, bool grabFrames) {
  if (WiFi.getMode() == WIFI_MODE_NULL) return;  // VDD33 needs the PHY powered
  int vMin = INT32_MAX, vMax = INT32_MIN;
  long vSum = 0, rSum = 0;
  int n = 0, rN = 0, frames = 0, frameFails = 0;
  unsigned long start = millis();
  while (millis() - start < 1500) {
    if (grabFrames) {
      camera_fb_t *fb = esp_camera_fb_get();
      if (fb) { frames++; esp_camera_fb_return(fb); } else frameFails++;
    }
    int v = rom_phy_get_vdd33();
    vMin = min(vMin, v); vMax = max(vMax, v); vSum += v; n++;
    if (WiFi.status() == WL_CONNECTED) { rSum += WiFi.RSSI(); rN++; }
    esp_task_wdt_reset();
    delay(grabFrames ? 5 : 50);
  }
  Serial.printf("[power] %s: vdd33 min/avg/max %d/%ld/%d (raw, %d mẫu) | RSSI %s",
                phase, vMin, n ? vSum / n : 0, vMax, n, rN ? "" : "n/a (chưa kết nối)");
  if (rN) Serial.printf("%ld dBm", rSum / rN);
  if (grabFrames) Serial.printf(" | khung hình %d OK / %d lỗi", frames, frameFails);
  Serial.println();
}
