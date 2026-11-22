// DurianCare — cấu hình KHÔNG bí mật, dùng chung cho trạm cảm biến và ESP32-CAM.
// File này GIỐNG HỆT nhau ở sketch_jul25a/ và CameraWebServer/ (cùng với dc_net.*, dc_endpoint.*, dc_http.*).
// Không đặt mật khẩu, khóa hay token ở đây.
#ifndef DURIANCARE_CONFIG_H
#define DURIANCARE_CONFIG_H

// ===== Backend mặc định trên internet (bước 3 trong thứ tự tìm Backend) =====
// Để "" cho tới khi Backend được deploy. Khi deploy chỉ cần điền, ví dụ:
//   #define DURIANCARE_CLOUD_BASE_URL "https://api.duriancare.io.vn/iot"
// (base URL: scheme://host[:port][/prefix], KHÔNG có "/" cuối, KHÔNG có /api/...).
// Người dùng cũng có thể nhập Server URL trong portal Wi-Fi; giá trị trong NVS luôn được thử trước.
#define DURIANCARE_CLOUD_BASE_URL ""

// ===== Thứ tự tìm Backend =====
//   1. Server URL đã lưu NVS (portal)  2. beacon UDP 4210 v1 trên LAN  3. DURIANCARE_CLOUD_BASE_URL
#define DC_DISCOVERY_PORT              4210
#define DC_DISCOVERY_MAX_PACKET_BYTES  256
#define DC_DISCOVERY_MAX_PACKETS_LOOP  8
#define DC_DISCOVERY_WINDOW_MS         12000UL  // chờ beacon (~4 gói) trước khi chuyển sang domain cloud
#define DC_ANNOUNCEMENT_STALE_MS       30000UL  // ~10 gói beacon bị lỡ
#define DC_NOT_DISCOVERED_LOG_MS       10000UL
#define DC_TRANSPORT_FAILS_TO_SWITCH   3        // lỗi mạng liên tiếp -> thử nguồn kế tiếp
#define DC_SERVER_ERRORS_TO_SWITCH     5        // HTTP 5xx liên tiếp -> thử nguồn kế tiếp
#define DC_ROUND_BACKOFF_MIN_MS        5000UL   // hết mọi nguồn: chờ min(5 s * 2^n, 120 s) + jitter
#define DC_ROUND_BACKOFF_MAX_MS        120000UL
#define DC_MAX_URL_LEN                 120

// Đường dẫn thiết bị (nối sau base URL)
#define DC_PATH_SENSOR_DATA      "/api/v1/sensors/data"
#define DC_PATH_ACTIVATE         "/api/v1/public/devices/activate"
#define DC_PATH_CAMERA_REGISTER  "/api/v1/public/cameras/register"
#define DC_PATH_CAMERA_HEARTBEAT "/api/v1/public/cameras/heartbeat"
#define DC_PATH_CAMERAS          "/api/v1/public/cameras/"   // + <cameraId>/commands | /frames
#define DC_HEADER_DEVICE_TOKEN   "X-Device-Token"
#define DC_HEADER_CAMERA_KEY     "X-Camera-Key"

// ===== Wi-Fi + portal =====
#define DC_WIFI_ATTEMPT_TIMEOUT_MS   20000UL
#define DC_WIFI_RETRY_MIN_MS         5000UL
#define DC_WIFI_RETRY_MAX_MS         60000UL
#define DC_WIFI_OFFLINE_RESTART_MS   (30UL * 60UL * 1000UL)  // mất Wi-Fi 30 phút: khởi động lại một lần
#define DC_WIFI_FAILS_BEFORE_PORTAL  6        // N lượt Wi-Fi thất bại liên tiếp -> mở portal
#define DC_PORTAL_TIMEOUT_MS         (10UL * 60UL * 1000UL)  // portal tự đóng sau 10 phút rồi thử lại Wi-Fi
#define DC_PORTAL_AP_PREFIX          "DurianCare-Setup-"
#define DC_BOOT_BUTTON_GPIO          0        // nút BOOT/IO0 (ESP32-CAM: cũng là XCLK -> chỉ đọc lúc khởi động)
#define DC_BOOT_BUTTON_HOLD_MS       5000UL   // giữ 5 s khi khởi động: xóa cấu hình, mở portal
#define DC_NVS_NAMESPACE             "dcnet"

// ===== Thời gian (UTC) =====
#define DC_NTP_SERVER_1      "pool.ntp.org"
#define DC_NTP_SERVER_2      "time.google.com"
#define DC_NTP_SERVER_3      "time.windows.com"
#define DC_MIN_VALID_EPOCH   1767225600UL   // 2026-01-01T00:00:00Z — dưới mốc này coi là chưa đồng bộ

#endif  // DURIANCARE_CONFIG_H
