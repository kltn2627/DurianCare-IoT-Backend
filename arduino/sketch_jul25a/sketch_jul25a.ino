// ======================================================================================================
// DurianCare — trạm cảm biến ESP32 DevKit (DHT22 GPIO18 + YL-69/LM393 AO GPIO34)
// ======================================================================================================
// CHANGELOG
// 2.0.0 (2026-11-19) — cloud-ready. CHƯA KIỂM THỬ TRÊN PHẦN CỨNG (chỉ biên dịch).
//   + Tìm Backend dùng chung với ESP32-CAM (dc_endpoint.*): Server URL trong NVS -> beacon UDP 4210 v1
//     -> DURIANCARE_CLOUD_BASE_URL (duriancare_config.h). Hỗ trợ http:// (LAN) và https:// (CA bundle của core).
//   + Portal Wi-Fi WiFiManager "DurianCare-Setup-XXXX": SSID/mật khẩu, Server URL, mã nhận; lưu NVS.
//     Mở khi chưa có Wi-Fi, sau 6 lượt Wi-Fi thất bại, hoặc giữ BOOT 5 s lúc khởi động (xóa cấu hình).
//   + Kích hoạt bằng mã nhận: POST /api/v1/public/devices/activate -> token lưu NVS, gửi X-Device-Token.
//   + Payload thêm wifi_rssi (tùy chọn). Đồng hồ SNTP (cần cho HTTPS).
//   - BỎ: #error khi thiếu sensor_secrets.h (file chỉ còn là giá trị Wi-Fi mặc định tùy chọn, __has_include).
//   - BỎ: Wi-Fi state machine, parser beacon và HTTPClient viết riêng trong sketch (chuyển sang dc_net/dc_endpoint/dc_http,
//     cùng hành vi: một lượt WiFi.begin() mỗi 20 s, backoff 5..60 s, khởi động lại sau 30 phút mất Wi-Fi).
//   - BỎ: các trạng thái NetState DISCOVERING_SERVER/SERVER_DISCOVERED... (log [endpoint] thay thế).
//   = GIỮ: payload (device_id, temperature, humidity, soil_raw, soil_status, dht_status, soil_moisture?,
//     firmware_version, config_version), chu kỳ 10 s (cấu hình từ xa 5–3600 s), công thức và trạng thái độ ẩm đất,
//     backoff gửi 10->20->40->60 s, watchdog 30 s.
// 1.4.2 — bản trước (beacon-only, Wi-Fi trong sensor_secrets.h).
// ======================================================================================================
#include <Arduino.h>
#include <WiFi.h>
#include <ArduinoJson.h>
#include <esp_task_wdt.h>
#include "DHT.h"
#include "sensor_types.h"
#include "duriancare_config.h"
#include "dc_net.h"
#include "dc_endpoint.h"
#include "dc_http.h"

// Wi-Fi mặc định tùy chọn (chỉ dùng khi NVS chưa có cấu hình từ portal). Không có file vẫn biên dịch được.
#if __has_include("sensor_secrets.h")
#include "sensor_secrets.h"
#endif
#ifndef WIFI_SSID
#define WIFI_SSID ""
#endif
#ifndef WIFI_PASSWORD
#define WIFI_PASSWORD ""
#endif

// ESP32 PHY measurement of the 3.3 V rail (raw, uncalibrated: compare readings of the same board only).
extern "C" int rom_phy_get_vdd33();

// ======================================================
// 1. THIẾT BỊ
// ======================================================
const char *deviceId = "esp32-01";
const char *FIRMWARE_VERSION = "2.0.0";

const unsigned long DEFAULT_SEND_INTERVAL_MS = 10000;  // 10 giây (mặc định khi chưa có cấu hình từ xa)
const unsigned long BACKOFF_MAX_MS           = 60000;
const unsigned long AUTH_RETRY_MS            = 60000;  // 401: chờ lâu, không dò mật khẩu
const uint32_t      HTTP_CONNECT_TIMEOUT_MS  = 8000;   // -82 dBm: 3 s connects failed ~25 % (31/10)
const uint16_t      HTTP_RESPONSE_TIMEOUT_MS = 5000;
const uint32_t      HTTPS_RESPONSE_TIMEOUT_MS = 10000; // bắt tay TLS trên ESP32 mất ~1–2 s
// Watchdog: loop() không bao giờ chặn lâu hơn mức này (HTTPS tối đa ~18 s; portal lưu cấu hình ~15 s).
const uint32_t      WATCHDOG_TIMEOUT_MS      = 30000;

// ======================================================
// 2. CẢM BIẾN (DHT22 & ĐỘ ẨM ĐẤT)
// ======================================================
#define DHTPIN 18
#define DHTTYPE DHT22
#define SOIL_PIN 34       // ADC1_CH6 (đọc được khi Wi-Fi bật), suy hao 11 dB

DHT dht(DHTPIN, DHTTYPE);

// Đầu dò điện trở + LM393 (AO): khô -> raw lớn, ướt -> raw nhỏ.
const int SOIL_RAW_MIN_VALID = 50;     // raw <= 50   : NO_SIGNAL (AO/VCC chưa nối, chập)
const int SOIL_RAW_MAX_VALID = 4040;   // raw >= 4040 : SATURATED (đầu dò hở / chưa cắm đất)
const uint8_t SOIL_SAMPLES = 8;
const int SOIL_MAX_SPREAD = 400;       // dao động > 400 trong 8 mẫu: UNSTABLE (ADC thả nổi)
// HIỆU CHUẨN: chỉ đặt true sau khi đo thật raw đất khô (SOIL_DRY_RAW) và đất bão hòa (SOIL_WET_RAW).
// s = clamp((r_dry - raw) / (r_dry - r_wet) * 100, 0, 100). Chưa hiệu chuẩn: chỉ gửi raw.
const bool SOIL_CALIBRATED = false;
const int  SOIL_DRY_RAW = 3200;
const int  SOIL_WET_RAW = 1200;

// ======================================================
// 3. TRẠNG THÁI
// ======================================================
unsigned long sendIntervalMs = DEFAULT_SEND_INTERVAL_MS;
long appliedConfigVersion = -1;   // -1: chưa nhận cấu hình nào kể từ khi khởi động
unsigned long nextSendMs = 0;
unsigned long nextActivationMs = 0;
uint8_t consecutiveFailures = 0;
bool timeStarted = false;
dc::HttpChannel httpChannel;

static bool timeReached(unsigned long now, unsigned long at) { return (long)(now - at) >= 0; }

static bool isHttps() { return dc::endpointBase().startsWith("https://"); }

// --- CẤU HÌNH TỪ XA (trả kèm phản hồi POST) ---
// Chỉ nhận chu kỳ 5–3600 giây; giá trị sai bị bỏ qua và giữ cấu hình hiện tại.
void applyServerConfig(const String &body) {
  if (body.length() == 0 || body.length() > 1024) return;
  JsonDocument doc;
  if (deserializeJson(doc, body)) return;
  JsonVariant config = doc["config"];
  if (config.isNull()) return;
  if (!config["version"].is<long>() || !config["publish_interval_seconds"].is<long>()) return;
  long version = config["version"].as<long>();
  long seconds = config["publish_interval_seconds"].as<long>();
  if (version < 1 || seconds < 5 || seconds > 3600) {
    Serial.println("[config] Bỏ qua cấu hình không hợp lệ từ server.");
    return;
  }
  sendIntervalMs = (unsigned long)seconds * 1000UL;
  appliedConfigVersion = version;
  nextSendMs = millis() + sendIntervalMs;
  Serial.printf("⚙️ [config] Áp dụng phiên bản %ld: gửi mỗi %ld giây\n", version, seconds);
}

// --- ĐỌC CẢM BIẾN ĐẤT: raw riêng, trạng thái riêng ---
SoilReading readSoil() {
  long sum = 0;
  int lo = 4095, hi = 0;
  for (uint8_t i = 0; i < SOIL_SAMPLES; i++) {
    int v = analogRead(SOIL_PIN);
    sum += v;
    lo = min(lo, v);
    hi = max(hi, v);
    delay(2);
  }
  SoilReading r = { (int)(sum / SOIL_SAMPLES), hi - lo, "OK", -1 };
  if (r.raw <= SOIL_RAW_MIN_VALID) r.status = "NO_SIGNAL";
  else if (r.raw >= SOIL_RAW_MAX_VALID) r.status = "SATURATED";
  else if (r.spread > SOIL_MAX_SPREAD) r.status = "UNSTABLE";
  else if (!SOIL_CALIBRATED) r.status = "UNCALIBRATED";
  else r.percent = constrain(map(r.raw, SOIL_DRY_RAW, SOIL_WET_RAW, 0, 100), 0, 100);  // = (dry-raw)/(dry-wet)*100
  return r;
}

// --- KÍCH HOẠT BẰNG MÃ NHẬN -> TOKEN THIẾT BỊ ---
void activateIfNeeded() {
  const dc::NetSettings &cfg = dc::settings();
  if (cfg.claim.length() == 0 || !timeReached(millis(), nextActivationMs)) return;
  JsonDocument doc;
  doc["device_id"] = deviceId;
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
  req.responseTimeoutMs = isHttps() ? HTTPS_RESPONSE_TIMEOUT_MS : HTTP_RESPONSE_TIMEOUT_MS;
  dc::HttpResponse res = httpChannel.send(req);
  dc::endpointReport(res.code);
  if (res.code == 200) {
    JsonDocument rd;
    const char *token = nullptr;
    if (!deserializeJson(rd, res.body)) token = rd["device_token"] | (const char *)nullptr;
    if (token && strlen(token) >= 16) {
      dc::saveSecret(String(token));
      dc::clearClaim();
      Serial.println("🔑 [activate] Kích hoạt thành công — đã lưu token thiết bị (không hiển thị).");
      return;
    }
    Serial.println("[activate] Phản hồi 200 không có token hợp lệ — thử lại sau 60 s.");
  } else if (res.code == 400 || res.code == 409 || res.code == 410 || res.code == 429) {
    // Mã sai/đã dùng/hết hạn/bị khóa: bỏ mã để không đốt thêm lượt; mở portal nhập mã mới nếu cần.
    Serial.printf("[activate] Mã nhận không dùng được (HTTP %d) — đã bỏ mã. Xin mã mới rồi nhập lại trong portal.\n", res.code);
    dc::clearClaim();
    return;
  } else {
    Serial.printf("[activate] Chưa kích hoạt được (%s) — thử lại sau 60 s.\n", dc::errorText(res.code).c_str());
  }
  nextActivationMs = millis() + AUTH_RETRY_MS;
}

// --- ĐỌC CẢM BIẾN & GỬI DỮ LIỆU ---
void sendSensorData(bool online) {
  unsigned long now = millis();
  nextSendMs = now + sendIntervalMs;

  float h = dht.readHumidity();
  float t = dht.readTemperature();
  bool dhtReady = !isnan(h) && !isnan(t) && (t != 0.0 || h != 0.0);
  SoilReading soil = readSoil();

  Serial.println("\n---------------------------------");
  if (!dhtReady) Serial.println("🌡️ DHT22: LỖI ĐỌC (SENSOR_ERROR) — không gửi nhiệt độ/độ ẩm.");
  else Serial.printf("🌡️ Nhiệt độ %.1f °C | 💧 Độ ẩm %.1f %%\n", t, h);
  Serial.printf("[power] vdd33 %d (raw) | RSSI %d dBm\n", rom_phy_get_vdd33(), WiFi.RSSI());
  Serial.printf("🌱 Đất: raw %d (dao động %d) | %s", soil.raw, soil.spread, soil.status);
  if (soil.percent >= 0) Serial.printf(" | %d %%", soil.percent);
  Serial.println();

  if (!online || !dc::endpointReady()) {
    Serial.printf("[offline] Wi-Fi %s | máy chủ %s — chưa gửi được.\n", online ? "OK" : "chưa kết nối",
                  dc::endpointReady() ? dc::endpointBase().c_str() : "chưa có");
    return;
  }

  JsonDocument doc;
  doc["device_id"] = deviceId;
  if (dhtReady) {
    doc["temperature"] = t;
    doc["humidity"] = h;
  }
  doc["dht_status"] = dhtReady ? "OK" : "SENSOR_ERROR";
  doc["soil_raw"] = soil.raw;
  doc["soil_status"] = soil.status;
  if (soil.percent >= 0) doc["soil_moisture"] = (float)soil.percent;
  doc["firmware_version"] = FIRMWARE_VERSION;
  if (appliedConfigVersion >= 0) doc["config_version"] = appliedConfigVersion;
  doc["wifi_rssi"] = WiFi.RSSI();
  String requestBody;
  serializeJson(doc, requestBody);

  const String &token = dc::settings().secret;
  dc::HttpHeader headers[] = { { DC_HEADER_DEVICE_TOKEN, token.c_str() } };
  dc::HttpRequest req;
  req.method = "POST";
  req.url = dc::endpointSensorUrl();
  req.contentType = "application/json";
  req.body = (const uint8_t *)requestBody.c_str();
  req.bodyLen = requestBody.length();
  req.headers = headers;
  req.headerCount = token.length() ? 1 : 0;
  req.connectTimeoutMs = HTTP_CONNECT_TIMEOUT_MS;
  req.responseTimeoutMs = isHttps() ? HTTPS_RESPONSE_TIMEOUT_MS : HTTP_RESPONSE_TIMEOUT_MS;
  dc::HttpResponse res = httpChannel.send(req);
  dc::endpointReport(res.code);

  if (res.code == 201 || res.code == 200) {
    Serial.printf("📡 [IoT Push] OK %d -> %s (%s)\n", res.code, req.url.c_str(), dc::endpointSourceName());
    consecutiveFailures = 0;
    applyServerConfig(res.body);
    return;
  }
  if (consecutiveFailures < 255) consecutiveFailures++;
  unsigned long backoff;
  if (res.code == 401) {
    backoff = AUTH_RETRY_MS;
    Serial.println("🔒 [IoT Push] 401 — thiếu/sai token thiết bị. Nhập mã nhận trong portal (giữ BOOT 5 s khi khởi động).");
  } else if (res.code == 404 || res.code == 409) {
    backoff = AUTH_RETRY_MS;
    Serial.printf("[IoT Push] HTTP %d — thiết bị chưa được cấp phát (404) hoặc chưa gắn vườn (409).\n", res.code);
  } else if (res.code == 400 || res.code == 422) {
    backoff = sendIntervalMs;  // số đo bị từ chối: không dồn backoff, lần sau có số đo mới
    Serial.printf("[IoT Push] HTTP %d — Backend từ chối số đo: %s\n", res.code, res.body.substring(0, 160).c_str());
  } else {
    // Backoff có giới hạn: 10 s, 20 s, 40 s, tối đa 60 s — không chặn vòng lặp.
    backoff = sendIntervalMs << (consecutiveFailures > 3 ? 3 : consecutiveFailures - 1);
    if (backoff > BACKOFF_MAX_MS) backoff = BACKOFF_MAX_MS;
    backoff += esp_random() % 1000;
    Serial.printf("❌ [IoT Push] Thất bại %u lần | %s | Đích: %s | Thử lại sau %lus\n", consecutiveFailures,
                  dc::errorText(res.code).c_str(), req.url.c_str(), backoff / 1000);
  }
  nextSendMs = millis() + backoff;
}

void setup() {
  Serial.begin(115200);  // không chờ Serial: thiết bị chạy cả khi không cắm máy tính
  delay(200);
  Serial.println("\n=================================");
  Serial.println("   GIÁM SÁT CẢM BIẾN DURIANCARE   ");
  Serial.printf("   %s firmware %s | reset %s\n", deviceId, FIRMWARE_VERSION, dc::resetReasonName((int)esp_reset_reason()));
  Serial.println("=================================");

  esp_task_wdt_config_t wdt = { .timeout_ms = WATCHDOG_TIMEOUT_MS, .idle_core_mask = 0, .trigger_panic = true };
  if (esp_task_wdt_reconfigure(&wdt) != ESP_OK) esp_task_wdt_init(&wdt);
  esp_task_wdt_add(NULL);

  analogSetPinAttenuation(SOIL_PIN, ADC_11db);  // dải 0–3,3 V
  dht.begin();

  dc::NetOptions options = { deviceId, WIFI_SSID, WIFI_PASSWORD, "", false };
  dc::netBegin(options);
  dc::endpointBegin(dc::settings().server);
  nextSendMs = millis() + 2000;  // DHT22 cần ~2 s sau khi cấp nguồn
}

void loop() {
  esp_task_wdt_reset();
  bool online = dc::netLoop();
  if (online) {
    if (dc::netTakeConnectedEdge()) {
      dc::endpointOnWiFiConnected();
      dc::timeBegin();
      timeStarted = true;
    }
    dc::endpointLoop();
    if (dc::endpointReady()) activateIfNeeded();
  }
  // Đọc cảm biến theo chu kỳ cả khi mất mạng (log chẩn đoán); chỉ gửi khi có máy chủ. Portal: không đọc.
  if (!dc::portalActive() && timeReached(millis(), nextSendMs)) sendSensorData(online);
  delay(10);
}
