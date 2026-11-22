// TÙY CHỌN (firmware >= 3.0.0). Không có file này firmware vẫn biên dịch và chạy:
//   - Wi-Fi nhập trong portal "DurianCare-Setup-XXXX" (lưu NVS);
//   - khóa camera nhận khi kích hoạt bằng mã nhận (nhập trong portal, lưu NVS).
// Chỉ dùng file này để giữ cấu hình cũ (Wi-Fi mặc định + khóa trong CAMERA_DEVICE_BINDINGS của Backend).
// Nếu dùng: sao chép thành "camera_secrets.h". KHÔNG commit, KHÔNG gửi file camera_secrets.h cho người khác.
#ifndef CAMERA_SECRETS_H
#define CAMERA_SECRETS_H

#define WIFI_SSID          "your-ssid"
#define WIFI_PASSWORD      "your-wifi-password"

// Trùng CHÍNH XÁC "key" của camera trong CAMERA_DEVICE_BINDINGS (>= 32 ký tự), hoặc để "" nếu kích hoạt bằng mã nhận.
// Firmware không bao giờ in khóa này.
#define CAMERA_DEVICE_KEY  ""

#endif // CAMERA_SECRETS_H
