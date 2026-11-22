// DurianCare — Wi-Fi + NVS + nút BOOT + portal (WiFiManager by tzapu 2.0.17). File dùng chung 2 sketch.
#include "dc_net.h"
#include "duriancare_config.h"
#include <WiFi.h>
#include <Preferences.h>
#include <WiFiManager.h>
#include <esp_task_wdt.h>
#include <esp_system.h>

namespace dc {

static NetSettings s_cfg;
static NetOptions s_opt;
static Preferences s_prefs;
static WiFiManager *s_wm = nullptr;
static WiFiManagerParameter *s_paramServer = nullptr;
static WiFiManagerParameter *s_paramClaim = nullptr;
static bool s_portal = false;
static bool s_portalSaved = false;
static unsigned long s_portalStartMs = 0;

static bool s_attemptActive = false;
static unsigned long s_attemptStartMs = 0;
static unsigned long s_nextAttemptMs = 0;
static unsigned long s_retryDelayMs = DC_WIFI_RETRY_MIN_MS;
static unsigned long s_lostSinceMs = 0;
static bool s_wasConnected = false;
static bool s_connectedEdge = false;
static uint8_t s_failedAttempts = 0;
static uint32_t s_reconnects = 0;

static bool timeReached(unsigned long now, unsigned long at) { return (long)(now - at) >= 0; }

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
    case ESP_RST_BROWNOUT: return "BROWNOUT";
    case ESP_RST_SDIO: return "SDIO";
    default: return "UNKNOWN";
  }
}

String apName() {
  uint8_t mac[6];
  WiFi.macAddress(mac);
  char suffix[5];
  snprintf(suffix, sizeof(suffix), "%02X%02X", mac[4], mac[5]);
  return String(DC_PORTAL_AP_PREFIX) + suffix;
}

static void loadSettings() {
  s_prefs.begin(DC_NVS_NAMESPACE, true);
  s_cfg.ssid = s_prefs.getString("ssid", "");
  s_cfg.pass = s_prefs.getString("pass", "");
  s_cfg.server = s_prefs.getString("server", "");
  s_cfg.claim = s_prefs.getString("claim", "");
  s_cfg.secret = s_prefs.getString("secret", "");
  s_prefs.end();
  // *_secrets.h chỉ còn là giá trị mặc định tùy chọn khi NVS trống.
  if (s_cfg.ssid.length() == 0 && s_opt.defaultSsid && s_opt.defaultSsid[0]) {
    s_cfg.ssid = s_opt.defaultSsid;
    s_cfg.pass = s_opt.defaultPass ? s_opt.defaultPass : "";
  }
  if (s_cfg.secret.length() == 0 && s_opt.defaultSecret && s_opt.defaultSecret[0]) s_cfg.secret = s_opt.defaultSecret;
}

static void putString(const char *key, const String &value) {
  s_prefs.begin(DC_NVS_NAMESPACE, false);
  s_prefs.putString(key, value);
  s_prefs.end();
}

static bool getPortalFlag() {
  s_prefs.begin(DC_NVS_NAMESPACE, true);
  bool flag = s_prefs.getBool("portal", false);
  s_prefs.end();
  return flag;
}

static void setPortalFlag(bool on) {
  s_prefs.begin(DC_NVS_NAMESPACE, false);
  s_prefs.putBool("portal", on);
  s_prefs.end();
}

static void eraseSettings() {
  s_prefs.begin(DC_NVS_NAMESPACE, false);
  s_prefs.clear();
  s_prefs.end();
}

void saveSecret(const String &secret) {
  s_cfg.secret = secret;
  putString("secret", secret);
}

void clearClaim() {
  s_cfg.claim = "";
  putString("claim", "");
}

const NetSettings &settings() { return s_cfg; }
bool portalActive() { return s_portal; }
uint32_t wifiReconnects() { return s_reconnects; }

// Giữ BOOT (GPIO0) liên tục DC_BOOT_BUTTON_HOLD_MS ngay khi khởi động -> xóa cấu hình.
static bool bootButtonHeld() {
  pinMode(DC_BOOT_BUTTON_GPIO, INPUT_PULLUP);
  delay(20);
  if (digitalRead(DC_BOOT_BUTTON_GPIO) != LOW) return false;
  Serial.println("[setup] Nút BOOT đang được giữ — giữ tiếp 5 giây để xóa cấu hình Wi-Fi...");
  unsigned long start = millis();
  while (millis() - start < DC_BOOT_BUTTON_HOLD_MS) {
    esp_task_wdt_reset();
    if (digitalRead(DC_BOOT_BUTTON_GPIO) != LOW) {
      Serial.println("[setup] Đã thả nút trước 5 giây — giữ nguyên cấu hình.");
      return false;
    }
    delay(50);
  }
  return true;
}

static void startPortal(const char *reason) {
  if (s_portal) return;
  Serial.printf("[portal] Mở portal cấu hình (%s): kết nối Wi-Fi \"%s\" rồi mở http://192.168.4.1\n",
                reason, apName().c_str());
  WiFi.disconnect(false);
  s_attemptActive = false;
  if (!s_wm) {
    s_wm = new WiFiManager();
    s_paramServer = new WiFiManagerParameter("server", "Server URL (tùy chọn, ví dụ https://api.duriancare.io.vn/iot)",
                                             s_cfg.server.c_str(), DC_MAX_URL_LEN);
    s_paramClaim = new WiFiManagerParameter("claim", "Mã nhận thiết bị (tùy chọn, 10 ký tự)", "", 16);
    s_wm->addParameter(s_paramServer);
    s_wm->addParameter(s_paramClaim);
    s_wm->setTitle("DurianCare");
    s_wm->setConfigPortalBlocking(false);
    s_wm->setBreakAfterConfig(true);  // lưu cả khi thử kết nối thất bại; vòng Wi-Fi của firmware sẽ thử lại
    s_wm->setConnectTimeout(15);
    s_wm->setWiFiAutoReconnect(false);
    s_wm->setCaptivePortalEnable(true);
    s_wm->setSaveConfigCallback([]() { s_portalSaved = true; });
    s_wm->setSaveParamsCallback([]() { s_portalSaved = true; });
  }
  s_wm->startConfigPortal(apName().c_str());
  s_portal = true;
  s_portalStartMs = millis();
}

static void stopPortal() {
  if (!s_portal) return;
  s_wm->stopConfigPortal();
  s_portal = false;
  WiFi.mode(WIFI_STA);
  s_nextAttemptMs = millis();
  s_failedAttempts = 0;
}

static String trimmedParam(WiFiManagerParameter *param) {
  String value = param ? String(param->getValue()) : String();
  value.trim();
  return value;
}

// Người dùng đã bấm Save trong portal: ghi NVS rồi khởi động lại để mọi thành phần bắt đầu sạch.
static void persistPortalResult() {
  String ssid = s_wm->getWiFiSSID(false);
  String pass = s_wm->getWiFiPass(false);
  String server = trimmedParam(s_paramServer);
  String claim = trimmedParam(s_paramClaim);
  s_prefs.begin(DC_NVS_NAMESPACE, false);
  if (ssid.length() > 0) {
    s_prefs.putString("ssid", ssid);
    s_prefs.putString("pass", pass);
  }
  s_prefs.putString("server", server);  // ô trống = xóa Server URL (dùng beacon/cloud)
  if (claim.length() > 0) s_prefs.putString("claim", claim);
  s_prefs.putBool("portal", false);
  s_prefs.end();
  Serial.printf("[portal] Đã lưu cấu hình: Wi-Fi %s, Server URL %s, mã nhận %s. Khởi động lại...\n",
                ssid.length() ? "có" : "giữ nguyên", server.length() ? server.c_str() : "(không)",
                claim.length() ? "có" : "(không)");
  delay(300);
  ESP.restart();
}

void requestPortal(const char *reason) {
  if (s_portal) return;
  if (s_opt.portalViaRestart) {
    Serial.printf("[portal] %s — khởi động lại vào chế độ portal.\n", reason);
    setPortalFlag(true);
    delay(200);
    ESP.restart();
  }
  startPortal(reason);
}

void netBegin(const NetOptions &options) {
  s_opt = options;
  if (bootButtonHeld()) {
    eraseSettings();
    Serial.println("[setup] Đã xóa cấu hình Wi-Fi/Server/mã nhận/token trong NVS.");
    setPortalFlag(true);
  }
  loadSettings();
  WiFi.persistent(false);
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.setAutoReconnect(false);  // một nơi duy nhất điều khiển kết nối: netLoop()
  s_nextAttemptMs = millis();
  Serial.printf("[setup] %s | Wi-Fi %s | Server URL %s | mã nhận %s | token %s\n", s_opt.deviceLabel,
                s_cfg.ssid.length() ? "đã cấu hình" : "CHƯA cấu hình",
                s_cfg.server.length() ? s_cfg.server.c_str() : "(không)",
                s_cfg.claim.length() ? "chờ kích hoạt" : "(không)",
                s_cfg.secret.length() ? "đã có (không hiển thị)" : "chưa có");
  bool flag = getPortalFlag();
  if (flag) setPortalFlag(false);
  if (s_cfg.ssid.length() == 0) startPortal("chưa có Wi-Fi");
  else if (flag) startPortal("yêu cầu từ lần chạy trước / nút BOOT");
}

bool netTakeConnectedEdge() {
  bool edge = s_connectedEdge;
  s_connectedEdge = false;
  return edge;
}

bool netLoop() {
  unsigned long now = millis();
  if (s_portal) {
    s_wm->process();
    if (s_portalSaved) persistPortalResult();
    if (now - s_portalStartMs >= DC_PORTAL_TIMEOUT_MS) {
      if (s_cfg.ssid.length() > 0) {
        Serial.println("[portal] Hết 10 phút không cấu hình — đóng portal, thử lại Wi-Fi đã lưu.");
        stopPortal();
      } else {
        s_portalStartMs = now;  // chưa có Wi-Fi nào: giữ portal
      }
    }
    return false;
  }

  if (WiFi.status() == WL_CONNECTED) {
    if (!s_wasConnected) {
      s_wasConnected = true;
      s_connectedEdge = true;
      s_attemptActive = false;
      s_lostSinceMs = 0;
      s_failedAttempts = 0;
      s_retryDelayMs = DC_WIFI_RETRY_MIN_MS;
      s_reconnects++;
      Serial.printf("[wifi] OK — IP %s | gateway %s | subnet %s | RSSI %d dBm | kênh %d\n",
                    WiFi.localIP().toString().c_str(), WiFi.gatewayIP().toString().c_str(),
                    WiFi.subnetMask().toString().c_str(), WiFi.RSSI(), WiFi.channel());
    }
    return true;
  }

  if (s_wasConnected) {
    Serial.println("[wifi] Mất kết nối — đang kết nối lại...");
    s_wasConnected = false;
    s_nextAttemptMs = now;
  }
  if (s_lostSinceMs == 0) s_lostSinceMs = now;
  if (now - s_lostSinceMs >= DC_WIFI_OFFLINE_RESTART_MS) {
    Serial.println("[wifi] Mất Wi-Fi quá 30 phút — khởi động lại module một lần.");
    delay(200);
    ESP.restart();
  }
  if (s_attemptActive) {
    if (now - s_attemptStartMs < DC_WIFI_ATTEMPT_TIMEOUT_MS) return false;  // đang kết nối: KHÔNG gọi begin() lại
    s_failedAttempts++;
    Serial.printf("[wifi] Lượt kết nối hết hạn (status %d, lỗi %u/%u) — thử lại sau %lus\n", (int)WiFi.status(),
                  s_failedAttempts, DC_WIFI_FAILS_BEFORE_PORTAL, s_retryDelayMs / 1000);
    WiFi.disconnect(false);
    s_attemptActive = false;
    s_nextAttemptMs = now + s_retryDelayMs;
    s_retryDelayMs = min(s_retryDelayMs * 2, (unsigned long)DC_WIFI_RETRY_MAX_MS);
    if (s_failedAttempts >= DC_WIFI_FAILS_BEFORE_PORTAL) {
      s_failedAttempts = 0;
      requestPortal("không vào được Wi-Fi đã lưu sau nhiều lượt");
    }
    return false;
  }
  if (timeReached(now, s_nextAttemptMs) && s_cfg.ssid.length() > 0) {
    WiFi.begin(s_cfg.ssid.c_str(), s_cfg.pass.c_str());
    s_attemptActive = true;
    s_attemptStartMs = now;
  }
  return false;
}

}  // namespace dc
