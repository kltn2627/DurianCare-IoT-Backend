# IoT device lifecycle, configuration, alerts — API contract

Service: `duriancare-iot-service` (behind the Gateway, route `/api/iot/**`, `/api/v1/sensors/**`). Identity comes from
Gateway headers `X-Auth-User-Id` / `X-Auth-Role`; farm/zone permissions come from farm-service
(`/internal/v1/farm-access/check`, permissions `READ_IOT` and `CONFIGURE_DEVICE`).

## 1. Lifecycle (four separate concepts)

| Concept | Field | Values | Set by |
|---|---|---|---|
| Provisioning | `iot_devices.provisioning_status` | `PROVISIONED` → `CLAIMED` | admin provisioning / farmer claim |
| Ownership + assignment | `farm_id`, `cultivation_area_id`, `claimed_by`, `claimed_at` | farm + optional zone | claim, then `PATCH` to move/unassign the zone |
| Administrative status | `status` | `ACTIVE` / `INACTIVE` / `MAINTENANCE` / `DELETED` | farm manager |
| Connection | derived from `last_seen_at` | `ONLINE` / `STALE` / `OFFLINE` / `UNKNOWN` | ingestion; thresholds `IOT_EXPECTED_TELEMETRY_INTERVAL_SECONDS` × `IOT_STALE_AFTER_INTERVALS` / `IOT_OFFLINE_AFTER_INTERVALS` |

Also: `device_type` (`SENSOR` / `CAMERA`), `firmware_version` (reported by the device in telemetry).
Devices registered before this change keep working: they default to `provisioning_status = CLAIMED`.

### Provisioning (UC79) — administrator

`POST /api/iot/devices/provision` `{deviceUid, deviceType?, name?, isTest?}` → `201 {device, claimCode, claimCodeExpiresAt}`

- `isTest: true` marks a test/simulated device (`iot_devices.is_test`, default `false`, existing rows unchanged). Its telemetry is stored as usual (`source = DEVICE`) but excluded — together with readings of `DELETED` devices — from cross-device reads (`GET /api/v1/sensors/latest` and `/history` without `device_id`). Reads for one `device_id` are unaffected.

- ADMIN only (403 otherwise). Duplicate UID (case-insensitive) → 409. A `DELETED` UID can be provisioned again.
- Claim code: 10 Crockford-base32 characters (`ABCDE-12345`, 50 bits), single use, expires after
  `IOT_CLAIM_CODE_TTL_HOURS` (default 72). Stored as `sha256(deviceId:code)` only; shown once.
- `POST /api/iot/devices/{id}/claim-code` (ADMIN) issues a new code and revokes the pending one (409 if claimed).
- `GET /api/iot/admin/devices` (ADMIN) lists the inventory with claim-code state.
- The old direct binding `POST /api/iot/devices` is now ADMIN-only; farmers get 403 "Use the device claim code".

### Claim + assignment (UC82) — farmer

`POST /api/iot/devices/claim` `{deviceUid, claimCode, farmId, cultivationAreaId?, name?}` → `200 {device}`

| Situation | Result |
|---|---|
| Actor cannot `CONFIGURE_DEVICE` on farm/zone, or zone not in farm | 403 (checked first, no attempt consumed) |
| Unknown UID or wrong code | 400 (same message — no UID enumeration); wrong code increments `failed_attempts` |
| 5 wrong codes | 429 until an admin issues a new code |
| Correct but expired code | 410 |
| Code already used for this device | 409 |
| Success | device `CLAIMED`, `ACTIVE`, farm/zone set, claim row `CLAIMED` (one transaction, row locks) |

Unclaimed devices are invisible in farmer lists and detail endpoints (404).

## 2. Telemetry ingestion

`POST /api/v1/sensors/data` `{device_id, temperature?, humidity?, soil_moisture?, firmware_version?, config_version?}`

- Unknown UID → 404 `DEVICE_NOT_REGISTERED`; provisioned but unclaimed → 409 `DEVICE_NOT_CLAIMED`. Nothing stored.
- Stored row carries `farm_id` / `cultivation_area_id` captured at ingestion (moving a device later does not
  re-attribute old readings). Provenance column `source` (`DEVICE` / `SYNTHETIC_BACKFILL`).
- MQTT (`duriancare/devices/{uid}/telemetry`) applies the same registration/claim gate (dropped and logged otherwise).
- Latest values: `GET /api/iot/devices/{id}` returns `latestByMetric` (most recent non-null value per metric with its
  timestamp — readings may carry only some metrics) and `openAlerts`. No separate snapshot table: the
  `(device_id, timestamp DESC)` index serves it from the single source of truth.
- Aggregation: `GET /api/iot/devices/{id}/telemetry/buckets?from&to&maxBuckets` computes avg/min/max/count per bucket
  on demand (`date_bin`, bucket ladder 1 min … 5 min … 1 h … 1 day, Asia/Ho_Chi_Minh day boundaries); measured and
  synthetic data are never mixed. No materialized agg tables (nothing to keep consistent or re-run).

## 3. Device configuration (UC81) — honest delivery states

`GET|PUT /api/iot/devices/{id}/config` `{publishIntervalSeconds (5–3600), alertsEnabled}`

| deliveryStatus | Meaning |
|---|---|
| `NOT_CONFIGURED` | no config saved |
| `PENDING` | saved, not yet sent |
| `DELIVERED` | returned to the device in an ingestion response (`config: {version, publish_interval_seconds}`) |
| `APPLIED` | the device reported `config_version` ≥ this version in a later reading |

Transport: the sensor uses HTTP, so configuration rides on the `POST /api/v1/sensors/data` response (no MQTT
subscription exists on the sensor firmware). Only `publishIntervalSeconds` is applied by the device; `alertsEnabled`
is server-side (applied immediately, no version bump). Sensor sketch `1.3.0` implements apply + echo (compiled, not
flashed in this phase). CAMERA devices report `deviceSupportsRemoteConfig: false`.

## 4. Alert rules and events (UC83–UC85)

Rules: `GET /api/iot/alert-rules?farmId&cultivationAreaId` (read access) · `POST` · `PATCH /{id}` · `DELETE /{id}`
(manage access). Body: `metric` (`TEMPERATURE|HUMIDITY|SOIL_MOISTURE|LIGHT`), `minValue`, `maxValue`, `hysteresis`,
`severity` (`INFO|WARNING|CRITICAL`), `enabled`, `notify`, scope `farmId` + optional `cultivationAreaId` / `deviceId`.

- Precedence per metric: device > zone > farm > service default (`IOT_TEMPERATURE_*`, `IOT_HUMIDITY_*`, `IOT_LIGHT_*`;
  no default for soil moisture). A disabled specific rule switches that metric off (open alerts closed silently).
- HTTP readings are evaluated when the farm has its own rules for the device or `IOT_HTTP_TELEMETRY_ALERTS_ENABLED=true`;
  MQTT readings are always evaluated. A device config with `alertsEnabled=false` opens no threshold alerts.
- Events (`iot_alerts`): `ALERTING` (≈OPEN) → `ACKNOWLEDGED` (≈ACKED, `POST /api/iot/alerts/{id}/acknowledge`) →
  `RECOVERED` (≈RESOLVED). One open episode per device+type (unique index over ALERTING|ACKNOWLEDGED); an acknowledged
  alert is still touched and still recovers. Recovery needs the value back inside the threshold by `hysteresis`.
  Severity and rule id are stored on the event. Offline alerts come from the watchdog (`DEVICE_OFFLINE`).

## 5. Notifications (UC84)

New/recovered alerts publish to the notification events topic — `KAFKA_ALERT_TOPIC` (docker compose: currently `iot.alert.email`; code default `duriancare.notification.events`), the same expression notification-service consumes — (one event per recipient: farm owner and
authorized agronomists with `READ_IOT`). notification-service stores them as IN_APP notifications (Web + Mobile
inbox). PUSH (no FCM provider configured) and EMAIL for alerts (SMTP is used for OTP only) are **not** integrated.

## 6. Local run & verification

```bash
cd duriancare-iot-service
npm test                      # unit + fixture tests
npm run check                 # syntax check
# real-Postgres tests (outer transaction always rolled back):
IOT_FIXTURE_PG_URI=postgres://USER:PASS@127.0.0.1:5432/duriancare node --test test/device-lifecycle-pg.test.js
```

Schema changes are additive and applied idempotently at service start (`ensureDeviceLifecycleSchema`): new columns
on `iot_devices`, `telemetry`, `iot_alerts`; new tables `iot_device_claims`, `iot_device_configs`, `iot_alert_rules`;
`iot_devices.farm_id` becomes nullable (only for `PROVISIONED`, enforced by a CHECK). No data is deleted or rewritten.
Deploying requires rebuilding/recreating the `duriancare-iot-service` container (needs the owner's approval).

## 7. Web / Mobile

| Screen | Web | Mobile |
|---|---|---|
| Admin inventory + claim codes | `/dashboard/admin/devices` | — (admin is Web-only) |
| Claim with code (farm + zone) | Sensors page → "Thêm thiết bị" | Climate monitor → "Thêm & gắn thiết bị" |
| Device status, firmware, latest per metric | `IotDevicePanel` | `IotDeviceSettingsCard` + device detail |
| Open alerts + "Đã xem" | `IotDevicePanel` | alert list card |
| Configuration + delivery state | `IotDevicePanel` | `IotDeviceSettingsCard` |
| Threshold rules | `IotDevicePanel` (zone/farm scope) | `IotDeviceSettingsCard` |
