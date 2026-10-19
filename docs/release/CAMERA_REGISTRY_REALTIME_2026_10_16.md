# Camera Registry / Realtime Completion - 2026-10-16

Result: IMPLEMENTED_OFFLINE_VERIFIED.
HARDWARE_NOT_TESTED. DEPLOYMENT_NOT_TESTED. No live video or physical camera reachability claim.

## Baseline and Scope

Read current Git status/diffs, camera/IoT source, V9 schema, Gateway routes and previous
FINAL_UI_OPERATION_GIT_REPORT_2026_10_16.md. All three repositories stay on duy/cleanup-fixes.
Preserved earlier Backend IoT/UDP/AI/infrastructure/docs dirty work and Mobile navigation,
notification/tree/dependency/dist changes. No staging, commit, push, deployment or firmware edit.

Existing design already had camera_devices, registration/heartbeat, signed UDP discovery and
HTTP snapshot polling. Root problems were static pretend IP defaults, environment rereads,
silent mDNS-to-old-IP substitution, shared default camera key, forwarded-header address inference,
unscoped camera routes, stale frame fallback and one-time registry loads in clients.

## Implemented

- Reused the same registry and schema; no migration or business-data edit.
- Registry-mode startup no longer requires ESP32_CAMERA_URL/DEFAULT_ESP32_IP.
- Explicit legacy opt-in only; existing registry row always wins, including when offline.
- Firmware REST updates require unique per-camera credentials, provisioned farm ownership,
  explicit validated LAN origin and fresh announcedAt. No forwarded-IP inference or client
  ownership claims. Timestamp-guarded SQL prevents delayed A from replacing newer B;
  identical retries return the existing result without duplicate writes.
- Unknown camera IDs require operator provisioning; no public account/ownership creation.
- Read-time UNKNOWN/ONLINE/OFFLINE derives from last_seen and configured timeout. Offline
  registry rows and origins remain stored. A fresh heartbeat is NOT capture evidence.
- Camera UI reads and writes use existing Farm READ_IOT / CONFIGURE_DEVICE checks.
- DTO retains compatibility fields and adds status, timestamps, stable proxy snapshot URL,
  opaque endpointVersion and explicit streamAvailable=false.
- Web polls scoped registry and snapshots at 8 seconds, invalidates obsolete responses and
  revokes old blob URLs; removes fake device fallback and the misleading LIVE badge.
- Mobile uses the same scoped list with device selection, focus-scoped polling/cleanup,
  endpoint-keyed images, request generations and authenticated snapshot image headers.
- Probes use authenticated GET plus successful JPEG response, not HEAD/status-less-than-500.
- Snapshot proxy drops obsolete-origin frames, rejects offline/invalid/empty/non-JPEG frames,
  bounds size/deadline and does not serve unbounded stale cache on failure.
- Existing UDP protocol is unchanged; no second registry or video transport introduced.
- Docker camera settings no longer default to a private IP; real .env files were not edited.

## Files Changed in This Execution

Backend:
- .gitignore (allow only the non-secret camera .env.example template).
- duriancare-iot-service/.env.example.
- duriancare-iot-service/src/config.js.
- duriancare-iot-service/src/camera/deviceService.js.
- duriancare-iot-service/src/camera/cameraRoutes.js.
- duriancare-iot-service/src/camera/cameraService.js.
- duriancare-iot-service/src/camera/cameraScheduler.js.
- duriancare-iot-service/src/camera/registryRuntime.js (new shared validation/auth/DTO module).
- duriancare-iot-service/src/public/publicRoutes.js (replace insecure camera subroutes only).
- duriancare-iot-service/test/camera-registry-runtime.test.js (new).
- duriancare-iot-service/test/camera-capture.test.js and test/udp-camera-cache.test.js
  (authorized fixture context and explicit stale-heartbeat behavior).
- infrastructure/docker-compose.yml (camera settings only, preserving existing dirty hunks).
- docs/CAMERA_REGISTRY_FIRMWARE_INTEGRATION.md and this new report.

Web:
- src/components/camera/CameraSection.tsx.
- src/lib/camera/client.ts and types.ts.
- src/__tests__/camera-registry.test.ts (new).

Mobile:
- src/features/camera/CameraMonitorScreen.tsx.
- src/lib/iotApi.ts.
- tests/camera-registry.test.cjs (new).

GitNexus impact was requested before edits; CJS/JSX caller coverage returned UNKNOWN and Web is
not registered. Backend/Mobile indexes were refreshed with npx gitnexus analyze; analyzer-generated
instruction-file changes were removed without reverting unrelated work. Manual source tracing
confirmed the relevant consumers: public registration, manual/scheduled capture, snapshot cache,
Web CameraSection/client and Mobile CameraMonitorScreen/iotApi. No LOW-risk claim is inferred
from missing graph edges or cached staleness warnings.

## Actual Tests and Checks

| Command | Exit / result |
| --- | --- |
| IoT npm test | 0: 72 tests, 70 PASS, 2 SKIP, 0 FAIL. |
| IoT npm run check | 0: configured Node syntax checks. |
| node --check src/camera/registryRuntime.js in IoT | 0. |
| mvn -pl duriancare-gateway -am test -q | 0: 12 PASS / 4 Gateway suites, no failures/skips. |
| Web npm test -- --runInBand | 0: 112 PASS / 12 suites. |
| Web npm run typecheck | 0; also covered by final production build TypeScript stage. |
| Web npm run lint | 0: 0 errors, 8 existing warnings; no broad cleanup attempted. |
| Web npm run build | 0: 44 generated pages. |
| Mobile node --test tests/*.test.cjs | 0: 6 PASS, helper/source-contract tests. |
| Mobile npm run typecheck | 0 after final timer-cleanup change. |
| Mobile npx expo export --platform all --output-dir C:/Users/minhd/.codex/tmp/duriancare-camera-registry-verified-20261016 | 0: Android/iOS/Web bundles and 75 static routes from final source. |
| docker compose --project-directory . -f infrastructure/docker-compose.yml config --quiet | 0: syntax validation only; missing environment variable warnings, no service started. |
| git diff --check in Backend/Web/Mobile | 0; existing CRLF conversion notices only. |

IoT skips: optional real PostgreSQL camera registration/heartbeat fixture and signed-UDP-to-PG
fixture require explicit isolated connection environment. No fixture connection was supplied,
no business database was written, no cleanup of existing records was attempted.
New REST integration tests use Express on localhost with mocked PostgreSQL/Farm access.
Image transport fixtures are marker-only bytes, not decoded physical camera images.
Web tests exercise API mocks plus source guards; Mobile tests do not mount native UI.

Intermediate failures (capture-path regex/empty-base validation, Mobile header typing/remaining
device constant, obsolete example-IP source assertions) were corrected and rerun. Counts above
are the successful final runs, not the intermediate failures.

## Network, Firmware and Deployment Prerequisites

Full contract, migration and firmware sequence:
docs/CAMERA_REGISTRY_FIRMWARE_INTEGRATION.md.

1. Operator verifies/provisions the camera_devices ID and farm/zone, then securely supplies
   CAMERA_DEVICE_BINDINGS with a different key for each camera. No keys were created for real devices.
2. Firmware must synchronize time, register explicit current address with X-Camera-Key and
   announcedAt, heartbeat about every 60 seconds and announce DHCP changes immediately.
   Old default/shared-key firmware must migrate; it is not silently trusted.
3. IoT container must have routing/DNS access to the camera LAN. Cloud-to-private-LAN still
   requires an approved VPN/relay/tunnel; storing an IP is insufficient.
4. Deployment environment must supply normal service/database/auth secrets. Compose validation
   warnings are not evidence these are configured; no deployment readiness is claimed.
5. Keep IoT service internal behind the trusted Gateway, which supplies identity headers.
   Existing public stored-image privacy and multi-replica capture-scheduler leadership remain
   separate deployment considerations documented in the integration guide.
6. Later hardware acceptance: boot/reconnect, DHCP A->B, expiry/recovery, actual JPEG decoding,
   latency/timeout and deployed route access. None was performed here.

No physical camera/USB request, Arduino change, live business write E2E, database reset,
volume deletion, service restart or model change was performed. No old report was overwritten.
