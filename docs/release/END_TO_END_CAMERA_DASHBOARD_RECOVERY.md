# End-to-End Camera / Dashboard Recovery

Date: 2026-10-18, ~13:40-14:15Z (20:40-21:15 Asia/Ho_Chi_Minh). Environment: local Docker Compose stack
(`-p duriancare`) on the Windows host 192.168.1.8; Web `next dev` on :3000; real Farmer browser session
(user "Huynh Le Minh Duy") in the Claude desktop browser pane. Earlier history: `CAMERA_REGISTRY_401_2026_10_17.md`.
No secrets are included in this report.

## 1. Current deployment (verified read-only)

- `duriancare-iot-service` runs image `89a32c171dbc` (started 13:35:31Z, 0 restarts); `src/telemetryRegistry.js`
  present; `recordDeviceSeen` referenced 3x in `main.js`. Ports `0.0.0.0:3001` and `[::]:3001`; volume
  `duriancare_iot_uploads -> /app/uploads`; `[kafka] Producer connected`; health UP.
- Rollback tags present: `rollback-20261018b` = `c77bf888207c`, `rollback-20261018` = `dd9e05cda889`,
  `rollback-20261017` = `5fee64a9b759`. The other 17 containers kept their ~5 h uptime.
- Freshness (read-only SQL): esp32-01 `last_seen_at` age 6 s; ESP32-CAM-001 `last_seen` age 17-40 s, online=true.
- Unauthenticated `GET /api/iot/devices`: 401 via Gateway :8080 and direct :3001.
- The source changes in section 5 are NOT deployed (approval required, see section 9).

## 2. Dashboard (Web repo `DurianCare-IoT-Web-Client`; `DurianCare-IoT-Web` does not exist)

Root causes (source + live API):
- Date `06/06/2026` and greeting "Chào buổi sáng" were string literals in `src/app/dashboard/client/page.tsx`.
- `UNKNOWN`: backend `connectivityForDevice` returns UNKNOWN for NULL `iot_devices.last_seen_at`; the HTTP
  telemetry route (used by esp32-01) never set it. Fixed and deployed earlier (image `89a32c171dbc`).
- `12/12`, `440`, `4 phân khu`, `79%`, `03`, "TRANG TRẠI MINH PHÁT": mock `dashboardStats.owner`
  (`src/constants/durianMockData.js`) and a literal farm name.

### Mock-data inventory (live values in the Farmer session, 2026-10-18 ~13:55Z)

| Card | Before | Source now | Live value |
|---|---|---|---|
| Farm label | literal "TRANG TRẠI MINH PHÁT" | `GET /api/farms` name (multi-farm: "N TRANG TRẠI") | VƯỜN SẦU RIÊNG CAI LẬY |
| Date / greeting | literals | `Intl` Asia/Ho_Chi_Minh, minute tick | 18/10/2026, "Chào buổi tối" |
| Telemetry mới nhất | API (first device with telemetry) | unchanged source | 30.1 -> 30 °C (live update seen) |
| Thiết bị IoT | API count + raw status | API count + Vietnamese label of backend `connectivityStatus` | 01, Đang kết nối |
| Tổng cây quản lý | mock 440 / "4 phân khu" | sum of `treeCount` over `GET /api/farms/{id}/zones` | 126 (50+26+25+25), 4 phân khu |
| Trạm IoT hoạt động | mock 12/12 | ONLINE count / farm-scoped device total from `/api/iot/devices` | 1/1, Kết nối ổn định |
| Độ ẩm đất trung bình | mock 79% "Trong ngưỡng tối ưu" | mean latest `soilMoisture` of devices; note from existing `statusFromSoil` | 100%, "Đất đang quá ẩm · 1 cảm biến" |
| Cảnh báo cần xử lý | mock 03 | count of `GET /api/iot/alerts?status=ALERTING` | 00, "Không có cảnh báo IoT đang mở" |

Each section fails independently and shows `--` with "Không tải được ..."; no demo value is ever substituted.
The admin dashboard still uses `dashboardStats.admin` (out of scope, unchanged).

Refresh: SSR renders initial values (no hydration mismatch); the client component polls every 30 s through
the existing cookie-authenticated `/api/backend` proxy with an in-flight guard, out-of-order response guard,
pause while hidden, refresh on visible, and cleanup on unmount; the clock re-renders at each minute boundary.

Browser evidence (real Farmer session): page text and screenshot show the live values above; after a clean
reload, 0 console errors in 35 s and exactly one device poll at ~30.7 s. One earlier console TypeError
(`reading 'signedOut'`) occurred only during hot reload while props were changing and did not recur.

## 3. Camera capture, local storage and retrieval (AWS)

Architecture (verified in source): the IoT service downloads the JPEG from the camera's `/capture`
(validated: 200, image/jpeg, strict SOI/EOI, <= 4 MB), stores `uploads/cameras/cam_<digits>.jpg` on the
persistent named volume `duriancare_iot_uploads`, and inserts a `camera_captures` row. The IoT path has no
S3/AWS code, so it is already local-only.

AWS evidence (no AWS calls made): AI service `/actuator/health` reports `s3StorageEnabled: false`, no
`s3LoadError`; S3 variables are set (values not inspected); no S3/AWS lines in AI logs (24 h). Latent risk:
if `S3_ENABLED` were turned on while AWS is locked, `app/api/predict.py:210-223` raises 502 after the model
has produced a prediction, discarding it. Not changed (AI service has pre-existing uncommitted edits; runtime
unaffected while S3 is disabled). Observation: user avatars load from a public S3 bucket URL.

Defects found:
1. Web camera API broken: `next.config.ts` rewrote `/api/backend/v1/camera/*` straight to IoT :3001, bypassing
   the authenticated proxy -> no identity -> 401 for every camera call (verified live: history 401).
   Fixed: rewrite removed -> proxy -> Gateway (JWT) -> IoT farm-scoped `cameraAccess`. Live: devices 200
   (ESP32-CAM-001, farm-khoa-luan-2026, ONLINE), history 200. Test `iot-history.test.ts` asserted the bypass
   rule existed; updated to assert it does not.
2. Stored `image_url` is an absolute Gateway URL; a plain `<img>` cannot send the token -> FAILED (verified).
   Fixed in Web: `src/lib/camera/imageSrc.ts` maps it to `/api/backend/v1/camera/image/<file>`; live: the real
   12:00:01Z capture loads in the camera page gallery at 1600x1200 (91,256 B, valid SOI/EOI).
3. IoT image route skipped `cameraAccess` (public at IoT; any logged-in user via Gateway). Fixed (not deployed):
   image reads require identity + read permission on the capture's camera farm; no capture record -> 404.
4. `persistCapture` wrote the file before the DB insert (orphans on failure), non-atomically, with a
   millisecond-only name. Fixed (not deployed): temp file + rename, file removed if the insert fails, random
   digit suffix (route regex unchanged), result tagged `storage: "LOCAL"`, explicit error on storage failure.
5. Camera AI diagnosis always failed in Docker (`fetch failed`): the IoT service has no `AI_SERVICE_URL`, so it
   used `localhost:8000` inside its own container (verified ECONNREFUSED; `http://duriancare-ai-service:8000`
   answers 200 from the same container). Fixed in compose (not deployed).

Evidence classes: unit/integration tests (stub camera, temp dirs) = section 6; real persisted capture
retrieval through the browser = PASS; new physical capture after deployment = NOT TESTED.

## 4. HTTP sensor threshold alerts

Status: implemented behind `IOT_HTTP_TELEMETRY_ALERTS_ENABLED` (default `false`; not set in compose).
The HTTP route now passes an MQTT-shaped event (no fabricated light value) to the existing
`evaluateTelemetryAlerts` (dedupe via open ALERTING row, recovery, recipient lookup via farm access).
Production activation requires approval: it starts real notifications for esp32-01.

## 5. Camera heartbeat timeouts

- COM4 logs: NOT AVAILABLE (none supplied; no log file found in the workspace, Documents or Downloads).
- `-11` = `HTTPC_ERROR_READ_TIMEOUT` in ESP32 Arduino core 3.3.12 (`HTTPClient.h:57`, `HTTPClient.cpp:1363-1364`):
  connection open and request written, no response header within `_tcpTimeout` (firmware 2.0.3: 15 s).
- Previous measurements (see the 2026-10-17 report): the server commits heartbeats in milliseconds; about
  6 of 15 responses missed the camera within 15 s; RSSI -78 dBm, 5% ping loss. Cause of the downlink loss
  remains unresolved. No firmware change in this pass.

## 6. Files changed in this pass

Backend `duriancare-iot-service` (not deployed): `src/camera/registryRuntime.js` (image authorization),
`src/camera/cameraRoutes.js` (comment; `Cache-Control: private`), `src/camera/cameraService.js` (atomic local
save, orphan cleanup, unique name, LOCAL tag), `src/telemetryRegistry.js` (`httpTelemetryEvent`),
`src/config.js` (`httpTelemetryAlertsEnabled`), `src/main.js` (gated HTTP alert evaluation),
`test/camera-storage.test.js` (new), `test/alert-engine.test.js` (HTTP cases).
`infrastructure/docker-compose.yml`: IoT `AI_SERVICE_URL`.
Web (live in `next dev`): `next.config.ts`, `src/app/dashboard/client/page.tsx`,
`src/components/dashboard/OwnerDashboardLive.tsx`, `src/lib/dashboard/ownerOverview.ts` (new),
`src/lib/iot/summary.ts`, `src/lib/time/vietnamClock.ts`, `src/lib/camera/imageSrc.ts` (new),
`src/components/camera/CameraSection.tsx` (2 image `src` lines; pre-existing edits preserved),
`src/__tests__/owner-overview.test.ts` (new), `src/__tests__/dashboard-live.test.ts`, `src/__tests__/iot-history.test.ts`.
GitNexus: `cameraAccess`/`persistCapture` not in the index (uncommitted files) -> manual caller review:
`cameraAccess` only in `createCameraRouter`; `captureAndStore` in `/capture-now` and the scheduler.

## 7. Commands and results

| Command | Result |
|---|---|
| IoT `npm run check` | exit 0 |
| IoT `node --test` (no PG) | 105 tests: 101 PASS, 0 FAIL, 4 SKIP |
| IoT `node --test` with disposable isolated PG `duriancare-camera-fixture-20261018d` | 105/105 PASS; fixture 0 schemas/0 tables after; container removed |
| Web `npx jest` | 14 suites, 124/124 PASS |
| Web `tsc --noEmit` (excluding stale generated `.next/dev` types) | exit 0 |
| Web `eslint` (changed files) | exit 0 |
| Web `next build` (Next 16.2.7) | exit 0 |
| Gateway / Mobile / firmware | unchanged in this pass; not re-run |

## 8. Acceptance checklist

| Item | Status | Evidence |
|---|---|---|
| Deployed IoT image contains the last_seen fix | PASS | section 1 |
| esp32-01 freshness + ONLINE via backend rule | PASS | age 6 s; dashboard ONLINE |
| Camera heartbeat updating | PASS | age 17-40 s, online=true |
| Unauthenticated device API rejected | PASS | 401 on :8080 and :3001 |
| Authenticated Farmer API (devices/farms/zones/alerts) | PASS | browser-session GETs, 200 |
| Dashboard date/greeting (Asia/Ho_Chi_Minh, rollover) | PASS | live + unit tests |
| Dashboard device status and active-station count | PASS | live 1/1 from ONLINE count |
| Mock dashboard values removed (owner) | PASS | live 126 / 100% / 00 / farm name |
| Dashboard refresh without errors | PASS | poll at 30.7 s, 0 errors |
| Web camera API through the authenticated proxy | PASS | devices/history 200 live |
| Persisted real capture retrievable and rendered | PASS | 1600x1200 in camera page |
| Farm-scoped image authorization deployed | PASS | 2026-10-18 14:11Z deploy; no identity 401 (:3001 and :8080), Farmer 200, unknown file 404 |
| Atomic local storage / orphan cleanup deployed | PASS | deployed; failed capture left no file, no .tmp, no row |
| New physical capture -> local save -> retrieval | FAIL | one approved manual capture 14:13Z: 502 after 10,058 ms, camera /capture timed out |
| Camera AI diagnosis in Docker | NOT TESTED | AI_SERVICE_URL now set in the container; no capture succeeded to exercise it |
| AWS fallback | PASS (by configuration) | S3 disabled at runtime; capture path local-only |
| HTTP threshold alerts | BLOCKED | implemented + tested, flag off; activation needs approval |
| Heartbeat read-timeout root cause | BLOCKED | needs COM4 logs or approved packet capture |

## 9. Next actions (approval required)

1. Deploy IoT backend changes and compose `AI_SERVICE_URL`: tag rollback, rebuild and recreate only
   `duriancare-iot-service` (same commands as the earlier deployment; rollback to `rollback-<tag>`).
   Then verify: Farmer image 200, other-farm 403; one manual "Chụp hình ngay" from the camera page creates a
   LOCAL capture with AI status COMPLETED.
2. Decide whether to set `IOT_HTTP_TELEMETRY_ALERTS_ENABLED=true` for esp32-01 (starts notifications).
3. Paste about 10 minutes of COM4 Serial Monitor output (timestamps on): heartbeat POST, `HTTP 200 (N ms)`,
   timeout code/duration, retry, boot/Wi-Fi/time lines.

## 10. Deployment 2026-10-18 14:11Z (operator-approved)

- Rollback tag `duriancare/iot-service:rollback-20261018c` = `89a32c171dbc` (previously running).
- Rebuilt only IoT -> `duriancare/iot-service:local` = `01a0e3e92bc4` (contains image authorization, LOCAL storage,
  `httpTelemetryAlertsEnabled`). Recreated only `duriancare-iot-service` at 14:11:34Z; other 17 containers unchanged;
  volume `duriancare_iot_uploads` mounted; `AI_SERVICE_URL=http://duriancare-ai-service:8000`;
  `IOT_HTTP_TELEMETRY_ALERTS_ENABLED` unset (alerts off); `[kafka] Producer connected`; health UP.
- Image access: unauthenticated `:3001/api/v1/camera/image/cam_1792324800356.jpg` 401 (was public before),
  via Gateway 401; Farmer browser session via `/api/backend` 200, image/jpeg, 91,256 B, valid SOI/EOI;
  unknown `cam_1.jpg` 404.
- One manual capture (approved) at 14:13Z from the Farmer session: HTTP 502 `CAMERA_CAPTURE_FAILED` after
  10,058 ms; IoT log `[camera] Capture failed: ESP32-CAM connection timed out` (the 10 s download timeout).
  No new file, no `.tmp`, capture rows for ESP32-CAM-001 still 1 -> failure handling verified.
  Camera at that moment: registry online (heartbeat 41 s old); ping 192.168.1.3 x10: 0% loss, 3-672 ms (avg 106).
  Interpretation: host->camera traffic was badly delayed, matching the unresolved heartbeat-response loss;
  the same path delivered the 12:00:01Z scheduled capture successfully. AI diagnosis therefore not exercised.
- Rollback: `docker image tag duriancare/iot-service:rollback-20261018c duriancare/iot-service:local` then
  `docker compose -p duriancare -f infrastructure/docker-compose.yml up -d --no-deps --no-build --pull never --force-recreate duriancare-iot-service`.

Remaining: retry one capture when the link is better (operator "Chụp hình ngay" or approval to retry),
improve camera RSSI (-78 dBm), and supply COM4 logs for the heartbeat/downlink investigation.

## 11. Capture retry 2026-10-18 ~14:20Z (operator-approved, exactly one)

- Pre-check: ping 192.168.1.3 x5 0% loss, 3-87 ms (avg 38); registry online (heartbeat 58 s old).
- `POST /api/v1/camera/capture-now?device_id=ESP32-CAM-001` from the Farmer session: 502 `CAMERA_CAPTURE_FAILED`
  after 10,359 ms; IoT log `Capture failed: ESP32-CAM connection timed out`. No file, no `.tmp`, capture rows still 1.
- Read-only probes that do not take a photo: camera `GET /status` 200 (378 B) in 198 ms from the host and 152 ms
  from inside the IoT container; TCP connect from the container 79 ms.
- Conclusion: networking and the camera web server are healthy; the `/capture` handler does not return within the
  backend's 10 s download timeout. Earlier today `/capture` answered in 0.83 s (11:56Z) and the 12:00:01Z scheduled
  capture succeeded. Most likely an on-device frame-acquisition stall (`esp_camera_fb_get`); unconfirmed without the
  camera's serial output. No code changed for this.
- Status: physical capture -> LOCAL save -> AI -> retrieval remains FAIL for 14:13Z and ~14:20Z attempts;
  AI diagnosis with the fixed AI_SERVICE_URL remains NOT TESTED.
- Next evidence: COM4 Serial Monitor (DTR/RTS off, timestamps on) while the operator presses "Chụp hình ngay"
  once; look for `cam_hal`/`Failed to get the frame on time`, `Camera capture failed`, `Discarding incomplete JPEG`,
  plus the registry heartbeat lines. A camera power cycle followed by one capture would show whether a reboot clears it.

## 12. Capture timeout root cause (2026-10-18 ~14:21Z)

Evidence:
- One timed direct `GET http://192.168.1.3/capture` (30 s limit; no app/DB write): HTTP 200, connect 7 ms,
  first byte 0.169 s, total 7.13 s, 66,509 B `image/jpeg`, valid SOI/EOI; `X-Timestamp` 4649.8 s since boot.
  => the sensor produced a frame immediately; the body arrived at ~9.3 KB/s. Earlier (11:56Z) 91 KB took 0.83 s.
- IoT `downloadImageBuffer` (`src/camera/cameraService.js`) had TWO limits with the same message: a socket idle
  timeout (10 s, correct) and a hard 10 s TOTAL deadline that destroyed transfers still receiving bytes.
  At ~9 KB/s a 66-91 KB UXGA frame needs 7-10 s, so captures at 14:13Z and ~14:20Z were cut at ~10 s.
- Other limits on the path are not binding: Gateway `response-timeout: 120s`; Web proxy has none.
- Camera `/status` 150-200 ms and the single-threaded port-80 httpd responsive => not an httpd/frame stall.

Root cause: hard 10 s total download deadline in the IoT service vs. a weak camera uplink (RSSI -78 dBm).
Not a firmware or sensor defect; no firmware change made.

Fix (IoT service, NOT yet deployed):
- `cameraService.js`: `cameraDownloadLimits()` - idle limit 10 s (no connect / no bytes) and total cap 30 s
  (env `CAMERA_DOWNLOAD_IDLE_MS`, `CAMERA_DOWNLOAD_TOTAL_MS`); distinct messages "no data for N ms" vs
  "transfer exceeded N ms total"; success log `[camera] frame received device bytes ms`.
- `cameraRoutes.js`: live snapshot proxy uses the same idle/total rule (was a fixed 12 s for both).
- Bounds: capture-now worst case 30 s download + 60 s AI < Gateway 120 s.
- Test `test/camera-slow-link.test.js`: slow progressing transfer captured and stored; the old fixed cap reproduces
  the 502 with no file/row; stalled camera fails at the idle limit and the next capture succeeds; snapshot slow link.

Results: IoT `npm run check` exit 0; `node --test` 109 tests: 105 PASS + 4 SKIP without PG;
109/109 PASS with disposable isolated PG `duriancare-camera-fixture-20261018e` (cleanup verified, removed).

Acceptance (camera):
| Criterion | Status | Evidence |
|---|---|---|
| Camera init / sensor frame acquisition | PASS | direct /capture first byte 0.169 s, valid 66.5 KB JPEG |
| /status responds | PASS | 150-200 ms |
| Root cause identified | PASS | total deadline vs 9.3 KB/s transfer |
| Fix + regression tests | PASS | 109/109 |
| Fix deployed | BLOCKED | needs approval (rebuild/recreate IoT only) |
| Real capture -> LOCAL save -> AI -> retrieval in frontend | BLOCKED | after deployment, one approved capture |
| Failed capture leaves no file/row; next capture possible | PASS | runtime 14:13Z/14:20Z + tests |

Optional follow-up (not done, needs approval to flash): a smaller capture frame size (e.g. XGA/SVGA) would cut
transfer time 2-4x on this link; improving RSSI helps both captures and heartbeats.

## 13. Capture fix deployed and real capture verified - 2026-10-19 07:53-07:56Z (operator-approved)

Deployment:
- Rollback tag `duriancare/iot-service:rollback-20261019a` = `01a0e3e92bc4` (previously running).
- Rebuilt only IoT -> `duriancare/iot-service:local` = `45de4f07aa9b` (`cameraDownloadLimits` present). Recreated only
  `duriancare-iot-service` at 07:53:16Z; other 17 containers unchanged (23 h uptime); uploads volume mounted;
  `AI_SERVICE_URL=http://duriancare-ai-service:8000`; HTTP alerts flag unset (off); idle 10 s / total 30 s defaults;
  Kafka connected; health UP. Pre-capture: camera online, ping 5-9 ms, 0% loss.

One manual capture (approved), Farmer browser session, `POST /api/v1/camera/capture-now?device_id=ESP32-CAM-001`:
- HTTP 201 in 24,613 ms; `storage: "LOCAL"`; capture `35b825a3-63b8-4a78-a969-4bd2114c7778`, MANUAL,
  captured_at 2026-10-19T07:54:03.547Z, file `cam_1792396442896805205.jpg`.
- IoT log: `frame received device=ESP32-CAM-001 bytes=80122 ms=650`.
- AI service (reached via the fixed AI_SERVICE_URL): decoded 1600x1200, 80,122 B; detector 23.7 s; HTTP 422
  `No durian leaf detected.` -> `ai_status=FAILED`. This is the model's verdict on the frame, not a transport error.
- DB (read-only): exactly 1 row references the new file; volume file 80,122 B (sha256 prefix 7c94d6ecbb80); 0 `.tmp` files.
- Frontend (real Farmer session, camera page): gallery shows all 4 captures at 1600x1200, newest first; the new
  image via `/api/backend/v1/camera/image/...` -> 200 image/jpeg 80,122 B, valid SOI/EOI.
- Visual check: genuine, fresh but very dark frame of the same ceiling/wall view seen in the 11:56Z daylight
  capture (low room light; no leaf in view).

Overnight scheduled captures (read-only): 2026-10-18 17:00:05Z SCHEDULED (AI FAILED), 2026-10-19 07:00:01Z
SCHEDULED `ai_status=COMPLETED`, PHOMOPSIS_LEAF_SPOT, confidence 0.410, risk MEDIUM -> a full
camera -> IoT -> local storage -> AI -> DB run succeeded with the deployed AI_SERVICE_URL fix.

Final camera acceptance:
| Criterion | Status | Evidence |
|---|---|---|
| Camera initialization healthy | PASS | frames produced on demand (0.169 s first byte; 650 ms frame) |
| /status responds | PASS | 150-200 ms |
| Fresh /capture from the physical sensor | PASS | new 80,122 B 1600x1200 JPEG at 07:54:03Z |
| JPEG integrity, not stale/mock | PASS | SOI/EOI valid; new file and hash; visual check |
| IoT receives JPEG without unexplained timeout | PASS | 650 ms download; limits idle 10 s / total 30 s |
| Persisted locally (AWS unused) | PASS | `storage: LOCAL`, file on `duriancare_iot_uploads` |
| Metadata consistent, no orphan/temp/duplicate | PASS | 1 row for the file, 0 .tmp |
| Authorized retrieval + display in the frontend | PASS | camera page gallery, 200 via proxy; no identity 401 |
| Failed capture returns useful error, no false success | PASS | 14:13Z/14:20Z 502 with no file/row |
| Subsequent capture possible after failure | PASS | 07:54Z capture after the failures |
| AI diagnosis reachable and persisted | PASS | 07:00Z COMPLETED; 07:54Z valid 422 verdict recorded |
| Regression tests | PASS | IoT 109/109 (isolated PG); Web 124/124 |
| Heartbeat read-timeout root cause | BLOCKED | needs COM4 logs |

Notes / follow-ups:
- `ai_status=FAILED` is used both for "AI unreachable" and "no leaf detected"; a distinct status (e.g. NO_LEAF)
  would be clearer in the UI (product decision, not changed).
- Captures are UXGA (~66-91 KB); on a weak link a smaller frame size would shorten transfers (firmware change,
  needs flash approval). Low light makes captures unusable for diagnosis; the schedule is 07/12/17 UTC
  (14/19/00 Vietnam), and two of those slots are after dark; schedules in Vietnam daylight would help.
- Defect found (not fixed): camera schedules run in UTC. `src/camera/cameraScheduler.js:24` calls
  `cron.schedule(cronExpression, ...)` without a timezone and the container has no TZ (date shows UTC), while the
  Web UI asks farmers for daylight hours "6:00 – 16:00" local. Stored `0 7/0 12/0 17` therefore fire at 14:00,
  19:00 and 00:00 Asia/Ho_Chi_Minh (12:00Z and 17:00Z captures dark). Fix: pass `{ timezone: "Asia/Ho_Chi_Minh" }`
  (node-cron option) + test; needs an IoT redeploy.

## Follow-up 2026-10-19: camera schedules in Vietnam local time (source fixed, not deployed)

- Fix: `duriancare-iot-service/src/camera/cameraScheduler.js` now calls
  `cron.schedule(expr, fn, { timezone: "Asia/Ho_Chi_Minh" })` (`SCHEDULE_TIMEZONE`). The installed node-cron 3.0.3
  reads `options.timezone`. Cron expressions in the DB stay as they are; the container TZ is not changed.
- UI check: `DurianCare-IoT-Web-Client` `CameraSection.tsx` slots `0 6`…`0 16` are labelled "06:00 – 16:00" with no
  timezone, which farmers read as local time. That now matches the backend; no UI change needed.
- Effect after redeploy: stored `0 7/0 12/0 17 * * *` fire at 07:00/12:00/17:00 Vietnam (00:00Z/05:00Z/10:00Z),
  not 14:00/19:00/00:00. `0 17` is outside the 6–16 range the UI offers now (it is an older schedule), and at 17:00
  Vietnam the light is low but not dark; re-saving the schedule from the camera page replaces it with UI slots.
- Tests: `test/camera-scheduler-timezone.test.js` (stubbed node-cron checks that `loadAll` and `reschedule` pass the
  timezone; real node-cron TimeMatcher matches `0 6 * * *` at 23:00Z the day before). `npm run check` OK;
  `node --test` 107 pass / 4 skipped (isolated-PG fixtures not configured) / 0 fail.
- Deployment (prepared, NOT run; needs approval). From `DurianCare-IoT-Backend/infrastructure`:

  ```bash
  docker tag duriancare/iot-service:local duriancare/iot-service:rollback-pre-cron-tz-20261019
  docker compose -p duriancare build duriancare-iot-service
  docker compose -p duriancare up -d --no-deps --force-recreate duriancare-iot-service
  docker logs --tail 50 duriancare-iot-service   # expect "[camera-scheduler] N active schedule(s) loaded"
  ```

  Rollback:

  ```bash
  docker tag duriancare/iot-service:rollback-pre-cron-tz-20261019 duriancare/iot-service:local
  docker compose -p duriancare up -d --no-deps --force-recreate duriancare-iot-service
  ```
