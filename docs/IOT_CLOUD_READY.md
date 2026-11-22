# DurianCare IoT — chạy được cả LOCAL lẫn CLOUD (bàn giao)

Ngày: 2026-11-19. Kế hoạch đã duyệt: `IOT_CLOUD_READY_PLAN.md`.
**Trạng thái:** code đã viết, test tự động đã chạy (kết quả thật ở §8). **Chưa nạp firmware, chưa kiểm thử trên phần cứng,
chưa deploy.** Mọi hành vi phần cứng/mạng dưới đây là thiết kế, chưa có bằng chứng chạy thật.

---

## 1. Tóm tắt

| Trước | Sau |
|---|---|
| Thiết bị chỉ biết máy chủ qua beacon UDP 4210 trong LAN | Thứ tự: Server URL trong NVS (portal) → beacon UDP 4210 v1 → `DURIANCARE_CLOUD_BASE_URL` |
| Wi-Fi viết cứng trong `*_secrets.h` (`#error` nếu thiếu) | Portal WiFiManager `DurianCare-Setup-XXXX`; `*_secrets.h` chỉ là mặc định tùy chọn |
| BE kéo ảnh `GET http://<IP camera>/capture` | Camera **đẩy** ảnh `POST /api/v1/public/cameras/{id}/frames`; lệnh chụp qua hàng đợi |
| Sensor không xác thực | Token thiết bị `X-Device-Token` (bắt buộc ở CLOUD, tùy chọn ở LOCAL) |
| Dữ liệu sai miền → HTTP 500 | HTTP 422 `VALUE_OUT_OF_RANGE` |
| FE có ô nhập URL/IP camera | Đã xóa; FE chỉ dùng URL của BE; có trang "Hướng dẫn kết nối thiết bị" |
| Sensor offline sau 10 phút (mặc định code) | 5 phút (khớp báo cáo) |

---

## 2. Cài thiết bị lần đầu (người dùng)

1. **Admin** cấp phát thiết bị trên Web (`/dashboard/admin/devices`): nhập `device_uid` (ví dụ `esp32-01`, `ESP32-CAM-001`),
   loại `SENSOR`/`CAMERA` → nhận **mã nhận 10 ký tự** (`ABCDE-12345`, hết hạn sau 72 giờ, dùng một lần cho claim).
2. **Cắm nguồn** thiết bị. Lần đầu (chưa có Wi-Fi trong NVS và không có `*_secrets.h`) thiết bị phát Wi-Fi
   `DurianCare-Setup-<4 ký tự hex cuối MAC>`.
3. Điện thoại vào Wi-Fi đó → trang cài đặt (captive portal; nếu không tự mở: `http://192.168.4.1`) →
   **Configure WiFi**: chọn Wi-Fi 2,4 GHz của vườn, mật khẩu, *Mã nhận thiết bị* (mã ở bước 1),
   *Server URL* để trống (trừ khi BE không ở LAN và firmware chưa có domain cloud) → **Save**. Thiết bị lưu NVS và khởi động lại.
4. Thiết bị vào Wi-Fi, tìm Backend, gọi `POST /api/v1/public/devices/activate` với mã nhận → nhận token/khóa, lưu NVS.
5. **Nông dân** nhập cùng mã ở Web (Cảm biến IoT → "Thêm thiết bị") hoặc Mobile (Khí hậu vườn → "Thêm & gắn thiết bị"),
   chọn vườn/khu. Bước 4 và 5 không phụ thuộc thứ tự. Sau đó thiết bị **tự hiện** trong danh sách của vườn
   (camera: hàng `camera_devices` được tạo tự động ở lần đăng ký đầu tiên, vườn/khu lấy từ claim).

Cài lại / đổi Wi-Fi: rút nguồn, **giữ nút BOOT** (ESP32-CAM-MB: nút IO0) rồi cắm nguồn, giữ tiếp **5 giây** → xóa
Wi-Fi, Server URL, mã nhận, token trong NVS và mở portal. Không vào được Wi-Fi đã lưu **6 lượt liên tiếp** (~4–5 phút)
cũng mở portal (10 phút rồi quay lại thử Wi-Fi). Camera: portal chỉ chạy lúc khởi động (camera khởi động lại vào portal)
vì GPIO0 là chân XCLK và portal/httpd cùng dùng cổng 80.

Cấp lại token cho thiết bị đã claim: admin gọi `POST /api/iot/devices/{id}/claim-code` với body
`{"purpose":"ACTIVATION"}` → mã mới → nhập vào portal. Token cũ bị thay khi kích hoạt bằng mã mới.

---

## 3. Khi deploy Backend lên internet — chỉ cần làm gì

### 3.1 DNS (Long Vân, domain `duriancare.io.vn`)
- Bản ghi `A api.duriancare.io.vn → <IP VPS>` (hoặc `CNAME` nếu dùng dịch vụ có hostname).

### 3.2 Reverse proxy HTTPS (ví dụ Caddy trên VPS; nginx tương đương)
```caddy
api.duriancare.io.vn {
  # Đường thiết bị: thẳng tới iot-service, CHỈ các path này, và xóa mọi header X-Auth-* do client gửi.
  @device path /iot/api/v1/sensors/data /iot/api/v1/public/devices/activate /iot/api/v1/public/cameras/* /iot/actuator/health
  handle @device {
    uri strip_prefix /iot
    request_header -X-Auth-User-Id
    request_header -X-Auth-Role
    request_body { max_size 3MB }
    reverse_proxy duriancare-iot-service:3001
  }
  handle /iot/* { respond 404 }
  # Mọi thứ khác (Web/Mobile): Gateway (JWT).
  handle { reverse_proxy duriancare-gateway:8080 }
}
```
Đường public dự kiến:
- `https://api.duriancare.io.vn/iot/api/v1/sensors/data`
- `https://api.duriancare.io.vn/iot/api/v1/public/devices/activate`
- `https://api.duriancare.io.vn/iot/api/v1/public/cameras/{register|heartbeat|<id>/commands|<id>/frames}`
- Web/Mobile: `https://api.duriancare.io.vn/api/...` qua Gateway (như hiện tại).

Không mở trực tiếp cổng 3001 ra internet: các route UI của iot-service tin header `X-Auth-*` do Gateway gắn.

### 3.3 Biến môi trường iot-service (`infrastructure/.env` trên máy chủ, KHÔNG commit)
| Biến | Giá trị CLOUD |
|---|---|
| `IOT_DEPLOYMENT_MODE` | `CLOUD` |
| `IOT_DEVICE_AUTH_MODE` | để trống (= `REQUIRED` ở CLOUD) |
| `CAMERA_DELIVERY_MODE` | `PUSH` (PULL bị bỏ qua ở CLOUD) |
| `CAMERA_PULL_FALLBACK` | `false` (chỉ có tác dụng ở LOCAL) |
| `CAMERA_FRAME_MAX_BYTES` | `2097152` (khớp `max_size` của proxy) |
| `IOT_PUBLIC_BASE_URL` | `https://api.duriancare.io.vn/iot` |
| `SERVER_BASE_URL` | URL Gateway công khai (dùng ghép `image_url`) |
| `CAMERA_DEVICE_BINDINGS` | không bắt buộc nữa (camera kích hoạt bằng mã nhận); có thể giữ cho camera cũ |

Web Netlify: `NEXT_PUBLIC_API_BASE_URL=https://api.duriancare.io.vn` (thay Cloudflare Quick Tunnel). Mobile: base URL tương tự.

### 3.4 Firmware
Trong `duriancare_config.h` (giống hệt ở 2 sketch):
```c
#define DURIANCARE_CLOUD_BASE_URL "https://api.duriancare.io.vn/iot"
```
Hoặc không nạp lại: nhập đúng chuỗi đó vào ô *Server URL* trong portal. Không sửa logic nào khác.
HTTPS dùng bundle CA có sẵn của core ESP32 3.3.12 (`useBuiltinCACertBundle()`); chứng chỉ Let's Encrypt (Caddy mặc định)
nằm trong bundle — **cần xác minh khi deploy**. Thiết bị cần SNTP (UDP 123 ra internet) để có giờ đúng trước khi gọi HTTPS.

### 3.5 Beacon
Ở CLOUD không cần beacon. Ở LOCAL giữ nguyên Scheduled Task "DurianCare Discovery Beacon" (không đổi).

---

## 4. Hợp đồng API (mới / đổi)

Chi tiết request/response: `IOT_CLOUD_READY_PLAN.md` §3. Điểm khác so với kế hoạch:
- Khóa camera kích hoạt bằng mã nhận lưu ở `iot_devices.device_token_hash` (cùng cột với token sensor), **không** thêm
  `camera_devices.device_key_hash` — một chỗ lưu cho mọi thiết bị.
- `CAMERA_DEVICE_BINDINGS` rỗng giờ là cấu hình hợp lệ → khóa lạ trả **401** (trước đây 503). Giá trị env hỏng (không rỗng)
  vẫn trả 503 `CAMERA_BINDINGS_UNAVAILABLE`. Test `firmware-contract` và `camera-registration-auth` đã cập nhật theo.
- `capture-now` ở PUSH với firmware cũ (không báo `push`) trả **409 `CAMERA_PUSH_UNSUPPORTED`**, trừ khi LOCAL +
  `CAMERA_PULL_FALLBACK=true` (đi đường kéo ảnh cũ, 201).
- Sensor gửi thêm `wifi_rssi` (tùy chọn, −127..0); BE lưu `iot_devices.last_wifi_rssi`, trả `statusReasons`
  (`TELEMETRY_DELAYED`, `NO_DATA_5_MIN`, `WIFI_WEAK`) và `wifiRssi` trong danh sách/chi tiết thiết bị.
- Camera `statusReasons`: `CAMERA_INIT_FAILED|CAMERA_STREAM_FAILED`, `WIFI_WEAK` (< −80 dBm), `CAPTURE_FAILING` (≥ 3 lỗi
  liên tiếp), `HEARTBEAT_TIMEOUT`. DEGRADED chỉ vì Wi-Fi yếu vẫn cho xem ảnh/chụp.
- Phản hồi camera cho người không phải ADMIN không còn `ip_address/port/protocol/mac_address/ssid`.

Giữ nguyên (đúng báo cáo): beacon v1 UDP 4210 mỗi 3 s và kiểm tra IP nguồn; `POST /api/v1/sensors/data` + payload;
chu kỳ 10 s (5–3600 s qua `config`); 404 chưa cấp phát / 409 chưa gắn vườn; Kafka `iot.telemetry.received`; hysteresis
(`alert-engine.js`); heartbeat camera 60 s, `announcedAt` ±30 s, OFFLINE 5 phút, `cameraReady=false` → DEGRADED;
`telemetry/buckets` `date_bin` avg/min/max; ảnh camera vào luồng AI `POST /api/v1/predict` của ai-service.

Thay đổi CSDL (idempotent lúc startup, chỉ cộng thêm): `iot_devices.device_token_hash/device_token_issued_at/last_wifi_rssi`,
`iot_device_claims.activated_at`, bảng `camera_commands`, `camera_captures.command_id/delivery/received_at`,
`camera_devices.diagnostics/wifi_rssi/capabilities_reported/last_frame_at`. Không xóa/ghi đè dữ liệu.

---

## 5. Camera chập chờn — bảng nguyên nhân

| # | Nguyên nhân | Bằng chứng | Kết luận | Đã sửa bằng phần mềm | Khuyến nghị phần cứng (chưa làm) |
|---|---|---|---|---|---|
| 1 | Wi-Fi yếu / vùng phủ | RSSI −83/−87/−88 dBm ở 3 vị trí, có lần quét không thấy mạng (log `[wifi-scan]` phiên trước); `/capture` chậm khi RSSI ≤ −85 (`IOT_TROUBLESHOOTING.md`); UXGA 66,5 KB mất 7,1 s (`cameraService.js` ghi chú đo 2026-10-18) | **Có bằng chứng — nguyên nhân chính khả dĩ nhất** | Camera tự đẩy ảnh một lần (không còn BE kéo qua link yếu, không còn proxy snapshot mỗi 8 s); ảnh XGA thay UXGA; upload timeout 30 s + 1 lần thử lại; RSSI/kênh trong heartbeat; DEGRADED `WIFI_WEAK` khi < −80 dBm | Kiểm tra điện trở 0 Ω chọn ăng-ten (PCB/IPEX) trên board; nếu đang chọn IPEX thì lắp ăng-ten ngoài 2,4 GHz; đặt AP/repeater gần hơn; tránh vỏ kim loại |
| 2 | Kênh / công suất phát | Chưa có số liệu kênh trước đây | **Chưa xác định** | Gửi `wifiChannel`; **không** đổi công suất phát (chưa có số đo nguồn khi TX tối đa) | Thử đổi kênh AP 1/6/11 và so RSSI trong diagnostics |
| 3 | Nguồn / brownout | VDD33 lệch < 1 %, không reset BROWNOUT (`power_diag.cpp`, đo USB phiên trước) | **Đã loại trừ ở điều kiện đo USB** | Giữ A/B probe lúc boot; gửi `vdd33`, `resetReason` mỗi heartbeat | Đo lại khi dùng nguồn ngoài vườn; nguồn 5 V ≥ 2 A riêng, dây ngắn |
| 4 | UXGA + `fb_count=2` + `CAMERA_GRAB_LATEST` | `initCamera()` 2.2.3: UXGA, 2 buffer PSRAM, chụp liên tục | **Giả thuyết** (driver chụp liên tục chiếm băng thông PSRAM/DMA cạnh Wi-Fi; chưa đo) | `fb_count=1`, `CAMERA_GRAB_WHEN_EMPTY`, XGA, bỏ khung cũ trước khi chụp | — |
| 5 | XCLK 20 MHz | `config.xclk_freq_hz = 20000000` (2.2.3) | **Giả thuyết**; cần so sánh A/B | XCLK 10 MHz mặc định, báo `xclkMhz` để so | Cáp FPC cắm chắc, không gập |
| 6 | Khởi tạo lại camera / heap phân mảnh | SCCB NACK khi deinit/init lại lúc chạy, nguồn ổn (31/10) → đã bỏ cơ chế tạm dừng ở 2.2.2 | **Có bằng chứng cho re-init**; phân mảnh heap **chưa có số liệu** | Không re-init lúc chạy (restart sau 2 lỗi init như cũ); gửi `freeHeap/minFreeHeap/freePsram/largestFreePsram` | Nếu NACK cả lúc boot: cắm lại cáp camera |
| 7 | Lệch giờ → `announcedAt` bị 400/409 | Registry chỉ nhận ±30 s (`registryRuntime.js`); FW có SNTP + header Date | **Cơ chế có, tần suất chưa đo** | Đếm `timeRejects` trong diagnostics; làm mới giờ sau 400/409; HTTPS chặn khi chưa có giờ | — |

---

## 6. Việc còn lại — cần test trên phần cứng / môi trường thật

1. Nạp `sketch_jul25a` 2.0.0 lên COM3 (xác minh MAC `8c:94:df:69:1c:6c` trước), `CameraWebServer` 3.0.0 lên COM4
   (MAC `68:09:47:2d:8e:60`). Sau khi nạp, firmware cũ có `*_secrets.h` vẫn dùng Wi-Fi mặc định; NVS trống.
2. Portal: thấy AP `DurianCare-Setup-XXXX`, captive portal mở, lưu SSID/Server URL/mã nhận, khởi động lại; giữ BOOT 5 s xóa cấu hình;
   6 lượt Wi-Fi lỗi mở portal; camera vào portal bằng khởi động lại.
3. LOCAL: beacon UDP 4210 vẫn được nhận (sensor log `SERVER_DISCOVERED (BEACON)`), số đo 201 mỗi 10 s, đường `path` của beacon dùng đúng.
4. Kích hoạt: `POST /activate` → token/khóa trong NVS; sensor gửi `X-Device-Token`; `IOT_DEVICE_AUTH_MODE=REQUIRED` từ chối thiết bị không token.
5. Camera PUSH: Chụp ngay từ Web/Mobile → lệnh nhận trong ≤ 5 s → ảnh 201 → AI chạy → hiện trong lịch sử; lịch chụp tạo lệnh SCHEDULED; ảnh mới nhất hiện ở "Ảnh camera định kỳ".
6. Đo lại tình trạng chập chờn với XGA/fb_count 1/XCLK 10 MHz: so `captureFailures`, `uploadFailures`, RSSI, `minFreeHeap` qua vài giờ.
7. CLOUD: deploy BE + proxy HTTPS, kiểm tra CA bundle chấp nhận chứng chỉ, SNTP qua internet, latency poll lệnh qua HTTPS keep-alive.
8. Chạy test PG thật (9 test đang skip) khi có Postgres: `IOT_FIXTURE_PG_URI`, `DURIANCARE_CAMERA_FIXTURE_PG_URI` (Docker Desktop đang lỗi nên chưa chạy).
9. Rebuild container iot-service (cần người dùng duyệt) — chưa làm.
10. mDNS trên Mobile: hoãn (cần `react-native-zeroconf` + dev build). Firmware đã quảng bá `duriancare-cam-esp32-cam-001.local` (chưa kiểm thử).

---

## 7. Thay đổi từng file

### Backend `duriancare-iot-service`
| File | Thay đổi |
|---|---|
| `src/config.js` | `IOT_DEPLOYMENT_MODE`, `IOT_DEVICE_AUTH_MODE`, `CAMERA_DELIVERY_MODE`/`PULL_FALLBACK`/`FRAME_MAX_BYTES`/`COMMAND_TTL`/`POLL_MS`, `IOT_PUBLIC_BASE_URL`; offline mặc định 5 chu kỳ (5 phút) |
| `src/sensorRanges.js` (mới) | Miền giá trị ingest (nhiệt độ −40..80, độ ẩm/đất 0..100), `wifi_rssi` |
| `src/main.js` | 422 thay 500 (kể cả lỗi CHECK 23514), xác thực `X-Device-Token`, lưu RSSI, kiểm tra miền cho MQTT, gọi `ensureDeviceAuthSchema`/`ensureCameraPushSchema` |
| `src/devices/deviceAuth.js` (mới) | Cột token, `POST /api/v1/public/devices/activate`, `authenticateSensor`, `findCameraByKey` |
| `src/devices/deviceLifecycle.js` | `claim-code` cho thiết bị đã claim khi `purpose=ACTIVATION`; select thêm `last_wifi_rssi` |
| `src/public/publicRoutes.js` | Mount router kích hoạt |
| `src/camera/commandService.js` (mới) | Bảng `camera_commands` + cột push; enqueue/take/complete/fail/get |
| `src/camera/cameraService.js` | `storeCaptureRecord` dùng chung; `storePushedFrame` (AI chạy nền); `latestCapture`; `captureFilePath` |
| `src/camera/registryRuntime.js` | Xác thực env **hoặc** khóa DB; tự tạo `camera_devices` từ claim; diagnostics/capabilities; `statusReasons`; ẩn IP với non-admin; route `/:id/commands`, `/:id/commands/:cid/result`, `/:id/frames` |
| `src/camera/cameraRoutes.js` | `capture-now` 202/409/201; `GET /commands/:id`; `snapshot` = ảnh mới nhất ở PUSH |
| `src/camera/cameraScheduler.js` | Lịch chụp tạo lệnh SCHEDULED ở PUSH |
| `src/read-api.js`, `src/alert-engine.js` | Mặc định offline 5 chu kỳ; `statusReasons`, `wifiRssi` cho thiết bị |
| `.env.example`, `package.json` (`check`) | Biến mới; kiểm tra cú pháp file mới |
| `test/device-auth.test.js`, `test/camera-push.test.js` (mới) | Token/kích hoạt/422; luồng push đầy đủ (BE không mở kết nối tới camera) |
| `test/*` (camera-capture, slow-link, storage, udp-camera-cache, work-drain) | Đặt `CAMERA_DELIVERY_MODE=PULL` (chúng kiểm đường kéo ảnh cũ) |
| `test/firmware-contract`, `camera-registration-auth`, `config` | Cập nhật: env rỗng → 401; offline mặc định 5 |
| `infrastructure/docker-compose.yml` | Thêm biến mới cho iot-service (mặc định giữ LOCAL) |

### Firmware (ngoài Git, `Documents/Arduino`)
| File | Thay đổi |
|---|---|
| `duriancare_config.h`, `dc_net.*`, `dc_endpoint.*`, `dc_http.*` (mới, giống hệt ở 2 sketch) | Cấu hình chung, Wi-Fi + NVS + BOOT + portal, tìm Backend, HTTP/HTTPS + SNTP |
| `sketch_jul25a/sketch_jul25a.ino` → 2.0.0 | Viết lại trên phần chung; kích hoạt token; `wifi_rssi`; CHANGELOG đầu file |
| `sketch_jul25a/sensor_secrets.example.h` | Ghi chú: tùy chọn |
| `CameraWebServer/CameraWebServer.ino` → 3.0.0 | Viết lại: push, poll lệnh, diagnostics, mDNS, XGA/fb1/10 MHz; CHANGELOG đầu file |
| `CameraWebServer/camera_registry_config.h` | Hằng số camera mới; phần chung chuyển sang `duriancare_config.h` |
| `CameraWebServer/camera_secrets.example.h` | Ghi chú: tùy chọn, khóa có thể để trống |
| Giữ nguyên | `camera_pins.h`, `board_config.h`, `partitions.csv`, `app_httpd.cpp`, `camera_diag.cpp`, `power_diag.cpp`, `camera_index.h`, `sensor_types.h`, mọi `*_secrets.h` |

### Web
`src/lib/camera/{client,types,status}.ts` (202/command, ảnh mới nhất kèm giờ chụp, lý do trạng thái, bỏ `updateConfig`/`pingSnapshot`,
bỏ IP khỏi type), `src/components/camera/CameraSection.tsx` (xóa hộp "Cấu hình Camera"/URL, luồng Chụp ngay theo lệnh, nút
→ hướng dẫn), `src/app/dashboard/client/devices/setup/page.tsx` (mới), `DashboardShell.tsx` (mục "Kết nối thiết bị"),
`src/components/iot/IotDevicePanel.tsx` + `src/lib/iot/types.ts` (STALE = "Hoạt động hạn chế" + lý do), test camera.

### Mobile
`src/lib/iotApi.ts` (captureNow 202, `waitForCameraCommand`, bỏ `updateCameraConfig`, bỏ IP khỏi type),
`src/features/camera/{CameraMonitorScreen.tsx,cameraStatus.ts}` (xóa modal nhập URL, luồng lệnh, DEGRADED do Wi-Fi vẫn xem ảnh),
`src/features/devices/DeviceSetupScreen.tsx` + `app/(main)/device-setup.tsx` (mới), `app/(main)/_layout.tsx`, test.

---

## 8. Lệnh đã chạy và kết quả thật (2026-11-19)

| Lệnh | Kết quả |
|---|---|
| `npm run check` (iot-service) | OK |
| `node --test` (iot-service) | 185 test: **176 pass, 0 fail, 9 skip** (test PG thật, không có biến môi trường) |
| Web `npx tsc --noEmit` | OK (không lỗi) |
| Web `npx jest` | 21 suite, **166/166 pass** |
| Web `npx eslint` (file đã sửa) | 0 lỗi, 4 cảnh báo có từ trước (`<img>`, biểu thức `toggleSlot`) |
| Mobile `npx tsc --noEmit` | OK |
| Mobile `node --test tests/*.test.cjs` | **43/43 pass** |
| `arduino-cli lib install WiFiManager@2.0.17` | Đã cài |
| `arduino-cli compile --fqbn esp32:esp32:esp32` (sensor, **không có** `sensor_secrets.h`) | OK — 1.254.220 B (95 % phân vùng app mặc định 1,25 MB), RAM 50.032 B |
| `arduino-cli compile --fqbn esp32:esp32:esp32cam` (camera, **không có** `camera_secrets.h`, `partitions.csv` của sketch) | OK — 1.478.109 B (46 % của 3 MB), RAM 77.656 B |
| Biên dịch lại tại chỗ (có `*_secrets.h` hiện có của người dùng, không đọc/in nội dung) | xem §8.1 |

Không chạy: `docker compose config` (Docker Desktop đang lỗi), test PG thật, nạp firmware, mọi kiểm thử phần cứng.

**Lưu ý dung lượng:** sensor dùng 95 % phân vùng app mặc định ("Default 4MB with spiffs", 1,25 MB). Nếu thêm tính năng,
chọn `PartitionScheme=min_spiffs` (1,9 MB, vẫn OTA) khi nạp.

### 8.1 Biên dịch tại chỗ (thư mục `Documents/Arduino/...`, có `*_secrets.h` cũ)
- `sketch_jul25a` (esp32:esp32:esp32): OK — 1.254.252 B (95 %), RAM 50.032 B.
- `CameraWebServer` (esp32:esp32:esp32cam): OK — 1.478.173 B (46 %), RAM 77.656 B.
- Các file dùng chung (`duriancare_config.h`, `dc_net.*`, `dc_endpoint.*`, `dc_http.*`) có sha256 giống hệt nhau ở 2 sketch.
- Bản sao lưu file không bí mật trước khi ghi đè: `Documents/Arduino/_backup_2026-11-19_pre-cloud/`.
- Không nạp (upload) lên thiết bị.

---

## 9. Chênh lệch code ↔ báo cáo (báo cho người dùng, báo cáo KHÔNG bị sửa)

- Báo cáo: offline sau 5 phút; code cũ mặc định 10 phút → **đã đổi code về 5 phút**.
- Bảng payload trong báo cáo thiếu `soil_raw`, `soil_status`, `dht_status` (và nay `wifi_rssi`, header `X-Device-Token`).
- Báo cáo ghi "đường truyền thiết bị chưa có xác thực và TLS" — nay có token + HTTPS ở CLOUD (nếu nộp lại cần cập nhật).
- Báo cáo mô tả BE nhận ảnh camera; nay camera đẩy ảnh (`/frames`) thay vì BE kéo `/capture`.
- TC39/TC40: nay trả 422 thay vì 500.
- Công thức độ ẩm đất `(r − r_dry)/(r_wet − r_dry)` tương đương `(r_dry − r)/(r_dry − r_wet)`; firmware vẫn chưa hiệu chuẩn (`SOIL_CALIBRATED=false`) nên không gửi %.
