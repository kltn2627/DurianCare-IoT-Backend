# Kết nối chưa xác minh và câu hỏi cho chủ dự án

Rev B · 29/10/2026. Mỗi mục dưới đây được đánh dấu ⚠ **CẦN XÁC NHẬN** trên wiring diagram, sơ đồ nguyên lý và
hướng dẫn lắp ráp. Khi đã có câu trả lời: sửa `CONNECTIONS` hoặc ghi chú trong `generate_hardware_docs.py`, đổi
`status`, rồi chạy lại script.

## 1. Những gì đã xác minh và bằng chứng

| Mục | Bằng chứng |
|---|---|
| Chip của esp32-01 là ESP32-D0WD-V3, MAC `8c:94:df:69:1c:6c` | `esptool read-mac` trên COM3 |
| DHT22 ở GPIO18 hoạt động | `#define DHTPIN 18`; dữ liệu thật 33 °C / 81 % (22/10/2026) |
| Cảm biến đất đọc ở GPIO34 | `#define SOIL_PIN 34` (chỉ có trong firmware; dữ liệu nghi lỗi) |
| ESP32-CAM là board AI-Thinker, pin map camera, LED GPIO4 | `board_config.h`, `camera_pins.h` (đối chiếu tự động: 17/17 hằng số khớp) |
| Camera từng gửi ảnh | backend nhận ảnh 19/10/2026 (xác minh gián tiếp, chưa đọc PID cảm biến) |

## 2. Kết nối và linh kiện chưa xác minh

| # | Mục | Đang giả định trên sơ đồ | Rủi ro nếu sai |
|---|---|---|---|
| U1 | Loại board ESP32 | DevKit V1 30 chân (DOIT), suy từ chú thích "D18", "D34" trong firmware | Vị trí chân trên hình khác board thật (nối theo tên chân thì vẫn đúng) |
| J1 | Cách chia chân 3V3 duy nhất cho S1 và S2 | Có một điểm chia (breadboard, dây chữ Y hoặc mối hàn) | Một cảm biến không có nguồn |
| W2 | S1 đang cấp 3V3 hay 5V | 3V3 | Nếu cấp 5V, DATA có thể lên 5 V qua điện trở kéo lên của module |
| W3 | S2 đang cấp 3V3 hay 5V | 3V3 | Nếu 5V: AO > 3,3 V, có thể hỏng GPIO34 |
| W7 | AO thực sự nối vào D34 | Có | Giá trị thô ≈ 0 trong log là dấu hiệu sai dây hoặc mất nguồn |
| R1 | S1 là module 3 chân hay cảm biến 4 chân trần | Module 3 chân (không cần R1) | Đọc lỗi / NaN khi thiếu điện trở kéo lên |
| P1 | Loại đầu dò (YL-69 điện trở hay cảm biến điện dung) | YL-69 + LM393 (theo báo cáo B) | Hiệu chuẩn 3200/1200 không đúng |
| PS1 | Nguồn USB của trạm | Sạc / PC 5 V | — |
| J2 | Bộ nạp ESP32-CAM: đế MB hay adapter USB-TTL (loại nào) | Vẽ cả hai phương án | Hướng dẫn nạp sai |
| C1 | Adapter có cấp đủ dòng cho ESP32-CAM | Ghi chú "chỉ khi đủ dòng" | Brownout khi nạp |
| PS2 | Nguồn ESP32-CAM khi vận hành | 5 V ≥ 1 A (khuyến nghị, chưa đo) | Reset liên tục, ảnh hỏng |
| GND | Chân "GND" trên bản clone ESP32-CAM | Theo nhãn AI-Thinker | Một số clone in nhãn khác → đo thông mạch |

## 3. Câu hỏi cần bạn trả lời

1. Board của esp32-01 là loại nào? Gửi ảnh chụp mặt trên board có chữ in cạnh chân là đủ.
2. DHT22 là module 3 chân hay cảm biến 4 chân trần? Chân VCC của nó đang nối vào 3V3 hay 5V/VIN?
3. Module ẩm đất đang lấy nguồn từ 3V3 hay 5V/VIN? Dây AO có thực sự vào chân D34 không? Nếu được, đo giúp điện áp AO
   (đầu dò khô và ướt).
4. Chân 3V3 đang được chia cho hai cảm biến bằng cách nào: breadboard, dây chữ Y hay hàn?
5. Đầu dò đất là loại hai nhánh kim loại (YL-69) hay loại điện dung (v1.2, có mạch trên thân)?
6. ESP32-CAM được nạp bằng đế ESP32-CAM-MB hay adapter USB-TTL (CP2102 / CH340 / FT232)? Adapter có jumper 3V3/5V không?
7. Khi vận hành, ESP32-CAM lấy nguồn từ đâu, điện áp và dòng định mức bao nhiêu?
8. Có thêm linh kiện nào ngoài danh sách (hộp, pin/solar, mạch hạ áp, breadboard) cần đưa vào BOM không?
9. Cảm biến ánh sáng hoặc cảm biến mưa có tồn tại về mặt vật lý không? Hiện tài liệu không vẽ chúng vì không có bằng
   chứng.

## 4. Điểm không nhất quán đã phát hiện

| # | Ở đâu | Nội dung | Tài liệu mới theo | Đề xuất |
|---|---|---|---|---|
| 1 | Báo cáo B | DHT22 ở GPIO23 | Firmware GPIO18 (dữ liệu thật chứng minh) | Sửa báo cáo B |
| 2 | Báo cáo B | Quy đổi ẩm đất `map(raw, 0, 4095, 0, 100)` | Firmware `map(raw, 3200, 1200, 0, 100)` | Sửa báo cáo B; hiệu chuẩn lại sau khi kiểm tra dây |
| 3 | Báo cáo B | Cảm biến mưa GPIO35, trường `light` | Không có trong firmware | Ghi là hướng phát triển |
| 4 | Sơ đồ cũ `durian_iot_schematic.*` | Nối 3V3 tới cả hai cảm biến nhưng không cho thấy một chân phải chia đôi | J1 "điểm chia 3V3", ⚠ | Dùng bộ Rev B; giữ sơ đồ cũ để tham chiếu |
| 5 | `HARDWARE.md` (Rev A) | Nguồn ESP32-CAM "5 V ≥ 500 mA" | "≥ 1 A khuyến nghị" (cả hai đều là giả định) | Đã thống nhất trong `HARDWARE.md` |
| 6 | Log thiết bị | AO ≈ 0 gần như mọi lần | Sơ đồ giữ đúng GPIO34 theo firmware | Đo theo mục 7 của hướng dẫn |

Giữa wiring diagram, sơ đồ nguyên lý và firmware **không có điểm vênh nào**: cả 16 kết nối và 19 hằng số GPIO đều khớp
(xem `DurianCare_Consistency_Check.md`).
