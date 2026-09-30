# Camera Dynamic IP — Audit Report

**Date:** 2026-09-29  
**Branch:** duy/cleanup-fixes  
**Scope:** duriancare-iot-service (Node.js) + gateway (Spring) + Web + Mobile

---

## 1. Current Architecture

```
ESP32-CAM
  → (HTTP GET /capture or /stream)
  ← IP resolved from ESP32_CAMERA_URL env variable

IoT Service (Node.js, port 3001)
  ├── src/config.js          — reads ESP32_CAMERA_URL from .env on every call
  ├── src/camera/
  │   ├── cameraRoutes.js    — /api/v1/camera/* endpoints; calls getCameraConfig()
  │   ├── cameraService.js   — downloadImageBuffer + AI diagnosis + DB writes
  │   └── cameraScheduler.js — cron jobs; calls getCameraConfig() on each tick
  └── src/public/
      └── publicRoutes.js    — /api/v1/public/* (traceability only, no camera)

Gateway (Spring Cloud Gateway, port 8080)
  └── Routes /api/v1/camera/**, /api/v1/public/** → IoT service

PostgreSQL (duriancare_iot schema)
  ├── camera_captures  — stores each captured frame + AI result
  └── camera_schedules — cron-based auto-capture config per device

Web Client (Next.js)
  └── src/lib/camera/client.ts — calls /api/v1/camera/* via gateway; passes device_id

Mobile App (Expo)
  └── src/lib/iotApi.ts — calls /api/v1/camera/* via gateway; passes device_id
```

---

## 2. Current Camera Flow

```
[Web/Mobile: captureNow("esp32-cam-01")]
    → POST /api/v1/camera/capture-now?device_id=esp32-cam-01
    → IoT service receives request
    → getCameraConfig()  ← reads ESP32_CAMERA_URL from .env file
    → GET http://192.168.1.100/capture  (hardcoded in .env)
    → Buffer downloaded
    → Saved to ./uploads/cameras/cam_<ts>.jpg
    → AI service called with image
    → Result stored in camera_captures table
    → Response returned to client
```

---

## 3. Current IP Storage

| Location | Variable | Value | Problem |
|----------|----------|-------|---------|
| `infrastructure/.env` | `ESP32_CAMERA_URL` | NOT SET (uses default) | No persistent IP at all |
| `src/config.js:8` | default | `"http://192.168.1.100"` | Hardcoded fallback |
| `src/config.js:32` | `esp32CameraUrl` | from env | Same |
| `src/config.js:5-11` | `getCameraConfig()` | re-reads .env on every call | Workaround, requires file edit |

The `infrastructure/.env` does **not** contain `ESP32_CAMERA_URL` — the hardcoded default `http://192.168.1.100` is the only camera config.

---

## 4. Current API Endpoints

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| POST | `/api/v1/camera/capture-now` | JWT | Trigger manual capture + AI |
| GET | `/api/v1/camera/snapshot` | JWT | Live frame proxy |
| GET | `/api/v1/camera/image/:filename` | None | Serve stored image file |
| GET | `/api/v1/camera/history` | JWT | Paginated capture history |
| GET | `/api/v1/camera/schedule` | JWT | Get cron schedules |
| PUT | `/api/v1/camera/schedule` | JWT | Replace cron schedules |

**Missing:** No registration endpoint. No heartbeat. No device listing. No tree assignment.

---

## 5. Current Frontend/Mobile Dependency

### Web — `src/components/camera/CameraSection.tsx`
```typescript
const CAMERA_DEVICES = [
  { id: "esp32-cam-01", label: "ESP32-CAM 01 (Vườn chính)" },
  { id: "esp32-cam-02", label: "ESP32-CAM 02 (Vườn phụ)" },
];
```
→ Hardcodes device IDs in UI for display, but passes them as `device_id` params to the API. **Does NOT construct ESP32 URLs directly.** Backend resolves URLs.

### Mobile — `src/features/camera/CameraMonitorScreen.tsx`
```typescript
const DEVICE_ID = "esp32-cam-01";
```
→ Single device ID hardcoded in screen constant. **Does NOT construct ESP32 URLs directly.**

### Key finding:
Frontend never touches the ESP32 IP. All URL resolution is in the IoT service backend. This is already the correct separation — only the backend needs to change.

---

## 6. Exact Files to Modify

### Backend (IoT Service — Node.js):

| File | Action | Reason |
|------|--------|--------|
| `db/migration/V9__add_camera_devices.sql` | CREATE NEW | New camera_devices table |
| `src/camera/deviceService.js` | CREATE NEW | CRUD for camera_devices |
| `src/public/publicRoutes.js` | MODIFY | Add /cameras/register + /cameras/heartbeat |
| `src/camera/cameraRoutes.js` | MODIFY | Resolve URL from DB; return 503 if offline |
| `src/camera/cameraScheduler.js` | MODIFY | Resolve URL from DB per device |
| `src/main.js` | MODIFY | Mount device routes; add offline detection cron |
| `src/config.js` | MODIFY | Add CAMERA_REGISTRATION_KEY, CAMERA_OFFLINE_TIMEOUT_MIN |
| `tests/camera-registration.test.js` | CREATE NEW | Integration test simulation (Phase 12) |

### Backend (Gateway — Spring Java): **NO CHANGES**
- `/api/v1/public/**` already in `PUBLIC_PATHS` (no JWT for registration/heartbeat)
- `/api/v1/camera/**` already routed to IoT service

### Frontend (Web):

| File | Action | Reason |
|------|--------|--------|
| `src/lib/camera/client.ts` | MODIFY | Add listDevices(), getDevice(), assignTree() |
| `src/lib/camera/types.ts` | MODIFY | Add CameraDevice type |
| `src/components/camera/CameraSection.tsx` | MODIFY | Show online/offline status from backend |

### Frontend (Mobile):

| File | Action | Reason |
|------|--------|--------|
| `src/lib/iotApi.ts` | MODIFY | Add listCameraDevices(), getCameraDevice() |
| `src/features/camera/CameraMonitorScreen.tsx` | MODIFY | Show online/offline status; fetch device from backend |

---

## 7. Database Schema — Current

```sql
-- camera_captures (V3 + V4 + V7)
id UUID PK, device_id VARCHAR, image_url, capture_type,
captured_at TIMESTAMPTZ, ai_status, disease_detected,
confidence_score, severity, diagnosis_result JSONB, notes

-- camera_schedules (V3)
id UUID PK, device_id VARCHAR, cron_expression, label, enabled,
created_at, updated_at
```

**Missing:** No `camera_devices` table. IP is stored nowhere in the database.

---

## 8. Backward Compatibility Concerns

1. **Existing captures**: All have `device_id = "esp32-cam-01"` (or similar string). After migration, this device_id will be a foreign reference to `camera_devices.device_id`. No cascade needed — captures stay intact.
2. **Fallback to env**: If no `camera_devices` record exists for a given `device_id`, the service must fall back to `getCameraConfig()` for backward compatibility (existing env-based deployments continue to work).
3. **Web CAMERA_DEVICES list**: Still functional — will be replaced with API call to list registered devices.
4. **`capture-now` and `snapshot`**: Will now return 503 if camera is registered but `online=false`, instead of hitting a dead IP blindly.

---

## 9. Implementation Plan (Phases 2–14)

See `artifacts/CAMERA_DYNAMIC_IP_IMPLEMENTATION.md` for full implementation details.

**Summary of changes:**
- New table: `camera_devices` (stable identity, runtime IP, online/offline, tree assignment)
- New public endpoints: `POST /api/v1/public/cameras/register`, `POST /api/v1/public/cameras/heartbeat`  
- New authenticated endpoints: `GET /api/v1/camera/devices`, `GET /api/v1/camera/devices/:id`, `PATCH /api/v1/camera/devices/:id/tree`
- Modified capture/snapshot: resolve URL from DB, return 503 if offline
- Security: `X-Camera-Key` header with `CAMERA_REGISTRATION_KEY` env var
- Offline detection: cron job marks cameras offline after configurable timeout
