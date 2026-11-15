# DurianCare IoT — Final Implementation Report (31/10/2026)

Xem nguyên nhân gốc chi tiết ở `IOT_FINAL_PRODUCTION_AUDIT.md`. Không commit/push/deploy production.

## 1. Kiến trúc trước → sau

| Thành phần | Trước | Sau |
|---|---|---|
| Discovery | Người dùng phải mở terminal `npm run discovery:beacon` | Scheduled Task Windows "DurianCare Discovery Beacon" tự chạy khi đăng nhập, tự restart, file trạng thái + `check-beacon.ps1` |
| ESP32 cảm biến | `connectWiFi()` chặn vô hạn; raw 0 → 100 % | State machine không chặn, backoff 5→60 s, watchdog 30 s; đất gửi `soil_raw` + `soil_status`, chỉ gửi % khi đã hiệu chuẩn |
| ESP32-CAM | `WiFi.reconnect()` mỗi 5 s chồng auto-reconnect (`sta is connecting`); init lỗi → restart vô hạn; không tự kiểm tra ảnh | Một nơi điều khiển Wi-Fi; init lỗi → `CAMERA_INIT_FAILED`, thử lại 30 s, restart sau 5 lần; chụp thử trước mỗi heartbeat; gửi `cameraReady/lastError/macAddress/firmwareVersion`; tạm dừng camera khi Wi-Fi kết nối lại |
| Backend | Không biết sensor lỗi; camera chỉ ONLINE/OFFLINE | `soil_raw/soil_status/dht_status`; bỏ giá trị firmware đánh dấu không hợp lệ; camera `DEGRADED` |
| Docker | App service không healthcheck | Healthcheck iot/notification/gateway/farm/config/discovery; Spring service chờ config-server `healthy`; gateway chờ iot/notification/farm `healthy` |
| Web/Mobile | Lỗi thao tác thay cả danh sách; confirm/Alert native; camera tải ảnh khi offline | Toast thành công/lỗi, modal xác nhận, chống double-submit; camera DEGRADED/OFFLINE không tải ảnh, có backoff, hiện giờ chụp |

## 2. File đã sửa

**Firmware** (ngoài Git; sao lưu nguồn + nhị phân ở `Documents/Arduino/_backup_2026-10-31/`, có `SHA256SUMS.txt`):
- `sketch_jul25a/sketch_jul25a.ino` (1.4.0 → 1.4.2), `sensor_types.h` (mới), `sensor_secrets.h` (mới, chứa Wi-Fi — KHÔNG chia sẻ), `sensor_secrets.example.h` (mới).
- `CameraWebServer/CameraWebServer.ino` (2.2.0 → 2.2.3), `camera_registry_config.h`, `power_diag.cpp` (mới).

**Backend** (`DurianCare-IoT-Backend`):
- `duriancare-iot-service/src/sensorQuality.js` (mới), `src/main.js`, `src/read-api.js`, `src/camera/deviceService.js`, `src/camera/registryRuntime.js`, `src/discovery/hostBeacon.js`.
- Test: `test/camera-health.test.js` (mới), `test/sensor-ingest-discovery.test.js`, `test/device-lifecycle-pg.test.js`, `test-support/{sensor-ingest,main-lifecycle}.cjs`.
- `infrastructure/docker-compose.yml`, `infrastructure/discovery-beacon/{install-beacon-task,check-beacon,uninstall-beacon-task}.ps1` (mới).
- Docs: 6 file `docs/IOT_*.md`, `docs/NOTIFICATION_UX_FINAL_REPORT.md`.

**Web** (`DurianCare-IoT-Web-Client`): `components/feedback/Toast.tsx`, `components/feedback/ConfirmDialog.tsx` (mới), `components/notifications/{NotificationInbox,NotificationBell}.tsx`, `components/camera/CameraSection.tsx`, `components/iot/IotDevicePanel.tsx`, `components/charts/SensorCharts.tsx`, `lib/camera/{status.ts (mới),types.ts}`, `lib/iot/{soil.ts (mới),client.ts,types.ts,lifecycle.ts}`, `lib/notification/presentation.ts` (mới), test `__tests__/iot-hardening.test.ts` (mới), `__tests__/camera-registry.test.ts`.

**Mobile** (`DurianCare-IoT-Mobile-App`): `components/{ConfirmModal,ToastBanner}.tsx` (mới), `features/notification/{DurianNotificationInboxScreen,NotificationDetailScreen}.tsx`, `features/notification/presentation.ts` (mới), `features/camera/{cameraStatus.ts (mới),CameraMonitorScreen.tsx}`, `features/iot/{soil.ts (mới),IotDeviceSettingsCard.tsx,lifecycleApi.ts}`, `lib/iotApi.ts`, test `tests/iot-hardening.test.cjs` (mới).

## 3. API / DB thay đổi (tương thích ngược)

- `POST /api/v1/sensors/data` nhận thêm (tùy chọn): `soil_raw` (0–4095), `soil_status` (`OK|UNCALIBRATED|NO_SIGNAL|SATURATED|UNSTABLE`), `dht_status` (`OK|SENSOR_ERROR`). `soil_status ≠ OK` ⇒ `soil_moisture` bị bỏ (không lưu, không cảnh báo); `dht_status = SENSOR_ERROR` ⇒ bỏ nhiệt độ/độ ẩm. Payload chỉ có chẩn đoán vẫn được nhận (thiết bị còn sống). Firmware cũ không gửi các trường này → hành vi như cũ.
- Đọc: `/api/v1/sensors/{latest,history}` và read-API thiết bị trả thêm `soil_raw/soil_status/dht_status` (`soilRaw/soilStatus/dhtStatus`); `latestByMetric.soilSensor`; nếu phán quyết mới nhất của đầu dò không OK và mới hơn giá trị % cuối, `soilMoisture` = null (không hiển thị số cũ như hiện tại).
- Camera register/heartbeat nhận thêm (tùy chọn, được kiểm tra): `cameraReady`, `lastError` (`CAMERA_INIT_FAILED|CAMERA_STREAM_FAILED`), `firmwareVersion`, `macAddress`. View camera có `status: DEGRADED`, `cameraReady`, `lastError` (`HEARTBEAT_TIMEOUT` khi quá hạn), `firmwareVersion`; `previewAvailable` chỉ khi ONLINE.
- DB (cộng thêm, idempotent khi khởi động, không đổi dữ liệu cũ): `telemetry.soil_raw INTEGER`, `soil_status VARCHAR(20)`, `dht_status VARCHAR(20)` + CHECK `ck_telemetry_sensor_quality`; `camera_devices.camera_ready BOOLEAN`, `last_error VARCHAR(64)`. 25 685 bản ghi telemetry cũ giữ nguyên (status NULL).

## 4. Docker service

Không thêm container (container không broadcast được ra LAN trên Docker Desktop — audit §3). Thay đổi: healthcheck + `depends_on: service_healthy`. Đã recreate từng service (`--no-deps`), volume giữ nguyên. Rollback image: `duriancare/iot-service:pre-hardening-2026-10-31`; compose cũ: `%TEMP%\docker-compose.yml.bak-2026-10-31`.

## 5. Firmware đã nạp

| Board | COM | Nhận diện | Firmware | Flash |
|---|---|---|---|---|
| ESP32 DevKit (CP210x VID 10C4 PID EA60) | COM3 | log boot "GIÁM SÁT CẢM BIẾN", `esptool read-mac` = `8c:94:df:69:1c:6c` (khớp `HARDWARE.md`), ESP32-D0WD-V3 | `sketch_jul25a` 1.4.0, nay 1.4.2, FQBN `esp32:esp32:esp32` | `Hash of data verified` ×4 |
| AI-Thinker ESP32-CAM (FTDI VID 0403 PID 6001) | COM4 | log "sta is connecting" của firmware camera, MAC `68:09:47:2d:8e:60` (khớp registry), ESP32-D0WD-V3, PSRAM 4 MB | `CameraWebServer` 2.2.0, nay 2.2.3, FQBN `esp32:esp32:esp32cam` | `Hash of data verified` ×4 (lần nạp cuối) |

Sao lưu nhị phân trước khi nạp: `esp32-01_flash_before_1.4.0.bin`, `esp32-cam-001_flash_before_2.2.0.bin` (4 MB mỗi file; chứa Wi-Fi/khóa thiết bị — KHÔNG chia sẻ). Khôi phục: `esptool --port COMx --baud 115200 write-flash 0 <file>.bin`.
GPIO0: adapter COM4 tự vào bootloader qua DTR/RTS và khởi động bình thường sau `Hard resetting via RTS` (rst `POWERON_RESET`, `SPI_FAST_FLASH_BOOT`) — không cần jumper.

## 6. Kết quả compile / test

| Hạng mục | Kết quả |
|---|---|
| Firmware cảm biến | compile OK (964 KB, 73 %) |
| Firmware camera | compile OK (1,15 MB, 36 %) |
| iot-service `npm test` | 181 tests, 172 pass, 0 fail, 9 skip |
| iot-service PostgreSQL thật (rollback) | 3 pass, 0 fail, 5 skip; không còn schema fixture |
| Web `tsc --noEmit` | OK |
| Web `jest` | 20 suites, 159 tests pass |
| Web `eslint` | 0 error, 8 warning (có từ trước) |
| Web `next build` (webpack, bản sao tạm để không đụng dev server) | OK — 45 trang; đã sửa `src/app/search/page.tsx` (`searchParams` là Promise trong Next 16, lỗi có sẵn từ HEAD) |
| Mobile `tsc --noEmit` | OK |
| Mobile `node --test tests/*.cjs` | 18 pass |
| Mobile `expo export --platform android` | OK (bundle 6,9 MB) |

## 7. Bằng chứng trên phần cứng thật (log COM3/COM4 + PostgreSQL)

- Beacon chạy bằng Scheduled Task → ESP32 firmware cũ gửi `201` ngay (09:03 UTC) — trước đó im lặng từ 22/10.
- ESP32 1.4.0 (không mở Serial Monitor/IDE): `WIFI_CONNECTING` → lượt hết hạn (status 6) → `RECONNECTING` sau 5 s → `WIFI_CONNECTED` → `SERVER_DISCOVERED` → `RUNNING`, `201` mỗi 10 s (30 bản ghi / 5 phút), firmware 1.4.0 ghi nhận trong registry.
- Đất: raw dao động 0–4095 → `UNSTABLE`; DB `soil_moisture` NULL, `soil_raw` lưu riêng; số cảnh báo không tăng (8).
- Cảnh báo cũ esp32-01 DEVICE_OFFLINE phục hồi lúc 09:00:53 với `recovery_notification_status = SKIPPED/OPEN_UNTRACKED` (không gửi "trở lại bình thường" sai).
- ESP32-CAM 2.2.0: registry ghi IP mới `192.168.1.5` (trước `.3`, DHCP) không cần sửa gì; `camera_ready=false, CAMERA_INIT_FAILED` → DEGRADED; quá hạn heartbeat → OFFLINE (`[camera-offline] Marked 1 camera(s) offline`); sau đó `camera_ready=true` ONLINE (10:00:12 UTC).
- Lỗi do chính tôi gây ra và đã sửa trong lúc test: bản 2.2.0 đầu tiên khởi động HTTP server trước khi có TCP/IP stack → `assert xQueueSemaphoreTake` boot loop (giải mã backtrace: `netconn_apimsg`). Sửa: `initWiFi()` trước camera; đã nạp lại và xác nhận khởi động bình thường.

## 8. Chỉ mô phỏng / chưa xác minh thực tế

- Router khởi động lại, Backend restart khi thiết bị đang chạy, beacon restart: logic có test tự động (rediscovery, backoff) và quan sát một lần mất Wi-Fi thật trên camera; chưa tắt router thật.
- Kafka retry/dedup: test tự động (mock + PostgreSQL thật), không chạy qua Kafka live với tài khoản thật.
- UI Web/Mobile: kiểm bằng typecheck/unit test/source test; chưa chụp màn hình trên thiết bị thật.
- Ảnh camera qua Backend: **chưa ổn định** — `/capture` trả header 200 nhưng không nhận đủ byte trong 20 s; lịch chụp nhận `HTTP 500`.

## 9. Rủi ro còn lại

1. **Sóng Wi-Fi tại ESP32-CAM** (quan trọng nhất). Đo lại 31/10 theo yêu cầu cấp nguồn USB:
   - **Nguồn:** VDD33 lệch dưới 1% khi bật camera và chụp liên tục, không có brownout, hai board chạy đồng thời trên USB laptop; kết luận "thiếu nguồn" trước đó **bị rút lại**.
   - **Sóng:** camera quét không thấy SSID trong khi cảm biến vẫn kết nối ở −82 dBm và laptop thấy 66%. Cần chỉnh vị trí/hướng ăng-ten, đưa AP gần hơn, hoặc kiểm tra điện trở chọn ăng-ten (chi tiết ở `IOT_HARDWARE_STARTUP_GUIDE.md` §6). Firmware không thể bù được.
2. Đầu dò đất: chân ADC thả nổi (dao động 0–4095) — kiểm tra dây AO/VCC/GND, cấp 3V3; sau đó hiệu chuẩn (đo raw khô/bão hòa, đặt `SOIL_CALIBRATED = true`).
3. Telemetry cảm biến chưa có khóa thiết bị (chỉ kiểm tra registered + CLAIMED); camera đã có `X-Camera-Key`.
4. Endpoint LAN của camera (`/control`, `/reg`) không có xác thực (mã gốc CameraWebServer) — chỉ trong LAN, không mở ra Internet.
5. Beacon chạy theo người dùng đăng nhập Windows (Scheduled Task "At logon"); laptop chưa đăng nhập ⇒ không có beacon.
6. Codex cũng đang chạy trên máy (tiến trình `codex.exe`); không thấy nó sửa file trong 3 giờ trước khi bắt đầu, nhưng hãy tránh để hai agent sửa cùng repo.

## 10. Bổ sung 31/10 — cấp nguồn USB laptop (không pin)

- Firmware cuối: cảm biến **1.4.2**, camera **2.2.3**. Cả hai đã nạp và xác minh MAC, hash 4/4.
- Cảm biến 1.4.2: thêm log `vdd33` và RSSI cho mỗi lần đọc; tăng connect timeout từ 3 lên 8 giây. Ở −82 dBm, timeout 3 giây làm khoảng 25% lần connect thất bại.
- Camera:
  - **2.2.1:** thêm `power_diag.cpp`, đo A/B VDD33/RSSI ở ba trạng thái camera tắt / đứng yên / đang chụp, và in tên reset reason.
  - **2.2.2:** gỡ cơ chế "tạm dừng camera khi Wi-Fi kết nối lại". Đây là lỗi do tôi đưa vào: khởi tạo lại sau `esp_camera_deinit` bị SCCB NACK, trong khi rail điện vẫn ổn định. Firmware giờ khởi động lại sau 2 lần init lỗi, tức đi lại đường boot vốn luôn thành công.
  - **2.2.3:** khi một lượt kết nối Wi-Fi thất bại, firmware quét `[wifi-scan]` để tách lỗi "ngoài vùng phủ" khỏi lỗi kết nối.
- Kiểm thử A/B 3 + 3 phút:
  - camera giữ reset: cảm biến 9 OK / 3 lỗi;
  - camera chạy: cảm biến 17 OK / 0 lỗi, camera 7 heartbeat 200.
- Rút/cắm lại USB với IDE đóng:
  - cảm biến 6/6 bản ghi mỗi phút trong 13 phút;
  - camera tự đăng ký 13:00:48 UTC, `camera_ready=true`;
  - sau đó camera mất Wi-Fi vì sóng yếu (quét không thấy SSID) và Backend chuyển OFFLINE đúng hạn.
- Thử nối dài cáp USB 2.0 để đưa camera ra xa laptop (21:40): RSSI còn −86…−87 dBm, kém hơn lúc cắm sát laptop. Giả thuyết nhiễu từ laptop bị loại. Kết luận có bằng chứng: tín hiệu Wi-Fi tại vị trí camera không đủ. Nguồn USB và firmware không phải nguyên nhân.
- **Trạng thái nghiệm thu:**
  - Cảm biến **ĐẠT**: cắm USB là tự chạy, không cần IDE, gửi 100% telemetry.
  - Camera **CHƯA ĐẠT** ở vị trí hiện tại: tự khởi động, chụp được, đăng ký được khi có sóng, nhưng không giữ được Wi-Fi ở −83…−87 dBm.
- AP đưa lại gần (21:55): camera vẫn −88 dBm, sau đó quét thấy 0 mạng. Laptop 60%, cảm biến 100%.
  RSSI của camera không đổi qua 3 vị trí (−83 / −87 / −88 dBm) ⇒ **lỗi đường ăng-ten của board ESP32-CAM-001**: điện trở chọn IPEX mà không có ăng-ten, hoặc RF hỏng. Không phải nguồn USB, không phải firmware, không phải khoảng cách tới AP. Cần kiểm tra/sửa phần cứng hoặc thay board (xem `IOT_HARDWARE_STARTUP_GUIDE.md` §6).
