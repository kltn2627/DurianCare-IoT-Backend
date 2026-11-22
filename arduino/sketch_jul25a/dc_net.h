// DurianCare — Wi-Fi state machine, cấu hình NVS, nút BOOT và portal WiFiManager (dùng chung 2 sketch).
// Chỉ MỘT nơi điều khiển Wi-Fi: dc::netLoop(). Auto-reconnect của driver bị tắt.
#ifndef DC_NET_H
#define DC_NET_H
#include <Arduino.h>

namespace dc {

struct NetSettings {
  String ssid;      // Wi-Fi vườn
  String pass;      // KHÔNG bao giờ in ra log
  String server;    // Server URL do người dùng nhập ("" = không có)
  String claim;     // mã nhận thiết bị chờ kích hoạt ("" = không có)
  String secret;    // token thiết bị / khóa camera nhận khi kích hoạt — KHÔNG in ra log
};

struct NetOptions {
  const char *deviceLabel;      // ví dụ "esp32-01" (chỉ để log)
  const char *defaultSsid;      // từ *_secrets.h nếu có, "" nếu không
  const char *defaultPass;
  const char *defaultSecret;    // ví dụ CAMERA_DEVICE_KEY cũ, "" nếu không
  // true: mở portal bằng cách khởi động lại vào chế độ portal (camera: tránh tranh cổng 80 với httpd).
  bool portalViaRestart;
};

// Gọi sớm trong setup() (camera: TRƯỚC esp_camera_init vì GPIO0 là XCLK).
// Đọc NVS, kiểm tra nút BOOT, quyết định có mở portal không.
void netBegin(const NetOptions &options);
// Gọi mỗi vòng loop(); trả về true khi STA đang kết nối. Không chặn (trừ lúc portal lưu cấu hình).
bool netLoop();
// true đúng một lần sau mỗi lần (re)connect.
bool netTakeConnectedEdge();
bool portalActive();
const NetSettings &settings();
void saveSecret(const String &secret);
void clearClaim();
void requestPortal(const char *reason);  // mở portal (hoặc khởi động lại vào portal)
uint32_t wifiReconnects();
String apName();
const char *resetReasonName(int reason);

}  // namespace dc

#endif
