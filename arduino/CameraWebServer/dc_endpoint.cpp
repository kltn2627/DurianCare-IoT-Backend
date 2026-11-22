// DurianCare — tìm Backend (dùng chung 2 sketch). Parser beacon giữ nguyên hợp đồng v1 của firmware cũ:
//   {"service":"duriancare-iot","v":1,"ip":"<IPv4>","port":3001,"path":"/api/v1/sensors/data"}
// JSON <= 256 byte, service đúng, v == 1, ip IPv4 không 0.x/127.x/multicast, port nguyên 1-65535, path hợp lệ,
// và gói phải đến từ CHÍNH IP nó công bố. Gói legacy "DURIANCARE_SERVER:..." bị bỏ qua.
#include "dc_endpoint.h"
#include "duriancare_config.h"
#include <WiFi.h>
#include <WiFiUdp.h>
#include <ArduinoJson.h>

namespace dc {

static WiFiUDP s_udp;
static String s_nvsUrl;
static String s_cloudUrl;
static String s_beaconBase;      // http://ip:port của gói beacon gần nhất
static String s_beaconPath;      // path trong gói (đường gửi số đo của cảm biến)
static unsigned long s_lastBeaconMs = 0;
static bool s_legacyNotice = false;

static EndpointSource s_source = SRC_NONE;
static String s_base;
static bool s_failed[4] = {false, false, false, false};  // nguồn đã hỏng trong vòng hiện tại
static uint8_t s_transportFails = 0;
static uint8_t s_serverErrors = 0;
static unsigned long s_connectedMs = 0;
static unsigned long s_waitUntilMs = 0;
static unsigned long s_roundBackoffMs = DC_ROUND_BACKOFF_MIN_MS;
static unsigned long s_nextNotDiscoveredLogMs = 0;

static bool timeReached(unsigned long now, unsigned long at) { return (long)(now - at) >= 0; }

bool validBaseUrl(const String &url) {
  if (url.length() < 10 || url.length() > DC_MAX_URL_LEN) return false;
  int hostStart;
  if (url.startsWith("http://")) hostStart = 7;
  else if (url.startsWith("https://")) hostStart = 8;
  else return false;
  if (url.endsWith("/")) return false;
  for (size_t i = 0; i < url.length(); i++) {
    char c = url[i];
    if (c == '?' || c == '#' || c == '@' || c == ' ' || c == '\\' || c < 33 || c > 126) return false;
  }
  int slash = url.indexOf('/', hostStart);
  String hostPort = slash < 0 ? url.substring(hostStart) : url.substring(hostStart, slash);
  if (hostPort.length() == 0 || url.indexOf("..") >= 0) return false;
  int colon = hostPort.indexOf(':');
  if (colon >= 0) {
    long port = hostPort.substring(colon + 1).toInt();
    if (port < 1 || port > 65535) return false;
  }
  return true;
}

const char *endpointSourceName() {
  switch (s_source) {
    case SRC_NVS: return "NVS";
    case SRC_BEACON: return "BEACON";
    case SRC_CLOUD: return "CLOUD";
    default: return "NONE";
  }
}

EndpointSource endpointSource() { return s_source; }
const String &endpointBase() { return s_base; }
String endpointUrl(const char *path) { return s_base + path; }

String endpointSensorUrl() {
  if (s_source == SRC_BEACON && s_beaconPath.length() > 0) return s_base + s_beaconPath;
  return s_base + DC_PATH_SENSOR_DATA;
}

void endpointBegin(const String &storedServer) {
  if (storedServer.length() > 0) {
    if (validBaseUrl(storedServer)) s_nvsUrl = storedServer;
    else Serial.println("[endpoint] Server URL trong NVS không hợp lệ — bỏ qua (mở portal để sửa).");
  }
  String cloud = DURIANCARE_CLOUD_BASE_URL;
  if (cloud.length() > 0) {
    if (validBaseUrl(cloud)) s_cloudUrl = cloud;
    else Serial.println("[endpoint] DURIANCARE_CLOUD_BASE_URL không hợp lệ — bỏ qua.");
  }
  Serial.printf("[endpoint] Thứ tự: NVS %s -> beacon UDP %u -> cloud %s\n",
                s_nvsUrl.length() ? s_nvsUrl.c_str() : "(trống)", DC_DISCOVERY_PORT,
                s_cloudUrl.length() ? s_cloudUrl.c_str() : "(trống)");
}

static void use(EndpointSource source, const String &base, const char *why) {
  if (source == s_source && base == s_base) return;
  if (s_source == SRC_NONE) Serial.printf("[endpoint] SERVER_DISCOVERED (%s): %s — %s\n", source == SRC_NVS ? "NVS" :
                                           source == SRC_BEACON ? "BEACON" : "CLOUD", base.c_str(), why);
  else Serial.printf("[endpoint] SERVER_CHANGED: %s (%s) -> %s (%s) — %s\n", s_base.c_str(), endpointSourceName(),
                     base.c_str(), source == SRC_NVS ? "NVS" : source == SRC_BEACON ? "BEACON" : "CLOUD", why);
  s_source = source;
  s_base = base;
  s_transportFails = 0;
  s_serverErrors = 0;
}

static void clearEndpoint(const char *why) {
  if (s_source != SRC_NONE) Serial.printf("[endpoint] ENDPOINT_STALE (%s): bỏ %s (%s)\n", why, s_base.c_str(), endpointSourceName());
  s_source = SRC_NONE;
  s_base = "";
}

void endpointOnWiFiConnected() {
  clearEndpoint("Wi-Fi vừa kết nối");
  for (bool &f : s_failed) f = false;
  s_beaconBase = "";
  s_beaconPath = "";
  s_lastBeaconMs = 0;
  s_connectedMs = millis();
  s_waitUntilMs = 0;
  s_roundBackoffMs = DC_ROUND_BACKOFF_MIN_MS;
  s_nextNotDiscoveredLogMs = millis() + DC_NOT_DISCOVERED_LOG_MS;
  s_udp.stop();
  if (s_udp.begin(DC_DISCOVERY_PORT)) Serial.printf("[discovery] Đang lắng nghe máy chủ trên UDP %u...\n", DC_DISCOVERY_PORT);
  else Serial.printf("[discovery] LỖI: không mở được UDP %u\n", DC_DISCOVERY_PORT);
}

static bool validPath(const char *path) {
  size_t len = strlen(path);
  if (len < 1 || len > 64 || path[0] != '/' || strstr(path, "..") != nullptr) return false;
  for (size_t i = 0; i < len; i++) {
    char c = path[i];
    if (!(isalnum((unsigned char)c) || c == '/' || c == '-' || c == '_' || c == '.' || c == '~')) return false;
  }
  return true;
}

static bool parseAnnouncement(const char *text, size_t len, const IPAddress &sender, String &base, String &path) {
  if (len == 0 || len > DC_DISCOVERY_MAX_PACKET_BYTES) return false;
  JsonDocument doc;
  if (deserializeJson(doc, text, len)) return false;
  const char *service = doc["service"];
  const char *ip = doc["ip"];
  const char *p = doc["path"];
  if (!service || strcmp(service, "duriancare-iot") != 0) return false;
  if (!doc["v"].is<int>() || doc["v"].as<int>() != 1) return false;
  if (!doc["port"].is<int>()) return false;
  long port = doc["port"].as<long>();
  IPAddress addr;
  if (!ip || !addr.fromString(ip) || port < 1 || port > 65535 || !p || !validPath(p)) return false;
  if (addr[0] == 0 || addr[0] == 127 || addr[0] >= 224) return false;
  if (addr != sender) return false;
  base = String("http://") + addr.toString() + ":" + String(port);
  path = p;
  return true;
}

static void drainBeacon() {
  for (uint8_t i = 0; i < DC_DISCOVERY_MAX_PACKETS_LOOP; i++) {
    int size = s_udp.parsePacket();
    if (size <= 0) return;
    IPAddress sender = s_udp.remoteIP();
    if ((size_t)size > DC_DISCOVERY_MAX_PACKET_BYTES) { s_udp.clear(); continue; }
    char buffer[DC_DISCOVERY_MAX_PACKET_BYTES + 1];
    int len = s_udp.read(buffer, DC_DISCOVERY_MAX_PACKET_BYTES);
    if (len <= 0) continue;
    buffer[len] = 0;
    String base, path;
    if (!parseAnnouncement(buffer, (size_t)len, sender, base, path)) {
      if (strncmp(buffer, "DURIANCARE_SERVER:", 18) == 0 && !s_legacyNotice) {
        Serial.println("[discovery] Bỏ qua gói định dạng cũ DURIANCARE_SERVER (chờ gói v1).");
        s_legacyNotice = true;
      }
      continue;
    }
    s_lastBeaconMs = millis();
    if (base != s_beaconBase || path != s_beaconPath) {
      s_beaconBase = base;
      s_beaconPath = path;
      s_failed[SRC_BEACON] = false;  // một máy chủ LAN mới xuất hiện: cho phép thử lại ngay
    }
    // LAN đứng trước cloud trong thứ tự; NVS luôn được ưu tiên khi còn dùng được.
    if (!s_failed[SRC_BEACON] && (s_source == SRC_BEACON || s_source == SRC_CLOUD || s_source == SRC_NONE)) {
      if (!(s_source == SRC_BEACON && s_base == base)) {
        use(SRC_BEACON, base, "gói beacon UDP 4210 hợp lệ");
        s_waitUntilMs = 0;
      }
    }
  }
}

static bool beaconFresh() {
  return s_beaconBase.length() > 0 && millis() - s_lastBeaconMs <= DC_ANNOUNCEMENT_STALE_MS;
}

// Chọn nguồn khi chưa có (theo thứ tự NVS -> beacon -> cloud, bỏ nguồn đã hỏng trong vòng này).
static void choose() {
  unsigned long now = millis();
  if (s_source != SRC_NONE) return;
  if (s_nvsUrl.length() && !s_failed[SRC_NVS]) { use(SRC_NVS, s_nvsUrl, "Server URL đã lưu"); return; }
  if (beaconFresh() && !s_failed[SRC_BEACON]) { use(SRC_BEACON, s_beaconBase, "gói beacon UDP 4210 hợp lệ"); return; }
  bool windowOpen = now - s_connectedMs < DC_DISCOVERY_WINDOW_MS;
  if (windowOpen) return;  // còn trong cửa sổ chờ beacon
  if (s_cloudUrl.length() && !s_failed[SRC_CLOUD]) { use(SRC_CLOUD, s_cloudUrl, "domain cloud mặc định"); return; }
  bool anyCandidate = s_nvsUrl.length() || s_cloudUrl.length() || s_beaconBase.length();
  if (anyCandidate) {
    // Đã thử hết nguồn trong vòng này: chờ backoff rồi thử lại từ đầu.
    unsigned long wait = s_roundBackoffMs + (esp_random() % 1000);
    Serial.printf("[endpoint] Mọi nguồn đều lỗi — thử lại từ đầu sau %lus\n", wait / 1000);
    s_waitUntilMs = now + wait;
    s_roundBackoffMs = min(s_roundBackoffMs * 2, (unsigned long)DC_ROUND_BACKOFF_MAX_MS);
    for (bool &f : s_failed) f = false;
    return;
  }
  if (timeReached(now, s_nextNotDiscoveredLogMs)) {
    Serial.printf("[discovery] SERVER_NOT_DISCOVERED: chưa nhận gói UDP %u hợp lệ, chưa có Server URL và domain cloud. "
                  "Kiểm tra beacon DurianCare cùng mạng (gateway %s) hoặc nhập Server URL trong portal.\n",
                  DC_DISCOVERY_PORT, WiFi.gatewayIP().toString().c_str());
    s_nextNotDiscoveredLogMs = now + DC_NOT_DISCOVERED_LOG_MS;
  }
}

void endpointLoop() {
  drainBeacon();
  if (s_waitUntilMs && !timeReached(millis(), s_waitUntilMs)) return;
  s_waitUntilMs = 0;
  choose();
}

bool endpointReady() {
  return s_source != SRC_NONE && (s_waitUntilMs == 0 || timeReached(millis(), s_waitUntilMs));
}

void endpointReport(int code) {
  if (s_source == SRC_NONE) return;
  if (code > 0 && code < 500) {
    s_transportFails = 0;
    s_serverErrors = 0;
    s_roundBackoffMs = DC_ROUND_BACKOFF_MIN_MS;
    return;
  }
  if (code <= 0) s_transportFails++;
  else s_serverErrors++;
  bool switchNow = s_transportFails >= DC_TRANSPORT_FAILS_TO_SWITCH || s_serverErrors >= DC_SERVER_ERRORS_TO_SWITCH;
  // Beacon vẫn đang phát đúng địa chỉ này: máy chủ còn sống, giữ nguyên (lỗi tạm thời).
  if (switchNow && s_source == SRC_BEACON && beaconFresh() && s_beaconBase == s_base) {
    s_transportFails = 0;
    s_serverErrors = 0;
    return;
  }
  if (switchNow) {
    s_failed[s_source] = true;
    clearEndpoint(code <= 0 ? "nhiều lần lỗi mạng" : "nhiều lần lỗi máy chủ 5xx");
    choose();
  }
}

}  // namespace dc
