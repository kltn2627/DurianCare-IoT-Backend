# Camera Dynamic IP — Implementation Report

**Date:** 2026-09-29  
**Branch:** duy/cleanup-fixes  
**Author:** Minhduy407

---

## Problem Statement

The IoT service previously resolved the ESP32-CAM URL by reading `ESP32_CAMERA_URL` from `.env` on every request (`getCameraConfig()`).  
This required a manual file edit every time the camera changed its DHCP-assigned IP address — a runtime problem with a configuration-file workaround.

**Goal:** Cameras self-announce their current IP on boot and heartbeat.  
The backend stores the runtime state in PostgreSQL and all URL resolution happens at runtime — no `.env` edits, no service restart.

---

## Architecture Change

### Before

```
ESP32-CAM changes IP
  → Admin manually edits ESP32_CAMERA_URL in .env
  → getCameraConfig() re-reads .env on next request
  → IoT service uses the (hopefully correct) hardcoded value
```

### After

```
ESP32-CAM boots / Wi-Fi reconnects
  → POST /api/v1/public/cameras/register  (X-Camera-Key: <secret>)
  → camera_devices table upserted: device_id=stable, ip_address=current
  
ESP32-CAM sends heartbeat every 60s
  → POST /api/v1/public/cameras/heartbeat
  → last_seen refreshed, ip_address kept current, online=true

IoT service handles capture/snapshot request
  → resolveCameraUrl(pool, schema, deviceId)
  → reads ip_address from camera_devices row
  → if camera.online=false → returns HTTP 503 immediately
  → if no row exists → falls back to getCameraConfig() (backward compat)

Background cron every 5 min
  → markOfflineStaleCameras()
  → camera_devices.online=false where last_seen < NOW() - timeout
```

---

## Files Changed

### Backend — `duriancare-iot-service`

| File | Action | Description |
|------|--------|-------------|
| `db/migration/V9__add_camera_devices.sql` | NEW | `camera_devices` table with stable `device_id` identity, runtime `ip_address`, `online` flag, tree/zone/farm assignment |
| `src/camera/deviceService.js` | NEW | `upsertCamera`, `heartbeat`, `getCamera`, `listCameras`, `assignTree`, `markOfflineStaleCameras`, `resolveCameraUrl` |
| `src/config.js` | MODIFIED | Added `cameraRegistrationKey` and `cameraOfflineTimeoutMinutes` config fields |
| `src/public/publicRoutes.js` | MODIFIED | Added `POST /cameras/register` and `POST /cameras/heartbeat` with `X-Camera-Key` auth |
| `src/camera/cameraRoutes.js` | MODIFIED | `capture-now` and `snapshot` now call `resolveCameraUrl()`, return 503 if camera offline. Added `GET /devices`, `GET /devices/:id`, `PATCH /devices/:id/tree`. Per-device snapshot cache (Map). |
| `src/camera/cameraScheduler.js` | MODIFIED | `scheduleOne` calls `resolveCameraUrl()` per tick; skips capture if camera offline |
| `src/main.js` | MODIFIED | Added `node-cron` import, `markOfflineStaleCameras` import, offline detection cron every 5 min |
| `tests/camera-registration.test.js` | NEW | 8 test cases — see test report |

### Frontend — Web (`DurianCare-IoT-Web-Client`)

| File | Action | Description |
|------|--------|-------------|
| `src/lib/camera/types.ts` | MODIFIED | Added `CameraDevice`, `DeviceListResponse`, `DeviceResponse`, `AssignTreeInput` interfaces |
| `src/lib/camera/client.ts` | MODIFIED | Added `listDevices()`, `getDevice()`, `assignTree()` methods |
| `src/components/camera/CameraSection.tsx` | MODIFIED | Fetches device list from backend; shows online/offline badge in selector and warning banner |

### Frontend — Mobile (`DurianCare-IoT-Mobile-App`)

| File | Action | Description |
|------|--------|-------------|
| `src/lib/iotApi.ts` | MODIFIED | Added `CameraDevice` type, `listCameraDevices()`, `getCameraDevice()` |
| `src/features/camera/CameraMonitorScreen.tsx` | MODIFIED | Fetches device status; shows Online/Offline pill badge |

---

## New Endpoints

### Public (no JWT required — `X-Camera-Key` instead)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/public/cameras/register` | ESP32-CAM self-registration. Body: `{cameraId, ipAddress?, port?, protocol?, macAddress?, firmwareVersion?, ssid?, treeId?, zoneId?, farmId?, capabilities?}`. `ipAddress` defaults to `X-Real-IP` / `X-Forwarded-For` / `req.socket.remoteAddress` if omitted. |
| `POST` | `/api/v1/public/cameras/heartbeat` | Heartbeat. Body: `{cameraId, ipAddress?, port?}`. Auto-registers if camera not found. |

### Authenticated (JWT required)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/camera/devices` | List all registered cameras, online first |
| `GET` | `/api/v1/camera/devices/:deviceId` | Single camera details |
| `PATCH` | `/api/v1/camera/devices/:deviceId/tree` | Assign `{treeId, zoneId, farmId}` to camera |

---

## Security

- ESP32-CAM must include `X-Camera-Key: <value>` header
- Default dev key: `duriancare-esp32-dev-key`
- Production: set `CAMERA_REGISTRATION_KEY` in `.env`
- Gateway's `/api/v1/public/**` is already in `PUBLIC_PATHS` — no JWT required for registration/heartbeat

---

## Backward Compatibility

- If no `camera_devices` row exists for a `device_id`, `resolveCameraUrl()` returns `{ url: fallbackUrl, online: false, found: false }` and the caller uses `getCameraConfig()` (env-based fallback)
- Existing `camera_captures` and `camera_schedules` rows are untouched
- Capture/snapshot endpoints now return **HTTP 503** instead of a connection error when the camera is registered but `online=false`

---

## Database Schema (V9)

```sql
CREATE TABLE camera_devices (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id        VARCHAR(150) NOT NULL,   -- stable identity (never changes)
    mac_address      VARCHAR(50),             -- optional dedup key
    ip_address       VARCHAR(45)  NOT NULL,   -- dynamic, overwritten on every heartbeat
    port             INTEGER      NOT NULL DEFAULT 80,
    protocol         VARCHAR(8)   NOT NULL DEFAULT 'http',
    online           BOOLEAN      NOT NULL DEFAULT false,
    last_seen        TIMESTAMPTZ,
    firmware_version VARCHAR(100),
    ssid             VARCHAR(150),
    tree_id          VARCHAR(150),            -- assignment: which tree this camera monitors
    zone_id          VARCHAR(150),
    farm_id          VARCHAR(150),
    capabilities     JSONB,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_camera_devices_device_id UNIQUE (device_id),
    CONSTRAINT uq_camera_devices_mac       UNIQUE (mac_address)
);
```

---

## ESP32-CAM Arduino Sketch (Reference)

```cpp
// On boot / Wi-Fi reconnect:
HTTPClient http;
http.begin("http://<gateway>:8080/api/v1/public/cameras/register");
http.addHeader("Content-Type", "application/json");
http.addHeader("X-Camera-Key", "duriancare-esp32-dev-key");
String body = "{\"cameraId\":\"esp32-cam-01\",\"port\":81}";
// No need to include ipAddress — backend auto-detects from request origin
int code = http.POST(body);

// Every 60s:
http.begin("http://<gateway>:8080/api/v1/public/cameras/heartbeat");
http.addHeader("Content-Type", "application/json");
http.addHeader("X-Camera-Key", "duriancare-esp32-dev-key");
http.POST("{\"cameraId\":\"esp32-cam-01\"}");
```
