# DurianCare IoT — Troubleshooting

| Triệu chứng (log Serial / UI) | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| `SERVER_NOT_DISCOVERED` | Beacon không chạy, laptop khác mạng, AP isolation | `check-beacon.ps1`; nếu task "NOT INSTALLED" chạy `install-beacon-task.ps1`; xem `%USERPROFILE%\.duriancare\discovery-beacon.log`; đảm bảo thiết bị và laptop cùng Wi-Fi (gateway in trong log) |
| Beacon `UNHEALTHY (HTTP_UNREACHABLE)` | Docker/iot-service chưa chạy | `docker ps`; chờ iot-service "(healthy)" |
| Beacon `UNHEALTHY (NO_LAN)` | Laptop chưa có IPv4 Wi-Fi/Ethernet | Kết nối Wi-Fi |
| `[wifi] Lượt kết nối hết hạn (status 1)` | Không thấy SSID (sai tên, 5 GHz, quá xa, nhiễu) | Đưa AP lại gần; kiểm tra `*_secrets.h` |
| `status 6`/`status 4` lặp | Sai mật khẩu hoặc sóng yếu | Kiểm tra mật khẩu; RSSI trong log `[wifi] OK … RSSI` nên ≥ −75 dBm |
| `IoT Push … HTTP_STATUS 404 DEVICE_NOT_REGISTERED` / `409 DEVICE_NOT_CLAIMED` | Thiết bị chưa provision/claim | Admin provision, nông dân claim trên Web |
| Đất `NO_SIGNAL` (raw ≈ 0) | AO/VCC chưa nối, chập | Kiểm tra dây GPIO34, cấp 3V3 |
| Đất `SATURATED` (raw ≈ 4095) | Đầu dò hở / chưa cắm đất | Cắm đầu dò vào đất, kiểm tra cáp 2 sợi |
| Đất `UNSTABLE` | ADC thả nổi (dây lỏng) | Siết dây, nối GND chung |
| Đất `UNCALIBRATED` | Chưa hiệu chuẩn | Xem `IOT_HARDWARE_STARTUP_GUIDE.md` §6 |
| Camera `CAMERA_INIT_FAILED`, `[diag] SCCB ACK at: none` | Cảm biến camera không phản hồi: cáp FPC lỏng; hoặc khởi tạo lại lúc đang chạy (board này chỉ init chắc chắn lúc boot) | Firmware 2.2.2+ tự khởi động lại sau 2 lần lỗi; nếu vẫn lỗi lúc boot, cắm lại cáp camera. Xem dòng `[power]` để loại trừ sụt nguồn (vdd33 ổn định, không `BROWNOUT`) |
| Camera UI "Hoạt động hạn chế" | Heartbeat có nhưng chụp thử lỗi | Như trên; thiết bị tự thử lại 30 s, sau 2 lần lỗi khởi tạo tự khởi động lại |
| Camera "Offline" | Không heartbeat > 5 phút | Nguồn, Wi-Fi, beacon |
| `/capture` chậm/không ra ảnh, `[registry] read Timeout` | RSSI ≤ −85 dBm | Cải thiện sóng (AP gần hơn, repeater) |
| `[wifi-scan] … KHÔNG thấy SSID cấu hình` | Camera ngoài vùng phủ / ăng-ten bị che / điện trở chọn ăng-ten IPEX mà không có ăng-ten | Xoay/đặt lại camera, đưa AP gần hơn; xem `IOT_HARDWARE_STARTUP_GUIDE.md` §6 |
| `reset reason … BROWNOUT` | Sụt nguồn thật | Đo bằng đồng hồ USB; dùng hub USB có nguồn riêng (5 V ≥ 2 A), mỗi board một cáp |
| Camera tự reset khi mở COM4 | Mạch auto-reset của đế ESP32-CAM-MB | Không để chương trình serial mở COM4 khi vận hành |
| `[registry] 401` | `CAMERA_DEVICE_KEY` không khớp `CAMERA_DEVICE_BINDINGS` | Đồng bộ khóa (không in khóa ra log) |
| `[registry] 400/409` | Đồng hồ lệch > 30 s | Thiết bị tự đồng bộ lại (SNTP / HTTP Date của Backend) |
| Arduino "Access to the port COMx is denied" | Serial Monitor đang mở | Đóng Serial Monitor |
| `assert failed: xQueueSemaphoreTake` khi boot camera | (Đã sửa ở 2.2.0) HTTP server khởi động trước TCP/IP stack | Dùng firmware camera 2.2.3 hiện tại |

## Phục hồi nhanh

- Mất Wi-Fi/Backend: không cần làm gì — thiết bị tự thử lại (backoff tối đa 60 s) và dò lại máy chủ.
- Khôi phục firmware cũ: `esptool --port COMx --baud 115200 write-flash 0 Documents/Arduino/_backup_2026-10-31/<board>_flash_before_*.bin`.
- Khôi phục iot-service: đổi image về `duriancare/iot-service:pre-hardening-2026-10-31` rồi `docker compose -p duriancare up -d --no-deps duriancare-iot-service`.
