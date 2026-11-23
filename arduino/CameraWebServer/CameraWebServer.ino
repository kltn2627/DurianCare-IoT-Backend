// ======================================================================================================
// DurianCare — ESP32-CAM AI-Thinker (OV2640, PSRAM)
// ======================================================================================================
// CHANGELOG
// 3.0.0 (2026-11-19) — cloud-ready, camera ĐẨY ảnh. CHƯA KIỂM THỬ TRÊN PHẦN CỨNG (chỉ biên dịch).
//   + Tìm Backend dùng chung với trạm cảm biến (dc_endpoint.*): Server URL NVS -> beacon UDP 4210 v1
//     -> DURIANCARE_CLOUD_BASE_URL. http:// (LAN) và https:// (CA bundle của core, không setInsecure()).
//   + Portal WiFiManager "DurianCare-Setup-XXXX" (Wi-Fi, Server URL, mã nhận); giữ BOOT/IO0 5 s lúc khởi động
//     để xóa cấu hình. Portal chạy khi camera CHƯA khởi tạo (cổng 80 của portal và httpd không tranh nhau;
//     GPIO0 là XCLK nên nút chỉ được đọc trước esp_camera_init()).
//   + Kích hoạt bằng mã nhận -> khóa camera lưu NVS (X-Camera-Key). Khóa cũ trong camera_secrets.h vẫn dùng được.
//   + PUSH: poll GET /api/v1/public/cameras/<id>/commands mỗi 5 s (+ commandsPending trong heartbeat),
//     chụp và POST JPEG lên /frames?capturedAt&reason&commandId; lỗi chụp báo /commands/<id>/result.
//     Backend không còn phải gọi IP camera.
//   + Heartbeat gửi capabilities ["push","commands"] và diagnostics (vdd33, resetReason, heap/PSRAM, số lần
//     chụp/upload lỗi, RSSI, kênh, XCLK, khung hình, nguồn endpoint, số lần lệch giờ).
//   + Ảnh mặc định XGA, fb_count 1, CAMERA_GRAB_WHEN_EMPTY, XCLK 10 MHz (bản 2.x: UXGA, 2 buffer, GRAB_LATEST, 20 MHz).
//   + mDNS duriancare-cam-<id>.local (chỉ để cài đặt/debug trong LAN).
//   - BỎ: #error/static_assert khi thiếu camera_secrets.h hoặc khóa < 32 ký tự (file chỉ còn là mặc định tùy chọn).
//   - BỎ: Wi-Fi state machine, parser beacon riêng trong sketch (chuyển sang dc_net/dc_endpoint, cùng hành vi).
//   - BỎ: quét Wi-Fi sau mỗi lượt kết nối lỗi (diagnoseWiFiScan; cần SSID từ secrets) — thay bằng RSSI/kênh trong
//     log và heartbeat. BỎ in URL http://<ip>/capture lúc khởi động (FE không dùng IP camera).
//   = GIỮ: register/heartbeat 60 s với X-Camera-Key + announcedAt (epoch ms UTC, ±30 s), xử lý 400/401/403/404/409/503,
//     SNTP + header Date của Backend làm dự phòng, /capture và /stream trong LAN để debug, A/B probe nguồn lúc boot,
//     khởi động lại sau 2 lần init camera lỗi (không deinit/init lại lúc chạy), watchdog 60 s.
// 2.2.3 — bản trước (beacon-only, BE kéo ảnh qua /capture).
// ======================================================================================================
#include "esp_camera.h"
#include <WiFi.h>
#include <ESPmDNS.h>
#include <ArduinoJson.h>
#include <esp_task_wdt.h>
#include <esp_heap_caps.h>
#include "board_config.h"
#include "duriancare_config.h"
#include "camera_registry_config.h"
#include "dc_net.h"
#include "dc_endpoint.h"
#include "dc_http.h"

// Giá trị mặc định tùy chọn (Wi-Fi, khóa cũ). Không có file vẫn biên dịch được.
#if __has_include("camera_secrets.h")
#include "camera_secrets.h"
#endif
#ifndef WIFI_SSID
#define WIFI_SSID ""
#endif
#ifndef WIFI_PASSWORD
#define WIFI_PASSWORD ""
#endif
#ifndef CAMERA_DEVICE_KEY
#define CAMERA_DEVICE_KEY ""
#endif
static_assert(sizeof(CAMERA_ID) - 1 >= 1 && sizeof(CAMERA_ID) - 1 <= 150, "CAMERA_ID không hợp lệ");

void startCameraServer();
void setupLedFlash();
void diagnoseCameraBus();                               // camera_diag.cpp
void powerRfProbe(const char *phase, bool grabFrames);  // power_diag.cpp
extern "C" int rom_phy_get_vdd33();

// ==========================================
// Trạng thái
// ==========================================
static bool          s_registered        = false;
static IPAddress     s_registeredIP;
static unsigned long s_lastHeartbeatMs   = 0;
static unsigned long s_nextAttemptMs     = 0;
static unsigned long s_backoffMs         = RETRY_BACKOFF_MIN_MS;
static bool          s_timeRefresh       = false;
static unsigned long s_ntpStartMs        = 0;
static bool          s_timeValidLogged   = false;
static unsigned long s_nextActivationMs  = 0;
static unsigned long s_nextPollMs        = 0;
static unsigned long s_lastCommandMs     = 0;
static unsigned long s_serverPollMs      = COMMAND_POLL_MS;
static bool          s_mdnsStarted       = false;

static bool          s_cameraInitialized = false;
static bool          s_serverStarted     = false;
static bool          s_cameraReady       = false;
static const char   *s_cameraError       = nullptr;  // CAMERA_INIT_FAILED | CAMERA_STREAM_FAILED | nullptr
static uint8_t       s_cameraInitFailures = 0;
static unsigned long s_nextCameraInitMs  = 0;

// Diagnostics gửi trong heartbeat
static uint32_t s_captureOk = 0, s_captureFailures = 0, s_consecutiveCaptureFailures = 0;
static uint32_t s_uploadFailures = 0, s_timeRejects = 0;

static dc::HttpChannel s_http;      // register/heartbeat/activate/upload
static dc::HttpChannel s_poll;      // poll lệnh (keep-alive khi HTTPS)

enum CamState {
  CS_BOOT, CS_PORTAL, CS_WIFI_CONNECTING, CS_CAMERA_INITIALIZING, CS_CAMERA_READY, CS_REGISTERING_BACKEND, CS_ONLINE,
  CS_CAMERA_INIT_FAILED, CS_CAMERA_STREAM_FAILED, CS_BACKEND_UNREACHABLE, CS_REGISTRATION_FAILED
};
static const char *const CAM_STATE_NAMES[] = {
  "BOOT", "PORTAL", "WIFI_CONNECTING", "CAMERA_INITIALIZING", "CAMERA_READY", "REGISTERING_BACKEND", "ONLINE",
  "CAMERA_INIT_FAILED", "CAMERA_STREAM_FAILED", "BACKEND_UNREACHABLE", "REGISTRATION_FAILED"
};
static int s_state = CS_BOOT;
static void setState(int next, const char *detail = nullptr) {
  if (next == s_state) return;
  Serial.printf("[state] %s -> %s%s%s\n", CAM_STATE_NAMES[s_state], CAM_STATE_NAMES[next],
                detail ? " | " : "", detail ? detail : "");
  s_state = next;
}

static bool timeReached(unsigned long now, unsigned long at) { return (long)(now - at) >= 0; }
static bool isHttps() { return dc::endpointBase().startsWith("https://"); }
static const String &cameraKey() { return dc::settings().secret; }

// ==========================================
// Retry/backoff có giới hạn + jitter
// ==========================================
static void scheduleRetry(const char *reason, bool slow = false) {
  if (slow) s_backoffMs = RETRY_BACKOFF_MAX_MS;
  unsigned long jitter = esp_random() % 1000;
  s_nextAttemptMs = millis() + s_backoffMs + jitter;
  Serial.printf("[registry] %s — thử lại sau %lus\n", reason, (s_backoffMs + jitter) / 1000);
  s_backoffMs = min(s_backoffMs * 2, (unsigned long)RETRY_BACKOFF_MAX_MS);
}

static void resetBackoff() {
  s_backoffMs = RETRY_BACKOFF_MIN_MS;
  s_nextAttemptMs = 0;
}

static void printSanitized(const String &s, size_t maxLen = 160) {
  for (size_t i = 0; i < s.length() && i < maxLen; i++) {
    char c = s[i];
    Serial.print((c >= 32 && c < 127) ? c : '?');
  }
  if (s.length() > maxLen) Serial.print("...");
  Serial.println();
}

// ==========================================
// Thời gian (UTC): SNTP, dự phòng header Date của Backend (chỉ qua http://, HTTPS cần giờ đúng trước)
// ==========================================
static bool syncTimeFromBackend() {
  if (isHttps()) return false;
  dc::HttpRequest req;
  req.url = dc::endpointUrl(TIME_SOURCE_PATH);
  req.collectDate = true;
  req.connectTimeoutMs = HTTP_CONNECT_TIMEOUT_MS;
  req.responseTimeoutMs = HTTP_TIMEOUT_MS;
  req.maxBody = 512;
  dc::HttpResponse res = s_http.send(req);
  if (res.code != 200 || !dc::timeSetFromHttpDate(res.date)) {
    Serial.printf("[time] Header Date của Backend không dùng được (%s).\n", dc::errorText(res.code).c_str());
    return false;
  }
  Serial.printf("[time] OK — epoch %ld (từ HTTP Date của Backend)\n", (long)time(nullptr));
  return true;
}

static bool ensureTime() {
  if (s_timeRefresh) {  // registry từ chối timestamp (400/409): làm mới một lần
    s_timeRefresh = false;
    if (syncTimeFromBackend()) return true;
  }
  if (dc::timeValid()) {
    if (!s_timeValidLogged) Serial.printf("[time] OK — epoch %ld (SNTP)\n", (long)time(nullptr));
    s_timeValidLogged = true;
    return true;
  }
  if (millis() - s_ntpStartMs >= NTP_WAIT_MS && syncTimeFromBackend()) return true;
  return false;
}

// ==========================================
// Camera
// ==========================================
static bool initCamera() {
  camera_config_t config = {};
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = CAMERA_XCLK_HZ;
  config.frame_size = CAMERA_FRAME_SIZE;
  config.pixel_format = PIXFORMAT_JPEG;
  // Một buffer, chụp khi được yêu cầu: không có DMA chụp liên tục cạnh Wi-Fi (bản 2.x: 2 buffer + GRAB_LATEST).
  config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
  config.fb_location = CAMERA_FB_IN_PSRAM;
  config.jpeg_quality = CAMERA_JPEG_QUALITY;
  config.fb_count = CAMERA_FB_COUNT;
  if (!psramFound()) {
    config.frame_size = FRAMESIZE_SVGA;
    config.fb_location = CAMERA_FB_IN_DRAM;
  }
  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("[camera] Khởi tạo thất bại, lỗi 0x%x\n", err);
    return false;
  }
  sensor_t *s = esp_camera_sensor_get();
  if (s && s->id.PID == OV3660_PID) {
    s->set_vflip(s, 1);
    s->set_brightness(s, 1);
    s->set_saturation(s, -2);
  }
  return true;
}

static bool validJpeg(const camera_fb_t *fb) {
  return fb && fb->format == PIXFORMAT_JPEG && fb->len >= 4 && fb->buf[0] == 0xFF && fb->buf[1] == 0xD8 &&
         fb->buf[fb->len - 2] == 0xFF && fb->buf[fb->len - 1] == 0xD9;
}

static bool cameraSelfTest() {
  camera_fb_t *fb = esp_camera_fb_get();
  bool ok = validJpeg(fb);
  if (fb) esp_camera_fb_return(fb);
  return ok;
}

// Khởi tạo camera không làm treo firmware; lỗi thì thử lại có giãn cách, 2 lần liên tiếp -> khởi động lại.
static void maintainCamera(bool selfTest) {
  unsigned long now = millis();
  if (!s_cameraInitialized) {
    if (!timeReached(now, s_nextCameraInitMs)) return;
    setState(CS_CAMERA_INITIALIZING);
    if (!initCamera()) {
      s_cameraInitFailures++;
      s_cameraReady = false;
      s_cameraError = "CAMERA_INIT_FAILED";
      setState(CS_CAMERA_INIT_FAILED);
      diagnoseCameraBus();
      if (s_cameraInitFailures >= CAMERA_INIT_MAX_FAILURES) {
        Serial.println("[camera] Khởi tạo lỗi nhiều lần liên tiếp — khởi động lại module.");
        delay(200);
        ESP.restart();
      }
      s_nextCameraInitMs = now + CAMERA_INIT_RETRY_MS;
      Serial.printf("[camera] Thử khởi tạo lại sau %lus (lỗi %u/%u).\n", CAMERA_INIT_RETRY_MS / 1000,
                    s_cameraInitFailures, CAMERA_INIT_MAX_FAILURES);
      return;
    }
    s_cameraInitialized = true;
    s_cameraInitFailures = 0;
    if (!s_serverStarted) {
      setupLedFlash();
      startCameraServer();  // /capture, /stream: chỉ để debug trong LAN
      s_serverStarted = true;
    }
    selfTest = true;
  }
  if (!selfTest) return;
  bool ok = cameraSelfTest();
  if (ok != s_cameraReady || s_state == CS_CAMERA_INITIALIZING) {
    Serial.printf("[camera] Chụp thử: %s\n", ok ? "OK" : "LỖI — không nhận được khung hình");
  }
  s_cameraReady = ok;
  s_cameraError = ok ? nullptr : "CAMERA_STREAM_FAILED";
  if (!ok) setState(CS_CAMERA_STREAM_FAILED);
  else if (s_state == CS_CAMERA_INITIALIZING || s_state == CS_CAMERA_STREAM_FAILED) setState(CS_CAMERA_READY);
}

// ==========================================
// Diagnostics (heartbeat)
// ==========================================
static void fillDiagnostics(JsonObject d) {
  d["vdd33"] = rom_phy_get_vdd33();
  d["resetReason"] = dc::resetReasonName((int)esp_reset_reason());
  d["uptimeSec"] = (uint32_t)(millis() / 1000);
  d["freeHeap"] = (uint32_t)ESP.getFreeHeap();
  d["minFreeHeap"] = (uint32_t)ESP.getMinFreeHeap();
  if (psramFound()) {
    d["freePsram"] = (uint32_t)ESP.getFreePsram();
    d["largestFreePsram"] = (uint32_t)heap_caps_get_largest_free_block(MALLOC_CAP_SPIRAM);
  }
  d["captureOk"] = s_captureOk;
  d["captureFailures"] = s_captureFailures;
  d["consecutiveCaptureFailures"] = s_consecutiveCaptureFailures;
  d["uploadFailures"] = s_uploadFailures;
  d["timeRejects"] = s_timeRejects;
  d["wifiRssi"] = WiFi.RSSI();
  d["wifiChannel"] = WiFi.channel();
  d["wifiReconnects"] = dc::wifiReconnects();
  d["xclkMhz"] = CAMERA_XCLK_HZ / 1000000;
  d["frameSize"] = CAMERA_FRAME_SIZE_NAME;
  d["endpointSource"] = dc::endpointSourceName();
}

// ==========================================
// Kích hoạt bằng mã nhận -> khóa camera (NVS)
// ==========================================
static void activateIfNeeded() {
  const dc::NetSettings &cfg = dc::settings();
  if (cfg.claim.length() == 0 || !timeReached(millis(), s_nextActivationMs)) return;
  JsonDocument doc;
  doc["device_id"] = CAMERA_ID;
  doc["claim_code"] = cfg.claim;
  doc["firmware_version"] = FIRMWARE_VERSION;
  String body;
  serializeJson(doc, body);
  dc::HttpRequest req;
  req.method = "POST";
  req.url = dc::endpointUrl(DC_PATH_ACTIVATE);
  req.contentType = "application/json";
  req.body = (const uint8_t *)body.c_str();
  req.bodyLen = body.length();
  req.connectTimeoutMs = HTTP_CONNECT_TIMEOUT_MS;
  req.responseTimeoutMs = HTTP_TIMEOUT_MS;
  dc::HttpResponse res = s_http.send(req);
  dc::endpointReport(res.code);
  if (res.code == 200) {
    JsonDocument rd;
    const char *key = nullptr;
    if (!deserializeJson(rd, res.body)) key = rd["camera_key"] | (const char *)nullptr;
    if (key && strlen(key) >= 16) {
      dc::saveSecret(String(key));
      dc::clearClaim();
      s_registered = false;
      resetBackoff();
      Serial.println("[activate] Kích hoạt thành công — đã lưu khóa camera (không hiển thị).");
      return;
    }
  } else if (res.code == 400 || res.code == 409 || res.code == 410 || res.code == 429) {
    Serial.printf("[activate] Mã nhận không dùng được (HTTP %d) — đã bỏ mã; xin mã mới và nhập lại trong portal.\n", res.code);
    dc::clearClaim();
    return;
  }
  Serial.printf("[activate] Chưa kích hoạt được (%s) — thử lại sau 60 s.\n", dc::errorText(res.code).c_str());
  s_nextActivationMs = millis() + 60000UL;
}

// ==========================================
// Registry: POST register / heartbeat (X-Camera-Key, không log)
// ==========================================
static int postRegistry(const char *path) {
  JsonDocument doc;
  doc["cameraId"]        = CAMERA_ID;
  doc["ipAddress"]       = WiFi.localIP().toString();  // chỉ để cài đặt/debug; Backend không gọi địa chỉ này
  doc["port"]            = CAMERA_HTTP_PORT;
  doc["protocol"]        = "http";
  doc["announcedAt"]     = dc::epochMillis();
  doc["firmwareVersion"] = FIRMWARE_VERSION;
  doc["cameraReady"]     = s_cameraReady;
  if (s_cameraError) doc["lastError"] = s_cameraError;
  doc["capturePath"]     = "/capture";
  doc["macAddress"]      = WiFi.macAddress();
  doc["wifiRssi"]        = WiFi.RSSI();
  JsonArray caps = doc["capabilities"].to<JsonArray>();
  caps.add("push");
  caps.add("commands");
  caps.add("live");
  fillDiagnostics(doc["diagnostics"].to<JsonObject>());
  String body;
  serializeJson(doc, body);

  dc::HttpHeader headers[] = { { DC_HEADER_CAMERA_KEY, cameraKey().c_str() } };
  dc::HttpRequest req;
  req.method = "POST";
  req.url = dc::endpointUrl(path);
  req.contentType = "application/json";
  req.body = (const uint8_t *)body.c_str();
  req.bodyLen = body.length();
  req.headers = headers;
  req.headerCount = 1;
  req.connectTimeoutMs = HTTP_CONNECT_TIMEOUT_MS;
  req.responseTimeoutMs = HTTP_TIMEOUT_MS;
  req.maxBody = REGISTRY_MAX_RESPONSE_BYTES;
  Serial.printf("[registry] POST %s (camera %s, %s)\n", req.url.c_str(), CAMERA_ID, dc::endpointSourceName());
  dc::HttpResponse res = s_http.send(req);
  dc::endpointReport(res.code);
  if (res.code <= 0) {
    Serial.printf("[registry] Lỗi kết nối (%s) sau %lums.\n", dc::errorText(res.code).c_str(), res.elapsedMs);
    return res.code;
  }
  Serial.printf("[registry] HTTP %d (%lums)\n", res.code, res.elapsedMs);
  if (res.code == 200) {
    JsonDocument rd;
    const char *status = nullptr, *cameraId = nullptr;
    if (res.body.length() > 0 && !deserializeJson(rd, res.body)) {
      status = rd["status"] | (const char *)nullptr;
      cameraId = rd["device"]["cameraId"] | (const char *)nullptr;
      long pending = rd["commandsPending"] | 0L;
      long pollAfter = rd["pollAfterMs"] | 0L;
      if (pollAfter >= 1000 && pollAfter <= 60000) s_serverPollMs = (unsigned long)pollAfter;
      if (pending > 0) s_nextPollMs = millis();  // có lệnh đang chờ: poll ngay
    }
    bool valid = status && cameraId && strcmp(cameraId, CAMERA_ID) == 0 &&
                 (strcmp(status, "ok") == 0 || strcmp(status, "registered") == 0);
    if (!valid) {
      Serial.println("[registry] HTTP 200 nhưng phản hồi không hợp lệ — không coi là thành công.");
      return REGISTRY_INVALID_RESPONSE;
    }
  } else if (res.body.length() > 0) {
    Serial.print("[registry] Phản hồi: ");
    printSanitized(res.body);
  }
  return res.code;
}

static void handleRegistryResult(int code, bool wasHeartbeat) {
  switch (code) {
    case 200:
    case 201:
      Serial.println(wasHeartbeat ? "[registry] Heartbeat OK." : "[registry] Đăng ký thành công.");
      setState(s_cameraReady ? CS_ONLINE : (s_cameraInitialized ? CS_CAMERA_STREAM_FAILED : CS_CAMERA_INIT_FAILED),
               s_cameraReady ? nullptr : "đã đăng ký nhưng camera chưa sẵn sàng (Backend: DEGRADED)");
      s_registered = true;
      s_registeredIP = WiFi.localIP();
      s_lastHeartbeatMs = millis();
      resetBackoff();
      return;
    case 400:
    case 409:
      s_registered = false;
      s_timeRejects++;
      s_timeRefresh = true;
      scheduleRetry(code == 400 ? "Backend từ chối dữ liệu (400) — đồng bộ lại thời gian"
                                : "Thông báo cũ hơn bản ghi hiện tại (409) — đồng bộ lại thời gian");
      return;
    case 401:
      s_registered = false;
      setState(CS_REGISTRATION_FAILED);
      scheduleRetry("Sai/thiếu khóa camera (401) — kích hoạt bằng mã nhận trong portal hoặc kiểm tra CAMERA_DEVICE_BINDINGS", true);
      return;
    case 403:
      s_registered = false;
      setState(CS_REGISTRATION_FAILED);
      scheduleRetry("Camera chưa được gán đúng vườn trên server (403)", true);
      return;
    case 404:
      s_registered = false;
      setState(CS_REGISTRATION_FAILED);
      scheduleRetry("Camera chưa được cấp phát trên server (404)", true);
      return;
    case 503:
      s_registered = false;
      scheduleRetry("Backend tạm lỗi (503)");
      return;
    default:
      if (code < 0 && code != REGISTRY_INVALID_RESPONSE && wasHeartbeat) {
        scheduleRetry("Heartbeat chưa xác nhận (lỗi mạng tạm thời) — giữ đăng ký, gửi lại");
        return;
      }
      s_registered = false;
      setState(code < 0 ? CS_BACKEND_UNREACHABLE : CS_REGISTRATION_FAILED);
      scheduleRetry(code < 0 ? "Không kết nối được Backend" : "Phản hồi không mong đợi");
  }
}

// ==========================================
// PUSH: lệnh chụp + đẩy ảnh
// ==========================================
static String camerasUrl(const char *suffix) {
  return dc::endpointUrl(DC_PATH_CAMERAS) + CAMERA_ID + suffix;
}

// ==========================================
// Live view: phát khung liên tục khi có người xem (chỉ trong bộ nhớ Backend, không lưu lịch sử)
// ==========================================
static dc::HttpChannel s_live;              // kênh riêng, keep-alive: không bắt tay TCP/TLS cho từng khung
static unsigned long s_liveUntilMs = 0;     // 0 = tắt
static unsigned long s_liveNextFrameMs = 0;
static unsigned long s_liveFrames = 0;
static unsigned long s_liveWindowStartMs = 0;
static unsigned long s_liveWindowFrames = 0;

static bool liveActive() { return s_liveUntilMs != 0 && !timeReached(millis(), s_liveUntilMs); }

static void stopLive(const char *why) {
  if (s_liveUntilMs == 0) return;
  s_liveUntilMs = 0;
  s_live.close();
  sensor_t *sensor = esp_camera_sensor_get();
  if (sensor && sensor->status.framesize != CAMERA_FRAME_SIZE) sensor->set_framesize(sensor, CAMERA_FRAME_SIZE);
  Serial.printf("[live] Dừng phát (%s) — đã gửi %lu khung.\n", why, s_liveFrames);
}

// remainingMs từ Backend: > 0 mở/gia hạn, 0 dừng.
static void setLiveWindow(long remainingMs) {
  if (remainingMs <= 0) { stopLive("không còn người xem"); return; }
  unsigned long window = min((unsigned long)remainingMs, (unsigned long)LIVE_MAX_WINDOW_MS);
  bool wasActive = liveActive();
  s_liveUntilMs = millis() + window;
  if (s_liveUntilMs == 0) s_liveUntilMs = 1;
  if (!wasActive) {
    s_liveFrames = 0;
    s_liveNextFrameMs = 0;
    s_liveWindowStartMs = millis();
    s_liveWindowFrames = 0;
    Serial.printf("[live] Bắt đầu phát trực tiếp (%lus).\n", window / 1000);
  }
}

static void pushLiveFrame() {
  unsigned long now = millis();
  if (!timeReached(now, s_liveNextFrameMs)) return;
  s_liveNextFrameMs = now + LIVE_FRAME_INTERVAL_MS;
  if (!s_cameraInitialized) { stopLive("camera chưa sẵn sàng"); return; }
  sensor_t *sensor = esp_camera_sensor_get();
  if (sensor && sensor->status.framesize != LIVE_FRAME_SIZE) {
    sensor->set_framesize(sensor, LIVE_FRAME_SIZE);  // lệnh chụp ảnh lớn có thể đã đổi kích thước
    camera_fb_t *old = esp_camera_fb_get();          // khung đầu sau khi đổi kích thước: bỏ
    if (old) esp_camera_fb_return(old);
  }
  camera_fb_t *fb = esp_camera_fb_get();
  if (!validJpeg(fb)) {
    if (fb) esp_camera_fb_return(fb);
    s_captureFailures++;
    return;
  }
  dc::HttpHeader headers[] = { { DC_HEADER_CAMERA_KEY, cameraKey().c_str() } };
  dc::HttpRequest req;
  req.method = "POST";
  req.url = camerasUrl("/live-frame");
  req.contentType = "image/jpeg";
  req.body = fb->buf;
  req.bodyLen = fb->len;
  req.headers = headers;
  req.headerCount = 1;
  req.connectTimeoutMs = LIVE_HTTP_TIMEOUT_MS;
  req.responseTimeoutMs = LIVE_HTTP_TIMEOUT_MS;
  req.maxBody = 256;
  req.keepAlive = true;
  esp_task_wdt_reset();
  dc::HttpResponse res = s_live.send(req);
  esp_camera_fb_return(fb);
  if (res.code == 200) {
    s_liveFrames++;
    s_liveWindowFrames++;
    JsonDocument doc;
    long liveMs = (!deserializeJson(doc, res.body)) ? (doc["liveMs"] | 0L) : 0L;
    unsigned long span = millis() - s_liveWindowStartMs;
    if (span >= 10000) {
      Serial.printf("[live] %.1f khung/giây | khung cuối %u byte, %lums | RSSI %d dBm\n",
                    s_liveWindowFrames * 1000.0f / span, (unsigned)req.bodyLen, res.elapsedMs, WiFi.RSSI());
      s_liveWindowStartMs = millis();
      s_liveWindowFrames = 0;
    }
    setLiveWindow(liveMs);
    return;
  }
  if (res.code == 401 || res.code == 403 || res.code == 404) {
    stopLive("Backend từ chối");
    s_registered = false;  // đăng ký lại sẽ báo lý do cụ thể
    return;
  }
  dc::endpointReport(res.code);
  if (res.code <= 0) s_liveNextFrameMs = millis() + 500;  // lỗi mạng: nghỉ ngắn rồi thử khung mới
}

static void reportCommandFailure(const char *commandId, const char *error) {
  if (!commandId || !commandId[0]) return;
  JsonDocument doc;
  doc["status"] = "FAILED";
  doc["error"] = error;
  String body;
  serializeJson(doc, body);
  dc::HttpHeader headers[] = { { DC_HEADER_CAMERA_KEY, cameraKey().c_str() } };
  dc::HttpRequest req;
  req.method = "POST";
  req.url = camerasUrl("/commands/") + commandId + "/result";
  req.contentType = "application/json";
  req.body = (const uint8_t *)body.c_str();
  req.bodyLen = body.length();
  req.headers = headers;
  req.headerCount = 1;
  req.connectTimeoutMs = HTTP_CONNECT_TIMEOUT_MS;
  req.responseTimeoutMs = HTTP_TIMEOUT_MS;
  dc::HttpResponse res = s_http.send(req);
  Serial.printf("[command] Báo lỗi %s cho lệnh %s: %s\n", error, commandId, dc::errorText(res.code).c_str());
}

// Chụp một ảnh và POST lên Backend. reason: ON_DEMAND | SCHEDULED.
static void captureAndUpload(const char *commandId, const char *reason, bool preview) {
  if (!s_cameraInitialized) {
    reportCommandFailure(commandId, "CAMERA_INIT_FAILED");
    return;
  }
  sensor_t *sensor = esp_camera_sensor_get();
  bool resized = preview && sensor && sensor->set_framesize(sensor, CAMERA_PREVIEW_SIZE) == 0;
  // Khung đầu sau khi đổi kích thước/chờ lâu có thể là khung cũ: bỏ.
  for (int i = 0; i < CAMERA_DISCARD_FRAMES; i++) {
    camera_fb_t *old = esp_camera_fb_get();
    if (old) esp_camera_fb_return(old);
    esp_task_wdt_reset();
  }
  camera_fb_t *fb = esp_camera_fb_get();
  int64_t capturedAt = dc::epochMillis();
  if (!validJpeg(fb)) {
    if (fb) esp_camera_fb_return(fb);
    if (resized) sensor->set_framesize(sensor, CAMERA_FRAME_SIZE);
    s_captureFailures++;
    s_consecutiveCaptureFailures++;
    s_cameraReady = false;
    s_cameraError = "CAMERA_STREAM_FAILED";
    Serial.println("[command] Chụp lỗi — không có khung JPEG hợp lệ.");
    reportCommandFailure(commandId, "CAMERA_CAPTURE_FAILED");
    return;
  }
  s_captureOk++;
  s_consecutiveCaptureFailures = 0;
  s_cameraReady = true;
  s_cameraError = nullptr;

  String url = camerasUrl("/frames") + "?capturedAt=" + String((long long)capturedAt) + "&reason=" + reason;
  if (commandId && commandId[0]) url += String("&commandId=") + commandId;
  dc::HttpHeader headers[] = { { DC_HEADER_CAMERA_KEY, cameraKey().c_str() } };
  dc::HttpRequest req;
  req.method = "POST";
  req.url = url;
  req.contentType = "image/jpeg";
  req.body = fb->buf;
  req.bodyLen = fb->len;
  req.headers = headers;
  req.headerCount = 1;
  req.connectTimeoutMs = HTTP_CONNECT_TIMEOUT_MS;
  req.responseTimeoutMs = UPLOAD_TIMEOUT_MS;
  dc::HttpResponse res;
  for (int attempt = 0; attempt <= FRAME_UPLOAD_RETRIES; attempt++) {
    esp_task_wdt_reset();
    res = s_http.send(req);
    if (res.code > 0) break;  // chỉ thử lại lỗi mạng
  }
  size_t bytes = fb->len;
  esp_camera_fb_return(fb);
  if (resized) sensor->set_framesize(sensor, CAMERA_FRAME_SIZE);
  dc::endpointReport(res.code);
  if (res.code == 201) {
    Serial.printf("[command] Đã đẩy ảnh %u byte (%s%s) trong %lums.\n", (unsigned)bytes, reason, preview ? ", preview" : "",
                  res.elapsedMs);
    return;
  }
  s_uploadFailures++;
  Serial.printf("[command] Đẩy ảnh thất bại (%s)\n", dc::errorText(res.code).c_str());
  if (res.body.length()) printSanitized(res.body);
  if (res.code != 409) reportCommandFailure(commandId, res.code == 413 ? "FRAME_TOO_LARGE" : "UPLOAD_FAILED");
}

static void pollCommands() {
  unsigned long now = millis();
  if (!timeReached(now, s_nextPollMs)) return;
  bool idle = isHttps() && now - s_lastCommandMs > COMMAND_IDLE_AFTER_MS;
  s_nextPollMs = now + (idle ? COMMAND_POLL_IDLE_MS : s_serverPollMs);
  dc::HttpHeader headers[] = { { DC_HEADER_CAMERA_KEY, cameraKey().c_str() } };
  dc::HttpRequest req;
  req.url = camerasUrl("/commands");
  req.headers = headers;
  req.headerCount = 1;
  req.connectTimeoutMs = HTTP_CONNECT_TIMEOUT_MS;
  req.responseTimeoutMs = HTTP_TIMEOUT_MS;
  req.maxBody = 2048;
  req.keepAlive = isHttps();
  dc::HttpResponse res = s_poll.send(req);
  if (res.code == 204) { dc::endpointReport(res.code); return; }
  dc::endpointReport(res.code);
  if (res.code == 401 || res.code == 403 || res.code == 404) {
    s_registered = false;  // đăng ký lại sẽ báo lý do cụ thể
    return;
  }
  if (res.code != 200) {
    if (res.code <= 0) s_nextPollMs = millis() + s_serverPollMs * 2;
    return;
  }
  JsonDocument doc;
  if (deserializeJson(doc, res.body)) return;
  long pollAfter = doc["pollAfterMs"] | 0L;
  if (pollAfter >= 1000 && pollAfter <= 60000) s_serverPollMs = (unsigned long)pollAfter;
  // Có người đang xem live: bật/gia hạn chế độ phát (từ 3.1.0).
  if (!doc["live"].isNull()) setLiveWindow(doc["live"]["remainingMs"] | 0L);
  JsonArray list = doc["commands"].as<JsonArray>();
  for (JsonObject cmd : list) {
    const char *id = cmd["id"] | "";
    const char *type = cmd["type"] | "";
    const char *reason = cmd["reason"] | "ON_DEMAND";
    bool preview = cmd["preview"] | false;
    if (strcmp(type, "CAPTURE") != 0 || strlen(id) != 36) continue;
    if (strcmp(reason, "ON_DEMAND") != 0 && strcmp(reason, "SCHEDULED") != 0) reason = "ON_DEMAND";
    s_lastCommandMs = millis();
    Serial.printf("[command] Nhận lệnh chụp %s (%s)\n", id, reason);
    captureAndUpload(id, reason, preview);
  }
}

static void startMdns() {
  if (s_mdnsStarted) return;
  String name = String(CAMERA_MDNS_PREFIX) + CAMERA_ID;
  name.toLowerCase();
  for (size_t i = 0; i < name.length(); i++) if (!isalnum((unsigned char)name[i]) && name[i] != '-') name[i] = '-';
  if (MDNS.begin(name.c_str())) {
    MDNS.addService("http", "tcp", CAMERA_HTTP_PORT);
    MDNS.addServiceTxt("http", "tcp", "camera", CAMERA_ID);
    s_mdnsStarted = true;
    Serial.printf("[mdns] %s.local (chỉ dùng cho cài đặt/debug trong LAN)\n", name.c_str());
  }
}

void setup() {
  Serial.begin(115200);  // không chờ Serial
  Serial.println();
  Serial.printf("[boot] DurianCare %s firmware %s — reset %s\n", CAMERA_ID, FIRMWARE_VERSION,
                dc::resetReasonName((int)esp_reset_reason()));

  esp_task_wdt_config_t wdt = { .timeout_ms = WATCHDOG_TIMEOUT_MS, .idle_core_mask = 0, .trigger_panic = true };
  if (esp_task_wdt_reconfigure(&wdt) != ESP_OK) esp_task_wdt_init(&wdt);
  esp_task_wdt_add(NULL);

  // TRƯỚC esp_camera_init(): đọc nút BOOT (GPIO0 = XCLK) và bật TCP/IP stack (WiFi.mode) cho httpd.
  dc::NetOptions options = { CAMERA_ID, WIFI_SSID, WIFI_PASSWORD, CAMERA_DEVICE_KEY, true };
  dc::netBegin(options);
  dc::endpointBegin(dc::settings().server);
  if (dc::portalActive()) {
    setState(CS_PORTAL, "camera chưa khởi tạo trong lúc cấu hình");
    return;
  }
  // Kết nối trước khi khởi tạo camera (có giới hạn) để A/B probe nguồn/RF so sánh camera OFF/ON.
  setState(CS_WIFI_CONNECTING);
  for (unsigned long t0 = millis(); !dc::netLoop() && millis() - t0 < DC_WIFI_ATTEMPT_TIMEOUT_MS;) {
    esp_task_wdt_reset();
    delay(100);
  }
  powerRfProbe("camera OFF (Wi-Fi bật)", false);
  maintainCamera(true);
  if (s_cameraInitialized) {
    powerRfProbe("camera ON, đứng yên", false);
    powerRfProbe("camera ON, đang chụp liên tục", true);
  }
  Serial.printf("[config] Khóa camera: %s\n", cameraKey().length() ? "đã có (không hiển thị)" : "CHƯA có — nhập mã nhận trong portal");
}

void loop() {
  esp_task_wdt_reset();
  if (dc::portalActive()) {  // chỉ cấu hình; camera/httpd chưa chạy
    dc::netLoop();
    delay(10);
    return;
  }
  bool online = dc::netLoop();
  maintainCamera(false);  // thử khởi tạo lại nếu đang lỗi (có giãn cách)
  if (!online) { delay(50); return; }
  if (dc::netTakeConnectedEdge()) {
    dc::endpointOnWiFiConnected();
    dc::timeBegin();
    s_ntpStartMs = millis();
    s_registered = false;
    resetBackoff();
    startMdns();
  }
  if (s_registered && WiFi.localIP() != s_registeredIP) {
    Serial.printf("[wifi] IP đổi %s -> %s (DHCP) — đăng ký lại.\n", s_registeredIP.toString().c_str(),
                  WiFi.localIP().toString().c_str());
    s_registered = false;
    resetBackoff();
  }
  String before = dc::endpointBase();
  dc::endpointLoop();
  if (dc::endpointBase() != before) { s_registered = false; resetBackoff(); s_poll.close(); }  // máy chủ mới: đăng ký lại ngay
  if (!dc::endpointReady()) { delay(50); return; }

  activateIfNeeded();
  if (cameraKey().length() == 0) { delay(50); return; }  // chưa có khóa: chờ kích hoạt
  if (!timeReached(millis(), s_nextAttemptMs)) { if (s_registered) pollCommands(); delay(50); return; }
  if (!ensureTime()) {
    if (millis() - s_ntpStartMs >= NTP_WAIT_MS) scheduleRetry("Chưa có thời gian UTC hợp lệ (SNTP và HTTP Date đều thất bại)");
    delay(100);
    return;
  }
  unsigned long now = millis();
  if (!s_registered) {
    maintainCamera(true);
    setState(CS_REGISTERING_BACKEND);
    handleRegistryResult(postRegistry(DC_PATH_CAMERA_REGISTER), false);
    if (s_registered) s_nextPollMs = millis();
  } else if (now - s_lastHeartbeatMs >= HEARTBEAT_INTERVAL_MS) {
    maintainCamera(true);
    handleRegistryResult(postRegistry(DC_PATH_CAMERA_HEARTBEAT), true);
  } else {
    pollCommands();
  }
  if (s_registered && liveActive()) {
    pushLiveFrame();  // tự giới hạn LIVE_FRAME_INTERVAL_MS
    delay(1);
    return;
  }
  if (s_liveUntilMs != 0) stopLive(s_registered ? "hết thời gian, không ai gia hạn" : "mất đăng ký");
  delay(20);
}
