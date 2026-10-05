// ===================================================================
// DurianCare_ESP32_CAM.ino
// AI-Thinker ESP32-CAM + OV2640
//
// Baseline: firmware gốc (camera pipeline + sensor tuning giữ nguyên)
// Thêm mới : Camera Self-Registration + Heartbeat + IP Change Detection
// ===================================================================

#include <Arduino.h>
#include "esp_camera.h"
#include <WiFi.h>
#include <HTTPClient.h>          // ← THÊM MỚI: gọi Backend API
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

// ======================================================
// 1. CẤU HÌNH BO AI-THINKER ESP32-CAM  (KHÔNG THAY ĐỔI)
// ======================================================
#define CAMERA_MODEL_AI_THINKER
#include "camera_pins.h"

// ======================================================
// 2. THÔNG TIN WI-FI  (KHÔNG THAY ĐỔI)
// ======================================================
const char *ssid     = "17771";
const char *password = "12121212";

// ======================================================
// 3. DURIANCARE BACKEND CONFIGURATION  ← THÊM MỚI
//
//  QUAN TRỌNG: BACKEND_BASE_URL phải là IP LAN của máy
//  tính đang chạy DurianCare Backend.
//  KHÔNG dùng localhost / 127.0.0.1.
//
//  Ví dụ: "http://192.168.1.10:8080"   (qua Gateway port 8080)
//         "http://192.168.1.10:3001"   (trực tiếp IoT service)
// ======================================================
// Windows LAN IP: 192.168.1.16  |  IoT service port: 3001 (direct, no Gateway)
const char *BACKEND_BASE_URL = "http://192.168.1.16:3001";

// Camera ID cố định — KHÔNG BAO GIỜ thay đổi dù IP router đổi
const char *CAMERA_ID        = "ESP32-CAM-001";

// Phải khớp CAMERA_REGISTRATION_KEY trong .env của Backend
// Mặc định dev (docker-compose không override): "duriancare-esp32-dev-key"
const char *CAMERA_KEY       = "duriancare-esp32-dev-key";  // ← SỬA NẾU ĐỔI KEY

// Thông tin firmware gửi kèm khi đăng ký
const char *FIRMWARE_VERSION = "1.0.0";

// Port camera web server (Backend gọi http://IP:PORT/capture)
const int   CAMERA_PORT      = 80;

// ======================================================
// 4. TIMING CONSTANTS  ← THÊM MỚI
// ======================================================
const unsigned long HEARTBEAT_INTERVAL_MS  = 60000UL;  // 60 giây
const unsigned long WIFI_CHECK_INTERVAL_MS =  5000UL;  // 5 giây
const int           HTTP_TIMEOUT_MS        =  7000;    // 7 giây mỗi request

// ======================================================
// 5. RUNTIME STATE  ← THÊM MỚI
// ======================================================
static IPAddress     s_registeredIP;
static unsigned long s_lastHeartbeatMs  = 0;
static unsigned long s_lastWifiCheckMs  = 0;
static bool          s_cameraRegistered = false;

// Khai báo trước — định nghĩa trong app_httpd.cpp (cùng thư mục sketch)
void startCameraServer();

// ======================================================
// 6. HÀM TỰ ĐỘNG KẾT NỐI WI-FI  (KHÔNG THAY ĐỔI)
// ======================================================
void connectWiFi() {
  WiFi.disconnect(true);
  delay(500);
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);
  WiFi.setSleep(false); // Tắt chế độ tiết kiệm điện để sóng Wi-Fi luôn mạnh nhất

  Serial.print("Đang kết nối Wi-Fi");
  int count = 0;
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
    count++;

    // Nếu quá 10 giây (20 lần thử) chưa vào được -> Reset lại Module Wi-Fi
    if (count > 20) {
      Serial.println("\n⚠️ Wi-Fi chập chờn! Đang khởi động lại module Wi-Fi...");
      WiFi.disconnect(true);
      delay(1000);
      WiFi.begin(ssid, password);
      count = 0;
    }
  }
  Serial.println("\n✅ Wi-Fi đã kết nối thành công!");
}

// ======================================================
// 7. JSON HELPER  ← THÊM MỚI
//    Escape double-quote và backslash trong chuỗi JSON
// ======================================================
static String jsonEscape(const String &s) {
  String out;
  out.reserve(s.length() + 4);
  for (unsigned int i = 0; i < s.length(); i++) {
    char c = s[i];
    if (c == '"' || c == '\\') out += '\\';
    out += c;
  }
  return out;
}

// ======================================================
// 8. REGISTER CAMERA  ← THÊM MỚI
//
//  POST /api/v1/public/cameras/register
//  Header : X-Camera-Key: <CAMERA_KEY>
//  Body   : {
//    "cameraId"        : "ESP32-CAM-001",
//    "ipAddress"       : "192.168.1.105",
//    "port"            : 80,
//    "protocol"        : "http",
//    "macAddress"      : "AA:BB:CC:DD:EE:FF",
//    "firmwareVersion" : "1.0.0",
//    "ssid"            : "17771"
//  }
//  Response 200: { "status": "registered", "device": {...} }
// ======================================================
bool registerCamera() {
  if (WiFi.status() != WL_CONNECTED) return false;

  String ip  = WiFi.localIP().toString();
  String mac = WiFi.macAddress();
  String url = String(BACKEND_BASE_URL) + "/api/v1/public/cameras/register";

  // Debug: hiện URL thực tế đang gọi
  Serial.printf("Register URL: %s\n", url.c_str());
  Serial.printf("Camera ID  : %s\n", CAMERA_ID);
  Serial.printf("Camera IP  : %s\n", ip.c_str());

  // Body khớp chính xác contract publicRoutes.js
  String body;
  body.reserve(280);
  body  = "{";
  body += "\"cameraId\":\"";        body += jsonEscape(String(CAMERA_ID));       body += "\",";
  body += "\"ipAddress\":\"";       body += jsonEscape(ip);                      body += "\",";
  body += "\"port\":";              body += CAMERA_PORT;                         body += ",";
  body += "\"protocol\":\"http\",";
  body += "\"macAddress\":\"";      body += jsonEscape(mac);                     body += "\",";
  body += "\"firmwareVersion\":\""; body += jsonEscape(String(FIRMWARE_VERSION)); body += "\",";
  body += "\"ssid\":\"";            body += jsonEscape(String(ssid));            body += "\"";
  body += "}";

  WiFiClient wifiClient;
  HTTPClient http;
  http.begin(wifiClient, url);
  http.setTimeout(HTTP_TIMEOUT_MS);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("X-Camera-Key",  CAMERA_KEY);

  int code = http.POST(body);
  http.end();

  if (code == 200 || code == 201) {
    Serial.printf("Register success (HTTP %d) — IP: %s\n", code, ip.c_str());
    s_registeredIP    = WiFi.localIP();
    s_cameraRegistered = true;
    return true;
  } else if (code > 0) {
    Serial.printf("Register failed — HTTP %d — URL: %s\n", code, url.c_str());
  } else {
    Serial.printf("Register failed — HTTP error %d — URL: %s\n", code, url.c_str());
  }
  return false;
}

// ======================================================
// 9. HEARTBEAT  ← THÊM MỚI
//
//  POST /api/v1/public/cameras/heartbeat
//  Header : X-Camera-Key: <CAMERA_KEY>
//  Body   : {
//    "cameraId"  : "ESP32-CAM-001",
//    "ipAddress" : "192.168.1.105",
//    "port"      : 80
//  }
//  Response 200: { "status": "ok", "device": {...} }
// ======================================================
void sendHeartbeat() {
  if (WiFi.status() != WL_CONNECTED) return;

  String ip = WiFi.localIP().toString();

  String body;
  body.reserve(128);
  body  = "{";
  body += "\"cameraId\":\"";  body += jsonEscape(String(CAMERA_ID)); body += "\",";
  body += "\"ipAddress\":\""; body += jsonEscape(ip);                 body += "\",";
  body += "\"port\":";        body += CAMERA_PORT;                   body += "}";

  String url = String(BACKEND_BASE_URL) + "/api/v1/public/cameras/heartbeat";

  WiFiClient wifiClient;
  HTTPClient http;
  http.begin(wifiClient, url);
  http.setTimeout(HTTP_TIMEOUT_MS);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("X-Camera-Key",  CAMERA_KEY);

  int code = http.POST(body);
  http.end();

  if (code == 200) {
    Serial.println("Heartbeat OK");
  } else if (code > 0) {
    Serial.printf("Heartbeat failed: HTTP %d\n", code);
  } else {
    Serial.printf("Heartbeat failed: connection error (%d)\n", code);
  }
}

// ======================================================
// 10. IP CHANGE DETECTION  ← THÊM MỚI
// ======================================================
void checkIPChange() {
  if (WiFi.status() != WL_CONNECTED) return;

  if (!s_cameraRegistered) {
    Serial.println("Registering camera...");
    registerCamera();
    return;
  }

  IPAddress currentIP = WiFi.localIP();
  if (currentIP != s_registeredIP) {
    Serial.println("IP CHANGE DETECTED");
    Serial.print("Old IP: "); Serial.println(s_registeredIP.toString());
    Serial.print("New IP: "); Serial.println(currentIP.toString());
    Serial.println("Registering camera...");
    if (registerCamera()) {
      Serial.println("Register success");
    } else {
      Serial.println("Register failed — will retry");
    }
  }
}

// ======================================================
// 11. SETUP
// ======================================================
void setup() {
  // 💥 TẮT CẢNH BÁO BROWNOUT  (KHÔNG THAY ĐỔI)
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);

  Serial.begin(115200);
  Serial.setDebugOutput(true);
  Serial.println();

  // ← THÊM: banner DurianCare
  Serial.println("================================");
  Serial.println("DurianCare ESP32-CAM");
  Serial.printf("Camera ID: %s\n", CAMERA_ID);
  Serial.println("================================");
  Serial.println();

  // ── Cấu hình chân Camera AI-Thinker  (KHÔNG THAY ĐỔI) ──────────────────
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer   = LEDC_TIMER_0;
  config.pin_d0       = Y2_GPIO_NUM;
  config.pin_d1       = Y3_GPIO_NUM;
  config.pin_d2       = Y4_GPIO_NUM;
  config.pin_d3       = Y5_GPIO_NUM;
  config.pin_d4       = Y6_GPIO_NUM;
  config.pin_d5       = Y7_GPIO_NUM;
  config.pin_d6       = Y8_GPIO_NUM;
  config.pin_d7       = Y9_GPIO_NUM;
  config.pin_xclk     = XCLK_GPIO_NUM;
  config.pin_pclk     = PCLK_GPIO_NUM;
  config.pin_vsync    = VSYNC_GPIO_NUM;
  config.pin_href     = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn     = PWDN_GPIO_NUM;
  config.pin_reset    = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;

  // Cấu hình khung hình
  // fb_count=1 + CAMERA_GRAB_WHEN_EMPTY: HTTP still-capture handler gets an
  // exclusive, stable buffer — no DMA overwrite race while sending the JPEG body.
  // CAMERA_GRAB_LATEST với fb_count=2 gây deadlock: DMA liên tục ghi đè buffer
  // trong khi httpd_resp_send() đang đọc → stall sau khi đã gửi headers.
  if (psramFound()) {
    config.frame_size   = FRAMESIZE_VGA;   // 640x480
    config.jpeg_quality = 12;
    config.fb_count     = 1;
    config.grab_mode    = CAMERA_GRAB_WHEN_EMPTY;
  } else {
    config.frame_size   = FRAMESIZE_SVGA;
    config.jpeg_quality = 12;
    config.fb_count     = 1;
  }

  // Khởi tạo OV2640  (KHÔNG THAY ĐỔI)
  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("❌ KHỞI TẠO CAMERA THẤT BẠI! Mã lỗi: 0x%x\n", err);
    return;
  }
  Serial.println("🎉 KHỞI TẠO CAMERA OV2640 THÀNH CÔNG!");

  // ── Sensor tuning  (KHÔNG THAY ĐỔI) ──────────────────────────────────────
  sensor_t *s = esp_camera_sensor_get();
  if (s != NULL) {
    s->set_whitebal(s, 1);       // Auto White Balance
    s->set_awb_gain(s, 1);       // AWB Gain
    s->set_wb_mode(s, 0);        // Auto WB
    s->set_sharpness(s, 2);      // Max Sharpness
    s->set_contrast(s, 1);       // Tăng nhẹ tương phản
    s->set_saturation(s, 1);     // Tăng nhẹ độ bão hòa
    s->set_denoise(s, 1);        // Denoise hardware
    s->set_gain_ctrl(s, 1);
    s->set_exposure_ctrl(s, 1);
    s->set_aec2(s, 1);           // AEC DSP
  }
  Serial.println("⚙️ ĐÃ THIẾT LẬP CẤU HÌNH SENSOR AI THÀNH CÔNG!");

  // ── Wi-Fi  (KHÔNG THAY ĐỔI, chỉ thêm log) ────────────────────────────────
  Serial.println("Connecting WiFi...");
  connectWiFi();

  // ── Camera Web Server  (KHÔNG THAY ĐỔI) ──────────────────────────────────
  // Khởi động TRƯỚC khi registration — camera luôn stream được dù backend down
  startCameraServer();

  Serial.print("📷 Camera Ready! Mở trình duyệt gõ địa chỉ IP: http://");
  Serial.println(WiFi.localIP());

  // ← THÊM: đăng ký camera với Backend (non-critical — failure không ảnh hưởng camera)
  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("WiFi connected\nIP: %s\n", WiFi.localIP().toString().c_str());
    Serial.println("Registering camera...");
    if (!registerCamera()) {
      Serial.println("Initial registration failed — will retry in loop");
    }
  }

  // ← THÊM: seed timers
  s_lastWifiCheckMs = millis();
  s_lastHeartbeatMs = millis();
}

// ======================================================
// 12. LOOP
//   Thay delay(5000) bằng millis() để heartbeat 60 s
//   hoạt động non-blocking.
//   Hành vi Wi-Fi check mỗi 5 s được giữ nguyên.
// ======================================================
void loop() {
  unsigned long now = millis();

  // ── Wi-Fi check mỗi 5 giây (giữ nguyên hành vi) ─────────────────────────
  if (now - s_lastWifiCheckMs >= WIFI_CHECK_INTERVAL_MS) {
    s_lastWifiCheckMs = now;

    if (WiFi.status() != WL_CONNECTED) {
      Serial.println("⚠️ Mất kết nối Wi-Fi! Đang tự động kết nối lại...");
      connectWiFi();

      if (WiFi.status() == WL_CONNECTED) {
        // ← THÊM: luôn re-register sau khi reconnect (IP có thể đổi)
        s_cameraRegistered = false;
        Serial.println("Registering camera...");
        registerCamera();
        s_lastHeartbeatMs = millis();  // reset heartbeat timer
      }
    } else {
      // ← THÊM: phát hiện IP đổi khi Wi-Fi đang kết nối bình thường
      checkIPChange();
    }
  }

  // ← THÊM: Heartbeat mỗi 60 giây (millis, không delay)
  if (WiFi.status() == WL_CONNECTED && s_cameraRegistered) {
    if (now - s_lastHeartbeatMs >= HEARTBEAT_INTERVAL_MS) {
      s_lastHeartbeatMs = now;
      sendHeartbeat();
    }
  }
}
