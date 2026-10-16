# DurianCare IoT Read API

Status: `IMPLEMENTED_E2E_PENDING_RUNTIME`.

## Ingestion Path

```text
ESP32 / device
  -> MQTT topic duriancare/devices/{deviceUid}/telemetry
  -> duriancare-iot-service
  -> PostgreSQL duriancare_iot.telemetry
  -> Kafka topic iot.telemetry.received
```

## MQTT Contract

| Topic | Direction | Payload | Consumer |
|---|---|---|---|
| `duriancare/devices/+/telemetry` | Device -> Backend | JSON with optional `temperature`, `humidity`, `light`, optional ISO `timestamp` | `duriancare-iot-service/src/main.js` |

No status, image, ack, or config topics were found in the current IoT service.

## Storage

Raw telemetry is stored in PostgreSQL:

- schema: `duriancare_iot`
- table: `telemetry`
- fields: `id`, `device_id`, `temperature`, `humidity`, `light`, `timestamp`, `received_at`

Device-to-farm scope is stored in:

- table: `iot_devices`
- fields: `id`, `device_uid`, `name`, `farm_id`, `cultivation_area_id`, `status`, `last_seen_at`, `created_at`, `updated_at`

`device_uid` maps to `telemetry.device_id`.

## Read API

All endpoints are behind gateway `/api/iot/**` and require the gateway-authenticated identity headers.

| Function | Endpoint |
|---|---|
| List accessible devices with latest telemetry | `GET /api/iot/devices` |
| Get accessible device detail | `GET /api/iot/devices/{idOrUid}` |
| Get latest telemetry server-side | `GET /api/iot/devices/{idOrUid}/telemetry/latest` |
| Get raw history | `GET /api/iot/devices/{idOrUid}/telemetry?from=&to=&limit=` |
| List accessible IoT alerts | `GET /api/iot/alerts?status=ALERTING` |
| Get accessible alert detail | `GET /api/iot/alerts/{id}` |
| Acknowledge accessible alert | `POST /api/iot/alerts/{id}/acknowledge` |

History defaults to the last 24 hours, raw resolution, and max `limit=1000`.

## Device Management API

The current phase adds device registry management, not physical secure claiming. Knowing a `deviceUid` is not proof that the user owns the hardware.

| Function | Endpoint | Required permission |
|---|---|---|
| Register/provision a device UID into a farm | `POST /api/iot/devices` | `CONFIGURE_DEVICE` on target farm/area |
| Rename, move area/farm, or change administrative status | `PATCH /api/iot/devices/{idOrUid}` | `CONFIGURE_DEVICE` on current scope and target scope when reassigned |
| Soft-delete a registry row | `DELETE /api/iot/devices/{idOrUid}` | `CONFIGURE_DEVICE` on current scope |

`POST /api/iot/devices` body:

```json
{
  "deviceUid": "esp32:north-01",
  "name": "North Block ESP32",
  "farmId": "farm-id",
  "cultivationAreaId": "area-id-or-null",
  "status": "ACTIVE"
}
```

`PATCH /api/iot/devices/{idOrUid}` accepts any of: `name`, `farmId`, `cultivationAreaId`, `status`.

`deviceUid` is case-sensitive, trimmed, unique, 3-150 characters, and currently allows letters, numbers, `.`, `_`, `:`, and `-`. Duplicate UIDs return `409`.

## Authorization

The IoT service does not authorize from raw `deviceUid`. It resolves the device registry row, then calls farm-service:

`POST /internal/v1/farm-access/check` with permission `READ_IOT`.

This reuses the existing owner/FarmAuthorization/allowedCultivationAreaIds source of truth. If farm-service is unavailable, IoT read access fails closed.

Management mutations call the same farm-service check with permission `CONFIGURE_DEVICE`. `READ_IOT` alone is intentionally insufficient for registration, reassignment, status changes, or soft delete.

## Lifecycle

1. MQTT telemetry may be observed first as raw `telemetry.device_id`.
2. A user with `CONFIGURE_DEVICE` registers/provisions `deviceUid` into `iot_devices`.
3. The registry row is assigned to exactly one farm and optionally one cultivation area.
4. Registry status can be `ACTIVE`, `INACTIVE`, or `MAINTENANCE`.
5. Removal is soft delete by setting `status = 'DELETED'`; telemetry is preserved.

## Status Semantics

`status` is registry status only: `ACTIVE`, `INACTIVE`, `MAINTENANCE`, or `DELETED`.

The read DTO also returns `administrativeStatus` and `connectivityStatus`. `ACTIVE` never means online.

Connectivity is inferred server-side from `last_seen_at` because no MQTT LWT/status topic or retained device status signal exists in the current service. The response includes `connectivity.basis = INFERRED_FROM_LAST_TELEMETRY`.

Config:

- `IOT_EXPECTED_TELEMETRY_INTERVAL_SECONDS` default `60`
- `IOT_STALE_AFTER_INTERVALS` default `3`
- `IOT_OFFLINE_AFTER_INTERVALS` default `10`
- `IOT_OFFLINE_WATCHDOG_INTERVAL_SECONDS` default `60`

These values describe the configured expected telemetry cadence, not verified firmware cadence. Firmware cadence was not found in the workspace. If firmware cadence changes, update config rather than client logic.

Health config is validated at IoT service startup. Values must be positive numbers and the stale interval count must be lower than the offline interval count.

Connectivity states:

- `UNKNOWN`: registered but never reported, or administratively `INACTIVE`/`MAINTENANCE`.
- `ONLINE`: latest telemetry age is within the stale threshold.
- `STALE`: latest telemetry age is older than stale threshold.
- `OFFLINE`: latest telemetry age is older than offline threshold.

Telemetry state is derived as:

- `HAS_TELEMETRY`: at least one reading exists.
- `NEVER_REPORTED`: no reading exists.

`INACTIVE` and `MAINTENANCE` suppress offline alert generation to avoid nuisance alerts for intentionally disabled devices.

An `ACTIVE` device with `last_seen_at = NULL` remains `UNKNOWN` until its first valid telemetry event. It does not become `OFFLINE` by age because there is no verified first-seen telemetry baseline yet.

## Alert Pipeline

Threshold alerts are evaluated after telemetry is persisted and after `last_seen_at` updates a registered active device. Unknown devices keep raw telemetry but do not create alerts because no farm/user mapping exists.

Supported threshold fields match the current telemetry contract only:

- `temperature`
- `humidity`
- `light`

Threshold defaults are centralized in config:

- `IOT_TEMPERATURE_HIGH`, `IOT_TEMPERATURE_LOW`
- `IOT_HUMIDITY_HIGH`, `IOT_HUMIDITY_LOW`
- `IOT_LIGHT_HIGH`, `IOT_LIGHT_LOW`

Alert lifecycle:

- `ALERTING`: first crossing opens one alert per device/type.
- Continued violation updates the same active alert and does not create notification storms.
- Return to normal marks the alert `RECOVERED`.
- User acknowledgement marks an accessible `ALERTING` alert `ACKNOWLEDGED`.

Offline alerts are evaluated by internal watchdog:

`POST /internal/iot/offline-watchdog/run`

The IoT service starts an in-process watchdog scheduler at runtime. The scheduler interval is externalized by `IOT_OFFLINE_WATCHDOG_INTERVAL_SECONDS`, defaulting to the configured expected telemetry interval. This project did not have an existing IoT scheduler framework; adding a local interval avoids introducing a second runtime service for a single closing watchdog task.

The internal endpoint remains available for operational/manual trigger and requires `X-Internal-Token`. It is not part of the public `/api/iot/**` gateway surface.

The watchdog reads only registry state from `iot_devices.last_seen_at`; it does not scan telemetry history. It evaluates registered rows whose status is `ACTIVE`, `INACTIVE`, or `MAINTENANCE`, skips never-reported active devices, creates one `DEVICE_OFFLINE` alert per active offline device, and recovers active offline alerts when telemetry resumes or when a device is moved to `INACTIVE`/`MAINTENANCE`.

Multi-instance behavior is guarded with PostgreSQL advisory lock around each watchdog run. If another replica already holds the lock, the run is skipped. Alert deduplication remains a secondary protection through the partial unique index on active `(device_uid, type)`.

Kafka and recipient lookup are not part of the alert database transaction. If recipient lookup or Kafka notification publishing fails, the persisted alert remains the source of truth, the failure is logged, and no fallback recipient is invented. Notification retry remains a future reliability concern.

Notification flow:

```text
IoT telemetry/offline evaluator
  -> PostgreSQL iot_alerts
  -> Kafka duriancare.notification.events
  -> duriancare-notification-service inbox
```

Recipients are resolved at dispatch time through farm-service internal API. The current rule is farm owner plus active authorized agronomists with `READ_IOT` and matching area scope. Revoked/expired authorization is not reused.

## Historical Telemetry Semantics

Telemetry rows store only `device_id`/`deviceUid`, measured values, and timestamps. They do not store historical farm or area assignment. Reassigning a device changes how future reads resolve the device registry, but it does not rewrite or fork old telemetry rows. A future audit feature should add assignment history if historical farm/area attribution becomes required.

## Schema Decision

The canonical schema change is `duriancare-iot-service/db/migration/V2__add_iot_device_registry.sql`.

`ensureReadSchema` in the IoT service mirrors the same table/index shape as a startup guard because this service can run standalone in local Docker. It is not a replacement for migration-managed environments.

## Known Gaps

- No QR code, pairing secret, firmware proof-of-possession, or hardware secure claim flow exists yet.
- No MQTT LWT/status topic exists; connectivity is inferred, not guaranteed network presence.
- No soil moisture, NPK, battery, firmware, or device health fields exist in the current MQTT/storage contract.
- No aggregate tables for 5m/1h/1d were found.
- Thresholds are system config defaults, not per-farm/per-device settings yet.
- Runtime PostgreSQL E2E depends on a reachable local PostgreSQL instance.
