#ifndef CAMERA_REGISTRY_CONFIG_H
#define CAMERA_REGISTRY_CONFIG_H

// ==========================================
// DurianCare ESP32-CAM — cấu hình KHÔNG bí mật, riêng cho camera.
// Phần dùng chung với trạm cảm biến (Backend cloud, beacon, Wi-Fi, portal): duriancare_config.h.
// Khóa thiết bị: NVS (nhận khi kích hoạt bằng mã nhận) hoặc mặc định cũ trong camera_secrets.h (tùy chọn).
// ==========================================

// Định danh cố định — trùng device_uid đã provision (iot_devices, loại CAMERA) / CAMERA_DEVICE_BINDINGS.
#define CAMERA_ID             "ESP32-CAM-001"
#define FIRMWARE_VERSION      "3.0.0"

// Web server debug trong LAN (/capture, /stream:81). Backend KHÔNG gọi cổng này ở chế độ PUSH.
#define CAMERA_HTTP_PORT      80
// mDNS: duriancare-cam-<id>.local (chỉ phục vụ màn hình cài đặt / debug trong LAN)
#define CAMERA_MDNS_PREFIX    "duriancare-cam-"

// Ảnh. Bản 2.x dùng UXGA + fb_count 2 + GRAB_LATEST + XCLK 20 MHz (xem bảng nguyên nhân trong
// DurianCare-IoT-Backend/docs/IOT_CLOUD_READY.md). Mặc định mới: ảnh nhỏ hơn, một buffer, chụp khi cần.
#define CAMERA_FRAME_SIZE        FRAMESIZE_XGA   // 1024x768; đổi FRAMESIZE_SVGA nếu Wi-Fi rất yếu
#define CAMERA_FRAME_SIZE_NAME   "XGA"
#define CAMERA_PREVIEW_SIZE      FRAMESIZE_VGA   // lệnh preview=true
#define CAMERA_JPEG_QUALITY      12
#define CAMERA_FB_COUNT          1
#define CAMERA_XCLK_HZ           10000000        // 10 MHz (bản cũ 20 MHz) — báo trong diagnostics.xclkMhz
#define CAMERA_DISCARD_FRAMES    1               // bỏ khung đầu sau khi đổi kích thước/chờ lâu (khung cũ)

// Thời gian (UTC). announcedAt = epoch ms UTC, Backend chấp nhận lệch tối đa ±30 s.
#define NTP_WAIT_MS           30000UL           // SNTP chờ tối đa trước khi dùng header Date của Backend (HTTP)
#define TIME_SOURCE_PATH      "/actuator/health"

// Nhịp
#define HEARTBEAT_INTERVAL_MS   60000UL
#define COMMAND_POLL_MS         5000UL          // poll GET .../commands; Backend có thể gợi ý pollAfterMs
#define COMMAND_POLL_IDLE_MS    15000UL         // giãn khi 10 phút không có lệnh (HTTPS)
#define COMMAND_IDLE_AFTER_MS   600000UL
#define FRAME_UPLOAD_RETRIES    1
// lwIP RTO 3 s (doubling). Connect: 2 SYN retransmits; response: 2 data retransmits + margin.
#define HTTP_CONNECT_TIMEOUT_MS 10000
#define HTTP_TIMEOUT_MS         15000
#define UPLOAD_TIMEOUT_MS       30000           // ảnh XGA ~60–120 KB trên link yếu (đo 66,5 KB/7,1 s ở UXGA)
#define REGISTRY_MAX_RESPONSE_BYTES 4096
#define REGISTRY_INVALID_RESPONSE   (-200)      // HTTP 200 nhưng thân phản hồi không đúng
// Camera: khởi tạo lỗi thì thử lại, sau CAMERA_INIT_MAX_FAILURES lần liên tiếp mới khởi động lại module.
#define CAMERA_INIT_RETRY_MS     30000UL
// Boot-time init is reliable on ESP32-CAM-001; a runtime re-init is not (SCCB NACK with a stable
// 3.3 V rail, measured 31/10) -> restart after 2 failures and take the boot path again.
#define CAMERA_INIT_MAX_FAILURES 2
// Watchdog loop(): lớn hơn một lượt HTTP dài nhất (upload connect 10 s + 30 s).
#define WATCHDOG_TIMEOUT_MS      60000
#define RETRY_BACKOFF_MIN_MS     5000UL
#define RETRY_BACKOFF_MAX_MS   120000UL

#endif // CAMERA_REGISTRY_CONFIG_H
