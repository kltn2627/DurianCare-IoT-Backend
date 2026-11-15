# DurianCare IoT — Final Production Audit (31/10/2026)

Audit thực hiện trước khi sửa. Mọi kết luận dưới đây có bằng chứng từ code, log thiết bị thật (COM3/COM4),
PostgreSQL hoặc Docker; điểm nào không có bằng chứng được ghi "CHƯA XÁC MINH".

## 1. Kiến trúc hiện tại

```
ESP32 cảm biến (esp32-01, COM3)                 ESP32-CAM-001 (COM4)
  DHT22 (GPIO18) + đầu dò đất YL-69/LM393 (GPIO34)   OV2640, AI-Thinker, PSRAM
        │ Wi-Fi STA                                    │ Wi-Fi STA
        │ nghe UDP 4210 ◄──── beacon trên Windows ────►│ nghe UDP 4210
        │ POST /api/v1/sensors/data                    │ POST /api/v1/public/cameras/{register,heartbeat}
        ▼                                              ▼  (X-Camera-Key, announcedAt ±30 s)
  host:3001 ──► Docker (duriancare-iot-service) ──► PostgreSQL duriancare_iot (telemetry, iot_devices,
                     │                                 iot_alerts, camera_devices, camera_captures)
                     │ GET http://<cam-LAN-IP>/capture  (Backend kéo ảnh, proxy cho Web/Mobile)
                     ├─ Kafka iot.alert.email ──► notification-service ──► MongoDB
                     ▼
  Gateway :8080 (JWT → X-Auth-User-Id) ──► Web (Next.js :3000) / Mobile (Expo)
```

## 2. Luồng kết nối thực tế và nguyên nhân lỗi

| # | Vấn đề | Bằng chứng | Nguyên nhân gốc |
|---|---|---|---|
| 1 | ESP32 log `SERVER_NOT_DISCOVERED` | log COM3; telemetry `esp32-01` cuối cùng 22/10 | Beacon UDP 4210 chỉ chạy khi có người mở terminal `npm run discovery:beacon`. Không ai chạy → không có gói → firmware (đúng thiết kế) không gửi. Ngay khi beacon chạy (31/10 09:03 UTC) firmware cũ gửi được `201` ngay. |
| 2 | ESP32-CAM `E (…) wifi:sta is connecting, return error` lặp mỗi 5 s | log COM4 (uptime ~65 phút, lặp liên tục) | `checkWiFi()` gọi `WiFi.reconnect()` mỗi 5 s trong khi `WiFi.setAutoReconnect(true)` cũng đang kết nối; mỗi lần gọi lại khi trạm đang "connecting" bị driver từ chối, và các lượt kết nối chồng nhau không bao giờ hoàn tất. |
| 3 | `http://0.0.0.0:80/capture` | log COM4 | `setup()` in `WiFi.localIP()` trước khi Wi-Fi kết nối. Chỉ là log; Backend **không** lưu 0.0.0.0 (registry từ chối IP không phải private LAN — `cameraOrigin()`). |
| 4 | Độ ẩm đất "Raw 0 → 100 %" | log COM3; DB: `soil_moisture` nhảy 100 → 61 → 100 trong 20 s | `map(raw, 3200, 1200, 0, 100)` + `constrain` biến 0 V thành 100 %. 0 V trên ADC1 nghĩa là dây AO/VCC chưa nối hoặc chập; dao động lớn = chân ADC thả nổi. Mốc 3200/1200 chưa có bằng chứng hiệu chuẩn (`DurianCare_Unverified_And_Questions.md`). |
| 5 | Firmware cảm biến chặn `loop()` | `connectWiFi()` vòng `while` vô hạn | Mất Wi-Fi → không đọc cảm biến, không watchdog, gọi `WiFi.begin()` lại mỗi 10 s khi đang kết nối. |
| 6 | Camera init lỗi → `ESP.restart()` sau 10 s, vô hạn | `setup()` | Không có trạng thái `CAMERA_INIT_FAILED`; Backend không biết camera lỗi. |
| 7 | Camera có thể báo ONLINE khi không chụp được | registry chỉ dựa heartbeat | Firmware không tự kiểm tra khung hình; Backend không có trạng thái DEGRADED. |
| 8 | Docker: service ứng dụng không có healthcheck; Spring service phụ thuộc config-server `service_started` | `docker compose config` | `running` ≠ `healthy`; lúc khởi động lạnh service có thể start trước config-server sẵn sàng. |
| 9 | Web: lỗi thao tác thông báo thay cả danh sách bằng hộp lỗi; "Đánh dấu tất cả" không xác nhận; thẻ hiện id nội bộ | `NotificationInbox.tsx` | — |
| 10 | Mobile: `Alert.alert` native, không toast, lỗi thao tác thay danh sách, double-tap không chặn đồng bộ | `DurianNotificationInboxScreen.tsx`, `NotificationDetailScreen.tsx` | — |
| 11 | Camera UI Web tải ảnh mỗi 8 s cả khi camera offline | `CameraSection.tsx` | Không gắn polling với trạng thái registry. |

## 3. Docker Desktop trên Windows và UDP broadcast

- Container chạy trong VM Linux (WSL2) sau NAT. Mạng `bridge` không thấy IP Wi-Fi của laptop và không broadcast được ra LAN Wi-Fi;
  `network_mode: host` trên Docker Desktop là host của **VM**, không phải Windows. Log iot-service xác nhận: `[udp-discovery] Discovery degraded; HTTP remains available`.
- ⇒ Beacon **phải** chạy trên Windows. Giải pháp: Scheduled Task theo người dùng (không cần quyền admin, không đổi firewall), chạy khi đăng nhập, tự restart, có file trạng thái + health check (`infrastructure/discovery-beacon/`).
- Cổng 3001 publish ra host; ESP32 → `http://<IP Wi-Fi laptop>:3001` hoạt động (bằng chứng: `201` từ COM3).
- Firewall Windows: beacon chỉ gửi broadcast ra ngoài (không cần rule). Kết nối vào 3001 đã hoạt động (Docker Desktop tự có rule).

## 4. Các phần đã tốt, giữ nguyên

- Camera registry: xác thực `X-Camera-Key` (so sánh timing-safe), `announcedAt` ±30 s chống replay, chống ghi IP cũ (`last_seen < announcedAt`), phạm vi farm/zone khóa, chỉ nhận IP private LAN, không tạo bản ghi trùng (khóa `device_id`).
- Ảnh camera đi qua Backend (`/api/v1/camera/snapshot`) — Web/Mobile không dùng IP private của camera; `streamAvailable: false` (không giả lập video).
- Notification: Kafka → dedup `sourceEventId` duy nhất, retry có giới hạn, recovery chỉ khi thông báo mở đã SENT, người nhận theo farm/zone của cảnh báo (đợt trước).

## 5. File/module cần sửa

| Phần | File |
|---|---|
| Firmware cảm biến | `Documents/Arduino/sketch_jul25a/sketch_jul25a.ino`, `sensor_types.h`, `sensor_secrets.h` (mới, không commit) |
| Firmware camera | `Documents/Arduino/CameraWebServer/CameraWebServer.ino`, `camera_registry_config.h` |
| Backend | `src/sensorQuality.js` (mới), `src/main.js`, `src/read-api.js`, `src/camera/deviceService.js`, `src/camera/registryRuntime.js`, `src/discovery/hostBeacon.js` |
| Docker / host | `infrastructure/docker-compose.yml`, `infrastructure/discovery-beacon/*.ps1` (mới) |
| Web | `components/feedback/{Toast,ConfirmDialog}.tsx`, `notifications/{NotificationInbox,NotificationBell}.tsx`, `camera/CameraSection.tsx`, `iot/IotDevicePanel.tsx`, `charts/SensorCharts.tsx`, `lib/{camera/status,iot/soil,notification/presentation}.ts` |
| Mobile | `components/{ConfirmModal,ToastBanner}.tsx`, `features/notification/*`, `features/camera/*`, `features/iot/{soil.ts,IotDeviceSettingsCard.tsx}` |

## 6. Không thể xác minh nếu không có thao tác vật lý

- Đầu dò đất có đang cắm, dây AO/VCC có nối, cấp 3V3 hay 5V — **CHƯA XÁC MINH** (raw 0 chỉ chứng minh 0 V tại GPIO34).
- Hiệu chuẩn đất khô/bão hòa — chưa có số đo thật ⇒ firmware không gửi phần trăm (`UNCALIBRATED`).
- Router khởi động lại, DHCP cấp IP mới cho camera, rút nguồn thật — cần thao tác tại chỗ.
- Nguồn ≥1 A cho ESP32-CAM, GPIO0 khi chạy — theo tài liệu phần cứng Rev B; COM4 tự vào bootloader qua DTR/RTS và chạy bình thường sau reset.
