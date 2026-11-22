#ifndef SENSOR_SECRETS_H
#define SENSOR_SECRETS_H
// TÙY CHỌN (firmware >= 2.0.0): Wi-Fi mặc định khi NVS chưa có cấu hình từ portal "DurianCare-Setup-XXXX".
// Không có file này firmware vẫn biên dịch và chạy (mở portal để nhập Wi-Fi).
// Nếu dùng: sao chép thành sensor_secrets.h. KHÔNG commit/chia sẻ sensor_secrets.h.
#define WIFI_SSID     "ten-wifi"
#define WIFI_PASSWORD "mat-khau-wifi"
#endif
