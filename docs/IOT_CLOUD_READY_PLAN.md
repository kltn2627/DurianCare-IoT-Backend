# IoT Cloud-Ready — Kế hoạch (CHỜ DUYỆT)

Ngày: 2026-11-18. **Đã duyệt 2026-11-18 và đã thực hiện 2026-11-19 — xem `IOT_CLOUD_READY.md` (bàn giao, các điểm
khác kế hoạch ở §4 của file đó, ví dụ khóa camera lưu ở `iot_devices.device_token_hash`).** Nội dung dưới đây là bản kế hoạch gốc.
Trạng thái lúc viết: **kế hoạch, chưa sửa code**. Có thay đổi CSDL (cột/bảng cộng thêm) và đổi
hợp đồng API đang dùng (`POST /api/v1/camera/capture-now`, ngữ nghĩa `GET /api/v1/camera/snapshot`), nên
cần người dùng duyệt trước khi thực hiện. Mọi phần dưới đây chưa kiểm thử trên phần cứng.

---

## 1. Luồng hiện tại (đọc từ code, 2026-11-18)

| Luồng | Hiện tại | File |
|---|---|---|
| Sensor | ESP32 (FW 1.4.2) chờ beacon UDP 4210 v1 → `POST http://<ip>:<port><path>` mỗi 10 s (5–3600 s qua `config`), backoff 10→60 s, 3 lỗi + beacon im 30 s ⇒ dò lại. Wi-Fi từ `sensor_secrets.h` (`#error` nếu thiếu). Không xác thực. | `sketch_jul25a.ino`, `src/main.js` |
| Discovery | `hostBeacon.js` chạy trên Windows (Scheduled Task), phát v1 + dòng legacy mỗi 3 s, chỉ khi TCP 3001 trả lời. | `src/discovery/*`, `infrastructure/discovery-beacon/` |
| Camera registry | ESP32-CAM (FW 2.2.3) dò beacon, `POST /api/v1/public/cameras/register|heartbeat` (60 s) với `X-Camera-Key` so với env `CAMERA_DEVICE_BINDINGS` (key + farmId). `announcedAt` ±30 s. Hàng `camera_devices` phải được operator tạo trước (404). | `CameraWebServer.ino`, `src/camera/registryRuntime.js`, `deviceService.js` |
| Chụp ảnh | **BE kéo ảnh**: `capture-now`, `snapshot`, lịch cron đều gọi `GET http://<ip camera LAN>/capture`. Ảnh lưu `uploads/cameras`, `camera_captures`, AI `POST /api/v1/predict` (chờ đồng bộ). | `cameraService.js`, `cameraRoutes.js`, `cameraScheduler.js` |
| FE camera | Web/Mobile lấy ảnh qua `/api/v1/camera/snapshot` (proxy có xác thực, poll 8 s), nhưng **vẫn có hộp thoại nhập URL camera** (`POST /api/v1/camera/config`, ví dụ `http://camera.local`). Mobile type còn `ip_address`. | `CameraSection.tsx:514-552,1019-1026`, `CameraMonitorScreen.tsx:265-302`, `src/lib/iotApi.ts:166,193` |
| Gateway | Route `/api/v1/sensors/**`, `/api/v1/camera/**`, `/api/v1/public/**` → iot-service. Chỉ `/api/v1/public/**` được bỏ qua JWT. | `duriancare-gateway/.../application.yml:44`, `PublicEndpointMatcher` |

### Những chỗ sẽ hỏng khi BE lên cloud

1. **Beacon UDP không tới LAN vườn**: broadcast không qua router/NAT ⇒ cả 2 thiết bị kẹt `SERVER_NOT_DISCOVERED` vĩnh viễn (không có endpoint dự phòng).
2. **BE cloud không gọi được IP LAN camera** (`192.168.x.x`): `capture-now`, `snapshot`, lịch chụp đều thất bại (502/504). Tài liệu đã ghi nhận ở `CAMERA_REGISTRY_FIRMWARE_INTEGRATION.md` §Network.
3. **Web HTTPS không được nhúng ảnh HTTP nội bộ** (mixed content + IP riêng) — hiện FE đã đi qua proxy, nhưng hộp nhập URL camera vẫn khuyến khích cấu hình sai.
4. **`/api/v1/sensors/data` qua Gateway cần JWT** ⇒ thiết bị không gửi được qua domain công khai nếu không có đường riêng.
5. **Không xác thực/không TLS** cho sensor (bài báo ghi rõ đây là hạn chế) ⇒ ai cũng POST được số liệu giả cho `device_id` đã claim.
6. **Camera bắt buộc env `CAMERA_DEVICE_BINDINGS` + hàng `camera_devices` tạo tay** ⇒ không "tự hiện sau khi claim".
7. Body `express.json({limit:"32kb"})` toàn cục ⇒ cần parser riêng cho ảnh JPEG đẩy lên.

---

## 2. Kiến trúc mới

```
            ┌──────────── LOCAL (cùng Wi-Fi) ─────────────┐     ┌────────────── CLOUD ──────────────┐
ESP32 ──┐   │ beacon UDP 4210 → http://<laptop>:3001       │     │ https://api.duriancare.io.vn/iot  │
CAM ────┼──►│ (iot-service trực tiếp)                      │ hoặc│ reverse proxy (TLS) → iot-service │
        │   └──────────────────────────────────────────────┘     │ (chỉ allowlist đường thiết bị)    │
        │                                                        └───────────────────────────────────┘
Web/Mobile ──► Gateway (JWT) ──► iot-service ──► ảnh đã lưu / ảnh mới nhất (không bao giờ IP camera)
```

### 2.1 Tìm Backend (dùng chung 2 firmware, `dc_endpoint.{h,cpp}`)

Thứ tự (mỗi lần Wi-Fi lên và mỗi lần "dò lại"):

1. **NVS** `serverUrl` do người dùng nhập ở portal (`http://…` hoặc `https://…`, không path query fragment ngoài base path).
2. **Beacon UDP 4210 v1** — giữ nguyên parser/validate hiện tại (service, v==1, ip hợp lệ, port nguyên, path, sender == ip). Nghe trong cửa sổ `DISCOVERY_WINDOW_MS` (mặc định 12 s ≈ 4 gói) không chặn `loop()`.
3. **`DURIANCARE_CLOUD_BASE_URL`** trong `duriancare_config.h` (không bí mật, mặc định `""` ⇒ bỏ qua bước này).

Base URL dạng `scheme://host[:port][/prefix]`. Đường dẫn thiết bị nối sau prefix:
`/api/v1/sensors/data`, `/api/v1/public/devices/activate`, `/api/v1/public/cameras/...`.
Beacon ⇒ base `http://<ip>:<port>` (path beacon chỉ dùng cho sensor, như hiện nay).

Chuyển nguồn: 3 lỗi transport liên tiếp (hoặc 5 phản hồi 5xx) ⇒ đánh dấu nguồn đó "hỏng" trong vòng hiện
tại và thử nguồn kế tiếp; hết vòng ⇒ backoff `min(5 s·2^n, 120 s) + jitter(0–1 s)` rồi vòng lại từ NVS.
Beacon mới khác endpoint hiện tại ⇒ `SERVER_CHANGED` như cũ (chỉ khi nguồn hiện tại là beacon hoặc đang hỏng).
Không bao giờ chặn `loop()`; watchdog giữ nguyên (sensor 30 s, camera 60 s).

### 2.2 HTTP client (`dc_http.{h,cpp}`)

- `http://` ⇒ `WiFiClient`. `https://` ⇒ `WiFiClientSecure` + **CA bundle của core** (`client.setCACertBundle(...)`/`esp_crt_bundle_attach` theo API core 3.3.12 — sẽ xác minh khi compile). **Không** `setInsecure()`.
- HTTPS cần giờ đúng ⇒ SNTP trước khi gọi HTTPS (cả sensor). Nếu SNTP chưa có giờ sau 30 s: không gọi HTTPS (log `TIME_NOT_SYNCED`), vẫn dùng được nguồn HTTP LAN.
- Timeout: connect 8–10 s, response 5–15 s (giữ giá trị hiện tại). `setReuse(false)` cho sensor; camera dùng một client giữ kết nối cho poll lệnh khi HTTPS (giảm handshake), đóng khi lỗi.
- Không log header `X-Camera-Key`, `X-Device-Token`, mật khẩu Wi-Fi; body phản hồi chỉ in khi ≤ 1 KB và đã làm sạch.

### 2.3 Wi-Fi + portal (`dc_wifi.{h,cpp}`, `dc_portal.{h,cpp}`, WiFiManager tzapu)

- Một nơi điều khiển Wi-Fi: state machine hiện tại (`maintainWiFi`/`checkWiFi`), `setAutoReconnect(false)`, `persistent(false)`.
- Credential lưu trong `Preferences` namespace `dcnet` (`ssid`, `pass`, `server`, `claim`, `token`/`camkey`). Mặc định lấy từ `*_secrets.h` nếu có (`__has_include`), không `#error`.
- Mở portal khi: (a) không có SSID; (b) `WIFI_FAILS_BEFORE_PORTAL` (mặc định 6) lượt Wi-Fi liên tiếp thất bại; (c) giữ BOOT/GPIO0 ≥ 5 s trong 8 s đầu sau reset ⇒ xóa `dcnet` rồi mở portal.
- AP: `DurianCare-Setup-<4 ký tự hex cuối MAC>`, captive portal của WiFiManager, tham số: SSID/mật khẩu (form chuẩn), `Server URL` (tùy chọn), `Mã nhận thiết bị` (tùy chọn, 10 ký tự Crockford, cho phép gạch ngang).
- Portal chạy ở chế độ non-blocking (`setConfigPortalBlocking(false)` + `process()` trong `loop()`, reset watchdog), timeout 10 phút rồi quay lại thử Wi-Fi đã lưu. Khi lưu: ghi NVS và `ESP.restart()` (đơn giản, tránh xung đột cổng 80 với `httpd` camera và với state machine).
- Camera: portal mở **trước** khi khởi động `startCameraServer()`; nếu portal cần mở lúc đang chạy thì khởi động lại vào chế độ portal (cờ NVS `portalOnBoot`).
- ESP32-CAM AI-Thinker: GPIO0 **cũng là XCLK** (`camera_pins.h:163`, `XCLK_GPIO_NUM 0`). Vì vậy nút BOOT chỉ được đọc trước `esp_camera_init()` (cửa sổ 5 s ngay sau reset), sau đó GPIO0 thuộc LEDC XCLK. Trên đế ESP32-CAM-MB nút "IO0" là nút này. Sẽ ghi rõ trong tài liệu.
- Mất Wi-Fi liên tục 30 phút ⇒ `ESP.restart()` một lần (giữ như cũ).

### 2.4 Kích hoạt thiết bị bằng mã nhận (token thiết bị)

Mã nhận 10 ký tự hiện dùng cho nông dân claim trên Web. Đề xuất dùng **cùng mã** cho thiết bị kích hoạt:

- Thiết bị có `claim` trong NVS và chưa có token ⇒ `POST /api/v1/public/devices/activate`.
- BE so `sha256(deviceId:code)` với claim row (PENDING hoặc CLAIMED), chưa hết hạn, chưa `activated`. Sai mã ⇒ tăng `failed_attempts` chung (5 lần ⇒ 429), giữ thông báo chung (không lộ UID).
- Thành công ⇒ sinh token ngẫu nhiên 32 byte (base64url, 43 ký tự), lưu `sha256(token)`; trả token **một lần**. Thiết bị lưu NVS, xóa `claim`.
- Thứ tự claim (Web) và activate (thiết bị) không phụ thuộc nhau; dữ liệu vẫn 409 tới khi farmer claim.
- Admin cấp lại token: `POST /api/iot/devices/{id}/claim-code` (đã có) cấp mã mới; activate với mã mới ⇒ token cũ bị thay.
- Camera: activate trả `deviceKey` (cùng cơ chế) lưu `camera_devices.device_key_hash`; register/heartbeat chấp nhận **hoặc** khóa trong env `CAMERA_DEVICE_BINDINGS` (cũ) **hoặc** hash trong DB (mới). Camera `CAMERA` iot_device đã claim ⇒ register lần đầu tự tạo hàng `camera_devices` với `farm_id/zone_id` lấy từ `iot_devices` (không lấy từ firmware).

### 2.5 Camera đẩy ảnh + hàng đợi lệnh

- Camera chụp khi có lệnh (ON_DEMAND/SCHEDULED) và `POST` JPEG lên BE; BE không bao giờ gọi IP camera ở chế độ PUSH.
- **Nhận lệnh: poll `GET .../commands` mỗi 5 s + lệnh đi kèm phản hồi heartbeat.**
  - Chỉ heartbeat (60 s): trễ trung bình ~30 s, tối đa 60 s — quá chậm cho "Chụp ngay".
  - Poll 5 s: trễ nhận lệnh ≤ 5 s (TB 2,5 s) + thời gian chụp + upload (đo trước: UXGA 66,5 KB mất 7,1 s ở RSSI yếu ⇒ ảnh mặc định đổi xuống XGA/SVGA). Tải: 720 request/giờ/camera, mỗi phản hồi rỗng ~60 byte (HTTP 204); BE: một `SELECT` theo index `(device_id, status)`. HTTPS: giữ kết nối (keep-alive) để không bắt tay TLS mỗi 5 s; nếu proxy đóng kết nối thì poll giãn ra 15 s khi không có lệnh 10 phút liền và về 5 s ngay khi có lệnh/khi heartbeat báo `commandsPending`.
  - Long-poll/WebSocket/MQTT không chọn: HTTPClient của ESP32 chặn `loop()`, EMQX đang anonymous.
- Lịch chụp (`cameraScheduler.js`): cron tick ⇒ chèn lệnh `SCHEDULED` (nếu camera có heartbeat mới); hết hạn sau 10 phút nếu camera không lấy.
- Dự phòng LOCAL: `CAMERA_PULL_FALLBACK=true` (chỉ khi `IOT_DEPLOYMENT_MODE=LOCAL`) ⇒ camera không báo capability `push` (FW cũ) thì đi đường kéo ảnh cũ. Mặc định `false`.
- `/capture`, `/stream` trên camera giữ để debug trong LAN; FE không còn biết IP camera.

### 2.6 "Xem trực tiếp"

`GET /api/v1/camera/snapshot?device_id=` (giữ URL) trả **ảnh mới nhất đã nhận** (header `X-Frame-Captured-At`, `X-Frame-Age-Ms`). FE làm mới mỗi 10 s khi màn hình mở; nút "Chụp ngay" tạo lệnh rồi theo dõi tới khi ảnh mới về. Khi đang "xem trực tiếp", FE có thể gửi lệnh `ON_DEMAND` với `preview=true` (không chạy AI, độ phân giải thấp) tối đa 1 lệnh/15 s — **tùy chọn, xin duyệt** (tăng tải upload).

---

## 3. Hợp đồng API mới / đổi

Base cục bộ: `http://<laptop>:3001`. Base cloud: `https://api.duriancare.io.vn/iot` (proxy bỏ prefix `/iot`).

### 3.1 Mới — kích hoạt thiết bị
`POST /api/v1/public/devices/activate`
```json
{ "device_id": "esp32-01", "claim_code": "ABCDE-12345", "firmware_version": "2.0.0" }
```
- 200 sensor: `{"device_id":"esp32-01","device_token":"<43 ký tự>","token_header":"X-Device-Token"}`
- 200 camera: `{"device_id":"ESP32-CAM-001","camera_key":"<43 ký tự>","key_header":"X-Camera-Key"}`
- 400 mã sai/UID lạ (cùng thông điệp) · 409 mã đã dùng để kích hoạt · 410 hết hạn · 429 quá 5 lần.

### 3.2 Đổi (tương thích ngược) — ingest sensor
`POST /api/v1/sensors/data` + header tùy chọn `X-Device-Token`.

| `IOT_DEVICE_AUTH_MODE` | Không token | Token sai | Token đúng |
|---|---|---|---|
| `OPTIONAL` (mặc định LOCAL) | nhận như cũ | 401 | nhận |
| `REQUIRED` (mặc định CLOUD) | 401 `DEVICE_TOKEN_REQUIRED` | 401 | nhận |

Thiết bị **đã kích hoạt** (có token hash) mà gửi không token ⇒ 401 ở cả hai chế độ (chống giả mạo sau khi đã nâng cấp).

**Sửa lỗi 500 → 422** (TC39/TC40): kiểm tra miền trước khi INSERT:
`humidity ∈ [0,100]`, `soil_moisture ∈ [0,100]`, `temperature ∈ [-40,80]` (dải DHT22; CSDL chưa có ràng buộc nhiệt độ — đề xuất thêm vào kiểm tra ứng dụng, **không** thêm CHECK DB).
```json
422 {"error":"humidity must be between 0 and 100","code":"VALUE_OUT_OF_RANGE","field":"humidity"}
```
Lỗi ràng buộc CSDL còn sót (`23514` check_violation) cũng map sang 422 thay vì 500. Cùng kiểm tra áp cho MQTT (bỏ bản ghi, log).

### 3.3 Mới — camera đẩy ảnh
`POST /api/v1/public/cameras/{cameraId}/frames?capturedAt=<epoch ms>&reason=SCHEDULED|ON_DEMAND[&commandId=<uuid>]`
Header `X-Camera-Key`, `Content-Type: image/jpeg`, body JPEG (FFD8…FFD9), tối đa `CAMERA_FRAME_MAX_BYTES` (mặc định 2 MiB).
- `capturedAt`: trong `[now − 10 phút, now + 30 s]` (ảnh có thể chờ upload lại), nếu không 400.
- 201 `{"captureId":"…","imageUrl":"/api/v1/camera/image/cam_….jpg","aiStatus":"PROCESSING","commandId":"…"}` — AI chạy nền (không giữ camera chờ ~17 s).
- 400 thiếu/sai tham số hoặc không phải JPEG đầy đủ · 401 khóa sai · 404 camera chưa có · 413 quá lớn · 409 `commandId` không thuộc camera/đã xong.
- `reason=ON_DEMAND` lưu `capture_type='MANUAL'` (giữ CHECK hiện tại), `SCHEDULED` ⇒ `'SCHEDULED'`.

### 3.4 Mới — lệnh cho camera
`GET /api/v1/public/cameras/{cameraId}/commands` (header `X-Camera-Key`)
- 204 không có lệnh; 200:
```json
{ "commands": [ { "id": "uuid", "type": "CAPTURE", "reason": "ON_DEMAND", "preview": false,
                  "issuedAt": 1763452800000, "expiresAt": 1763452920000 } ],
  "pollAfterMs": 5000 }
```
Lệnh trả ra ⇒ `DELIVERED`; ảnh có `commandId` ⇒ `COMPLETED`. Camera báo lỗi chụp:
`POST /api/v1/public/cameras/{cameraId}/commands/{id}/result` `{"status":"FAILED","error":"CAMERA_CAPTURE_FAILED"}` ⇒ 204.
Heartbeat 200 thêm trường (cộng thêm, FW cũ bỏ qua): `"commandsPending": 1`.

### 3.5 Đổi — heartbeat/register (cộng thêm)
Body thêm tùy chọn:
```json
"capabilities": ["push", "commands"],
"diagnostics": { "vdd33": 3301, "resetReason": "POWERON", "uptimeSec": 3600,
  "freeHeap": 81234, "minFreeHeap": 60123, "freePsram": 3801234, "largestFreePsram": 3700000,
  "captureOk": 42, "captureFailures": 1, "wifiRssi": -83, "wifiChannel": 6,
  "xclkMhz": 10, "frameSize": "XGA", "endpointSource": "BEACON" }
```
Chỉ nhận số/chuỗi trong miền định sẵn; trường lạ bỏ qua; sai kiểu ⇒ bỏ cả khối `diagnostics` (không 400, để không làm FW mất heartbeat).
`ipAddress` vẫn bắt buộc (private IPv4 / `.local`) để tương thích; ở chế độ PUSH chỉ để hiển thị cài đặt/debug, FE không hiển thị.

### 3.6 Đổi — UI endpoints (qua Gateway, JWT)
| Endpoint | Trước | Sau |
|---|---|---|
| `POST /api/v1/camera/capture-now` | 201 + capture (đồng bộ, kéo ảnh) | **PUSH**: 202 `{"commandId","status":"PENDING","expiresAt"}`; **PULL fallback**: 201 như cũ. FE xử lý cả hai. |
| `GET /api/v1/camera/commands/{id}` (mới) | — | `{"id","status":"PENDING|DELIVERED|COMPLETED|FAILED|EXPIRED","capture":{…}|null,"error"}` |
| `GET /api/v1/camera/snapshot` | proxy `/capture` | PUSH: ảnh mới nhất đã lưu (404 `NO_FRAME_YET` nếu chưa có). PULL fallback: như cũ. |
| `GET /api/v1/camera/devices[/id]` | có `ip_address`, `port`… | thêm `statusReasons: ["WIFI_WEAK","CAMERA_INIT_FAILED",…]`, `diagnostics`, `lastFrameAt`, `deliveryMode`; **bỏ `ip_address/port/protocol/mac_address` khỏi phản hồi cho FE** (giữ cho ADMIN, xin ý kiến). |
| `POST /api/v1/camera/config` | sửa URL camera | Giữ cho ADMIN ở LOCAL (debug), FE không còn gọi. |

### 3.7 Trạng thái thiết bị (5 phút)

- Camera: giữ `cameraView` (ONLINE/DEGRADED/OFFLINE/UNKNOWN, 5 phút). DEGRADED khi `cameraReady=false` **hoặc** `wifiRssi < -80` **hoặc** ≥ 3 lỗi chụp liên tiếp; `statusReasons` liệt kê lý do.
- Sensor: hiện là ONLINE/STALE/OFFLINE với mặc định `60 s × 10 = 10 phút` ⇒ **lệch báo cáo (5 phút)**. Đề xuất: đổi mặc định `IOT_OFFLINE_AFTER_INTERVALS` 10 → 5 (5 phút), STALE ⇒ hiển thị "DEGRADED" ở FE, thêm lý do (`DHT_SENSOR_ERROR`, `SOIL_UNSTABLE`, `WIFI_WEAK` nếu sensor gửi RSSI). **Xin duyệt** vì đổi hành vi watchdog `DEVICE_OFFLINE`.

---

## 4. Biến môi trường mới (iot-service)

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `IOT_DEPLOYMENT_MODE` | `LOCAL` | `LOCAL` hoặc `CLOUD`; chọn mặc định cho các biến dưới |
| `IOT_DEVICE_AUTH_MODE` | `OPTIONAL` (LOCAL) / `REQUIRED` (CLOUD) | token sensor |
| `CAMERA_DELIVERY_MODE` | `PUSH` | `PUSH` hoặc `PULL` (cũ) |
| `CAMERA_PULL_FALLBACK` | `false` | chỉ có hiệu lực khi LOCAL |
| `CAMERA_FRAME_MAX_BYTES` | `2097152` | giới hạn body ảnh |
| `CAMERA_COMMAND_TTL_SECONDS` | `120` (ON_DEMAND), `600` (SCHEDULED) | hết hạn lệnh |
| `CAMERA_COMMAND_POLL_MS` | `5000` | gợi ý `pollAfterMs` |
| `IOT_PUBLIC_BASE_URL` | `""` | base công khai cho thiết bị (`https://api.duriancare.io.vn/iot`), chỉ để hiển thị trong tài liệu/hướng dẫn trên FE |
| `CAMERA_DEVICE_BINDINGS` | (giữ) | vẫn hỗ trợ; không bắt buộc nữa khi camera kích hoạt bằng mã nhận |

Web: `NEXT_PUBLIC_API_BASE_URL` đã có (trỏ Gateway). Không biến IP nào.
Firmware: `DURIANCARE_CLOUD_BASE_URL` trong `duriancare_config.h` (cả 2 sketch, giá trị `""` tới khi deploy).

---

## 5. Thay đổi CSDL (idempotent lúc startup, chỉ cộng thêm, không xóa/ghi đè dữ liệu)

Trong `ensureDeviceLifecycleSchema` / `ensureCameraHealthSchema` / hàm mới `ensureCameraCommandSchema`:

```sql
ALTER TABLE iot_devices ADD COLUMN IF NOT EXISTS device_token_hash VARCHAR(64);
ALTER TABLE iot_devices ADD COLUMN IF NOT EXISTS device_token_issued_at TIMESTAMPTZ;
ALTER TABLE iot_device_claims ADD COLUMN IF NOT EXISTS activated_at TIMESTAMPTZ;

ALTER TABLE camera_devices ADD COLUMN IF NOT EXISTS device_key_hash VARCHAR(64);
ALTER TABLE camera_devices ADD COLUMN IF NOT EXISTS diagnostics JSONB;
ALTER TABLE camera_devices ADD COLUMN IF NOT EXISTS wifi_rssi INTEGER;
ALTER TABLE camera_devices ADD COLUMN IF NOT EXISTS capabilities_reported TEXT[];
ALTER TABLE camera_devices ADD COLUMN IF NOT EXISTS last_frame_at TIMESTAMPTZ;

ALTER TABLE camera_captures ADD COLUMN IF NOT EXISTS command_id UUID;
ALTER TABLE camera_captures ADD COLUMN IF NOT EXISTS delivery VARCHAR(8);   -- PUSH | PULL, NULL = cũ
ALTER TABLE camera_captures ADD COLUMN IF NOT EXISTS received_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS camera_commands (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id VARCHAR(150) NOT NULL,
  type VARCHAR(16) NOT NULL DEFAULT 'CAPTURE',
  reason VARCHAR(16) NOT NULL,                 -- ON_DEMAND | SCHEDULED
  preview BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  requested_by VARCHAR(150),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  delivered_at TIMESTAMPTZ, completed_at TIMESTAMPTZ, expires_at TIMESTAMPTZ NOT NULL,
  capture_id UUID, error VARCHAR(64),
  CONSTRAINT ck_camera_commands_reason CHECK (reason IN ('ON_DEMAND','SCHEDULED')),
  CONSTRAINT ck_camera_commands_status CHECK (status IN ('PENDING','DELIVERED','COMPLETED','FAILED','EXPIRED'))
);
CREATE INDEX IF NOT EXISTS idx_camera_commands_pending ON camera_commands (device_id, created_at) WHERE status IN ('PENDING','DELIVERED');
-- tối đa 1 lệnh SCHEDULED đang chờ mỗi camera (tránh dồn khi camera offline)
CREATE UNIQUE INDEX IF NOT EXISTS uq_camera_commands_one_scheduled ON camera_commands (device_id) WHERE status IN ('PENDING','DELIVERED') AND reason = 'SCHEDULED';
```
Không đổi ràng buộc cũ (`ck_camera_capture_type` vẫn MANUAL|SCHEDULED). Rollback: code cũ bỏ qua cột/bảng mới.

---

## 6. Firmware

Cấu trúc thư mục (file `dc_*` **giống hệt nhau** ở 2 sketch; một script `tools/sync-common.ps1` không cần — sẽ ghi checksum trong CHANGELOG):

```
sketch_jul25a/               CameraWebServer/
  sketch_jul25a.ino (2.0.0)    CameraWebServer.ino (3.0.0)
  duriancare_config.h          duriancare_config.h          (không bí mật: CLOUD URL, thời gian, cổng)
  dc_net.h / dc_net.cpp        dc_net.h / dc_net.cpp        (Wi-Fi state machine + NVS + nút BOOT + portal)
  dc_endpoint.h / .cpp         dc_endpoint.h / .cpp         (NVS → beacon → cloud, backoff, failover)
  dc_http.h / .cpp             dc_http.h / .cpp             (HTTP/HTTPS, CA bundle, SNTP gate)
  sensor_types.h               camera_registry_config.h, camera_pins.h, board_config.h, partitions.csv (giữ)
  sensor_secrets.example.h     camera_secrets.example.h     (chỉ còn giá trị mặc định tùy chọn)
                               app_httpd.cpp, camera_diag.cpp, power_diag.cpp (giữ, chỉnh nhỏ)
```

Sensor 2.0.0: giữ payload (`device_id, temperature, humidity, soil_raw, soil_status, dht_status, soil_moisture?, firmware_version, config_version`), chu kỳ 10 s (5–3600 s), công thức đất `s = clamp((r_dry − raw)/(r_dry − r_wet)·100, 0, 100)` (`r_dry=3200, r_wet=1200`, chỉ gửi % khi `SOIL_CALIBRATED`), trạng thái `NO_SIGNAL ≤ 50`, `SATURATED ≥ 4040`, `UNSTABLE` spread > 400 / 8 mẫu. Thêm `X-Device-Token`, `wifi_rssi` (tùy chọn, BE bỏ qua nếu không biết — **trường mới, BE sẽ nhận**), kích hoạt bằng mã nhận.

Camera 3.0.0: đăng ký/heartbeat như cũ + `capabilities`, `diagnostics`; poll lệnh 5 s; chụp ⇒ POST frame; mặc định `FRAMESIZE_XGA`, `jpeg_quality 12`, `fb_count=1`, `CAMERA_GRAB_WHEN_EMPTY`, XCLK 10 MHz (hằng số trong config, 20 MHz giữ làm lựa chọn); không deinit/reinit lúc chạy (lỗi init ⇒ restart như 2.2.2); mDNS `duriancare-cam-<id>.local` (thư viện `ESPmDNS` của core).

Thư viện: ArduinoJson (đã có), Preferences/WiFiClientSecure/ESPmDNS (core 3.3.12), **WiFiManager by tzapu — chưa cài**, sẽ `arduino-cli lib install "WiFiManager@2.0.17"` (nằm trong danh sách được phép). Compile-only `esp32:esp32:esp32` và `esp32:esp32:esp32cam:PartitionScheme=custom`(dùng `partitions.csv` trong sketch).

---

## 7. Camera chập chờn — bảng nguyên nhân (bản nháp, sẽ hoàn thiện ở `IOT_CLOUD_READY.md`)

| Nguyên nhân | Bằng chứng hiện có | Đánh giá | Khắc phục phần mềm | Phần cứng (khuyến nghị) |
|---|---|---|---|---|
| Wi-Fi yếu / kênh | RSSI −83/−87/−88 dBm ở 3 vị trí; có lúc quét không thấy mạng (log `[wifi-scan]`, ghi chú phiên trước); `IOT_TROUBLESHOOTING.md`: `/capture` chậm khi RSSI ≤ −85 | **Có bằng chứng**, nguyên nhân chính khả dĩ nhất | Ảnh nhỏ hơn (XGA), upload một lần thay vì BE kéo; gửi RSSI/kênh trong heartbeat; `WiFi.setTxPower(WIFI_POWER_19_5dBm)` (xin xác minh không làm sụt nguồn); timeout giữ 10/15 s | Kiểm tra điện trở chọn ăng-ten (PCB vs IPEX), lắp ăng-ten ngoài 2,4 GHz, AP/repeater gần |
| Nguồn / brownout | VDD33 lệch < 1 %, không reset BROWNOUT (`power_diag.cpp`, phiên trước) | **Đã loại trừ** ở điều kiện đo USB | Giữ báo `vdd33`, `resetReason` trong heartbeat | Nguồn 5 V ≥ 2 A riêng khi lắp ngoài vườn; đo lại khi đổi nguồn |
| UXGA + `fb_count=2` + `GRAB_LATEST` | `initCamera()` dòng 553–564: UXGA, 2 buffer PSRAM, chế độ chụp liên tục | **Giả thuyết**: driver chụp liên tục chiếm băng thông PSRAM/DMA cạnh Wi-Fi; khung 1600×1200 lớn ⇒ upload lâu (đo 66,5 KB/7,1 s) | `fb_count=1`, `GRAB_WHEN_EMPTY`, XGA mặc định; cấu hình được | — |
| XCLK 20 MHz | `config.xclk_freq_hz = 20000000` | **Giả thuyết** (tài liệu esp32-camera khuyến nghị thử giảm XCLK khi lỗi khung hình — cần xác minh trích dẫn khi viết tài liệu) | Mặc định 10 MHz, ghi `xclkMhz` vào diagnostics để so sánh A/B | Cáp FPC chắc, không gập |
| Khởi tạo lại camera / heap phân mảnh | SCCB NACK khi deinit/init lại (đo 31/10, nguồn ổn); đã bỏ tạm dừng camera ở 2.2.2 | **Có bằng chứng** cho reinit; phân mảnh heap **chưa có số liệu** | Không reinit lúc chạy (restart sau 2 lỗi); gửi `freeHeap/minFreeHeap/largestFreePsram` để có số liệu | Cắm lại cáp camera nếu NACK ngay cả lúc boot |
| Lệch giờ ⇒ `announcedAt` bị từ chối | Registry trả 400/409 khi lệch > 30 s (`registryRuntime.js`); FW đã có SNTP + Date header fallback | **Cơ chế có, tần suất chưa đo** | Đếm số lần 400/409 trong diagnostics; HTTPS cũng phải có giờ đúng | — |

---

## 8. Web / Mobile

Web:
- `CameraSection.tsx`: bỏ hộp "Cấu hình URL camera" và `cameraClient.updateConfig/pingSnapshot`; "Xem trực tiếp" làm mới ảnh mới nhất 10 s + nhãn "ảnh lúc HH:mm:ss"; "Chụp ngay" ⇒ 202 ⇒ poll `/commands/{id}` 2 s tới COMPLETED/FAILED/EXPIRED (vẫn chấp nhận 201 cũ).
- `lib/camera/status.ts`: thêm lý do (`WIFI_WEAK`, `CAPTURE_FAILING`, …).
- Trang mới `/dashboard/client/devices/setup` "Hướng dẫn kết nối thiết bị" (4 bước) + link từ trang cảm biến và camera.
- Danh sách thiết bị của vườn: camera đã claim tự hiện (từ `/api/v1/camera/devices`) cạnh sensor.

Mobile:
- `CameraMonitorScreen.tsx`: bỏ modal nhập URL; như Web. `iotApi.ts`: bỏ `updateCameraConfig`, bỏ `ip_address` khỏi type hiển thị.
- Màn hình "Hướng dẫn kết nối thiết bị".
- **mDNS dò LAN: cần thư viện native (`react-native-zeroconf` 0.13.x) + dev build — không chạy trong Expo Go. Đề xuất HOÃN, xin duyệt riêng.**

---

## 9. File sẽ sửa

Backend (`duriancare-iot-service`): `src/main.js` (422, token, raw parser), `src/config.js`, `src/devices/deviceLifecycle.js` (cột, activate), `src/public/publicRoutes.js` (+ `deviceActivationRoutes.js` mới), `src/camera/registryRuntime.js` (DB key, diagnostics, frames, commands), `src/camera/deviceService.js`, `src/camera/cameraService.js` (`storeFrame` tách từ `persistCapture`, AI nền), `src/camera/cameraRoutes.js` (capture-now 202, snapshot latest, commands/{id}), `src/camera/cameraScheduler.js` (enqueue), `src/camera/commandService.js` (mới), `.env.example`, `package.json` (`check`), test mới: `device-activation.test.js`, `sensor-range.test.js`, `camera-push.test.js`, `camera-commands.test.js`; cập nhật `firmware-contract.test.js`, `camera-capture.test.js` nếu đổi chữ ký.
`infrastructure/docker-compose.yml` (thêm biến môi trường, không đổi cổng). Gateway: **không đổi** (thiết bị đi `/api/v1/public/**` hoặc proxy `/iot`), trừ khi người dùng chọn đi qua Gateway cho sensor (xem §10 câu 4).
Docs: `IOT_CLOUD_READY.md` (mới), cập nhật `CAMERA_REGISTRY_FIRMWARE_INTEGRATION.md`, `IOT_SENSOR_CONNECTIVITY.md`, `IOT_TROUBLESHOOTING.md`.

Firmware: viết lại 2 sketch như §6. Web: `src/lib/camera/{client,types,status}.ts`, `src/components/camera/CameraSection.tsx`, trang setup mới, `src/components/iot/IotDevicePanel.tsx` (trạng thái/lý do). Mobile: `src/lib/iotApi.ts`, `src/features/camera/{CameraMonitorScreen,cameraStatus}.tsx`, màn hình setup mới + route `app/(main)/device-setup.tsx`.

Không đụng: Cultivation Schedule, Expert Chat, `*_secrets.h`, `infrastructure/.env`, dữ liệu CSDL, container.

---

## 10. Cần người dùng quyết định

1. Duyệt thay đổi CSDL §5 (cộng thêm, idempotent) và đổi hợp đồng `capture-now` (202 ở PUSH) + `snapshot` (ảnh mới nhất).
2. Dùng **cùng mã nhận 10 ký tự** cho thiết bị kích hoạt lấy token (§2.4)? (Phương án khác: Web hiển thị token sau claim để gõ vào portal — dài, dễ sai.)
3. Sensor offline: đổi mặc định 10 phút → 5 phút cho khớp báo cáo (§3.7)?
4. Đường cloud: proxy `https://api.duriancare.io.vn/iot/...` thẳng tới iot-service **chỉ allowlist** `POST /api/v1/sensors/data`, `/api/v1/public/devices/activate`, `/api/v1/public/cameras/**` và xóa header `X-Auth-*` (đề xuất) — hay đi qua Gateway (thêm `POST /api/v1/sensors/data` vào danh sách public của Gateway)?
5. Bỏ `ip_address/port/mac` khỏi phản hồi camera cho vai trò không phải ADMIN?
6. Hoãn mDNS trên Mobile (cần thư viện native)?

## 11. Chênh lệch code ↔ báo cáo (báo cho người dùng, KHÔNG sửa báo cáo)

- Báo cáo: "thiết bị không gửi quá 5 phút thì `DEVICE_OFFLINE`"; code mặc định `60 s × IOT_OFFLINE_AFTER_INTERVALS(10)` = **10 phút** (`src/config.js`), compose không ghi đè.
- Bảng payload trong báo cáo không có `soil_raw`, `soil_status`, `dht_status` (code và firmware có; báo cáo chỉ nói "giá trị thô được lưu riêng").
- Công thức báo cáo `(r − r_dry)/(r_wet − r_dry)` tương đương đại số với `(r_dry − r)/(r_dry − r_wet)` trong yêu cầu; firmware dùng `map()` (số nguyên) — khớp.
- Báo cáo ghi "Đường truyền thiết bị chưa có xác thực và TLS" — sau thay đổi này sẽ có token + HTTPS ở chế độ CLOUD (báo cáo cần cập nhật nếu nộp lại).
- TC39/TC40 (HTTP 500) — sẽ sửa thành 422.
- Hysteresis (`alert-engine.js:184-186`) và `date_bin` avg/min/max (`read-api.js:374`) khớp báo cáo, không đổi.
