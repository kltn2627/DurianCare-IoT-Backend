# Camera Registry HTTP 401 Investigation

Date: 2026-10-17. Latest verdict: READY_FOR_BUILD_APPROVAL (see final implementation section).
Live registration remains unverified; earlier sections below are chronological evidence, not current unresolved-source verdicts.

## Runtime evidence (read-only)

- Gateway POST http://localhost:8080/api/v1/public/cameras/register with empty JSON and no key returned 401, error `Invalid or missing X-Camera-Key`.
- The same unauthenticated request to IoT localhost inside its container returned 401 with the identical legacy error. Neither request passed authentication or wrote a record.
- Running IoT publicRoutes contains the legacy shared-key handler. Its image has no src/camera/registryRuntime.js and does not mount the per-device registration router.
- Running container environment has neither CAMERA_DEVICE_BINDINGS nor CAMERA_REGISTRATION_KEY. Values were not printed. The effective legacy key and firmware key were not compared; this report does not claim the firmware key is incorrect.
- A scoped PostgreSQL SELECT inside an explicit read-only transaction found ESP32-CAM-001 with farm_id=null and zone_id=null. No ownership was assigned and no migration was executed.
- Sanitized log counts over the available two-hour window found zero registration success/failure/device-credential messages. Legacy rejections are not logged, so this is not evidence of zero requests.

The reproduced 401 is produced by the deployed legacy IoT handler, not Gateway Farmer-JWT enforcement. The firmware expects the new per-camera contract while the deployed image still uses the old shared-key contract. The original firmware request was not replayed and its secret was not inspected. A successful live registration cannot yet be claimed.

## Source trace and minimal fix

Gateway application.yml routes /api/v1/public/** to IOT_SERVICE_URL. PublicEndpointMatcher marks this prefix public and JwtAuthenticationFilter forwards it without requiring a Farmer JWT. IoT main mounts createPublicRouter, whose /cameras mount invokes createRegistrationRouter in src/camera/registryRuntime.js.

The working-tree implementation validates X-Camera-Key against CAMERA_DEVICE_BINDINGS, a fresh epoch-millisecond announcedAt within 30 seconds, a persisted stable device_id, trusted farm/optional zone binding, and explicit LAN address. Body ownership claims are rejected. Wrong/absent keys and unknown bindings remain 401; a valid binding without a persisted camera returns 404; farm/zone conflicts return 403. The schematic announcedAt=0 is not treated as evidence of the actual firmware timestamp.

Confirmed source defect: absent/empty binding configuration was classified as invalid device credentials (401). Parsing now validates object shape, binding IDs, key length and farm/optional zone values; missing/malformed configuration returns 503 with CAMERA_BINDINGS_UNAVAILABLE and no secret details. Validation occurs at request time, NOT at process startup; unrelated telemetry startup remains unchanged. No authentication fallback was added.

GitNexus impact for createRegistrationRouter returned UNKNOWN / target not indexed. Manual caller review scoped this edit to public register/heartbeat, retaining the existing database and ownership guards. No Gateway production code changed.

## Files changed in this task

1. duriancare-iot-service/src/camera/registryRuntime.js: distinguish unavailable service configuration from rejected device credentials.
2. duriancare-iot-service/test/camera-registration-auth.test.js: configuration, absent/wrong key, unknown ID, invalid timestamp and unprovisioned-row regression coverage; mock database only.
3. duriancare-gateway/src/test/java/com/duriancare/gateway/filter/CameraRegistrationRoutingTest.java: public auth-chain forwarding preserves header/body without JWT; private endpoint remains protected. This is a filter test, not a live upstream-routing test.
4. This report.

Existing runtime tests also cover valid authenticated update, retry without duplicate writes, outdated announcements, body ownership rejection, farm mismatch, origin validation and UI ownership denial. Unrelated accumulated changes were preserved. Web, Mobile and firmware were not edited.

## Commands and results

| Command | Exit | Result |
| --- | --- | --- |
| npm test (duriancare-iot-service) | 0 | 73 tests: 71 pass, 0 fail, 2 skipped |
| npm run check (duriancare-iot-service) | 0 | configured syntax checks pass |
| node --check duriancare-iot-service/src/camera/registryRuntime.js | 0 | pass |
| mvn -pl duriancare-gateway -am test -q | 0 | Gateway 14 tests, 0 failures/errors/skips |
| git diff --check | 0 | no whitespace errors; pre-existing LF/CRLF warnings |

IoT skipped tests require explicitly supplied isolated PostgreSQL fixture connections (DURIANCARE_CAMERA_FIXTURE_PG_URI and the separate UDP fixture configuration). No live business-write tests or physical capture tests ran. Full Maven reactor was not rerun; Java production code was unchanged.

## Operator follow-up (not executed)

1. Confirm the authorized farm and optional zone for ESP32-CAM-001; provision its existing row through the controlled operator procedure. Do not infer ownership from LAN address or device name. Current missing farm blocks the new ownership gate even after the image is updated.
2. Supply CAMERA_DEVICE_BINDINGS securely in the Compose environment. Redacted shape:

   ```json
   {"ESP32-CAM-001":{"key":"<REDACTED: unique secret, at least 32 characters>","farmId":"<operator-confirmed farm ID>","zoneId":"<operator-confirmed zone ID, or omit>"}}
   ```

   Match the firmware key locally without logging it or sending it in chat. Keep exact stable camera ID casing. Do not use CAMERA_REGISTRATION_KEY as a fallback.
3. Review/approve the accumulated IoT source and image changes separately. Loading this source requires building and recreating ONLY IoT with the reviewed Compose configuration. A restart alone neither replaces the old image nor reloads changed container environment. No build/recreate/restart was performed here; preserve databases and volumes.
4. After approval and provisioning, let actual firmware send its fresh timestamp and current LAN address. Expected response: 200 registered, then heartbeat 200. Reconcile 401 credentials, 403 scope, 404 provisioning, 400 payload/clock, 409 obsolete announcement, 503 configuration/dependency. Never disable any gate to obtain success.
5. Independently verify capture later under separate hardware authorization. Registration success alone does not prove a valid JPEG capture.

No deployment, DB mutation, migration, secret disclosure, hardware request, commit or push was performed.

## Recovery execution preflight rerun - 2026-10-17 17:50 local

Status: BLOCKED_PREREQUISITES. Inspection and tests were executed; no build or runtime change.

- Read both camera release reports, firmware integration guide, registry handler, Dockerfile and current Git status/diff. The accumulated IoT diff also includes UDP lifecycle, telemetry matching/history/thresholds, capture and read authorization changes; it is not only the small binding-diagnostics correction. A camera-only patch review is not approval of the whole build context.
- `docker compose -p duriancare -f infrastructure/docker-compose.yml config --quiet`: exit 0, no output. Resolved JSON was subsequently parsed in memory and only selected non-secret fields/booleans were emitted: service duriancare-iot-service, image duriancare/iot-service:local, context DurianCare-IoT-Backend/duriancare-iot-service, Dockerfile Dockerfile, resolvedBindingPresent=false. No resolved environment values were printed or persisted.
- Filtered `docker inspect` confirmed project duriancare, the expected Compose file, running state and restart count 1. Runtime image ID before inspection and at completion (no build): `sha256:5fee64a9b759802714d9c5cb541e61e62e2e04992462b3025dd634323b147627`. No after-build image exists for this execution.
- Read-only `docker exec ... node -e` checked file existence/source fingerprints/environment presence only: registryModulePresent=false, legacyHandlerPresent=true, perDeviceRouterMounted=false, bindingVariablePresent=false, bindingVariableNonempty=false.
- GET http://localhost:3001/actuator/health: HTTP 200, status UP. This is service health only, not device registration evidence.
- `npm test` in IoT: exit 0, 73 total / 71 pass / 2 skip / 0 fail. The two skips still require explicitly authorized isolated PostgreSQL fixture connections.
- `mvn -pl duriancare-gateway -am test -q`: exit 0. Fresh Gateway Surefire XML totals: 14 tests, zero failures/errors/skips.
- `npm run check` in IoT and `node --check duriancare-iot-service/src/camera/registryRuntime.js`: both exit 0.
- Source still distinguishes invalid credentials/unknown binding (401), ownership conflicts (403), valid binding with missing camera row (404), and absent/invalid global bindings (503 CAMERA_BINDINGS_UNAVAILABLE). No production code change was needed in this rerun.

Required operator inputs before proceeding:

1. Explicit authorized ownership confirmation for ESP32-CAM-001, including exact farm and optional zone, plus evidence that persisted provisioning matches. Prior approval for telemetry device esp32-01 is not camera ownership approval. The earlier read-only DB finding of null farm/zone was not re-queried in this rerun and must not be treated as a fresh DB observation.
2. Securely supplied per-device binding with an operator-local check that its key matches firmware. Neither the running container nor currently resolved Compose supplies it. No key was generated, requested in chat or inferred.
3. Review/approval of the complete IoT build context, followed by a separate runtime approval gate. The build, rollback-image preservation and recreate steps remain NOT EXECUTED. No hardware registration/heartbeat/capture request was sent.

Next authorized sequence: verify provisioning and protected configuration; rerun Compose validation; preserve current runtime image under a unique rollback tag; build only IoT; record old/new image IDs; present the exact recreate command and scheduler/consumer interruption plus rollback configuration to the operator; wait for explicit runtime approval. Health and physical-device verification follow only under their respective approvals. No other service needs rebuilding or recreating for this contract change.

Only this report was edited during the recovery preflight rerun. Existing source and unrelated dirty work were preserved; no database write, environment update, deploy, commit or push occurred.

## Account and ownership verification - 2026-10-17

Status: READ_ONLY_VERIFIED; CONFIGURATION_AND_PROVISIONING_APPROVAL_REQUIRED.

### Fresh database evidence

- PostgreSQL container duriancare-postgres: explicit BEGIN READ ONLY / SELECT / ROLLBACK against duriancare_auth.users, filtering case-insensitively by the operator-specified email (report masked as min***@gmail.com). Exactly one account: user ID `9ffc2a41-eaa6-4567-ad6d-d6741acafeb7`, role FARMER, status ACTIVE. Projection contained only id/role/status, never credential columns. An initial shell-quoting attempt failed before SQL execution; the corrected stdin SQL completed with exit 0 and ROLLBACK.
- MongoDB container duriancare-mongodb: SELECT-equivalent find on duriancare_farm.farms by that exact ownerUserId, projecting only IDs/status/zone codes. Exactly one farm: `farm-khoa-luan-2026`, ownerUserId matches, status ACTIVE. Embedded zones are `zone-a-khu-bac`, `zone-b-khu-nam`, `zone-c-khu-dong`, `zone-d-khu-tay`, all ACTIVE. No camera location within those zones was established.
- PostgreSQL via the running IoT pool: explicit BEGIN READ ONLY / parameterized SELECT device_id,farm_id,zone_id / ROLLBACK on `duriancare_iot.camera_devices`. Camera `ESP32-CAM-001` exists; farm_id=null, zone_id=null. This is a fresh observation, not a reuse of the previous audit.
- Database authentication used existing runtime environment in memory; no passwords, tokens, cookies or camera keys were printed or stored. No records were created or changed. These are administrative read-only DB observations, not authenticated Farmer API E2E evidence.

### Current contract and source references

- Auth model: duriancare-auth-service/src/main/java/com/duriancare/auth/entity/User.java:20 maps users; UserRepository.java:14 performs case-insensitive email lookup.
- Farm.java:10/13/22 maps farms, ownerUserId and embedded zones; FarmZone.java:9 maps each zone ID. Paths are under duriancare-farm-service/src/main/java/com/duriancare/farm/domain/.
- IoT src/main.js:312 mounts /api/v1/public; src/public/publicRoutes.js:23 mounts /cameras using createRegistrationRouter. src/camera/cameraRoutes.js:25 applies cameraAccess for private UI routes.
- IoT src/camera/registryRuntime.js:71/78 defines the router and parses CAMERA_DEVICE_BINDINGS. Register/heartbeat POST JSON uses `cameraId`, NOT `device_id`; cameraId maps to the persisted device_id. X-Camera-Key must match its exact per-device binding using timing-safe comparison. announcedAt is an integer UTC epoch timestamp in milliseconds within 30 seconds (line 98); explicit ipAddress must be a permitted LAN IPv4 or .local origin, with valid HTTP/HTTPS protocol and port. Ownership fields in firmware JSON are forbidden. A fresh heartbeat uses the same body contract; recommended cadence is about 60 seconds, with newer timestamps and immediate address updates after DHCP changes. Stored ownership is checked before timestamp-guarded update (lines 101-114).
- `device_id` remains the database column and the existing snapshot/capture query parameter; do not rename firmware cameraId to device_id.
- infrastructure/docker-compose.yml:314 passes CAMERA_DEVICE_BINDINGS through environment substitution. Fresh resolved config was parsed in memory, emitting only resolvedBindingPresent=false. Fresh container file/presence checks report registryModulePresent=false and bindingPresent=false. The source integration exists but is not deployed.

### Small-project recommendation and sharing

There is sufficient database evidence to propose `farm-khoa-luan-2026` for the specified Farmer. The operator's wording is an intended assignment, not proof that physical camera location or persisted ownership is already established. No new camera-management system or schema is needed. Prefer one trusted farm assignment to the existing camera row, only after explicit provisioning approval. Zone may remain null for a deliberate farm-level assignment; otherwise the operator must identify one of the four verified zones. Do not select a zone automatically.

Proposed non-secret binding shape (not applied):

```json
{"ESP32-CAM-001":{"key":"<securely supplied matching per-camera key, at least 32 characters>","farmId":"farm-khoa-luan-2026"}}
```

Add zoneId only after its location and provisioning are confirmed. Provisioning and binding must agree; this report does not authorize a DB write or supply a key.

Existing optional sharing: FarmAccessCheckService.java:34-59 permits FARMER access only for the matching owner, and ENGINEER/EXPERT only with an ACTIVE, unexpired farm authorization, required permission and allowed zone. AgronomistRolePolicy.java:10-17 limits those roles. AgronomistAuthorizationController.java:51 exposes the existing owner invitation flow, with acceptance at line 118. Use that approved flow with READ_IOT and the smallest zone scope for viewing; do not grant CONFIGURE_DEVICE unless management is explicitly needed. IoT src/read-api.js:309/312 requests READ_IOT versus CONFIGURE_DEVICE. Other FARMER accounts are not supported as delegates by this existing policy; do not change their role or grant blanket ownership merely to share a camera. A Farmer-to-Farmer sharing requirement would need separate approval/design, not a fallback in registry authentication.

### Gate before build

1. Operator explicitly approves assigning ESP32-CAM-001 to the verified farm, and confirms farm-level placement or exact zone. An authorized provisioning step must then establish the same relationship; no such write was performed here.
2. Operator supplies CAMERA_DEVICE_BINDINGS through the protected environment and locally verifies its unique key matches firmware. No global CAMERA_REGISTRATION_KEY fallback is allowed.
3. Complete IoT build-context review and test evidence must be accepted before build; runtime recreation and any hardware test require their separate approvals. No build, restart, deployment, registry POST or camera request occurred in this verification.

Only this report changed. No production code changed, so tests were not rerun for this read-only investigation; the preceding test results remain historical and are not presented as fresh runs. GitNexus concept query returned no flows with an FTS-extension warning, so source was inspected directly; no index repair or source edit was attempted.

Conclusion: sufficient evidence exists for the proposed account-to-farm configuration, but camera ownership is not yet persisted and binding/key readiness is absent. Not ready for build/deployment until the above inputs and approvals are supplied.

## Full dirty IoT review and farm-level provisioning proposal

Date: 2026-10-17. Verdict: REVIEW_GAPS_REMAIN; NOT_READY_FOR_BUILD_APPROVAL.
The proposed owner/farm/zone selection is NOT permission to write PostgreSQL or deploy.
Branch remains duy/cleanup-fixes. All existing modifications were preserved. Only this report
was edited in this review. No production/test functions were changed.

### Per-file review of the complete dirty IoT scope

Paths below are relative to duriancare-iot-service. M=tracked modified, U=untracked.
Risk is a manual review assessment, not a new GitNexus verdict. The index previously did not
contain registryRuntime and concept search was degraded; no graph-based safety claim is made.

| File / location | Class | Change and regression assessment |
| --- | --- | --- |
| package.json:10 | M / tooling, low | Expands syntax checks only, no dependency version diff. Check script still omits registryRuntime; explicit check is needed. |
| src/camera/cameraRoutes.js:16,25,75,115,282 | M / capture+authorization, high | Fixes async URL resolution, removes guessed IP/auto-provisioning, applies read/manage permission, bounds JPEG/cache/deadline, CAS manual config. Existing clients now need explicit ID/auth; stale heartbeat intentionally denies capture. Stored images retain identity-only access, not per-camera farm lookup; historical privacy limitation must not be mistaken for complete image ownership enforcement. |
| src/camera/cameraScheduler.js:24 | M / scheduling, medium | Skips missing origin and known-offline cameras. No new test directly exercises cron callback. Existing tasks are not explicitly stopped/drained by shutdown. |
| src/camera/cameraService.js:16,113 | M / capture, medium | Rejects wrong content type, empty/incomplete/oversize JPEG with bounded deadline. File is still written before INSERT and capture does not recheck registry origin after download; DB failure/origin change can leave file or stale-origin capture. This persistence ordering predates the diff, but remains a build-context limitation. Marker validation is not JPEG decoding. |
| src/camera/deviceService.js:61,149,170 | M / persistence, high | REST heartbeat guards farm and newer last_seen; URL update guards farm and expected origin. Shared helpers retain optional guards for compatibility; review each caller, not helpers as standalone authorization. UDP URL update does not persist/check announcement timestamp against REST last_seen. |
| src/config.js:1,5,66 | M / config, medium | Removes dotenv overwrite, static camera default and default shared key; explicit legacy opt-in; positive timeout validation. Environment changes need recreation; old .env edits no longer hot-reload. |
| src/main.js:115,213,247,375,431,526,535 | M / mixed runtime, high | Legacy reads gain earlier authorization middleware; case-insensitive telemetry matches, history cap 1000, soil status changes from 30/80 to 60/85, humidity alias, UDP lifecycle. Case-folded IDs can be ambiguous if registry has case-only duplicates; query LIMIT 1 is not evidence those cannot exist. Shutdown drains UDP/HTTP but not camera cron jobs or all in-flight telemetry handlers. |
| src/public/publicRoutes.js:23 | M / device API, high | Replaces shared-key auto-upsert with per-device authenticated update-only router; intentionally incompatible with unprovisioned/shared-key firmware. Public traceability logic outside removed camera block is unchanged. |
| src/read-api.js:17,47,255,381,662 | M / telemetry+authorization, high | Scopes legacy reads, case-folded lookups, soil fields/status and humidity compatibility. No unowned-device fallback. LOWER queries may need workload/index assessment; soil status is separate from alert engine, whose rules currently cover temperature/humidity/light, not soil notifications. |
| test/read-api.test.js:49,122,157 | M / mock regression, low | Adds legacy auth denial, no implicit mapping, case-fold SQL assertions and converter coverage. Does not execute PostgreSQL indexes/uniqueness or main.js ingestion end-to-end. |
| tests/camera-registration.test.js:218 | M / optional PG fixture, medium | Explicit connection only, UUID schema, V9 DDL inside rollback transaction with cleanup verification; nine helper cases. Skipped without authorized fixture; does not test REST router against PG. |
| .env.example:1 | U / template, low | Blank binding and registry defaults, no populated key. Not a source of runtime secrets. |
| scripts/seed-iot-history.js:57,88 | U / independent seed, medium | Deterministic synthetic history, dry-run default; apply requires test namespace, fixture farm/zone, explicit URI and verified account timestamp. Never invoke during camera recovery. Dockerfile does not copy scripts. |
| src/camera/registryRuntime.js:7,30,71 | U / registry+auth, high | Binding validation (503), exact per-device key/ID (401), time/origin validation (400), update-only provision check (404), farm/optional zone enforcement (403), retry/CAS (409). Farm-level binding is valid; it does not force zone=null, so provisioning must establish it explicitly. |
| src/discovery/udpDiscoveryService.js:58,69,84,109,143,186 | U / UDP runtime, high | Signed envelope, peer-IP check, bounded rate, local replay map, farm/CAS guard, socket generation and shutdown drain. Separate UDP bindings are not REST bindings. Cross-transport/restart replay ordering is not durable; no global deadline on draining a stuck DB operation. Docker LAN broadcast remains environment-dependent. |
| test-support/camera-schema.cjs:4 | U / fixture helper, low | Restricts UUID fixture namespaces, rewrites V9 search_path with SET LOCAL and rejects business-schema leftovers. Requires rollback owner to finish cleanup. Not copied into production image. |
| test-support/main-lifecycle.cjs:1 | U / child mock, low | Replaces runtime dependencies only when explicitly preloaded by child tests; does not exercise real MQTT/Kafka/PG or in-flight cron shutdown. |
| test/camera-capture.test.js:19,38,55 | U / mock/local HTTP, low | Invalid body/status/timeout before persistence; route happy path and invalid-frame cache refusal. Uses marker-only transport bytes and temporary local files, not hardware or decoded JPEG evidence. |
| test/camera-registration-auth.test.js:8 | U / mock HTTP, low | Config absence/shape, missing/wrong key, unknown binding, bad timestamp and unprovisioned row; no DB writes. |
| test/camera-registry-runtime.test.js:9,27,36,53,98 | U / mock HTTP, low | Legacy opt-in, safe origin, status/version, valid registration/retry/stale/farm denial and scoped UI reads. Missing explicit zone-null happy path, zone mismatch and read-only capture-denial cases. |
| test/seed-history.test.js:10,28,43 | U / mock seed, low | Determinism/date bounds, rejected real-device inputs and mocked fixture serialization/rollback. Does not run seed apply on real DB. |
| test/udp-camera-cache.test.js:8 | U / local HTTP+mock, low | Address change invalidates cached frame and stale heartbeat returns 503; injects identity and farm access correctly. |
| test/udp-discovery-pg.test.js:15,30-34 | U / optional integration, high test gap | Skipped test constructs camera router without identity or Farm-access injection, then expects image bytes from unauthenticated fetch. Fresh mock reproduction returns 401 with zero DB calls. Its upsert also leaves last_seen null/online false, so authorized calls would still fail freshness until fixture adds legitimate heartbeat. Must repair fixture before claiming integration evidence. |
| test/udp-discovery.test.js:10-194 | U / mock+localhost UDP, low | Interface/parser, signatures/forgery/peer/replay, rate/concurrency, CAS/DB/socket errors, lifecycle. Does not prove Windows Docker LAN broadcast or REST/UDP ordering. |
| test/udp-lifecycle.test.js:6,26 | U / child mock, low | SIGINT/SIGTERM once/order and failed HTTP bind; no live dependency shutdown test. |

Unchanged IoT files (including Dockerfile, package lock, migrations, alert-engine and existing
config/runtime/alert tests) were not relabeled as new changes. infrastructure/docker-compose.yml
is outside this table: its IoT binding/default changes are relevant, while its dirty Auth/Farm
settings are unrelated; do not recreate those services. Build context excludes these other services.

### Findings and missing tests

1. HIGH verification gap: skipped UDP/PG snapshot fixture is incompatible with current auth and
   freshness contract. Reproduced only the first failure safely in memory: createCameraRouter
   with mocked pool, no identity, localhost GET snapshot -> 401, databaseCalls=0, exit 0.
   No fixture database connection was supplied or used. Repair fixture (not auth) in a separately
   authorized code/test step, then run only against an approved isolated DB and verify rollback.
2. MEDIUM conditional runtime risk: REST last_seen ordering and UDP process-local timestamp
   ordering are not unified (udpDiscoveryService.js:119-126; deviceService.js:170-185). A signed
   UDP announcement older than the last REST update but within 30 seconds can pass local checks
   and CAS against the currently read origin. Add mock cross-transport/restart tests before
   enabling both transports for a camera. No production replay was attempted. REST-only config
   with UDP bindings absent reduces this risk; it does not prove the general UDP implementation.
3. MEDIUM shutdown gap: main.js:535 waits for UDP work before its HTTP drain deadline; camera
   cron jobs are not stopped and camera/MQTT work is not tracked to completion. Add bounded
   slow-query/in-flight capture/telemetry shutdown tests. Existing child mocks only prove ordering.
4. MEDIUM deployment data risk: Compose IoT definition has no uploads volume. Capture files
   may be in the container writable layer; recreating can lose historical image bytes while DB
   URLs survive. Before any recreate, operator must identify actual mounts/storage and authorize
   backup/restore or a separately reviewed persistence arrangement. Do not use docker commit
   or dump whole container environment as a rollback shortcut.
5. Additional test gaps: farm-level zone=null registration success; explicit zone mismatch and
   forbidden zoneId/treeId claims; READ_IOT user denied config/capture/schedule writes; genuine
   two-request concurrency in REST+PG; malformed JSON/body types; exact 60/85 threshold boundaries
   and nulls in both converters; main.js history cap/pagination and MQTT case-folded registry update;
   case-only registry duplicate ambiguity; oversized/slow image stream and origin switch while
   capture is in-flight. Existing tests partially cover nearby paths, not all these contracts.

### Firmware contract (confirmed from Backend source, not physical firmware inspection)

POST /api/v1/public/cameras/register and /heartbeat; Content-Type application/json;
X-Camera-Key per exact ESP32-CAM-001 binding; no Farmer JWT for these device routes.
Body: cameraId=ESP32-CAM-001, explicit ipAddress, integer port 1..65535, protocol http/https,
announcedAt current integer UTC epoch milliseconds within +/-30 seconds. Protocol/port can
default from persisted row; firmware should send both explicitly. No farmId/zoneId/treeId body.
REST LAN origin accepts private IPv4 or constrained .local hostname, not public/loopback or
credentials/path/query/hash. No client-IP inference. Epoch seconds or zero are rejected.
Valid new registration returns 200 registered; heartbeat returns 200 ok; identical retry can
return 200 ok without another update; conflicting/older update returns 409. Heartbeat around
60 seconds and new announcement on address changes. 401/403/404/503 retain prior documented meaning.
Actual firmware key equality, clock sync and LAN reachability remain unverified, not assumed.

### Provisioning proposal - SQL NOT EXECUTED

Reverify Auth user ACTIVE/FARMER and Mongo farm.ownerUserId first. Cross-database ownership
cannot be guaranteed by a PostgreSQL FK; record operator approval and selected farm separately.
Use the existing row only, no INSERT/upsert. Under explicit DB-write approval, an authorized
operator may review this transaction (stop on any unexpected tree/zone/owner or row count):

```sql
BEGIN;
SET LOCAL lock_timeout = '5s';
SELECT id, device_id, farm_id, zone_id, tree_id, updated_at
FROM duriancare_iot.camera_devices
WHERE device_id = 'ESP32-CAM-001' FOR UPDATE;
DO $$
DECLARE affected integer;
BEGIN
  UPDATE duriancare_iot.camera_devices
  SET farm_id = 'farm-khoa-luan-2026', zone_id = NULL, updated_at = CURRENT_TIMESTAMP
  WHERE device_id = 'ESP32-CAM-001'
    AND farm_id IS NULL AND zone_id IS NULL AND tree_id IS NULL;
  GET DIAGNOSTICS affected = ROW_COUNT;
  IF affected <> 1 THEN
    RAISE EXCEPTION 'Provisioning precondition changed; rollback and review';
  END IF;
END $$;
SELECT device_id, farm_id, zone_id, tree_id, updated_at
FROM duriancare_iot.camera_devices WHERE device_id = 'ESP32-CAM-001';
-- Operator inspects results; COMMIT only under the separate write approval.
-- Otherwise ROLLBACK. Do not leave an interactive transaction/row lock open.
```

The tree_id precondition is intentionally conservative: this review did not establish its
current value. Do not clear a tree assignment to make SQL pass. Record the before/after non-secret
row snapshot for controlled rollback. A repeated run after successful provisioning must verify
the desired state instead of forcing the UPDATE to succeed. Rollback of ownership later needs
separate approval and guarded comparison with that snapshot; do not undo concurrent legitimate work.

### Protected configuration proposal - NOT APPLIED

Keep CAMERA_LEGACY_ENABLED=false. Existing Compose interpolation at line 314 consumes
CAMERA_DEVICE_BINDINGS. Supply only through the approved protected environment source:

```json
{"ESP32-CAM-001":{"key":"<EXISTING_FIRMWARE_KEY_SUPPLIED_SECURELY>","farmId":"farm-khoa-luan-2026"}}
```

Omit zoneId for farm-level binding; no key is printed, generated or saved by this review.
Operator checks the existing key locally (minimum 32 characters), exact ID and JSON validity.
Do not truncate or replace a shorter firmware key silently; that requires a coordinated separate
credential change. Do not reuse CAMERA_REGISTRATION_KEY as fallback. Validate resolved Compose
in memory/quiet mode, never print resolved YAML/JSON, env or HTTP authorization headers.

### Build / recreate / rollback checklist - ALL MUTATING COMMANDS NOT EXECUTED

Before build: resolve findings/test gaps or explicitly approve a constrained REST-only scope;
review all copied src files, verify source hash/test evidence, normal dependencies/env, approved
ownership provisioning and protected binding. Reconfirm actual mounts and preserve uploads first.
Dockerfile npm install --omit=dev is not npm ci: build reproducibility remains a documented risk.

PowerShell at repository root, using the same approved environment source and Compose project:

```powershell
$dc = @('-p','duriancare','-f','infrastructure/docker-compose.yml')
$svc = 'duriancare-iot-service'
docker compose @dc config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Configuration invalid' }
docker compose @dc ps $svc
$oldImage = docker inspect --format '{{.Image}}' $svc
if ($LASTEXITCODE -ne 0) { throw 'Cannot identify rollback image' }
$rollback = 'duriancare/iot-service:rollback-' + (Get-Date -Format yyyyMMddHHmmss)
docker image tag $oldImage $rollback
if ($LASTEXITCODE -ne 0) { throw 'Rollback tag failed' }
docker compose @dc build $svc
if ($LASTEXITCODE -ne 0) { throw 'Build failed; do not recreate' }
docker image inspect --format '{{.Id}} {{.Created}}' duriancare/iot-service:local
# STOP: present image IDs, protected-config readiness, storage backup and interruption;
# obtain separate runtime approval before the next command.
docker compose @dc up -d --no-deps --no-build --pull never --force-recreate $svc
docker compose @dc ps $svc
docker inspect --format '{{.Image}} {{.State.Status}} {{.RestartCount}}' $svc
(Invoke-WebRequest 'http://localhost:3001/actuator/health' -TimeoutSec 15).StatusCode
# STOP: separate firmware/device-test approval before any real registration or capture.
```

Preserve previous effective runtime settings through the approved secret-management source,
not environment dumps in artifacts. New Compose defaults are not the old container configuration.
Rollback requires restoring that protected old configuration as well as the saved image, then
separately approving the following commands. Abort if config cannot be restored faithfully:

```powershell
docker image tag $rollback duriancare/iot-service:local
if ($LASTEXITCODE -ne 0) { throw 'Rollback image unavailable' }
docker compose @dc config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Rollback configuration invalid' }
docker compose @dc up -d --no-deps --no-build --pull never --force-recreate $svc
```

Only IoT is recreated; do not run up without a service or down. Scheduler, MQTT ingestion and
Kafka publishing in IoT pause/reconnect; in-flight capture/AI/telemetry may fail or be retried.
Check duplicate/lost telemetry and schedule reload, not health alone. Legacy rollback restores
the old auth behavior and is a temporary fallback, not successful per-device recovery. It does
not undo DB writes, restore deleted writable-layer files or replay missed telemetry automatically.

### Fresh verification and approval conclusion

- `npm test` in IoT: exit 0; 73 total, 71 PASS, 2 SKIP, zero failures. Explicit fixture URI
  existence check returned false. Only mocks, local HTTP/UDP and child fixtures ran; no real DB writes.
- `npm run check`: exit 0. The isolated in-memory missing-identity snapshot probe: exit 0,
  expected observed status 401 / zero database calls; confirms the skipped-fixture incompatibility.
- No Gateway test rerun this review (Gateway unchanged); prior 14-pass result is historical.
- No new source/tests, no seed apply, no build/image tag/recreate/restart, DB provisioning, env
  modification, physical request, commit or push. Source fixes were not authorized by this review-only task.

Sufficient account/farm evidence and a concrete plan now exist, but the complete accumulated
IoT build is NOT cleared for build approval while the skipped integration fixture and runtime
scope/storage/shutdown risks remain unresolved or explicitly constrained. Passing mocks alone
does not close them. Independently approved isolated build is not approval to deploy.

Separate project-owner decisions required: (1) authorize targeted test/source remediation or
accept documented constrained REST-only risk; (2) authorize isolated PG fixture testing;
(3) authorize the guarded farm-level DB provisioning with zone=null; (4) authorize secure binding
installation/local firmware-key matching; (5) approve upload preservation and old-config rollback
preparation; (6) approve the reviewed IoT build; (7) approve IoT-only recreation; (8) separately
approve real registration/heartbeat/capture verification. None is inferred from farm selection.

## Final source implementation and isolated verification - 2026-10-17

Verdict: READY_FOR_BUILD_APPROVAL. This supersedes the preceding review-only source blockers,
NOT the requirement for operator approval of real provisioning/secrets/build/runtime/hardware.
The live IoT container still runs the legacy image; live HTTP 401 recovery is NOT claimed.

### Issues resolved and files changed in this execution

All IoT dirty files were reviewed using the preceding inventory plus final diff. Unrelated
Auth, AI, Search, Notification, Gateway production and earlier Farm changes were left intact.
GitNexus impact was run before production symbol edits: createUdpDiscoveryService,
updateCameraUrl, scheduleOne/loadAll/reschedule, start/shutdown, startOfflineWatchdogScheduler,
captureAndStore and the fixture schema helper. Results were UNKNOWN/no resolved callers, not LOW.
Manual callers were checked: main -> UDP/camera scheduler/watchdog; routes/scheduler -> capture;
UDP/manual config -> updateCameraUrl. This is an explicit analysis limitation, not suppression.

| File (IoT-relative unless stated otherwise) | Resolution |
| --- | --- |
| src/camera/deviceService.js:170 | Adds optional signed announcement timestamp to URL update. Atomic WHERE last_seen < timestamp and farm guard enforce ordering across processes/transports. Signed timestamp updates last_seen/online with the address. Manual configuration without timestamp retains expected-origin CAS. A newer signed UDP update does not lose solely because an older REST update changed its previously read origin. |
| src/discovery/udpDiscoveryService.js:124 | Rejects timestamp <= persisted REST/UDP last_seen before update and passes announcedAt into the guarded SQL. The DB guard closes the race after that initial SELECT; local replay map is no longer the only timestamp authority. Same-origin UDP packets still do not write or replace the normal REST heartbeat cadence. |
| src/workDrain.js | Small shared accepted-work tracker: stop admission, retain pending promises, drain settled work. Used only to close the demonstrated shutdown gaps. |
| src/camera/cameraScheduler.js:24,75 | Tracks callbacks including lookup failures; stops all camera cron jobs and awaits accepted work. |
| src/camera/cameraService.js:113-126 | Tracks manual/scheduled capture persistence/AI work and exposes drainCaptures; rejects new capture admission after capture shutdown. Image validation/persistence contract is otherwise preserved. |
| src/iot-runtime.js:62 | Watchdog timer stops admission and exposes drain of an already-running evaluation. |
| src/main.js:70,507,525,541 | Rejects new HTTP work during shutdown, stops offline cron/watchdog/camera cron, ends MQTT gracefully, stops UDP input, drains HTTP and accepted MQTT/background/capture work before Kafka disconnect and PG pool end. Global 75s deadline exits 1 with sanitized failure message if dependencies never settle; no claim of successful completion for forced shutdown. |
| infrastructure/docker-compose.yml, IoT only | Adds 90s stop grace, UPLOADS_DIR=/app/uploads and named iot_uploads volume. Existing unrelated dirty service settings preserved, not applied. |
| .dockerignore | Excludes .env files, uploads, node_modules, test/seed/cache material from build context; Dockerfile still copies package manifests and src only. No secret file was changed. |
| package.json | Extends npm run check to registryRuntime, workDrain, scheduler and capture service too; no dependency/lockfile upgrade. |
| test/udp-discovery-pg.test.js | Supplies test identity and explicit Farm-access fixture to the real production middleware, keeps a fresh upsert heartbeat and strictly newer signed UDP timestamp. No production auth bypass. |
| test-support/camera-schema.cjs | Before fixture DDL, verifies current_database() is exactly camera_registry_fixture; UUID namespace and rollback protections remain. |
| test/camera-ordering-pg.test.js | Real PG competing REST/UDP writes on separate pool clients, both choices of newest transport, stale expected origin, older retry rejection and final timestamp/address assertions. Own committed fixture namespace removed and absence verified. |
| test/camera-http-pg.test.js | Express register/heartbeat against real PG rollback fixture: valid/missing/wrong key, unknown binding/unprovisioned ID, bad timestamp/IP/protocol/port, forbidden ownership body fields, farm and explicit zone mismatch, farm-level zone=null success, idempotent retry, stale conflict, private read ownership and read-only capture denial. |
| test/udp-discovery.test.js | Adds persisted REST timestamp/replay rejection across discovery restart. |
| test/work-drain.test.js | Verifies admission stop, pending-work drain and camera scheduler stop/drain with slow mocked capture. |
| test-support/main-lifecycle.cjs and test/udp-lifecycle.test.js | Child process runs actual main shutdown with mocked dependencies: accepted slow MQTT persistence finishes before Kafka/PG close, late messages are not accepted, camera/capture drain order, SIGINT/SIGTERM idempotency and accelerated stuck-worker timeout exit 1. |
| This report | Fresh evidence, final scope/limits and unchanged operator gates. |

### Actual isolated PostgreSQL test environment and cleanup

Used the already-present postgres:16-alpine image (ID
sha256:16bc17c64a573ef34162af9298258d1aec548232985b33ed7b1eac33ba35c229).
Created ONLY a disposable test container, not a replacement/restart of any project service:

```powershell
docker run --pull never --rm -d --name duriancare-camera-fixture-20261017 `
  --label duriancare.purpose=camera-registry-test --network bridge `
  -p 127.0.0.1::5432 --tmpfs /var/lib/postgresql/data `
  -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_DB=camera_registry_fixture `
  -e POSTGRES_USER=fixture_runner postgres:16-alpine
docker port duriancare-camera-fixture-20261017 5432/tcp
```

Observed endpoint 127.0.0.1:63702. No production network attachment, host data bind or persistent
volume. Trust authentication is ONLY for this localhost disposable fixture, not real credentials
or camera keys. Before writes, psql verified current_database=camera_registry_fixture,
current_user=fixture_runner and zero non-system tables; docker inspect/port identified the target.
Tests used process-local DURIANCARE_CAMERA_FIXTURE_PG_URI pointing there; it was removed from
that command environment afterward. No actual service env/secrets were edited.

After all tests, psql again verified the fixture database, zero camera_fixture_/udp_fixture_
schemas and zero non-system tables. Rollback fixtures verified schema absence; committed race
fixture removed only its own random namespace. Then docker stop on this named --rm container
completed successfully; filtered docker ps -a returned no matching container. No project DB
or volume was modified. Temporary fixture lifecycle is the only Docker runtime change here.

### Final commands and results

| Command / scope | Exit | Actual result |
| --- | --- | --- |
| npm test, IoT with the isolated PG URI | 0 | 79 total, 79 PASS, 0 FAIL, 0 SKIP, 8 suites; includes four real-PG test cases and the legacy helper test's nine internal assertions/cases (not counted as nine extra Node tests). |
| npm run check, final expanded script | 0 | All configured syntax checks pass. |
| node --check for every src/**/*.js | 0 | All source JavaScript parses. |
| mvn -pl duriancare-gateway -am test -q | 0 | Fresh Surefire totals 14 tests, 0 failures/errors/skips. Gateway production unchanged. |
| docker compose -p duriancare -f infrastructure/docker-compose.yml config --quiet | 0 | Compose valid; no resolved secrets emitted. |
| Resolved Compose parsed in memory, selected booleans only | 0 | uploads mount/path verified, stop grace 1m30s, bindingPresent=false. |
| Fixture target/cleanup SELECT and docker stop/filtered ps | 0 | Correct isolated DB, no remaining test schemas/tables; fixture container absent. |
| git diff --check | 0 | No whitespace errors; pre-existing LF/CRLF notices only. |

Intermediate green runs (76/77/78 tests) were followed by final 79-pass run after the last
source/test additions. No skipped test is counted as PASS. HTTP tests validate device-key auth
and mocked Farmer/Farm authorization boundaries, not live Farmer login through Gateway. Gateway
filter tests and earlier read-only routing evidence remain separate from a deployed full E2E.

### Uploads and build context readiness

Fresh read-only check of the running IoT container resolved uploadsDir=/app/uploads, 0 files,
0 bytes; docker inspect Mounts=[] confirmed no persistence there today. No images were moved,
copied, deleted or hidden by a mount during this execution. Compose now declares iot_uploads
(project volume name duriancare_iot_uploads) at the same absolute path; it is NOT created/applied.
The existing root-user Node image can write the new Docker volume. Source/template keys remain blank.

Before recreation, repeat inventory after stopping/quiescing writers under runtime approval.
If new files exist, back them up from the old stopped container into an operator-approved protected
directory, verify file hashes/counts, and populate the named volume BEFORE attaching it to the new
container. An empty volume would otherwise mask those old files. These copy/volume operations
also require approval; none ran. Keep that volume on rollback. Never down -v or prune it.

The per-file inventory above remains the accumulated-diff review; its UDP ordering, skipped
fixture and shutdown findings are now resolved by the implementations/tests in this section.
Telemetry matching/history/soil-status changes remain as previously reviewed; all read/alert/seed
regressions passed. Soil labels still are not a new soil-alert notification engine. No unrelated
threshold, account, historical image authorization policy or AI behavior was redesigned.

### Provisioning, binding, build and rollback: prepared, NOT executed

The prior guarded transaction in this report remains the exact proposed provisioning plan:
recheck the Farmer user and Mongo owner; lock ESP32-CAM-001; require farm/zone/tree all null;
update ONLY farm_id to farm-khoa-luan-2026, zone_id null and updated_at; require exactly one row;
verify before/after; COMMIT only under separate DB-write approval, otherwise ROLLBACK. If
tree_id or ownership changed, stop rather than clear/overwrite it. After commit, any undo needs
a new approved guarded transaction matching the recorded post-provisioning fields/timestamp;
do not blindly revert an active camera after newer heartbeats or legitimate edits.

Protected binding remains exact camera ID -> existing firmware key (>=32 characters) ->
farm-khoa-luan-2026, with zoneId OMITTED. No secret was created, inferred, printed, persisted or
compared with hardware here. Fresh Compose still has no binding, so deployment is not ready.
Register and heartbeat still require cameraId, X-Camera-Key, fresh epoch milliseconds and LAN
origin; no firmware ownership claims or global shared-key fallback. Source mount is intact.

The earlier PowerShell build/recreate/rollback commands are prepared but NOT executed:
config --quiet; inspect/retain old image under unique rollback tag; build only IoT; record new ID;
STOP for runtime approval; up -d --no-deps --no-build --pull never --force-recreate IoT only;
verify image, volume, HTTP health; STOP for separately authorized real device checks. Retain
90s stop grace in both deployment and rollback configuration to allow 75s application shutdown.
If existing uploads require backup, runtime approval must include quiescing/stopping ONLY IoT
before copying (for example docker compose @dc stop --timeout 90 $svc), preserving its stopped
container until backup verification. Do not copy a changing directory and call it a stable backup.

Latest live image before/after this execution is unchanged:
sha256:5fee64a9b759802714d9c5cb541e61e62e2e04992462b3025dd634323b147627,
running, restart count 1. No new IoT image/tag exists from this task. Old runtime environment
must be recoverable via the approved protected configuration source, not a secret-bearing
docker inspect dump. Image rollback uses the earlier tag/up sequence only after restoring
the old effective environment; retain uploads persistence. It restores legacy behavior,
does not undo DB mutations or recover dropped telemetry automatically.

### Remaining limits and operator-only gates

- READY_FOR_BUILD_APPROVAL means reviewed source/test/Compose preparation, NOT a built image
  or deployed 401 fix. Docker build/reproducibility itself remains untested by instruction;
  Dockerfile still uses npm install, not a dependency-policy change in this task.
- No real camera request, decoded physical JPEG, deployed LAN/mDNS/UDP broadcast, Farmer login
  E2E or real broker shutdown was tested. These are distinct runtime/hardware acceptance items.
- Existing public /uploads/stored-image privacy, capture file-vs-DB failure atomicity and
  multi-replica scheduler leadership are pre-existing broader limitations, not claimed solved.
- Graceful drain is bounded, not lossless delivery: at 75s a stuck dependency causes exit 1;
  default MQTT/broker delivery semantics can still lose/retry traffic during downtime.
- Real camera farm provisioning, protected firmware-key binding and final stopped-container
  uploads inventory await owner action/approval. No production DB fingerprint claim is added;
  production databases were not written or used as a fixture.

Separate approvals still required: guarded real DB provisioning (zone=null); protected binding
installation/key match; build of the reviewed IoT context and rollback tag preparation; optional
uploads backup/volume population; IoT-only stop/recreate and potential rollback; then physical
registration/heartbeat/capture verification. Selecting the farm is not approval for any of these.

No business DB write, production environment change, IoT build/restart/recreate/deployment,
physical camera request, commit, push or deletion of existing dirty work occurred.

## Physical ESP32-CAM connectivity follow-up - 2026-10-18

Verdict: PARTIALLY_VERIFIED. Source, firmware compile and automated tests are done; live
registration, heartbeat, snapshot and diagnosis with the physical camera are NOT verified.

### Device log interpretation (evidence from installed library sources)

- `HTTP Code: -1`: ESP32 Arduino core 3.3.12 `HTTPClient.h:47` defines `HTTPC_ERROR_CONNECTION_REFUSED (-1)`;
  `HTTPClient.cpp:635/752` return it whenever `connect()` fails (TCP refused or connect timeout).
  No HTTP exchange took place, so the endpoint contract was never reached.
- `RC=-2`: PubSubClient 2.8 `PubSubClient.h:47` `MQTT_CONNECT_FAILED`; set at `PubSubClient.cpp:280`
  when the underlying TCP `_client->connect()` fails. Not a broker auth result (those are rc 4/5).
- Both are TCP-level failures from the camera to 192.168.1.8 on two independent ports.

### Host/runtime evidence (2026-10-18 ~10:20-11:00 local)

- Host Wi-Fi IPv4 is still 192.168.1.8/24 (`Get-NetIPAddress`).
- Docker Desktop restarted at 10:01; backend state stayed `"docker":"starting"`; `docker version` and
  `docker ps` do not answer; engine log shows container health-check execs failing with
  `DeadlineExceeded`. `netstat` shows NO listener on 3001, 1883 or 8080, and
  `Test-NetConnection 192.168.1.8 -Port 3001/1883` fails from the host itself.
  => Proven current cause: nothing is published on the LAN address while the Docker engine is hung.
- Windows Firewall: Wi-Fi profile is `Public`, DefaultInboundAction `Block` on all profiles. The only
  enabled inbound allow for published ports is `Docker Desktop Backend` (com.docker.backend.exe,
  TCP/UDP any, Public), so once the engine is healthy the published ports should be admitted; this
  still needs a LAN-client test (not possible from this host alone).
- Not provable from here: whether the device log was produced while Docker was down, or whether the
  router applies AP/client isolation. Both are covered by the operator checklist below.
- EMQX 5.8.4 (compose `emqx`): ports 1883/8084/18083 published, default `0.0.0.0:1883` listener, no
  authenticator configured (anonymous). Not changed; documented as a pre-existing hardening gap.
- IoT HTTP server: `app.listen(config.port)` on all interfaces, compose `"3001:3001"`; Gateway
  `application.yml:44` routes `/api/v1/public/**` to IoT. Endpoint served 200 via both 3001 and 8080
  on 2026-10-17 from the image built that day (not re-verifiable today because Docker is hung).

### Architecture decision

HTTP Camera Registry is canonical for camera registration/heartbeat; MQTT is telemetry-only.
The previous sketch published an unauthenticated `esp32/camera/register` message (ISO timestamp,
MAC-derived ID, no key) that no backend component consumed. That path was removed from firmware;
no MQTT registration consumer was added. See docs/CAMERA_REGISTRY_FIRMWARE_INTEGRATION.md.

### Changes in this execution

Firmware (`C:\Users\minhd\OneDrive\Documents\Arduino\CameraWebServer`, not a Git repo; originals
backed up outside the project):
- `CameraWebServer.ino` rewritten (firmware 2.0.0): HTTP registry register/heartbeat state machine,
  validated NTP epoch ms, backoff, Wi-Fi/DHCP handling, sanitized diagnostics, no MQTT.
- `camera_registry_config.h`: non-secret config; `camera_secrets.h` (local) + `camera_secrets.example.h`.
- `app_httpd.cpp`: JPEG SOI/EOI validation with one retry and buffer return in `/capture`.
Backend: `duriancare-iot-service/test/firmware-contract.test.js` (new); this report and
`docs/CAMERA_REGISTRY_FIRMWARE_INTEGRATION.md`. (2026-10-17: `src/public/deviceConfigRoutes.js`,
`src/public/publicRoutes.js` mount, `tests/device-mqtt-config.test.js`, compose/.env MQTT_PUBLIC_*.)
Mobile: local `.env` `EXPO_PUBLIC_API_BASE_URL` 192.168.1.10 -> 192.168.1.8 (stale host IP; file is gitignored).

### Commands and results (2026-10-18)

| Check | Result |
|---|---|
| arduino-cli 1.5.1 `compile --fqbn esp32:esp32:esp32cam` (scratch copy, dummy key) | PASS, 1,123,413 B flash (35%), 72,544 B RAM (22%) |
| Same on real sketch folder with empty key | FAIL by design: static_assert CAMERA_DEVICE_KEY |
| IoT `node --test` | 87 tests: 83 PASS, 0 FAIL, 4 SKIP (superseded below: 89/89 PASS with isolated PG) |
| IoT `npm run check` | PASS |
| Gateway `mvn -o test` | 14 PASS, 0 FAIL, 0 SKIP |
| Web `jest` | 12 suites, 112 PASS |
| Web `tsc` (sources; generated `.next/dev/types/validator.ts` is truncated and was excluded) | PASS |
| Mobile `tsc --noEmit` | PASS |
| Mobile `node --test tests/*.cjs` | 6 PASS |
| Web `next build`, Expo export/doctor | NOT RUN (dev cache in use / network) |

The 4 skipped PG tests could not run: Docker is hung and no native PostgreSQL is installed.

### Remaining operator actions (NOT executed)

1. Restore Docker Desktop (Quit + start, or `wsl --shutdown` then start Docker Desktop) - requires
   approval because it restarts every container. Then verify:
   `docker ps`, `netstat -ano | findstr ":3001 :1883"`, and from a phone on the same Wi-Fi open
   `http://192.168.1.8:3001/actuator/health`.
2. If the phone cannot connect while netstat shows listeners: either set the Wi-Fi network profile to
   Private, or add an inbound allow rule for TCP 3001,1883 scoped to `LocalSubnet` (admin action),
   and check the router for AP/client isolation.
3. Reserve DHCP addresses for the host (192.168.1.8) and camera; the host IP has already drifted (.10 -> .8).
4. Paste the ESP32-CAM-001 binding key into `camera_secrets.h` locally, compile, flash.
5. Serial monitor checklist: `[wifi] OK`, `[time] OK - epoch` >= 1767225600, `[registry] POST ...register`,
   `HTTP 200`, `Đăng ký thành công`, then `Heartbeat OK` every ~60 s; DB `last_seen` advances and
   `online=true`; Farmer `GET /api/v1/camera/snapshot?device_id=ESP32-CAM-001` returns image/jpeg.

### Docker Desktop restart and LAN verification - 2026-10-18 16:00 local (operator-approved)

- `docker desktop restart` did not return in 90 s (state stayed `starting`). Hard restart: stopped
  Docker Desktop processes, `wsl --shutdown` (only distro: docker-desktop), started Docker Desktop.
  Engine 29.1.3 answered after ~75 s; all 18 `restart: unless-stopped` containers came back; infra
  healthy. No compose up/down, no image rebuild, no volume or DB change.
- `netstat`: `0.0.0.0:1883`, `0.0.0.0:3001`, `0.0.0.0:8080` LISTENING, owned by com.docker.backend.
- IoT container image `dd9e05cda889` (= duriancare/iot-service:local built 2026-10-17); rollback tag
  `rollback-20261017` = `5fee64a9b759` still present.
- Via host LAN IP 192.168.1.8: `/actuator/health` 200 UP; `/api/v1/public/devices/mqtt-config` 200 on
  3001 and via Gateway 8080 with `{"mqtt_server":"192.168.1.8","mqtt_port":1883,"mqtt_topic":"esp32/camera/register"}`.
- MQTT CONNECT to `mqtt://192.168.1.8:1883` (mqtt.js, diagnostic client, no publish): CONNACK 0.
  EMQX `broker stats`: 1 live connection (IoT service), 1 subscription.
- Limit: these requests originate on the host itself; Windows Firewall does not filter them like
  inbound LAN traffic. A second LAN device (phone/camera) has NOT been tested. The enabled
  `Docker Desktop Backend` allow rule covers the Public profile, so no firewall block is expected.
- Conclusion for the device log: the `-1`/`-2` TCP failures are fully explained by no listener on
  192.168.1.8 while the Docker engine was hung; with the engine healthy, both ports accept TCP.

### Defect found during restart and fixed in source (deployed 2026-10-18, see below)

After the restart the IoT service started before Kafka/EMQX were ready (Docker restart policy
does not re-apply `depends_on` health conditions). MQTT recovered by itself (mqtt.js reconnect,
14 failed attempts then connected). Kafka did not: `producer.connect()` was attempted once
(`src/main.js`), so `kafkaProducerReady` stays false and telemetry/alert events are silently not
published for the life of the process (state of the container before the deployment below).
Fix: `src/kafkaConnect.js` background retry (5 s doubling to 60 s, unref timer, stopped on
shutdown) wired in `src/main.js`; `test/kafka-connect.test.js`.

### Test totals after this change

- IoT `node --test` without PG: 89 tests, 85 PASS, 0 FAIL, 4 SKIP.
- IoT `node --test` with disposable isolated PG (`duriancare-camera-fixture-20261018`, postgres:16-alpine,
  tmpfs, 127.0.0.1-only, bridge network, 0 mounts; pre-check db=camera_registry_fixture,
  user=fixture_runner, 0 tables): 89 tests, 89 PASS, 0 FAIL, 0 SKIP. Post-check 0 fixture schemas,
  0 tables; container stopped and removed (--rm).

### Deployment commands (rebuild/recreate path EXECUTED 2026-10-18 with operator approval; `docker restart` alternative not used)

```powershell
# Heal Kafka publishing now with the deployed code (restarts only the IoT container):
docker restart duriancare-iot-service
# Or deploy the Kafka retry fix (rebuild + recreate only IoT, keeps duriancare_iot_uploads):
docker image tag duriancare/iot-service:local duriancare/iot-service:rollback-20261018
docker compose -p duriancare -f infrastructure/docker-compose.yml build duriancare-iot-service
docker compose -p duriancare -f infrastructure/docker-compose.yml up -d --no-deps --no-build --pull never --force-recreate duriancare-iot-service
# Rollback:
docker image tag duriancare/iot-service:rollback-20261018 duriancare/iot-service:local
docker compose -p duriancare -f infrastructure/docker-compose.yml up -d --no-deps --no-build --pull never --force-recreate duriancare-iot-service
```
Recreate interrupts MQTT ingestion, camera scheduler and offline watchdog for ~10-20 s;
graceful shutdown drains in-flight work within the 90 s stop_grace_period.

### Kafka retry deployment - 2026-10-18 (operator-approved)

- Rollback tag `duriancare/iot-service:rollback-20261018` = `dd9e05cda889` (image running before this step).
- `docker compose ... build duriancare-iot-service` -> new `duriancare/iot-service:local` = `c77bf888207c`;
  image contains `src/kafkaConnect.js`, `src/public/deviceConfigRoutes.js`, `connectWithRetry` wired in main.js.
- `up -d --no-deps --no-build --pull never --force-recreate duriancare-iot-service`: only the IoT
  container was recreated (2 s); the other 17 containers kept their uptime.
- Verified: log `[kafka] Producer connected`, `DurianCare IoT service listening on port 3001`, no MQTT
  errors; mount `duriancare_iot_uploads -> /app/uploads`; `http://192.168.1.8:3001/actuator/health`
  200 UP; Gateway `http://192.168.1.8:8080/api/v1/public/devices/mqtt-config` 200; EMQX 1 live
  connection (IoT service) with 1 subscription.
- `[udp-discovery] Discovery degraded` is pre-existing under Docker Desktop NAT (see docs/IOT_UDP_DISCOVERY.md);
  HTTP registry is the canonical path and is unaffected.
- Rollback: `docker image tag duriancare/iot-service:rollback-20261018 duriancare/iot-service:local`
  then the same `up -d --no-deps --no-build --pull never --force-recreate duriancare-iot-service`.

### Status at end of 2026-10-18

Verdict: PARTIALLY_VERIFIED. Backend runtime, LAN-IP listeners, MQTT broker session, firmware compile
and all automated tests (IoT 89/89 with isolated PG, Gateway 14/14, Web 112/112, Mobile 6/6 + typecheck)
are verified. NOT verified with the physical camera: HTTP registration/heartbeat, snapshot, AI diagnosis
from a real capture, and reachability from a second LAN device. Remaining operator steps: paste the
binding key into `camera_secrets.h`, compile and flash firmware 2.0.0, follow the serial checklist above.

## End-to-end investigation - 2026-10-18 (second pass, read-only runtime)

Verdict: BLOCKED on physical-device actions. Backend/runtime ready; physical hardware not verified.

### 1. Firmware actually running on the board: pre-2.0.0 MQTT sketch (proven by strings)

- Serial strings `Đang lấy cấu hình MQTT từ API` and `Camera Server sẵn sàng!` exist ONLY in the
  pre-2.0.0 `CameraWebServer.ino` (backup taken before the 2.0.0 rewrite, lines 57 and 204, file mtime
  2026-10-17 18:54 local). Zero matches in the current sketch, in any other `.ino` under
  Documents/Arduino or the workspace, and in every cached `*.ino.bin` under `%LOCALAPPDATA%\arduino\sketches`.
- Firmware 2.0.0 has never been flashed. Its log prefixes (`[boot]`, `[wifi]`, `[time]`, `[registry]`) are absent.
- Evidence lost: the IDE build cache for this sketch path (`388A2814…`) was reset by the arduino-cli
  compile on 2026-10-18 10:33 (different FQBN options), so the 2026-10-17 IDE binary is no longer
  available as a second proof. Board profile used now: `esp32:esp32:esp32cam` (AI Thinker), core 3.3.12,
  ArduinoJson 7.4.3. PubSubClient 2.8 is installed but no longer used by 2.0.0.

### 2. Network path (read-only)

| Path | Result |
|---|---|
| Host Wi-Fi | 192.168.1.8/24, GW 192.168.1.1, SSID 17771, 2.4 GHz ch 9; profile `Public` |
| Host-local -> 192.168.1.8:3001/8080/1883 | PASS (listeners `0.0.0.0`, com.docker.backend) |
| Firewall | all profiles DefaultInbound Block; enabled allow `Docker Desktop Backend` (Public, TCP/UDP any); enabled blocks only Java/Python; LogBlocked=False (no drop evidence possible) |
| L2 host <-> camera | ARP cache 192.168.1.3 = 68-09-47-2D-8E-60 (= DB mac_address), state Stale |
| Other LAN device -> host | UNKNOWN (no second device tested) |
| Camera -> host | UNKNOWN. Device log `-1`/`RC=-2` timing vs the 10:01-16:02 Docker hang is unknown. |
| AP/client isolation | UNKNOWN (router not inspected) |

### 3. Database (BEGIN READ ONLY ... ROLLBACK)

- ESP32-CAM-001: farm_id=farm-khoa-luan-2026, zone_id/tree_id NULL, ip 192.168.1.3:80 http,
  online=false, last_seen=2026-10-17T11:59:36.857Z (21h29m old at db now 2026-10-18T09:28Z),
  updated_at=2026-10-18T09:05:00Z, firmware_version 1.0.0, mac 68:09:47:2D:8E:60.
- `updated_at` 09:05Z is the 5-minute offline watchdog after the Docker restart, NOT a heartbeat.
- `last_seen` can only be written by HTTP register/heartbeat (`deviceService.heartbeat`) or a signed UDP
  announcement (`updateCameraUrl` with announcedAt), both requiring the per-device key. The running
  MQTT sketch cannot produce it. Origin of the 11:59:36Z write: UNKNOWN (no session transcript or
  retained IoT log covers it; the IoT container was recreated since; ip_address is self-reported).
- camera_captures: no rows for ESP32-CAM-001. 7 rows for the different ID `esp32-cam-01` (latest
  2026-08-19) - not related and not used as evidence.

### 4. Capture/snapshot and Farmer path

- Backend capture (`cameraService.downloadImageBuffer`) and snapshot proxy (`cameraRoutes.js` GET /snapshot):
  10/12 s timeouts, non-200 -> 502, timeout -> 504, content-type must be image/jpeg, empty body rejected,
  4 MB cap, strict SOI(FFD8FF)..EOI(FFD9 as last two bytes), cache keyed by device + origin and re-checked
  against the registry before serving, ONLINE status required (`CAMERA_OFFLINE` 503 otherwise).
- Mismatch fixed: firmware 2.0.0 `is_valid_jpeg` accepted EOI anywhere in the last 64 bytes; now it uses
  the same strict rule as the backend (`app_httpd.cpp`). Regression case "JPEG with bytes after EOI"
  added to `test/camera-capture.test.js`.
- Unauthenticated `GET :8080/api/v1/camera/devices`, `:8080/.../snapshot?device_id=ESP32-CAM-001` and
  `:3001/.../snapshot` -> 401. Authenticated Farmer: NOT TESTED (no token requested or used).
- Web `CameraSection.tsx` already guards in-flight snapshots, invalidates by generation and polls 8 s;
  no reproducible frontend defect, no frontend change.

### 5. GitNexus

Index commit eb6edea, status up-to-date (uncommitted files not indexed). `impact --repo DurianCare-IoT-Backend`:
`createPublicRouter` LOW (1: main.js); `shutdown` LOW (2: start, main.js). Firmware is outside the index.

### 6. Tests and builds (this pass)

| Check | Exit | Result |
|---|---|---|
| IoT `npm run check` | 0 | PASS |
| IoT `node --test` (no PG) | 0 | 90 tests: 86 PASS, 0 FAIL, 4 SKIP |
| IoT `node --test` with isolated PG `duriancare-camera-fixture-20261018b` (before EOI case was added) | 0 | 89/89 PASS, 0 SKIP; cleanup 0 schemas/0 tables, container removed |
| Gateway `mvn -o test` | 0 | 14 PASS, 0 FAIL, 0 SKIP |
| Firmware 2.0.0 compile `esp32:esp32:esp32cam` (scratch copy, dummy key) | 0 | 1,123,405 B (35%), RAM 72,544 B (22%) |
| Web `jest` | 0 | 12 suites, 112 PASS |
| Mobile `tsc --noEmit` / `node --test tests/*.cjs` | 0 / 0 | PASS / 6 PASS |
| Web `next build`, Expo native build | - | NOT RUN |

Categories: mock/stub (registry, contract, capture tests), fixture DB (4 PG tests), runtime API
(health, mqtt-config, 401 checks), authenticated Farmer NOT TESTED, Web UI NOT TESTED in browser,
Mobile native NOT TESTED, physical hardware NOT TESTED.

### 7. Approval gates (NOT executed)

1. Flash firmware 2.0.0 (operator pastes the binding key into local `camera_secrets.h`; compile; upload to
   CP210x port, last seen COM3). Opening Serial Monitor may reset the board.
2. Camera -> host test: after flashing, the serial log alone proves it (`[registry] HTTP 200`); a `-1`
   with Docker healthy means firewall/isolation. Minimal firewall rule if needed (admin):
   `New-NetFirewallRule -DisplayName "DurianCare IoT LAN 3001" -Direction Inbound -Protocol TCP -LocalPort 3001 -RemoteAddress LocalSubnet -Profile Public -Action Allow`
   (rollback: `Remove-NetFirewallRule -DisplayName "DurianCare IoT LAN 3001"`). Optional diagnosis:
   `Set-NetFirewallProfile -Name Public -LogBlocked True` (rollback `-LogBlocked False`).
3. Authenticated Farmer test by the operator in the Web UI (owner <farm-owner account>):
   camera list shows ESP32-CAM-001 ONLINE; snapshot returns a new JPEG; Capture-now creates a
   camera_captures row for ESP32-CAM-001.
4. No DB write, ownership change, image rebuild or service restart is required for this step.

## Firmware 2.0.0 flash and hardware test - 2026-10-18 16:45-16:55 local (operator-approved)

Verdict: BLOCKED (hardware). Firmware 2.0.0 is on the board; camera/PSRAM initialization fails before Wi-Fi.

- Key check without printing: `camera_secrets.h` key length 48, equal to the `.env` binding; SHA-256
  prefix 529b73f7 for firmware, `.env` and the running container binding. (First check saw an empty key;
  the operator's save landed seconds later.)
- Port: COM3 = Silicon Labs CP210x (VID 10C4 PID EA60). COM4 (FTDI) is a different device and was not touched.
- Compile real sketch, `esp32:esp32:esp32cam` (defaults = CPUFreq 240, FlashMode qio, FlashFreq 80,
  huge_app; identical to the FQBN the IDE used for this board before): exit 0, 1,123,453 B (35%).
  Binary contains `[registry] POST`, no `MQTT Broker`.
- `arduino-cli upload -p COM3`: exit 0; bootloader, partitions, boot_app0 and app written, every region
  "Hash of data verified", hard reset via RTS.
- Serial capture 09:50:20Z-09:52:50Z (115200, 150 s): 14 boots (1 POWERON_RESET, 13 SW_CPU_RESET by the
  designed 10 s restart), each:
  `E (345) quad_psram: PSRAM ID read error: 0xffffffff, PSRAM chip not found or not supported`
  `[boot] DurianCare ESP32-CAM-001 firmware 2.0.0 — reset reason 1`
  `E (49) camera: Detected camera not supported.` / `Camera probe failed with error 0x106(ESP_ERR_NOT_SUPPORTED)`
  `[camera] Khởi tạo thất bại, lỗi 0x106` -> restart. No brownout message. No `[wifi]`/`[registry]` lines.
  The log contains neither the device key nor the Wi-Fi password.
- Interpretation: PSRAM (SPI) and camera SCCB both return invalid IDs on every boot; build options match the
  previously working board profile; the operator's earlier old-firmware log had no PSRAM error and passed
  camera init. Most likely causes: unstable 3.3 V supply (USB/CP210x power), module not fully seated in the
  ESP32-CAM-MB socket, or camera ribbon not latched. Not provable remotely.
- DB (read-only) after flash: unchanged, online=false, last_seen 2026-10-17T11:59:36.857Z,
  0 camera_captures for ESP32-CAM-001. ARP 192.168.1.3 still Stale.
- Fresh JPEG / snapshot / Farmer flow: BLOCKED (camera never initializes, device never joins Wi-Fi).
- Secret hygiene: the key-bearing scratch build output was deleted after upload. Arduino's own build cache
  (`%LOCALAPPDATA%\arduino\sketches\388A2814...\sketch\`) holds a copy of camera_secrets.h, as with any IDE build.

### Operator checklist to continue

1. Unplug USB for 10 s (true power cycle, not RTS reset). Reseat the ESP32-CAM on the MB board; open and
   re-latch the camera FPC ribbon (contacts toward the board).
2. Power from a short data cable on a direct USB port, or a 5 V >= 2 A supply on the 5V pin.
3. Re-open the serial monitor at 115200. Expected: no `PSRAM ID read error`, no `Camera probe failed`,
   then `[wifi] OK - IP 192.168.1.x`, `[time] OK - epoch >= 1767225600`, `[registry] HTTP 200`,
   `Đăng ký thành công`, `Heartbeat OK` every ~60 s.
4. If the probe still fails after a power cycle, try the pre-2.0.0 sketch (backup) once: if it also fails,
   the fault is hardware; if it works, report it so the init difference can be investigated.
5. After `HTTP 200`: read-only DB check of last_seen/online, then the Farmer Web UI snapshot and capture.

### Re-test after operator power cycle and reseat - 2026-10-18 09:55:15Z-09:57:46Z

- Serial (COM3, 150 s): burst of repeated `rst:0x1 (POWERON_RESET)` lines right after plug-in (module
  restarting during power-up), then 14 boots of firmware 2.0.0, each with
  `PSRAM ID read error: 0xffffffff` and `Camera probe failed with error 0x106`; 0 `[wifi] OK`, 0 `HTTP 200`,
  0 `Heartbeat OK`.
- DB read-only: unchanged (online=false, last_seen 2026-10-17T11:59:36.857Z, 0 captures for ESP32-CAM-001).
- Status: registration, heartbeat, last_seen, fresh JPEG and Farmer flow remain BLOCKED by hardware init.
- Side effect found: Arduino IDE and arduino-cli share the build cache folder per sketch path
  (`%LOCALAPPDATA%\arduino\sketches\388A2814...`). The arduino-cli compile with different board options
  reset that folder, and a concurrent IDE build then failed with
  `fatal error: opening dependency file ...\libraries\HTTPClient\HTTPClient.cpp.d: No such file or directory`.
  Source is unaffected; re-running Verify in the IDE (or closing the IDE and removing only that cache folder)
  regenerates it. Future CLI builds should use a separate `--build-path`.
- Next discriminating test (needs approval): flash the pre-2.0.0 backup sketch once with the same toolchain.
  Same PSRAM/probe errors -> hardware/power fault; clean boot -> investigate firmware/build difference.

### A/B test with the pre-2.0.0 sketch - 2026-10-18 10:06Z (operator-approved, flashed once)

- Pre-2.0.0 backup sketch compiled in a scratch copy with a separate `--build-path` (IDE cache untouched):
  exit 0, 1,231,189 B (39%), binary contains the MQTT strings. Upload to COM3: exit 0, all regions
  hash-verified, RTS reset.
- Serial 10:06:16Z-10:07:32Z: 1 POWERON boot, `E (374) quad_psram: PSRAM ID read error: 0xffffffff`,
  `camera: Detected camera not supported`, `Camera probe failed with error 0x106`,
  `Khởi tạo Camera thất bại với lỗi 0x106`; no Wi-Fi line (the old sketch returns from setup on failure).
- Conclusion: the same code that previously initialized the camera and reached Wi-Fi now fails identically
  to firmware 2.0.0 => HARDWARE/POWER FAULT (PSRAM and OV sensor both unreadable), not a firmware defect.
- Current board state: running the pre-2.0.0 sketch (idle after init failure). Firmware 2.0.0 must be
  re-flashed after the hardware is fixed (operator approval).
- Hardware checklist: 5 V >= 2 A supply on 5V/GND (not 3.3 V); different USB cable/port or powered hub;
  inspect the camera FPC connector latch and ribbon for damage; try a known-good OV2640 module; check
  the ESP32-CAM-MB socket pins; if PSRAM still reads 0xffffffff on a clean 5 V supply, replace the
  ESP32-CAM module.

### Re-flash firmware 2.0.0 after operator-reported hardware/power fix - 2026-10-18 10:17Z

- Key re-check (not printed): length 48, equals `.env` binding; SHA-256 prefix 529b73f7 = running container.
- Compile with separate `--build-path` (scratch; IDE cache untouched): exit 0, 1,123,453 B (35%);
  `[registry] POST` present, no MQTT. Upload COM3: exit 0, all regions hash-verified. Key-bearing scratch
  build output deleted afterwards.
- Serial 10:17:33Z-10:20:03Z: 14 boots, 14x `PSRAM ID read error: 0xffffffff`, 14x
  `Camera probe failed with error 0x106`; 0 `[wifi] OK`, 0 `[time] OK`, 0 `HTTP 200`, 0 `Heartbeat OK`.
- DB read-only at 10:20:17Z: online=false, last_seen 2026-10-17T11:59:36.857Z (unchanged), 0 captures.
- Result: hardware fault NOT resolved by the fix attempted. Registration, heartbeat, last_seen, fresh
  JPEG and Farmer flow: BLOCKED. Board now runs firmware 2.0.0 (10 s restart loop while init fails).
- Since both PSRAM and sensor fail on every boot with both firmwares, the remaining candidates are the
  ESP32-CAM module itself (PSRAM/SPI or camera bus damage) or a supply that still sags under load.
  Verify with a known-good ESP32-CAM module and OV2640, powered from 5 V >= 2 A.

## Camera init investigation (software first) - 2026-10-18 10:25-10:45Z

Verdict: PARTIALLY VERIFIED. Backend/runtime verified; camera init still failing; root cause not yet
distinguishable from source alone. A read-only on-device diagnostic (firmware 2.0.1) is compiled, NOT flashed.

### Toolchain and configuration facts

- Core esp32:esp32 3.3.12 (installed 2026-10-15 12:44, the only installed version; the operator's
  2026-10-17 old-sketch run that passed camera init was therefore built with this same core).
  Board `esp32:esp32:esp32cam` defaults: CPU 240, QIO, flash 80 MHz, huge_app; defines `-DBOARD_HAS_PSRAM`
  plus PSRAM cache workaround flags. esp32-libs sdkconfig: `CONFIG_SPIRAM=y` QUAD 80 MHz, all 15 sensor
  drivers enabled (incl. OV2640/OV3660/OV5640), SCCB new I2C driver on port 1 at 100 kHz,
  `CONFIG_LOG_MAXIMUM_LEVEL=1` (driver debug output compiled out).
- `camera_pins.h`, `camera_index.h`, `partitions.csv` byte-identical to the core 3.3.12 CameraWebServer
  example; `board_config.h` differs only by selecting `CAMERA_MODEL_AI_THINKER` (PWDN 32, RESET -1,
  XCLK 0, SIOD 26, SIOC 27, Y2-Y9 5/18/19/21/36/39/34/35, VSYNC 25, HREF 23, PCLK 22) = correct AI-Thinker map.
- camera_config_t fields set match the example. Not set (stack garbage in both the example and
  firmware 2.0.0): `conv_mode`, `sccb_i2c_port`, `jpeg_buffer_size`.

### Meaning of the errors (esp32-camera probe flow)

- `0x105 ESP_ERR_NOT_FOUND` = no SCCB device ACKs. We get `0x106 ESP_ERR_NOT_SUPPORTED` +
  "Detected camera not supported" = a device ACKed on a known sensor address, but no sensor detect()
  matched the ID registers it read. Typical when the sensor answers SCCB but returns invalid IDs
  (no/unstable XCLK, sensor held in power-down or brown-out, wrong/clone sensor) - not a missing driver.
- `PSRAM ID read error: 0xffffffff` is printed by IDF startup before `setup()`; firmware handles the
  no-PSRAM case (SVGA in DRAM, 1 frame buffer). It is a separate symptom, not the cause of 0x106.
- Reset reason 3 = SW reset from the firmware's deliberate 10 s `ESP.restart()` after init failure;
  1 = power-on.

### Hypotheses tested

| Hypothesis | Test | Result |
|---|---|---|
| Firmware 2.0.0 logic/init order | A/B with the pre-2.0.0 sketch (same pins/config) | Same failure -> not 2.0.0-specific |
| Board profile/build flags differ from the working build | compare FQBN defaults, sdkconfig, example files | identical -> refuted |
| Serial DTR/RTS (arduino-cli default on/on) pulls IO0 = XCLK low | capture with `-c dtr=off -c rts=off` (= IDE profile) | 14/14 boots still fail -> refuted |
| Uninitialized camera_config_t fields | source review | latent defect, cannot affect probe (explicit SCCB pins; buffers after probe); fixed anyway |
| Sensor/bus/power state | needs the on-device diagnostic below | OPEN |

### Changes (firmware folder; not a Git repo)

- `CameraWebServer.ino`: `camera_config_t config = {};` (zero-init); call `diagnoseCameraBus()` before the
  10 s restart when init fails; forward declaration.
- `camera_diag.cpp` (new): prints chip/flash/PSRAM facts, power-cycles the sensor via PWDN, drives XCLK
  20 MHz on GPIO0, scans SCCB 0x08-0x77 on GPIO26/27 and reads OV2640 (0x30: PIDH/PIDL/MIDH/MIDL,
  expect 0x26/0x41|0x42/0x7F/0xA2) and OV3660/OV5640 (0x3C: 0x300A/0x300B) IDs. Read-only except the
  OV2640 bank-select register. Prints no secrets.
- `camera_registry_config.h`: FIRMWARE_VERSION 2.0.1.
- Compile (separate `--build-path`, IDE cache untouched): exit 0, 1,133,561 B (36%), RAM 73,912 B (22%),
  no sketch warnings. NOT flashed (needs approval).
- Rollback: restore `camera_config_t config;` line, remove the `diagnoseCameraBus` declaration/call and
  `camera_diag.cpp`, set version 2.0.0; or re-copy the 2.0.0 files.

### HTTP registration / backend re-verification (read-only)

- Running IoT image `c77bf888207c`; 8 camera/registry/startup sources hash-identical to the working tree.
- Health 200; Gateway mqtt-config 200; heartbeat without key -> 401 (no write); uploads volume mounted.
- Firmware contract (source review, unchanged): POST `/api/v1/public/cameras/{register,heartbeat}`,
  body cameraId/ipAddress/port/protocol/announcedAt(epoch ms after NTP validity)/firmwareVersion,
  header X-Camera-Key never logged, 5 s connect / 7 s timeout, no redirects, 400/401/403/404/409/503/transport
  handled distinctly, backoff 5-120 s with jitter. Pinned by test/firmware-contract.test.js.
- IoT `node --test`: 90 tests, 86 PASS, 0 FAIL, 4 SKIP (PG fixture tests; last isolated-PG run 89/89).

### Next checklist

1. Approve flashing 2.0.1 to COM3; capture serial with DTR/RTS off; read the `[diag]` lines.
2. Interpretation: `SCCB ACK at: none` -> bus/ribbon; `0x30` with PIDH=0x26 -> OV2640 healthy, driver/clock
   timing issue; `0x30` with 0xFF/0x00/random IDs -> sensor not clocked/powered or damaged; other address ->
   different sensor. `PSRAM found=0` confirms missing/failed PSRAM independently.
3. Then decide: sensor/ribbon replacement, XCLK 10 MHz test, or driver follow-up.

### On-device diagnostic result - firmware 2.0.1, 2026-10-18 10:39:39Z-10:40:25Z (operator-approved)

- Flash COM3: exit 0, all regions hash-verified. Serial captured with DTR/RTS off.
- Every boot (4 in 45 s):
  `[diag] chip ESP32-D0WD-V3 rev 301, 2 cores, flash 4194304 B @ 80000000 Hz, PSRAM found=0 size=0 B`
  `[diag] PWDN=GPIO32 low, XCLK GPIO0 20 MHz on, SCCB SDA=GPIO26 SCL=GPIO27`
  `[diag] SCCB ACK at: none`
  `[diag] 0x30 OV2640: bank=NACK PIDH=NACK PIDL=NACK MIDH=NACK MIDL=NACK`
  `[diag] 0x3C OV3660/OV5640: PIDH=NACK PIDL=NACK`
- Interpretation (verified): with the sensor released from power-down and XCLK running, NO device acknowledges
  any SCCB address. The driver's 0x106 here means none of its sensor detectors found a sensor (driver 2.x
  reports NOT_SUPPORTED, not NOT_FOUND, when every per-sensor detect fails). Independently, the PSRAM chip
  does not answer (`found=0`). Pins, board profile, build flags, init order, serial control lines and
  firmware version have all been ruled out above.
- Root cause class: HARDWARE on the camera path - sensor not electrically present on SCCB (FPC not making
  contact / damaged ribbon or connector, dead sensor, or missing sensor supply rails), plus a non-responding
  PSRAM chip on the same module. Which part cannot be distinguished without swapping parts or measuring.
- Smallest discriminating hardware tests: (1) move this OV2640 to a known-good ESP32-CAM, or a known-good
  OV2640 to this board, then re-run 2.0.1; (2) with a multimeter, check 3.3 V at the module and the camera
  rails (about 2.8 V / 1.5 V / 1.2 V at the board's camera LDOs) while running.
- Board now runs firmware 2.0.1 (diagnostic only on init failure, 10 s restart). Key-bearing scratch build
  output deleted after upload. Registration, heartbeat, last_seen, JPEG and Farmer flow remain BLOCKED.
- Final status: PARTIALLY VERIFIED (backend, contract, runtime, firmware build verified; camera hardware not).

## Hardware fixed; NTP sync defect found in firmware 2.0.0/2.0.1 - 2026-10-18 ~11:30Z

### Operator serial evidence (firmware 2.0.1, after hardware fix)

`[boot] ... firmware 2.0.1 — reset reason 1` (power-on), no PSRAM/probe errors, `[wifi] OK — IP 192.168.1.3,
gateway 192.168.1.1, RSSI -78 dBm`, `[camera] Web server: http://192.168.1.3:80/capture`, then
`[time] LỖI: chưa có thời gian hợp lệ` twice and `[registry] Chưa có thời gian NTP hợp lệ — thử lại sau 5s`.
=> Camera initialization now PASSES on the real device (hardware fault resolved). Registration is blocked by time sync.
Note: the pre-2.0.0 sketch printed "Đã đồng bộ thời gian." unconditionally, so it never proved NTP worked.

### Network checks from the host on the same Wi-Fi (read-only)

- DNS via the DHCP servers (123.23.23.23) and via 192.168.1.1: pool.ntp.org and time.google.com resolve.
- NTP UDP/123 (`w32tm /stripchart`): pool.ntp.org, time.google.com, time.windows.com all answer (offset ~-0.07 s).
- Backend `Date` header equals host UTC to the second.
=> The network does not block NTP; the failure is in the device-side sync logic.

### Root cause (source-verified)

- Core 3.3.12 `cores/esp32/esp32-hal-time.c` `configTime()` performs `sntp_stop()` + `sntp_init()` on every call.
- esp32-libs sdkconfig: `CONFIG_LWIP_SNTP_STARTUP_DELAY=y` (random <= 5 s before first request); lwIP SNTP
  retransmits only after its 15 s receive timeout.
- Firmware 2.0.0/2.0.1 `syncTime()` called `configTime()` on every attempt with 15 s, then 10 s windows, so each
  retry reset the SNTP client before its own retransmission; one lost UDP exchange (RSSI -78 dBm) => never syncs.

### Fix (firmware 2.0.2; folder not a Git repo)

- `CameraWebServer.ino`: replaced blocking `syncTime()` with `startNtp()` (once per Wi-Fi connection, 3 servers)
  + non-blocking `ensureTime()` polling; after `NTP_WAIT_MS` (30 s) without a valid clock, or once after a
  400/409 from the registry, `syncTimeFromBackend()` sets UTC from the IoT service's HTTP `Date` header
  (GET `/actuator/health`, same timeouts, redirects disabled; parsed with strptime, validated >= 2026-01-01).
  SNTP keeps running and remains the fallback if the backend refresh fails. Wi-Fi reconnect restarts SNTP once.
  Never uses uptime; announcedAt still epoch ms from `gettimeofday`.
- `camera_registry_config.h`: `NTP_SERVER_3`, `NTP_WAIT_MS`, `TIME_SOURCE_PATH`; FIRMWARE_VERSION 2.0.2.
- Security note: the Date fallback trusts the same plaintext LAN backend already used for registration; the
  server still enforces +/-30 s freshness, key and ownership. No backend change.
- Backend test: `test/firmware-contract.test.js` pins the RFC 7231 Date format parsed by the firmware.

### Results

| Check | Result |
|---|---|
| Firmware 2.0.2 compile `esp32:esp32:esp32cam`, separate `--build-path` | exit 0, 1,135,257 B (36%), RAM 73,920 B (22%), no sketch warnings |
| IoT `node --test` | 91 tests: 87 PASS, 0 FAIL, 4 SKIP (isolated-PG tests) |
| Flash 2.0.2 / registration / heartbeat / JPEG | NOT YET (needs approval) |

Rollback: re-flash 2.0.1 (restore the `syncTime()` block and remove NTP_SERVER_3/NTP_WAIT_MS/TIME_SOURCE_PATH).

### Expected serial after flashing 2.0.2

`[time] SNTP bắt đầu (...)` then either `[time] OK — epoch N (SNTP)` or, after 30 s,
`[time] OK — epoch N (từ HTTP Date của Backend)`; then `[registry] POST .../register`, `HTTP 200`,
`Đăng ký thành công`, `Heartbeat OK` about every 60 s.

## CORRECTION AND REAL-DEVICE VERIFICATION - 2026-10-18 11:50-11:58Z

### The COM3 board is NOT ESP32-CAM-001 (proven)

- `esptool --port COM3 read_mac`: ESP32, MAC 8c:94:df:69:1c:6c (seen at 192.168.1.4 in the morning ARP table).
- ESP32-CAM-001 is MAC 68:09:47:2D:8E:60 (= DB mac_address), at 192.168.1.3 (ping reply 6 ms, ARP Reachable).
- Telemetry (read-only): sensor device `esp32-01` reported 307 readings in 24 h; last reading
  2026-10-18T09:49:34.861Z = the time of the first firmware upload to COM3 in this session.
  `sketch_jul25a` (deviceId "esp32-01", DHT sensor) was last built/flashed 2026-10-15 12:56 for
  `esp32:esp32:esp32` PSRAM=disabled (IDE `*_flashed.bin` markers).
- Conclusion: COM3 is the sensor node esp32-01. All uploads in this session (2.0.0 at ~09:49Z, the pre-2.0.0
  A/B sketch, 2.0.1, 2.0.2) overwrote its sensor firmware. The earlier "PSRAM 0xffffffff / camera 0x106 /
  SCCB ACK none" findings describe this camera-less, PSRAM-less dev board - NOT a camera hardware fault.
  The sections above that conclude "hardware fault" are superseded by this correction.
- Process error: the board identity (MAC) was not checked against the DB before the first flash.
  Impact: esp32-01 telemetry down since 09:49:34Z. No backend data was written or deleted.
- Restore prepared (NOT executed, needs approval): original binaries copied from the IDE cache
  (`sketch_jul25a.ino.bin` 1,039,552 B, sha256 prefix 71e5f649ac86, plus bootloader/partitions), FQBN
  `esp32:esp32:esp32:UploadSpeed=921600,CPUFreq=240,FlashFreq=80,FlashMode=qio,FlashSize=4M,PartitionScheme=default,DebugLevel=none,PSRAM=disabled,...`.

### ESP32-CAM-001 real-device evidence (camera at 192.168.1.3, operator-flashed firmware 2.0.1)

| Requirement | Evidence | Result |
|---|---|---|
| Camera init | operator serial: no probe error, `[camera] Web server: http://192.168.1.3:80/capture` | PASS |
| Wi-Fi | `[wifi] OK — IP 192.168.1.3, gateway 192.168.1.1, RSSI -78 dBm` | PASS |
| Registration | DB online=true, last_seen 2026-10-18T11:53:07.740Z, ip 192.168.1.3 (only writable with the per-device key) | PASS |
| Heartbeat | last_seen 11:53:07.740 -> 11:54:08.162 -> 11:55:08.271Z (~60 s), online=true | PASS |
| Fresh JPEG | GET http://192.168.1.3/capture at 11:56:17Z: HTTP 200, image/jpeg, 91,456 B, 1600x1200, SOI FFD8FF, EOI FFD9 last bytes, real scene (ceiling/wall), X-Timestamp 1374.99 s since boot | PASS |
| Farmer snapshot via Gateway | requires an authenticated Farmer session | NOT TESTED |
| capture persisted in camera_captures | no Capture-now performed | NOT TESTED |

- The camera still runs 2.0.1 (NTP retry defect fixed in 2.0.2); it eventually synced and registered.
  Upgrading it to 2.0.2 requires the camera's own USB/serial port (identify by MAC before flashing).
- Backend gap (not fixed): register/heartbeat do not persist `firmwareVersion`; DB still shows 1.0.0.

Status: CAMERA ONLINE — VERIFIED for device init, Wi-Fi, registration, heartbeat and direct JPEG.
Farmer-authorized Gateway snapshot and capture persistence remain to be tested by the operator.

### esp32-01 sensor firmware restored - 2026-10-18 12:33Z (operator-approved)

- Pre-flash identity check: `esptool --port COM3 read-mac` = 8c:94:df:69:1c:6c (esp32-01 board); also printed by the upload.
- Flashed the original IDE-cache binaries of `sketch_jul25a` (app sha256 prefix 71e5f649ac86, 1,039,552 B) with the
  original FQBN `esp32:esp32:esp32:...,PSRAM=disabled,...`: exit 0, partitions/boot_app0/app hash-verified, RTS reset 12:33:40Z.
- Read-only verification: esp32-01 telemetry resumed - 2 new rows since the flash, latest 2026-10-18T12:34:23.109Z.
  Outage window: 09:49:34Z - 12:34:23Z (no telemetry stored for esp32-01 in that interval; nothing was deleted).
- ESP32-CAM-001 unaffected: last_seen 12:33:47.670Z, online=true.
- Rule going forward: before any flash, confirm the target MAC (`esptool read-mac`) against the expected device
  (ESP32-CAM-001 = 68:09:47:2D:8E:60; esp32-01 = 8c:94:df:69:1c:6c). Firmware 2.0.2 for the camera must be flashed
  through the camera's own serial adapter, not COM3.

## Intermittent heartbeat read timeout (-11) - 2026-10-18 12:45-13:00Z

Status: root cause established; firmware fix 2.0.3 compiled and tested, NOT flashed. Not yet verified on the device.

### What -11 means (installed source)

ESP32 core 3.3.12 `HTTPClient.h:57` `HTTPC_ERROR_READ_TIMEOUT`; returned at `HTTPClient.cpp:1363-1364` when the TCP
connection is open and the request has been written, but no response-header byte arrives within `_tcpTimeout`
(firmware 2.0.0-2.0.2: `HTTP_TIMEOUT_MS` 7000). Not a connect failure (that is -1).

### Evidence

- Server path: `/api/v1/public/cameras/heartbeat` -> bindings check -> `getCamera` SELECT (0.29 ms, EXPLAIN ANALYZE,
  read-only) -> conditional UPDATE -> JSON. No blocking work; pool max 10. Host -> 192.168.1.8:3001 round trip
  3-119 ms (avg 10). ESP32-style requests (fresh connection, `Connection: close`, headers and body written
  separately, 401 path, no DB write): TTFB 22-35 ms via 192.168.1.8, 23-39 ms via 127.0.0.1 (30 each).
  Compose `3001:3001` -> com.docker.backend `0.0.0.0:3001`; the running image matches the working tree.
- Link to camera: ping 192.168.1.3 x20 -> 5% loss, 5-54 ms; device reported RSSI -78 dBm.
- lwIP config: `CONFIG_LWIP_TCP_RTO_TIME=3000` (initial RTO 3 s, doubling), `SYNMAXRTX=6`, `MAXRTX=12`.
- `last_seen` sampled every 2 s (read-only) 12:45:19-12:55:04Z; intervals (s):
  `60.5 60.3 60.2 60.1 13.1 64.9 12.3 17.3 60.6 13.0 71.6 17.5 27.8 60.7 12.2`.
  ~13 s = 7 s read timeout + ~5 s backoff after a heartbeat the server HAD committed (last_seen moved, camera
  never saw the 200); 17.3/17.5/27.8 = consecutive lost responses with backoff 10/20 s; 71.6/64.9 = a request
  not committed/delayed. About 8 of ~15 exchanges in 10 min hit the timeout; heartbeat was never lost for longer
  than ~72 s and last_seen kept advancing (online stayed true).
- IoT logs: no snapshot proxy or manual capture activity in the window (scheduled captures only at 07/12/17 UTC).

### Root cause

The server answers in milliseconds and commits the heartbeat; the response (or a request segment) is delayed
on the weak Wi-Fi link beyond the firmware's 7 s read timeout, which is shorter than lwIP's own retransmission
schedule (3 s, then +6 s). Contributing firmware behaviour in 2.0.0-2.0.2:
- a heartbeat transport error set `s_registered=false` and fell back to `/register`;
- success was logged on status 200 without validating the body;
- generic firewall hints were printed for every transport error.

### Fix (firmware 2.0.3; folder not a Git repo)

- `camera_registry_config.h`: `HTTP_CONNECT_TIMEOUT_MS` 5000 -> 10000 (2 SYN retransmits), `HTTP_TIMEOUT_MS`
  7000 -> 15000 (2 data retransmits ~9 s + margin; << 60 s heartbeat interval); `REGISTRY_MAX_RESPONSE_BYTES` 4096;
  `REGISTRY_INVALID_RESPONSE` (-200); FIRMWARE_VERSION 2.0.3.
- `CameraWebServer.ino` `postRegistry()`: measures elapsed ms; for 200 reads at most 4096 B and requires JSON with
  `status` in {ok, registered} and `device.cameraId == CAMERA_ID`, else returns -200 (not success); specific
  messages for -11 and -1. `handleRegistryResult()`: a transport error during heartbeat keeps the registration and
  retries the heartbeat with the existing bounded jittered backoff (5-120 s); no reboot path. Each retry carries a
  newer announcedAt; heartbeat only updates last_seen/ip/port (no capture), so retrying after a lost response is
  side-effect free, and a late duplicate is rejected by the server's ordering guard (409).
- No backend source change. Backend tests added to `test/firmware-contract.test.js`: lost-response timeline
  (commit -> retry advances -> late duplicate 409, writes=2) and handler latency < 1 s; response shape/size pinned.

### Results

| Check | Result |
|---|---|
| Firmware 2.0.3 compile `esp32:esp32:esp32cam`, separate `--build-path` | exit 0, 1,139,917 B (36%), RAM 73,920 B (22%), no sketch warnings; key-bearing output deleted |
| IoT `node --test` (no PG) | 93 tests: 89 PASS, 0 FAIL, 4 SKIP |
| IoT `node --test` with disposable isolated PG `duriancare-camera-fixture-20261018c` | 93/93 PASS, 0 SKIP; cleanup 0 schemas/0 tables, container removed |
| Client retry logic host-side tests | not possible: no host C++ compiler installed |

### Manual verification still required (approval needed to flash)

1. Identify the camera's serial port by MAC (`esptool --port COMx read-mac` must print 68:09:47:2D:8E:60; COM3 is
   the sensor esp32-01), then flash 2.0.3 there.
2. Serial: `[registry] HTTP 200 (N ms)` then `Heartbeat OK.`; on a slow exchange expect either a late 200 or
   `(-11: read Timeout) sau ~15000ms` followed by a heartbeat retry, never a fall-back to register or reboot.
3. Re-run the 2 s last_seen sampling for 10 min: expect ~60 s intervals; ~13 s pairs should disappear.
4. Improve RSSI (-78 dBm): antenna/placement or the IPEX external antenna; this reduces the underlying loss.

### Firmware 2.0.3 flashed to ESP32-CAM-001 and re-measured - 2026-10-18 13:03-13:14Z (operator-approved)

- Port identification: COM4 (FTDI) was held by the Arduino IDE `serial-monitor` helper (PID 24632, started 18:33 local
  by the IDE daemon) after the operator closed the monitor window; that helper was stopped. `esptool --port COM4
  read-mac` = 68:09:47:2d:8e:60 (ESP32-CAM-001). COM3 was not touched.
- Flash 2.0.3 to COM4 at 13:03:08Z: MAC repeated in the upload log; bootloader/partitions/boot_app0/app hash-verified;
  RTS reset. Key-bearing build output deleted.
- last_seen sampled every 2 s (read-only) 13:03:49-13:13:45Z; intervals (s):
  `62.7 20.8 63.1 60.4 63.3 23.3 65.3 61.0 23.7 25.3 60.6 20.7 26.9 69.9`; online=true throughout.
- Interpretation: ~20-21 s = new 15 s read timeout + ~5 s backoff after a heartbeat the server committed; 23-27 s =
  same with backoff 10 s. About 6 of 15 exchanges still received no response within 15 s. Heartbeats were never
  missing longer than ~70 s, and registration was not dropped.
- Ruled out (evidence): server latency (TTFB 22-39 ms for ESP32-style requests), DB time (0.29 ms), Docker port
  forwarding (same results via 192.168.1.8 and 127.0.0.1), Wi-Fi modem sleep (`WiFi.mode(WIFI_STA)` starts STA,
  so `setSleep(false)` calls `esp_wifi_set_ps(WIFI_PS_NONE)` immediately; core `WiFiGeneric.cpp:784-789`),
  duplicate IP (ARP for .3 stable at 68-09-47-2D-8E-60 over 12 samples), TCP retransmission timing alone
  (would not lose every retransmit for 15 s).
- Serial evidence for this window: NOT AVAILABLE - the IDE respawned its serial-monitor (PID 25620) and holds COM4
  (`Serial port busy`); the 10-minute arduino-cli capture recorded 0 bytes.
- Status: NOT FIXED. 2.0.3 improves handling (validated success, no registration drop, bounded retry) but the
  underlying downlink loss persists and its cause is not yet identified.

### Next evidence needed

1. Operator: paste the IDE Serial Monitor lines for COM4 over ~10 min, specifically `[registry] HTTP 200 (N ms)`,
   `Lỗi kết nối TCP/HTTP (code: ...) sau N ms` and `Heartbeat chưa xác nhận`, to get the error codes and elapsed ms.
2. With approval (admin, diagnostic only): host packet capture restricted to TCP 3001 for 10 min, e.g.
   `pktmon filter add -p 3001` / `pktmon start --capture` / `pktmon stop` / `pktmon etl2pcap`, to see whether the
   200 leaves the host and how often it is retransmitted to 192.168.1.3. Remove filters afterwards (`pktmon filter remove`).
3. Improve RSSI (-78 dBm): move the camera/AP or use the external IPEX antenna, then repeat the last_seen sampling.

## Dashboard real-time correctness + heartbeat follow-up - 2026-10-18 13:20-13:40Z

### A. Dashboard (Web repo `DurianCare-IoT-Web-Client`; the path `DurianCare-IoT-Web` does not exist)

Root causes (confirmed from source and read-only DB):
- Date: literal `06/06/2026` in `src/app/dashboard/client/page.tsx:106`; greeting "Chào buổi sáng" also literal.
- `12/12 — Kết nối ổn định`: mock `dashboardStats.owner` in `src/constants/durianMockData.js:141`, never from the backend.
- UNKNOWN: backend `connectivityForDevice` (`duriancare-iot-service/src/alert-engine.js:19-62`) returns UNKNOWN when
  `iot_devices.last_seen_at` is NULL. DB: esp32-01 is ACTIVE with `last_seen_at` NULL while sending 59 telemetry rows
  per 10 min (latest 13:26:04Z). The sensor posts via HTTP `POST /api/v1/sensors/data`
  (`sketch_jul25a.ino:20`); that handler (`src/main.js`) only inserted telemetry, while the MQTT path
  (`handleTelemetry`) also stamps `last_seen_at`. Consequence: UNKNOWN forever, offline watchdog never evaluates
  the device, threshold alerts never run for HTTP telemetry.

Fixes:
- Backend: new `src/telemetryRegistry.js` `recordDeviceSeen()` (the same UPDATE SQL as before: case-insensitive
  device_uid, never DELETED, last_seen_at = server receive time); used by `handleTelemetry` (behaviour unchanged)
  and by `POST /api/v1/sensors/data` (new). Threshold-alert evaluation deliberately NOT added to the HTTP path
  (would start new notifications; follow-up). GitNexus impact `handleTelemetry`: LOW (start, main.js).
  Test `test/telemetry-registry.test.js` (UNKNOWN -> ONLINE after stamp; DELETED/unregistered untouched;
  STALE/OFFLINE thresholds). NOT deployed: the running IoT container still has the old handler, so the
  dashboard will keep showing "Chưa có tín hiệu" for esp32-01 until an approved rebuild/recreate.
- Web: `src/lib/time/vietnamClock.ts` (Asia/Ho_Chi_Minh date dd/MM/yyyy, greeting by local hour, minute tick);
  `src/lib/iot/summary.ts` (counts by backend `connectivityStatus`; only ONLINE is active; denominator = devices
  returned by the farm-scoped `/api/iot/devices`; honest error/signed-out/empty states; labels match SensorCharts);
  `src/components/dashboard/OwnerDashboardLive.tsx` (client: initial values from SSR -> no hydration mismatch,
  minute-boundary clock, 30 s polling via the existing `/api/backend` proxy with in-flight guard, stale-response
  sequence check, pause when hidden, cleanup on unmount); `page.tsx` uses it. Test `src/__tests__/dashboard-live.test.ts`.
- Still mock (not changed, out of scope, flagged): farm name "TRANG TRẠI MINH PHÁT", "Tổng cây quản lý 440 / 4 phân
  khu", "Độ ẩm đất trung bình 79%", "Cảnh báo cần xử lý 03".

Results: Web jest 13 suites / 120 PASS; tsc (excluding stale generated `.next/dev` types) exit 0; eslint on changed
files exit 0; `next build` (Next 16.2.7, dev output isolated in `.next/dev`) exit 0. IoT `node --test` 96 tests:
92 PASS, 0 FAIL, 4 SKIP (isolated-PG tests). Browser UI with a real Farmer session: NOT TESTED.

### B. Camera heartbeat follow-up

- Real COM4 logs: NOT AVAILABLE. The previous message contained only the button text; no log file containing
  `[registry] HTTP 200 (` exists in the workspace, Documents or Downloads. No serial output was inspected or inferred.
- `-11` = `HTTPC_ERROR_READ_TIMEOUT` (core 3.3.12 `HTTPClient.h:57`, `HTTPClient.cpp:1363-1364`): connection open and
  request written, no response header within `_tcpTimeout`.
- Current state (read-only): last_seen 2026-10-18T13:34:17.23Z, online=true, age 6 s.
- Status unchanged from the 2.0.3 section above: server commits heartbeats in ms; ~6/15 responses still missed
  within 15 s; cause of the downlink loss unresolved (hypotheses: Wi-Fi link quality at RSSI -78 dBm; anything
  else needs the COM4 lines or an approved TCP 3001 packet capture). No camera/firmware change in this pass.

### HTTP telemetry last_seen fix deployed - 2026-10-18 13:35Z (operator-approved)

- Rollback tag `duriancare/iot-service:rollback-20261018b` = `c77bf888207c` (previously running image).
- Rebuilt only IoT -> `duriancare/iot-service:local` = `89a32c171dbc` (contains `src/telemetryRegistry.js`;
  `recordDeviceSeen` referenced in main.js). Recreated only `duriancare-iot-service` at 13:35:29Z; the other 17
  containers kept running; `duriancare_iot_uploads -> /app/uploads` mounted; `[kafka] Producer connected`.
- Read-only verification: esp32-01 `iot_devices.last_seen_at` 13:35:54.532 -> 13:36:04.531 -> 13:36:14.630Z
  (was NULL); production `connectivityForDevice` evaluated inside the new container on the DB row: `ONLINE`
  (age 11 s). `GET :8080/api/iot/devices` without a token -> 401 (auth unchanged).
- Not verified: the authenticated `/api/iot/devices` response and the Dashboard in a browser with a Farmer
  session (needs the operator's login).
- ESP32-CAM-001 resumed after the restart: last_seen 13:38:32.889Z, online=true (heartbeats paused during the
  recreate and recovered through firmware backoff).
- Rollback: `docker image tag duriancare/iot-service:rollback-20261018b duriancare/iot-service:local` then
  `docker compose -p duriancare -f infrastructure/docker-compose.yml up -d --no-deps --no-build --pull never --force-recreate duriancare-iot-service`.
