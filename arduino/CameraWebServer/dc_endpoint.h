// DurianCare — tìm Backend: (1) Server URL trong NVS -> (2) beacon UDP 4210 v1 -> (3) DURIANCARE_CLOUD_BASE_URL.
// Không chặn loop(); lỗi liên tiếp thì chuyển nguồn, hết nguồn thì backoff có giới hạn + jitter.
#ifndef DC_ENDPOINT_H
#define DC_ENDPOINT_H
#include <Arduino.h>

namespace dc {

enum EndpointSource { SRC_NONE = 0, SRC_NVS, SRC_BEACON, SRC_CLOUD };

// storedServer: Server URL từ NVS ("" nếu không có). Gọi một lần trong setup().
void endpointBegin(const String &storedServer);
// Gọi khi Wi-Fi vừa (re)connect: mở lại UDP 4210 và bắt đầu dò lại từ đầu.
void endpointOnWiFiConnected();
// Gọi mỗi vòng loop() khi Wi-Fi đang kết nối: đọc gói beacon, chọn nguồn.
void endpointLoop();
// true khi đã có endpoint để gọi và không trong thời gian chờ backoff.
bool endpointReady();
// Base URL hiện tại (không "/" cuối), ví dụ "http://192.168.1.10:3001" hoặc "https://api.duriancare.io.vn/iot".
const String &endpointBase();
// URL đầy đủ = base + path.
String endpointUrl(const char *path);
// URL gửi số đo: nguồn beacon dùng đúng "path" trong gói (hợp đồng báo cáo), nguồn khác dùng DC_PATH_SENSOR_DATA.
String endpointSensorUrl();
EndpointSource endpointSource();
const char *endpointSourceName();
// Kết quả mỗi lần gọi Backend: code > 0 là HTTP status, <= 0 là lỗi transport.
void endpointReport(int code);
// Kiểm tra URL người dùng nhập: http(s)://host[:port][/prefix], không userinfo/query/fragment.
bool validBaseUrl(const String &url);

}  // namespace dc

#endif
