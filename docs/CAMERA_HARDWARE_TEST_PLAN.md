# ESP32-CAM hardware test plan (next phase)

Status: **not executed.** The camera firmware (`Documents/Arduino/CameraWebServer`, discovery-based, firmware
`2.1.0`) compiles for `esp32:esp32:esp32cam`, but nothing in this phase was flashed or run on the camera. Backend
camera flows (signed registry `POST /api/v1/public/cameras/register|heartbeat`, capture, schedules, `camera_captures`
with AI diagnosis columns) are covered by iot-service tests against mocks and fixtures only.

Related docs: `CAMERA_REGISTRY_FIRMWARE_INTEGRATION.md`, `IOT_SENSOR_CONNECTIVITY.md`.

## Preconditions

1. Identify the camera's serial port by MAC before any flash: `esptool --port COMx read-mac` must print the camera
   MAC recorded in the device registry (`ESP32-CAM-001`). **COM3 is the sensor board `esp32-01` — never flash camera
   firmware there.**
2. `camera_secrets.h` filled locally (Wi-Fi + per-device key). It is never committed or printed.
3. Backend binding for the camera present (`UDP_DISCOVERY_CAMERA_BINDINGS` / `CAMERA_DEVICE_BINDINGS` per the
   integration doc) and the camera row exists in `camera_devices` for the right farm.
4. Host discovery beacon running on the laptop (`npm run discovery:beacon` in `duriancare-iot-service`).
5. Owner's approval for flashing and for any container rebuild.

## Test cases

| # | Scenario | Steps | Expected | Evidence to keep |
|---|---|---|---|---|
| C1 | Boot & discovery | Flash, open serial 115200, reset | Wi-Fi IP/gateway logged; `SERVER_DISCOVERED` with the laptop IP | Serial log |
| C2 | Registry register | Wait after discovery | Backend accepts register (200/201); `camera_devices.ip_address` = camera IP, `last_seen` updated | Serial + SQL row |
| C3 | Heartbeat | Keep running 5 min | Heartbeats accepted; camera stays online in Web camera section | SQL `last_seen` |
| C4 | Snapshot via backend | Web/Mobile camera screen → snapshot | Image returned through `/api/v1/camera/snapshot`; no direct LAN URL exposed to the browser | Screenshot |
| C5 | Capture now | Web/Mobile "chụp ngay" | Row in `camera_captures` with storage key/URL, device id, timestamp | SQL row |
| C6 | Scheduled capture (UC89) | Set a schedule 2 min ahead | Capture executes on time (Asia/Ho_Chi_Minh), one row per run | SQL rows |
| C7 | AI on captured image | Use a capture with a visible leaf | `ai_status` reaches COMPLETED or a clear failure state; **no result is fabricated when AI fails** | SQL row |
| C8 | Server IP change | Move laptop to another network/hotspot | Camera rediscovers within seconds; re-registers with new origin | Serial log |
| C9 | Offline detection | Power the camera off 6+ min | Camera marked offline after `CAMERA_OFFLINE_TIMEOUT_MIN`; Web shows offline | Web screenshot |
| C10 | Bad key | Flash with a wrong key (test build only) | Registry rejects (401/403); camera does not appear online | Serial + API response |

## Out of scope for this phase

- Remote configuration of the camera over the sensor `iot_device_configs` channel: the camera keeps its own capture
  configuration (`POST /api/v1/camera/config`). `GET /api/iot/devices/{id}/config` reports
  `deviceSupportsRemoteConfig: false` for CAMERA devices.
- Video streaming quality/latency measurements.
