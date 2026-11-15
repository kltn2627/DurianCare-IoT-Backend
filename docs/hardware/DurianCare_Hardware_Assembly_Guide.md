# Hướng dẫn lắp ráp phần cứng DurianCare IoT

Rev B · 29/10/2026 · Tài liệu cho người mới lắp mạch và cho phần trình bày đồ án.

Bộ tài liệu gồm ba sản phẩm tách riêng:

| Sản phẩm | Dùng để | Tệp |
|---|---|---|
| A. Sơ đồ lắp đặt (Wiring Diagram) | Cắm dây theo hình thực tế | `DurianCare_Wiring_Diagram.pdf` (A4 ngang, 2 trang), `_p1/_p2.png` 300 dpi, `.svg`, `.drawio` |
| B. Sơ đồ nguyên lý (Electrical Schematic) | Báo cáo kỹ thuật, giám khảo | `DurianCare_Electrical_Schematic.pdf` (2 tờ), `_p1/_p2.png`, `.svg`, `.drawio` |
| C. Hướng dẫn lắp ráp (tệp này) | Làm theo từng bước, kiểm tra trước khi cấp nguồn | tệp này, ảnh trong `assembly/` |

Kèm theo: `DurianCare_Pin_Mapping.csv`, `DurianCare_BOM.csv`, `DurianCare_Unverified_And_Questions.md`,
`DurianCare_Consistency_Check.md`. Mọi hình đều sinh từ `generate_hardware_docs.py`. Sửa netlist trong tệp đó rồi chạy
lại, không sửa tay từng hình.

> **Quy ước:** ⚠ **CẦN XÁC NHẬN** đánh dấu những gì chưa kiểm tra trên phần cứng thật. Không coi các mục này là
> hướng dẫn đã đúng. Nét đứt trên hình cũng mang nghĩa đó. Màu dây trên hình chỉ để minh họa. Hãy nối theo **tên chân**
> in trên board, không theo màu dây và không theo vị trí trên hình.

> **Không khẳng định:** sơ đồ không chứng minh thiết bị hoạt động. Trạm cảm biến đã gửi dữ liệu DHT22 thật về backend.
> Camera mới được xác minh gián tiếp (backend nhận ảnh ngày 19/10/2026). Kiểm thử camera sẽ làm ở giai đoạn riêng.

---

## 1. Hai thiết bị độc lập

| | Thiết bị 1 — Trạm cảm biến | Thiết bị 2 — Camera |
|---|---|---|
| Board | U1 ESP32 DevKit (chip ESP32-D0WD-V3, MAC đã đọc bằng esptool) | U2 AI-Thinker ESP32-CAM (OV2640, PSRAM) |
| Firmware | `sketch_jul25a` 1.3.0 | `CameraWebServer` 2.1.0 |
| Ngoại vi | S1 DHT22 (GPIO18), S2 + P1 đo ẩm đất (GPIO34) | Camera và LED flash có sẵn trên board |
| Liên lạc | Wi-Fi → HTTP `POST /api/v1/sensors/data`, tìm máy chủ qua UDP 4210 | Wi-Fi → đăng ký, heartbeat, backend lấy ảnh |

Hai thiết bị **không có dây điện nào nối với nhau**. Trên hình, kết nối Wi-Fi được vẽ bằng ký hiệu riêng (nét chấm xanh
ngọc), không phải dây.

## 2. Bảng nối dây

Thứ tự cột Bước là thứ tự nên làm. Cột "Trạng thái" lấy từ netlist:

- **firmware**: GPIO khai báo trong firmware.
- **bắt buộc**: dây nguồn/GND phải có về điện, nhưng cách nối thật chưa được kiểm tra.
- **⚠**: cần xác nhận.

### Thiết bị 1 — Trạm cảm biến

| Bước | Dây | Linh kiện nguồn | Chân nguồn | Linh kiện đích | Chân đích | Loại kết nối | Trạng thái | Lưu ý |
|---|---|---|---|---|---|---|---|---|
| 3a | W4 | U1 ESP32 DevKit | GND (cạnh 3V3) | S1 DHT22 | GND (−) | Mass | bắt buộc | |
| 3a | W5 | U1 ESP32 DevKit | GND (cạnh VIN) | S2 module ẩm đất | GND | Mass | bắt buộc | |
| 3b | W1 | U1 ESP32 DevKit | 3V3 | J1 điểm chia 3V3 | — | Nguồn 3,3 V | bắt buộc, ⚠ cách chia | Một chân 3V3 cấp cho hai cảm biến |
| 3b | W2 | J1 | — | S1 DHT22 | VCC (+) | Nguồn 3,3 V | bắt buộc, ⚠ đang cấp 3V3 hay 5V | DHT22 chạy 3,3–5,5 V |
| 3b | W3 | J1 | — | S2 module ẩm đất | VCC | Nguồn 3,3 V | bắt buộc, ⚠ đang cấp gì | **Không cấp 5V** (xem mục 5) |
| 4 | W6 | U1 ESP32 DevKit | D18 (GPIO18) | S1 DHT22 | DATA / OUT | Số, 1-wire | firmware + dữ liệu thật | `#define DHTPIN 18` |
| 4 | W7 | U1 ESP32 DevKit | D34 (GPIO34) | S2 module ẩm đất | AO | Analog | firmware, ⚠ giá trị ≈ 0 | `#define SOIL_PIN 34` |
| 4 | W8 | S2 module ẩm đất | 2 chân đầu dò | P1 đầu dò | 2 cực | Cáp 2 sợi | ⚠ | Không phân cực |
| 4 | — | S2 module ẩm đất | DO | — | — | Không nối | firmware không dùng | |
| 4 | — | R1 10 kΩ | DATA | — | VCC | Điện trở kéo lên | ⚠ | Chỉ khi S1 là cảm biến 4 chân trần |
| 8 | W9 | PS1 nguồn USB 5 V | USB | U1 | micro-USB | Cáp USB | ⚠ loại nguồn | |

### Thiết bị 2 — Camera

| Bước | Dây | Linh kiện nguồn | Chân nguồn | Linh kiện đích | Chân đích | Loại kết nối | Trạng thái | Lưu ý |
|---|---|---|---|---|---|---|---|---|
| 6 | C2 | J2 adapter USB-TTL | GND | U2 ESP32-CAM | GND (cạnh 5V) | Mass | bắt buộc | Nối GND trước |
| 6 | C1 | J2 adapter USB-TTL | 5V | U2 ESP32-CAM | 5V | Nguồn 5 V | ⚠ đủ dòng? | Không nối vào chân VCC hay 3V3 |
| 6 | C3 | J2 adapter USB-TTL | TXD | U2 ESP32-CAM | U0R (GPIO3) | UART, nối chéo | bắt buộc | Mức logic 3,3 V |
| 6 | C4 | J2 adapter USB-TTL | RXD | U2 ESP32-CAM | U0T (GPIO1) | UART, nối chéo | bắt buộc | |
| 6 | C5 | U2 ESP32-CAM | IO0 | U2 ESP32-CAM | GND (cạnh IO0) | Jumper chế độ nạp | bắt buộc khi nạp | **Tháo ra sau khi nạp** |
| 7 | C7 | PS2 nguồn 5 V | GND | U2 ESP32-CAM | GND (cạnh 5V) | Mass | bắt buộc | |
| 7 | C6 | PS2 nguồn 5 V | +5V | U2 ESP32-CAM | 5V | Nguồn 5 V | ⚠ nguồn thật | Khuyến nghị ≥ 1 A |

Nếu dùng đế **ESP32-CAM-MB**, không cần C1–C5. Chỉ cần cắm ESP32-CAM vào đế (panel A trên trang 2 của wiring diagram).

## 3. Bước 1 — Chuẩn bị

Xem `DurianCare_BOM.csv`. Danh sách tối thiểu:

- **Trạm cảm biến:** U1, S1, S2 + P1, dây jumper, cáp USB và nguồn 5 V (PS1). Thêm R1 10 kΩ nếu S1 là cảm biến 4 chân trần.
- **Camera:** U2, một bộ nạp (adapter USB-TTL 3,3 V **hoặc** đế ESP32-CAM-MB), nguồn 5 V ≥ 1 A (PS2) cho vận hành.
- **Dụng cụ:** đồng hồ vạn năng (bắt buộc cho mục 5), máy tính có Arduino IDE / arduino-cli với core ESP32.

Không có bằng chứng dự án dùng breadboard, cảm biến ánh sáng, cảm biến mưa hay ADC ngoài (ADS1115). Tài liệu này không
đưa chúng vào.

## 4. Bước 2 — Xác định đúng model và pinout

1. **U1:** đọc chữ in cạnh các chân. Nếu thấy `D18`, `D34`, `VIN`, `3V3` và hai hàng 15 chân thì đó là DevKit V1 30 chân,
   khớp hình (chú thích trong firmware cũng gọi chân là "D18", "D34"). Board 38 chân (DevKitC) có thứ tự khác: vẫn nối
   theo **tên** GPIO18 / GPIO34 / 3V3 / GND. ⚠ CẦN XÁC NHẬN loại board.
2. **S1 DHT22:**
   - Module 3 chân (`+`, `out`, `−`) thường đã có điện trở kéo lên, không cần R1.
   - Cảm biến trần 4 chân, nhìn mặt lưới từ trái sang phải: 1 VCC, 2 DATA, 3 bỏ trống, 4 GND. Loại này cần R1 10 kΩ nối
     DATA với VCC (datasheet AM2302 gợi ý 5,1 kΩ).
3. **S2:** module so sánh LM393 có 4 chân `VCC GND DO AO` và 2 chân nối đầu dò P1. Chỉ dùng `AO`.
4. **U2 ESP32-CAM:**
   - Hàng chân bên trái: `5V GND IO12 IO13 IO15 IO14 IO2 IO4`.
   - Hàng chân bên phải: `3V3 IO16 IO0 GND VCC U0R U0T GND`.
   - Chân `VCC` là **ngõ ra** 3,3 V (hoặc 5 V tùy cầu hàn), không phải ngõ vào nguồn.
   - Một số bản clone in nhãn GND khác nhau. Dùng đồng hồ đo thông mạch với vỏ khung USB/shield để xác nhận chân GND.
5. **J2 adapter USB-TTL:** tìm jumper chọn mức logic (3V3/5V) và đặt ở **3V3**. Chân ESP32 không chịu được 5 V.

## 5. Bước 3 — Nối GND và nguồn (chưa cắm USB)

![Bước 3a — GND](assembly/step1_gnd.png)

**3a.** Nối GND trước:

- W4: chân `GND` cạnh `3V3` của U1 → `GND (−)` của S1.
- W5: chân `GND` cạnh `VIN` của U1 → `GND` của S2.

![Bước 3b — 3V3](assembly/step2_3v3.png)

**3b.** Nối 3V3:

- W1 đi từ `3V3` của U1 tới điểm chia J1.
- Từ J1, W2 đi tới `VCC (+)` của S1 và W3 đi tới `VCC` của S2.
- U1 chỉ có **một** chân 3V3, nên phải chia nguồn. Có thể dùng thanh nguồn breadboard, dây chữ Y hoặc mối hàn.
  ⚠ Cách đang dùng CẦN XÁC NHẬN.
- **Không** lấy nguồn cho S2 từ `VIN`/`5V`. AO của module này tỉ lệ với VCC: cấp 5 V thì AO có thể lên tới khoảng 4–5 V,
  vượt mức 3,3 V tối đa của GPIO34.

## 6. Bước 4 — Nối tín hiệu

![Bước 4 — tín hiệu](assembly/step3_signals.png)

- W6: `D18` → `DATA/OUT` của S1.
- W7: `D34` → `AO` của S2.
- W8: hai chân đầu dò của S2 → hai cực của P1. Đầu dò không phân cực.
- `DO` của S2 để trống.
- Không đổi sang chân khác. Firmware cố định GPIO18 và GPIO34; GPIO34 thuộc ADC1, nên vẫn đọc được khi Wi-Fi bật
  (ADC2 thì không).

## 7. Bước 5 — Kiểm tra trước khi cấp nguồn

**Đo khi chưa cấp nguồn** (đồng hồ để thang thông mạch / điện trở):

| # | Kiểm tra | Kết quả đúng |
|---|---|---|
| 1 | `3V3` ↔ `GND` của U1 | **Không** thông mạch (không chập) |
| 2 | `VCC` của S2 ↔ `VIN` của U1 | Không thông (S2 không lấy 5 V) |
| 3 | `D18` ↔ `DATA` của S1 | Thông |
| 4 | `D34` ↔ `AO` của S2 | Thông; `D34` ↔ `DO` **không** thông |
| 5 | `GND` của S1, S2 ↔ `GND` của U1 | Thông |
| 6 | Nếu có R1: `DATA` ↔ `VCC` của S1 | ≈ 10 kΩ (hoặc 5,1 kΩ) |

**Đo sau khi cắm USB:**

| # | Đo | Giá trị đúng | Nếu sai |
|---|---|---|---|
| 7 | `3V3` – `GND` tại U1 | 3,2–3,4 V | Rút USB, tìm chập mạch |
| 8 | `VCC` – `GND` tại S1 và S2 | 3,2–3,4 V | Kiểm tra W1–W3 và điểm chia J1 |
| 9 | `AO` – `GND`, đầu dò ngoài không khí (khô) | Lớn, gần mức VCC | Nếu ≈ 0 V: AO chập GND, mất VCC, sai chân hoặc module hỏng |
| 10 | `AO` – `GND`, đầu dò cắm trong nước/đất ướt | Nhỏ hơn rõ rệt so với lúc khô | Nếu không đổi: đầu dò hoặc cáp W8 hỏng |
| 11 | `AO` lớn nhất | ≤ 3,3 V | Nếu > 3,3 V: S2 đang cấp 5 V. Rút điện ngay |

Hiện log thật cho thấy giá trị thô của AO ≈ 0 gần như mọi lần (có một lần 1566). Phải làm các phép đo 9–11 trước khi
tin số liệu độ ẩm đất. Hằng số hiệu chuẩn `3200 → 0 %` và `1200 → 100 %` trong firmware là số thực nghiệm; hiệu chuẩn
lại sau khi sửa dây.

## 8. Bước 6 — ESP32-CAM: chế độ nạp firmware

![Bước 6 — nạp firmware](assembly/step4_cam_flash.png)

**Phương án A — đế ESP32-CAM-MB:**

1. Cắm ESP32-CAM vào đế, nối USB với máy tính.
2. Giữ nút IO0, nhấn rồi thả RST, sau đó thả IO0. Một số đế tự vào chế độ nạp.
3. Bấm Upload. Nạp xong, nhấn RST để chạy.

**Phương án B — adapter USB-TTL:**

1. Đặt jumper mức logic của adapter ở **3V3**.
2. Nối dây theo thứ tự C2 (GND), C1 (5V), C3 (TXD → U0R), C4 (RXD ← U0T).
3. Cắm jumper C5 nối `IO0` với `GND` ngay cạnh nó, rồi nhấn RST (hoặc cấp nguồn lại).

**Kiểm tra UART:**

- Mở Serial Monitor 115200 baud, nhấn RST.
- Nếu thấy dòng có `waiting for download` thì board đã vào chế độ nạp.
- Nếu không thấy gì:
  - Đảo thử C3 và C4 (lỗi phổ biến nhất).
  - Kiểm tra GND chung.
  - Kiểm tra đúng cổng COM.
- Nếu reset liên tục hoặc báo `Brownout detector was triggered`: nguồn 5 V yếu. Dùng nguồn 5 V riêng (PS2), vẫn nối
  GND chung với adapter.
- Chỉ nạp khi đã xác nhận đúng board ESP32-CAM; trạm esp32-01 dùng cổng khác.

## 9. Bước 7 — ESP32-CAM: chế độ vận hành

![Bước 7 — vận hành](assembly/step5_cam_run.png)

1. **Tháo C5** (IO0–GND). Khi chạy, GPIO0 là xung XCLK cấp cho camera; để nối GND thì camera không khởi động.
2. Tháo C3/C4 nếu không cần xem log.
3. Cấp 5 V qua C6/C7 (PS2 ≥ 1 A) hoặc qua đế MB, rồi nhấn RST.
4. Xem log 115200 baud: firmware in `[boot] DurianCare … firmware …` khi khởi động.
5. Camera chỉ được coi là hoạt động khi backend nhận được heartbeat và ảnh mới. Kiểm thử này thuộc giai đoạn riêng
   (`../CAMERA_HARDWARE_TEST_PLAN.md`).

## 10. Bước 8 — Kiểm tra lại toàn bộ trước khi vận hành

**Checklist trước khi cấp nguồn:**

- [ ] Đúng model U1/U2, đã đọc chữ in cạnh chân (mục 4).
- [ ] S2 cấp **3V3**, không phải 5 V.
- [ ] `D34` nối `AO`, không phải `DO`.
- [ ] `D18` nối `DATA`; có R1 nếu S1 là cảm biến 4 chân trần.
- [ ] Không có chân nào của U1 nối với `VIN` ngoài cáp USB.
- [ ] Đã đo không chập `3V3`–`GND` (mục 7, phép đo 1).
- [ ] Camera: adapter ở mức logic 3V3; TX/RX nối chéo; GND chung.
- [ ] Camera: đã **tháo IO0–GND** trước khi vận hành.
- [ ] Không cấp nguồn vào chân `VCC` hoặc `3V3` của ESP32-CAM.
- [ ] Hai thiết bị không có dây điện nối với nhau.

## 11. Lỗi đấu dây thường gặp và cách nhận biết

| Lỗi | Dấu hiệu | Cách xử lý |
|---|---|---|
| DATA DHT22 nhầm chân / lỏng | Log in `DHT22: ⏳ Đang đợi phản hồi...`, không có nhiệt độ | Kiểm tra W6 về D18, nguồn S1, R1 |
| DHT22 thiếu điện trở kéo lên | Đọc lúc được lúc không, NaN | Thêm R1 nếu là cảm biến trần |
| AO cắm nhầm DO | Giá trị chỉ là 0 hoặc 4095, đổi đột ngột theo biến trở | Chuyển W7 sang AO |
| S2 mất VCC hoặc AO chập GND | Giá trị thô ≈ 0 mọi lúc (giống log hiện tại) | Đo theo mục 7 phép đo 8–9 |
| S2 cấp 5 V | AO > 3,3 V, có thể hỏng GPIO34 | Rút điện, chuyển S2 sang 3V3 |
| Dùng chân ADC2 thay GPIO34 | Đọc lỗi khi Wi-Fi bật | Giữ GPIO34 (ADC1) |
| ESP32-CAM: TX/RX không chéo | Không thấy log, upload báo không kết nối | Đảo C3/C4 |
| ESP32-CAM: quên tháo IO0–GND | Board kẹt ở `waiting for download`, camera không chạy | Tháo C5, nhấn RST |
| ESP32-CAM: nguồn yếu | Reset vòng, `Brownout detector was triggered`, ảnh hỏng | Nguồn 5 V riêng ≥ 1 A, dây ngắn |
| Cấp nguồn vào chân VCC của ESP32-CAM | Board không lên hoặc nóng | Chỉ cấp vào chân 5V |
| Adapter để mức logic 5 V | Có thể hỏng U0R | Đặt jumper 3V3 |

## 12. Bảng pin mapping

| Thiết bị | Tín hiệu | GPIO | Nhãn trên board | Hướng | Nguồn |
|---|---|---|---|---|---|
| U1 | DHT22 DATA | 18 | D18 | vào/ra (1-wire) | `#define DHTPIN 18` |
| U1 | Ẩm đất AO | 34 | D34 | vào analog, ADC1_CH6 | `#define SOIL_PIN 34` |
| U2 | UART0 RX / TX | 3 / 1 | U0R / U0T | nạp, log | ROM bootloader |
| U2 | Chọn chế độ khởi động + XCLK | 0 | IO0 | vào (khi boot) / ra XCLK | `XCLK_GPIO_NUM 0` |
| U2 | LED flash | 4 | IO4 | ra | `LED_GPIO_NUM 4` |
| U2 | Camera DVP Y2–Y9 | 5, 18, 19, 21, 36, 39, 34, 35 | trên board | vào | `camera_pins.h` |
| U2 | VSYNC / HREF / PCLK | 25 / 23 / 22 | trên board | vào | `camera_pins.h` |
| U2 | SCCB SDA / SCL, PWDN | 26 / 27, 32 | trên board | — | `camera_pins.h` |

Bảng đầy đủ theo từng dây nằm trong `DurianCare_Pin_Mapping.csv`. Kết quả đối chiếu tự động nằm trong
`DurianCare_Consistency_Check.md`.
