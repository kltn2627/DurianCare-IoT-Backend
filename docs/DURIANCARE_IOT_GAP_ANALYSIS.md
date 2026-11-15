# DurianCare IoT — Gap analysis & use-case traceability

Audit date: 2026-10-28. Sources compared:

- **Report B** (DurianCare, unfinished): scope, actors, UC79–UC89 (device onboarding, monitoring, configuration,
  assignment, telemetry & anomaly detection, notification, thresholds, camera).
- **Report A** (coffee project, reference only): UC83–UC88 specifications and activity diagrams.
- **Code** in `DurianCare-IoT-Backend`, `DurianCare-IoT-Web-Client`, `DurianCare-IoT-Mobile-App`, sensor sketch
  `Documents/Arduino/sketch_jul25a`, camera sketch `Documents/Arduino/CameraWebServer`.

Status legend: **OK** = implemented and exercised by tests/runtime · **PARTIAL** = exists but incomplete ·
**MISSING** · **NOT INTEGRATED** = code exists, not wired end-to-end · **UNVERIFIED** · **N/A** = out of scope / duplicate.

## 1. Entity mapping (reference → DurianCare)

| Reference entity (A) | DurianCare today (before this work) | Decision |
|---|---|---|
| IoTDevice | `duriancare_iot.iot_devices` (uid, name, farm_id, cultivation_area_id, status ACTIVE/INACTIVE/MAINTENANCE/DELETED, last_seen_at). No type, firmware, provisioning/claim state. Cameras live in a separate `camera_devices` registry. | Extend `iot_devices` (type, provisioning status, firmware, claimed_by/at). Keep camera registry separate. |
| DeviceClaim | MISSING. Any farm manager could register an unknown UID straight into their farm (UID acted as the secret). | New `iot_device_claims` (hashed single-use code, expiry, attempt lock). Admin provisions, farmer claims. |
| DeviceConfig | MISSING for sensors (camera has its own capture config). | New `iot_device_configs` + delivery over the existing HTTP ingestion response. |
| FarmPlot / FarmZone / Plant | farm-service `Farm` with embedded `zones` (FarmZone) and `DurianTree`. | Reuse. Plot = Farm, Zone = FarmZone (`cultivation_area_id`), Plant = Tree. No new entity. |
| SensorReadingSeries | `duriancare_iot.telemetry` (one row per reading, wide columns temperature/humidity/soil_moisture/light, `source` provenance). | Reuse; add farm/zone attribution captured at ingestion time. |
| SensorLatestReading | Derived per request (latest row via index `(device_id, timestamp desc)`). | Keep derived (no second source of truth); add latest value per metric. |
| sensor_reading_agg_5m/1h/1d | On-demand `date_bin` buckets (`/telemetry/buckets`), DEVICE vs SYNTHETIC kept apart. | Reuse; bucket ladder covers 5 min / 1 h / 1 day. Add min/max for every metric. |
| AlertRule | Global env thresholds only (temperature/humidity/light); no soil moisture; no per-farm/zone rule. | New `iot_alert_rules` (farm / zone / device scope, min/max, hysteresis, severity, enable, notify). |
| AlertEvent | `iot_alerts` ALERTING → ACKNOWLEDGED / RECOVERED, dedupe per device+type, offline alerts. **Bug:** an acknowledged alert never recovers and a new ALERTING row (and notification) is created on the next breach. | Reuse (ALERTING≈OPEN, ACKNOWLEDGED≈ACKED, RECOVERED≈RESOLVED); fix ACK lifecycle; add severity/rule link; hysteresis. |
| DeviceMediaEvent | `camera_captures` (+ AI diagnosis columns), schedules, signed camera registry. | Reuse; document hardware test plan. |
| Notification | notification-service: Kafka topic `KAFKA_ALERT_TOPIC` (compose: `iot.alert.email`, code default `duriancare.notification.events`) → IN_APP inbox (Web + Mobile). SMTP mail used for OTP. No FCM provider. | Reuse IN_APP. PUSH/EMAIL for alerts not claimed. |
| Plan / PlantEvent / ApplyPlan | cultivation-service (seasons, tasks, chemical logs — teammate) + farm-service tree care plans/tasks. | N/A here (owned by teammates); mapped in §4. |
| Conversation / Message | chat-service expert chat (teammate). | N/A (no change). |
| Post / Comment / Vote | auth-service community posts, comments/replies, reactions (V8–V10), Web/Mobile community screens. | Exists; not re-built. |

## 2. Use-case traceability (activity-diagram steps → code)

| Use case (B / A) | Step (activity diagram) | Backend/API | Web | Mobile | Data | Before | Action |
|---|---|---|---|---|---|---|---|
| UC79/UC83 Đăng ký thiết bị | Nhập/quét thông tin → kiểm tra → lưu PROVISIONED → sinh mã claim → hiển thị | `POST /api/iot/devices` (direct bind, no claim) | Register by UID into a farm | Registry form (UID + farm) | iot_devices | PARTIAL (no claim, UID-as-secret) | Admin provisioning endpoint with claim code; duplicate/missing-field branches |
| UC82/UC86 Gắn thiết bị vào vùng | Chọn farmPlot/zone → kiểm tra mã claim → gán chủ + vùng → cập nhật trạng thái claim | MISSING (claim); PATCH reassignment exists | MISSING | MISSING | iot_devices, claims | MISSING | `POST /api/iot/devices/claim` (expired / used / invalid / locked branches); reuse PATCH for move/unassign |
| UC80/UC84 Theo dõi thiết bị | Thiết bị gửi → kiểm tra → dashboard hiển thị mới nhất + firmware; nhánh không gửi/offline/chưa có dữ liệu | Devices API with connectivity ONLINE/STALE/OFFLINE/UNKNOWN | Sensor dashboard (gauges, charts) | Climate monitor | telemetry, iot_devices | PARTIAL (no firmware, no per-metric latest, no type) | firmware + type + latest per metric + open alerts per device |
| UC81/UC85 Cấu hình thiết bị | Sửa cấu hình → kiểm tra → lưu, chờ gửi → thiết bị nhận/áp dụng → ACK → hiển thị | MISSING | MISSING | MISSING | — | MISSING | DeviceConfig + delivery/ACK over HTTP ingestion; honest PENDING/DELIVERED/APPLIED states |
| UC83/UC87 Thu thập & phát hiện bất thường | Gửi định kỳ → kiểm tra → lưu → đánh giá ngưỡng → tạo cảnh báo → thông báo | Ingestion OK; HTTP alerts off by default; unregistered UIDs accepted | Charts | Charts | telemetry, iot_alerts | PARTIAL | Reject unregistered/unclaimed devices; zone attribution; rule-based evaluation |
| UC84/UC88 Gửi thông báo cảnh báo | Xác định thiết bị/nông hộ → AlertEvent OPEN → dashboard → push nếu đăng ký → ack/resolve | Kafka → notification IN_APP; ack endpoint | Notification inbox; alert list partial | Inbox; alert list in climate monitor | iot_alerts, notifications | PARTIAL (ACK bug) | Fix ACK lifecycle, severity in message, alert list + ack in Web |
| UC85 Thiết lập ngưỡng cảnh báo | — (B only) | Global env | MISSING | MISSING | — | MISSING | AlertRule CRUD (farm/zone/device), precedence device > zone > farm > default |
| UC86/UC89 Camera ảnh/lịch chụp | — | camera routes, scheduler, registry, captures | Camera section | Camera screen | camera_* | PARTIAL (hardware unverified) | Test plan only (no flashing) |
| UC87 Cập nhật thông tin thiết bị | — | PATCH name/status/zone | partial | registry edit | iot_devices | OK | keep |
| UC88 Gỡ thiết bị | — | DELETE (soft) | — | registry delete | iot_devices | OK | allow admin re-provisioning of a deleted UID |

## 3. Progress log

Implementation session 2026-10-28 finished; final status in §5. Contract details: `IOT_DEVICE_LIFECYCLE.md`.
Camera next phase: `CAMERA_HARDWARE_TEST_PLAN.md`.

## 4. Cultivation / chat / community entities (no code change in this phase)

| Entity | Where it already lives | Owner | Note |
|---|---|---|---|
| Plan | cultivation-service seasons/task templates; farm-service `TreeCarePlan` | teammate / farm-service | Not duplicated. |
| PlantEvent | cultivation-service task logs, chemical applications; farm-service tree diagnoses & care tasks | teammate | Not duplicated. |
| ApplyPlan | cultivation-service schedule application; farm-service accept care recommendation | teammate | Not duplicated. |
| Conversation / Message | chat-service (expert chat) | teammate | Untouched. |
| Notification | notification-service | shared | Reused for IoT alerts. |
| Post / Comment / Vote | auth-service community (posts, replies, reactions) | existing | Untouched. |

## 5. Final status (2026-10-28)

Deployed runtime is unchanged: the new code is **not** in the running containers until `duriancare-iot-service` and
`duriancare-notification-service` are rebuilt (owner approval required). Nothing was committed or flashed.

| Item | Before | Done in this session | Tested | Not tested / blocker | Main files |
|---|---|---|---|---|---|
| Provisioning + claim code (UC79) | MISSING | Admin `POST /api/iot/devices/provision`, hashed single-use code, expiry, regeneration, admin inventory; direct UID binding restricted to ADMIN | Unit + real-Postgres (rolled back) | Live Gateway call after rebuild | `src/devices/deviceLifecycle.js`, `src/read-api.js`, Web `AdminDeviceInventory.tsx` |
| Claim + assignment (UC82) | MISSING | `POST /api/iot/devices/claim` (403/400/429/410/409 branches), zone attribution; move/unassign via existing PATCH | Real-Postgres | Live UI click-through | same + Web `IotDeviceRegistration.tsx`, Mobile `DurianClimateMonitorScreen.tsx` |
| Device monitoring (UC80) | PARTIAL | type, firmware (reported in telemetry), provisioning, latest per metric, open alerts on detail | Unit + real-Postgres + fixture | Physical sensor not re-flashed with 1.3.0 | `read-api.js`, `main.js`, Web `IotDevicePanel.tsx`, Mobile `IotDeviceSettingsCard.tsx` |
| Device configuration (UC81) | MISSING | `iot_device_configs`, PUT/GET, delivery over ingestion response, PENDING→DELIVERED→APPLIED; firmware 1.3.0 applies + echoes | Real-Postgres + ingestion fixture; firmware **compiled** | Not flashed (task rule), so APPLIED never observed on hardware | `deviceLifecycle.js`, `main.js`, `Documents/Arduino/sketch_jul25a` |
| Ingestion gate + attribution (UC83) | PARTIAL | unregistered 404 / unclaimed 409 (HTTP), dropped (MQTT); farm/zone captured per reading | Fixture tests | — | `main.js` |
| Latest snapshot | derived | latest value per metric | Real-Postgres | — | `read-api.js` |
| Aggregation 5m/1h/1d | on-demand avg | + min/max for every metric | existing + suite | — | `read-api.js` |
| Alert rules (UC85) | MISSING | farm/zone/device rules, precedence, enable/notify, hysteresis, soil moisture | Unit + real-Postgres | — | `deviceLifecycle.js`, `alert-engine.js` |
| Alert events (UC83/UC84) | PARTIAL (ACK bug) | ACK episode fix, severity/rule id, hysteresis, rule-disabled close, Vietnamese messages | Unit | — | `alert-engine.js` |
| Notifications (UC84) | IN_APP only for some types | soil-moisture alert types accepted by notification-service | Unit (listener) | PUSH/EMAIL for alerts not integrated (no FCM provider; SMTP used for OTP) | `NotificationEventListener.java` |
| Camera / media (UC86/UC89) | PARTIAL | test plan written | — | hardware phase pending | `docs/CAMERA_HARDWARE_TEST_PLAN.md` |
| Plan / PlantEvent / ApplyPlan, Conversation / Message, Post / Comment / Vote | exist in teammate modules | mapping only | — | out of scope (owned by teammates) | §4 |

Test runs: iot-service `npm test` 137 pass / 0 fail / 7 skipped (DB-gated), `device-lifecycle-pg` 1/1 on local
Postgres (rolled back); notification-service 32 pass / 2 skipped; Web Jest 154/154, lint 0 errors, typecheck, build;
Mobile `node --test` 14/14, `tsc`, Expo Android export; sensor sketch compiled (73% flash).

## 6. Report B vs. code — suggested corrections (for the authors to review; the Word file was not edited)

| Report B says | Code / hardware today | Suggestion |
|---|---|---|
| Sensor sends every 3 s over MQTT to EMQX, topic `duydoan/sau-rieng/telemetry` | Sensor `esp32-01` sends every 10 s (configurable 5–3600 s) by HTTP `POST /api/v1/sensors/data`, server found by UDP discovery; MQTT path exists for `duriancare/devices/{uid}/telemetry` | Describe the HTTP + discovery path, keep MQTT as supported alternative |
| Remote config via MQTT `duriancare/devices/{uid}/config/set` + ACK | Config delivered in the HTTP ingestion response, ACK = `config_version` in the next reading | Update §1.7 / §4.4 |
| Snapshot in MongoDB, raw in PostgreSQL | Both raw and latest come from PostgreSQL `telemetry` (indexed latest per metric) | Update architecture text |
| Scheduler builds `agg_5m/1h/1d` | On-demand bucketed aggregation (avg/min/max/count) | Update §4.4 |
| DHT22 on GPIO23, soil `map(raw, 0, 4095, 0, 100)`, rain sensor for fertilizing | Sketch uses GPIO18 for DHT22, soil `map(raw, 3200, 1200, 0, 100)`; no rain sensor / watering detection in firmware | Align hardware table and edge-algorithm claims |
| FCM push + Brevo email for alerts, Transactional Outbox | IN_APP notifications via Kafka → notification-service; no FCM provider, SMTP only for OTP; no outbox | Mark push/email/outbox as future work |
| Web = React + Vite; IoT/Chat = Node.js/NestJS | Web = Next.js; iot-service = Node.js/Express | Fix technology lists |
| Offline INT8 MobileNetV2 on the phone | Mobile diagnosis calls the backend `/api/v1/predict` | Verify before claiming offline inference |

## 7. Hardware (2026-10-28)

Details, BOM and open questions: `hardware/HARDWARE.md`. Schematic: `hardware/durian_iot_schematic.drawio`
(editable, diagrams.net), `.png` (300 dpi) and `.svg`, all generated by `hardware/generate_schematic.py`.

| Device | Verified | Unverified / to check in the hardware phase |
|---|---|---|
| esp32-01 (ESP32-D0WD-V3) | DHT22 on GPIO18 (firmware + real readings); soil AO on GPIO34 (firmware); HTTP + UDP discovery | board variant, power path, DHT pull-up, soil-module supply (must be 3V3); soil raw ≈ 0 points to a wiring/power fault |
| ESP32-CAM-001 (AI-Thinker, OV2640, PSRAM) | fixed camera pin map used by firmware; images received by backend | power supply, programmer type; full test plan in `CAMERA_HARDWARE_TEST_PLAN.md` |
| Light sensor, rain sensor, ADS1115 | — | not present in firmware; report B / reference project only |

No firmware was flashed and no wiring was changed in this phase.
