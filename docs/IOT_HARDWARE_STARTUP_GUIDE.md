# DurianCare IoT — Cấp nguồn USB và vận hành phần cứng

Phương án nguồn: **chỉ USB từ laptop** (không pin, không ắc quy). Đo và kiểm thử ngày 31/10/2026 trên thiết bị thật.

## 1. Vận hành hằng ngày

1. Bật laptop, đăng nhập Windows (Docker Desktop và beacon discovery tự khởi động, chờ khoảng 2 phút).
2. Cắm cáp USB của **ESP32 cảm biến** và **đế ESP32-CAM-MB** vào laptop.
3. Xong. Không mở Arduino IDE, không mở Serial Monitor, không bấm nút, không nạp lại firmware.

Đã kiểm chứng (Arduino IDE đóng, rút cả 2 cáp 10 giây rồi cắm lại):
- **ESP32 cảm biến:** tự khởi động, tự vào Wi-Fi, tự tìm máy chủ, gửi đều 6/6 bản ghi mỗi phút trong 13 phút liên tục.
- **ESP32-CAM:** tự khởi động, camera tự kiểm tra OK, tự đăng ký Backend sau khoảng 20 giây (13:00:48 UTC).

## 2. Cấu hình USB đang dùng (đã xác minh trong Windows)

| Thiết bị | Mạch chuyển USB | Đường USB tới laptop | Cấp nguồn cho board |
|---|---|---|---|
| ESP32 cảm biến (`esp32-01`, MAC `8c:94:df:69:1c:6c`) | CP210x tích hợp trên board (VID 10C4 / PID EA60), COM3 | Qua hub USB 2.0 **WCH CH334/CH335** (VID 1A86 / PID 8095, không nguồn riêng), rồi vào cổng USB 3 của laptop | 5 V USB → LDO 3,3 V trên board |
| ESP32-CAM (`ESP32-CAM-001`, MAC `68:09:47:2d:8e:60`) | Đế **ESP32-CAM-MB** với FT232R (VID 0403 / PID 6001), COM4; tự vào chế độ nạp qua DTR/RTS | Cắm thẳng vào cổng USB 3 của laptop (bộ điều khiển AMD USB 3.10) | 5 V USB → chân 5V của ESP32-CAM → LDO 3,3 V trên ESP32-CAM |

Laptop: ASUS ROG Zephyrus G14 GA401QC. Theo chuẩn USB, mỗi cổng USB 3 Type-A cấp tối đa 900 mA ở 5 V; cổng đi qua hub không nguồn thì chia chung giới hạn của cổng phía trên.

## 3. Đo nguồn trên board (không cần pin)

Firmware camera 2.2.x tự đo lúc khởi động và in ra serial: `[power] … vdd33 min/avg/max … | RSSI … | khung hình …`.
VDD33 là giá trị raw do PHY của ESP32 tự đo (chưa quy ra mV), nên chỉ so được giữa các lần đo **cùng điều kiện** trên cùng board. Camera đo ở ba trạng thái; cảm biến 1.4.x in giá trị vdd33 kèm mỗi lần đọc.

| Lần đo trên ESP32-CAM, Wi-Fi đã kết nối | vdd33 avg (min) | RSSI | Ghi chú |
|---|---|---|---|
| Camera tắt | 6577 (6545) | −82 dBm | |
| Camera bật, đứng yên | 6571 (6568) | −82 dBm | |
| Camera chụp liên tục 1,5 giây | 6572 (6568) | −78 dBm | 21 khung OK, 0 lỗi |

Kết luận:
- Rail 3,3 V thay đổi dưới 1% khi khởi động camera, khi phát Wi-Fi và khi chụp liên tục.
- Reset reason luôn là `POWERON`/`SOFTWARE`, chưa lần nào là `BROWNOUT`.
- Không có bằng chứng sụt nguồn trên USB laptop với hai board chạy cùng lúc. Kết luận "thiếu nguồn" trong bản tài liệu trước bị **rút lại**.

**Chưa đo được bằng phần mềm:** dòng tiêu thụ tuyệt đối (mA), điện áp 5 V thực tế tại chân board, và sụt áp trên cáp. Muốn có số tuyệt đối, cắm một **đồng hồ USB (USB power meter)** giữa laptop và từng cáp. Theo datasheet, ESP32-CAM tiêu thụ đỉnh khoảng 300–400 mA khi phát Wi-Fi và chụp; ESP32 DevKit khoảng 250 mA; cả hai đều nằm trong 900 mA của một cổng USB 3.

## 4. Kiểm tra hai board chạy đồng thời

Thử A/B trong 3 + 3 phút:

| Pha | Kết quả cảm biến | Kết quả camera |
|---|---|---|
| Camera bị giữ reset | 9 OK / 3 lỗi | không chạy |
| Camera chạy | 17 OK / 0 lỗi | 7 lần đăng ký/heartbeat 200, 0 lỗi |

Camera chạy song song không làm giảm nguồn hay kết nối của cảm biến. **Không cần** hub USB có nguồn riêng cho cấu hình hiện tại.

Nên dùng hub có nguồn riêng (5 V ≥ 2 A, cắm tường; laptop chỉ nối dữ liệu) khi:
- đồng hồ USB cho thấy 5 V tại board dưới 4,75 V; hoặc
- log xuất hiện `reset reason … BROWNOUT`; hoặc
- cắm thêm thiết bị vào cùng hub CH334 không nguồn hiện tại.

## 5. Quy tắc an toàn nguồn

- Mỗi board chỉ lấy nguồn từ **một** cáp USB. **Không** cấp thêm 5 V ngoài vào chân 5V/3V3 khi USB đang cắm; chưa xác minh đế ESP32-CAM-MB và DevKit có diode chặn dòng ngược.
- Không cấp 5 V vào chân 3V3.
- Cảm biến đất lấy 3V3 của ESP32 (5 V có thể làm tín hiệu AO vượt 3,3 V và hỏng GPIO34).
- Cáp: USB 2.0 data + power ngắn (≤ 1 m), lõi đủ dày. Cáp chỉ sạc (không data) vẫn cấp nguồn được nhưng không nạp được firmware.

## 6. Wi-Fi: nguyên nhân chính khi camera không kết nối

Đo cùng lúc ở cùng chỗ:

| Thiết bị | Tín hiệu tới SSID | Ghi chú |
|---|---|---|
| Laptop | 66% (khoảng −67 dBm), kênh 8, 2,4 GHz | |
| ESP32 cảm biến | −81…−83 dBm | Kết nối ổn định với connect timeout 8 giây (firmware 1.4.2) |
| ESP32-CAM | −78…−87 dBm khi kết nối được | Có lúc firmware 2.2.3 quét **không thấy** SSID (chỉ thấy 1–2 mạng khác) trong khi cảm biến vẫn đang kết nối |

ESP32-CAM thu sóng kém hơn rõ rệt. Theo thứ tự nên làm:
1. Đặt ESP32-CAM sao cho đầu ăng-ten PCB (đường zigzag ở mép board, phía đối diện khe thẻ nhớ) **không bị laptop, kim loại hay tay che**, và hướng về phía router/hotspot.
2. Đưa router/hotspot lại gần, mục tiêu ≥ −75 dBm.
3. Kiểm tra điện trở 0 Ω chọn ăng-ten cạnh đầu IPEX:
   - nếu đang chọn IPEX mà không gắn ăng-ten ngoài, board gần như "điếc";
   - có thể gắn ăng-ten 2,4 GHz IPEX và chuyển điện trở sang IPEX.
   Đây là thay đổi phần cứng: chỉ người có kỹ năng hàn mới làm, và chưa được xác minh trên board này.

**Đo lại sau khi chỉnh vị trí (31/10, 21:10).** Khởi động sạch bằng firmware 2.2.3:
- **Nguồn:** VDD33 6582 / 6574 / 6575 ở ba trạng thái tắt / đứng yên / đang chụp; 21/21 khung hình OK.
- **Wi-Fi:** kết nối được ở −83 dBm rồi rớt sau vài giây; sau đó lúc quét không thấy SSID, lúc thấy ở −82 dBm (kênh 8).
- **So sánh:**
  - Cảm biến (qua hub USB 2.0, xa laptop hơn) vẫn gửi 6/6 bản ghi mỗi phút trong suốt thời gian đo.
  - Ba cấu hình SSID (camera, cảm biến, laptop) giống hệt nhau, nên loại trừ cấu hình sai.
- **Giả thuyết đang mở, chưa xác minh:** ở 1 Mbps, beacon thường vẫn nhận được tới khoảng −95 dBm. Camera "mất" AP ở −83 dBm cho thấy nhiễu nền 2,4 GHz cao sát laptop. Nguồn nhiễu có thể là cổng/thiết bị USB 3 và mạch điện của laptop. Đế ESP32-CAM-MB đang cắm thẳng vào cổng USB 3.
- **Thử nghiệm đề xuất (vẫn chỉ dùng USB laptop, không pin):**
  1. Dùng **cáp nối dài USB 2.0 1–2 m** (có lõi lọc ferrite càng tốt) để đặt camera cách laptop ≥ 1 m, gần router hơn.
  2. Hoặc cắm đế MB vào hub USB 2.0 cùng với cảm biến.
  3. Theo dõi dòng `[wifi] OK … RSSI` và `[wifi-scan]`.

**Kết quả thử nối dài cáp USB 2.0 (31/10, 21:40).** Camera ra xa laptop, firmware 2.2.3, khởi động sạch:
- **Nguồn:** VDD33 6582 / 6568 / 6570; 21/21 khung hình OK.
- **Wi-Fi:** RSSI −86…−87 dBm, **kém hơn** lúc cắm sát laptop (−83 dBm). Camera kết nối lúc boot rồi rớt, sau đó quét không thấy SSID.
- **Kết luận:** giả thuyết nhiễu từ laptop **không đứng vững**. Nguyên nhân có bằng chứng: **tín hiệu từ AP tới vị trí camera quá yếu**. Ở −83…−87 dBm, camera chỉ bám được vài giây.
- **Phân định cuối cùng:**

  | Nguyên nhân | Kết luận |
  |---|---|
  | Nguồn USB | Ổn, không cần pin hay hub có nguồn |
  | Firmware | Đúng: kết nối lúc boot, tự thử lại, tự quét, Backend đánh dấu OFFLINE đúng hạn |
  | Sóng Wi-Fi tại vị trí camera | Không đủ |

- **Cần làm (vẫn chỉ dùng nguồn USB):**
  1. Đặt router/hotspot gần camera, hoặc dùng cáp USB 2.0 dài hơn (tối đa khoảng 3–5 m có ferrite) để đưa camera lại gần AP. Mục tiêu: dòng log `[wifi] OK … RSSI` ≥ −75 dBm.
  2. Hoặc dùng repeater 2,4 GHz đặt gần camera.
  3. Hoặc dùng ăng-ten IPEX ngoài (cần chuyển điện trở chọn ăng-ten; việc này cần hàn, chưa xác minh trên board này).

**Kết quả sau khi đưa AP lại gần camera (31/10, 21:55) — kết luận cuối.**
- Camera: RSSI −88 dBm; kết nối vài giây rồi rớt; sau đó quét thấy **0 mạng**. Nguồn ổn định, 21/21 khung hình OK.
- Laptop giảm từ 66% xuống 60% (AP đã được dời đi). Cảm biến vẫn 6/6 bản ghi mỗi phút.

| Vị trí thử | RSSI camera |
|---|---|
| Cắm sát laptop | −83 dBm |
| Nối dài cáp ra xa laptop | −86…−87 dBm |
| AP đưa lại gần camera | −88 dBm |

RSSI của camera **gần như không đổi qua ba vị trí** và luôn kém laptop khoảng 15–20 dB. Dời AP không làm tín hiệu đổi, nên không phải do khoảng cách mà do **đường ăng-ten của chính board ESP32-CAM-001**.

**Cần kiểm tra board (không cần pin, không đổi phương án nguồn USB):**
1. Nhìn điện trở 0 Ω (hoặc cầu hàn) cạnh đầu IPEX ở mép board:
   - nếu đang nối về IPEX mà không gắn ăng-ten ngoài, board gần như không thu được sóng;
   - cách sửa: gắn ăng-ten 2,4 GHz IPEX, **hoặc** hàn điện trở về phía ăng-ten PCB (cần kỹ năng hàn).
2. Nếu có ESP32-CAM dự phòng: nạp cùng firmware 2.2.3 và so dòng `[wifi] OK … RSSI` ở cùng chỗ. Chênh lệch lớn xác nhận board hiện tại bị lỗi RF.
3. Sau khi sửa, đạt khi RSSI ≥ −75 dBm và camera giữ ONLINE trên Web ≥ 10 phút.

Firmware tự xử lý phần còn lại:
- tự thử lại kết nối với khoảng chờ 5 → 60 giây;
- quét và báo `[wifi-scan]` khi kết nối thất bại;
- tự khởi động lại một lần nếu mất Wi-Fi 30 phút;
- Backend đánh dấu camera OFFLINE sau 5 phút không có heartbeat.

## 7. Nạp firmware (chỉ người kỹ thuật, không cần khi vận hành)

1. Đóng hẳn Arduino IDE. Serial Monitor đang mở sẽ làm cổng báo "Access denied". Không bấm Save các sketch cũ đang mở trong IDE.
2. Xác minh MAC trước khi nạp: `esptool --port COMx read-mac`.
   - CP210x phải là `8c:94:df:69:1c:6c` (cảm biến).
   - FT232R phải là `68:09:47:2d:8e:60` (camera).
3. Nạp ở 115200 baud:
   - Cảm biến: `sketch_jul25a` 1.4.2, FQBN `esp32:esp32:esp32`.
   - Camera: `CameraWebServer` 2.2.3, FQBN `esp32:esp32:esp32cam`.
   - Đế MB tự vào chế độ nạp, không cần jumper GPIO0.
4. Lưu ý: mở cổng COM4 bằng chương trình serial có thể reset camera (mạch auto-reset của đế MB). Khi vận hành, đừng để chương trình nào mở COM4.

## 8. Cảm biến đất và hiệu chuẩn

- UI chỉ hiển thị phần trăm khi đầu dò hợp lệ **và** đã hiệu chuẩn.
- Đo 31/10: raw dao động 0–4095, tức chân ADC thả nổi (`UNSTABLE`). Hãy kiểm tra dây AO, VCC 3V3 và GND.
- Hiệu chuẩn:
  1. Đo raw khi đầu dò trong đất khô → `SOIL_DRY_RAW`.
  2. Đo raw khi đầu dò trong đất bão hòa nước → `SOIL_WET_RAW`.
  3. Đặt `SOIL_CALIBRATED = true` rồi nạp lại.
