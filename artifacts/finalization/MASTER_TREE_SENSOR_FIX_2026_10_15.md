# Tree and sensor follow-up - 2026-10-15

Overall: **FIXED BUT NOT FULLY VERIFIED**. Sensor routing/UI is verified with the existing authenticated browser session. Farm source tests pass, but its running container is stale; no live tree mutation or deployment was authorized/executed in this follow-up. Synthetic history was prepared, not mixed into real telemetry.

## Root causes and status

| Scope | Classification | Evidence |
| --- | --- | --- |
| Tree removal generic error | FIXED BUT NOT FULLY VERIFIED | Running Farm JAR has no `TreeController.removeTree` or `TreeService.removeTree`; current source has both. Its broad exception handler also maps unsupported methods to generic 500. New HTTP fixture verifies correct create/remove/error contracts; runtime write remains untested pending deployment approval. |
| New trees shown SUSPECTED | FIXED BUT NOT FULLY VERIFIED | `javap -c` of the running JAR proves both createTree and generateTrees load SUSPECTED. Existing dirty source already uses HEALTHY/ACTIVE and creates no diagnosis. This source correction was preserved, not redundantly rewritten or applied to historical trees. |
| Sensor history 401 | FIXED AND VERIFIED | Web beforeFiles sensor rewrite bypassed authenticated proxy and forwarded directly to IoT without Gateway identity. Removed ONLY sensor rewrite. Browser then loaded current values and over 1,000 history rows successfully. |
| IoT alert card | FIXED AND VERIFIED | Removed `Cảnh báo IoT đang mở`, Bell import, alert state and listAlerts calls from SensorCharts only. Backend alerts and other screens unchanged. |
| Filters/history | FIXED AND VERIFIED (automated and read-only UI scope) | Added Vietnam-calendar Today, explicit timezone for day/month bounds and labels; reused existing from/to/offset history API, fixed upper bound, unique IDs and scope checks. No new Backend contract or aggregation endpoint. |
| Seed safety/preparation | FIXED BUT NOT FULLY VERIFIED | Deterministic dry-run and mocked transactional writer tests pass. No approved isolated schema/registry exists for an actual seed run. No real-device seed writes were executed. |

No physical camera, firmware, UDP discovery, broadcast, snapshot or preview implementation was changed. Full existing IoT tests include their prior local fixtures, not new hardware work.

## Runtime/version evidence

Inspection copied the running Farm JAR temporarily and read class constants/bytecode without executing a tree mutation. Temporary copies were removed after inspection, not business data.

- Running Farm image: `sha256:5a6feb45a41808e5e201ab846687c485c7b66866548789fb7640ab4574dbc83d`.
- Running Farm JAR SHA256: `21ff600b3ffbbb824d15e774efe40506e6b4bbbc93ab632e8d6e5272b704b670`.
- Both controller/service lack removeTree; createTree and generateTrees bytecode use SUSPECTED.
- Host source already has owner-authorized, idempotent REMOVED persistence, active-map exclusion, retained diagnosis/care history and HEALTHY creation defaults.
- Missing headers now produce structured 400 at direct MVC boundary; unsupported methods produce 405, not generic 500. Gateway authentication remains required and unchanged. Repository save failure is not reported as success.
- Health probes: Farm 8082, IoT 3001 and Gateway 8080 return HTTP 200 / UP. Health does not certify the missing routes or write E2E.

The actual DELETE was not sent against a demo tree. The observed runtime version mismatch explains the absent operation and old default; no corresponding destructive reproduction was attempted merely to obtain a stack trace. Existing generic exception handling does not log that historical stack trace, so no claim of a recovered historical trace is made.

An operator approval question was submitted for build/recreate of ONLY Farm with a rollback image. No affirmative response has been received in this execution; deployment remains BLOCKED pending that approval. Do not rebuild IoT: doing so would also include accumulated out-of-scope UDP changes.

## Read-only telemetry audit and seed

Actual SQL inspection, selecting no email/password/token:

- Farmer UUID `9ffc2a41-eaa6-4567-ad6d-d6741acafeb7`, FARMER/ACTIVE; `users.created_at = 2026-07-22 06:01:31.848583` (UTC storage convention).
- Registered `esp32-01` -> `farm-khoa-luan-2026`; cultivation_area_id is null. No zone or tree is inferred from this null assignment.
- At the first audit: 4,611 rows, earliest `2026-08-12T04:09:21.933Z`, latest `2026-10-15T06:55:40.457Z`.
- A subsequent range audit observed Today=74, seven days=1,096, month=1,096. Live ingestion continued during testing, so later UI counts legitimately increase. Existing historical provenance cannot be inferred from timestamps alone.

Schema: PostgreSQL `duriancare_iot.telemetry` stores device_id, measurements, timestamp/received_at and UUID. Registry `iot_devices` supplies farm/optional area. There is no per-reading synthetic/provenance column or assignment history. No schema/migration was added. Real telemetry was not overwritten or relabeled.

`scripts/seed-iot-history.js` now defaults DRY_RUN, does not read application .env, requires the actual account creation timestamp, rejects future/invalid/excessive ranges and real device IDs, and reserves `demo-synthetic-*`. Temperature/humidity use local Vietnam daytime/nighttime; 5-6 samples/day retain bounded realistic variations. The summary explicitly says DEMO_SYNTHETIC.

For actual fixture writes only: require IOT_SEED_APPLY=true, IOT_SEED_ENVIRONMENT=test, explicit process-local fixture URI, schema `iot_demo_fixture_<suffix>`, fixture-prefixed farm/zone, existing active synthetic registry with DEMO_SYNTHETIC name, and active Farmer/account timestamp verification. Script creates no account/farm/device/schema. UTC account date is checked in SQL; a transaction/advisory lock plus stable UUID and existing device/timestamp check prevent repeated/concurrent seed duplication. Batch failures roll back. These are mocked writer tests, NOT real PostgreSQL seed integration evidence or proof of business farm ownership. Operator must approve/provision the isolated fixture and its mapping before execution.

Actual dry-run at `2026-10-15T07:12:01.544Z`: from `2026-07-22T06:01:31.848Z`, 482 candidates, **0 writes**. Earlier fixed-end dry-run at midnight produced 481 candidates. No June backfill, account-date change, real-device backfill or new farm/zone was performed. Prior seed provenance is not retroactively certified.

## Dashboard behavior and runtime evidence

- 8-second polling retained; manual refresh retained; read-only fallback still requires server-authorized device/farm access.
- Today starts midnight Asia/Ho_Chi_Minh; seven days/24h are elapsed windows; month starts local day 1. Tests cover leap/non-leap February and 30/31-day boundaries independently of host timezone.
- Range changes clear old data and invalidate prior request sequence. Failure clears readings instead of retaining the previous interval. Empty results do not generate synthetic points.
- Registry reads remain primary. If capped, existing legacy paginated history is fetched through the authenticated Web proxy/Gateway, not directly to IoT. A single fixed `to` is used across pages. Device/date/ID/offset inconsistencies reject the result. At 20,000 rows the loader errors rather than silently truncating; there is no new aggregation endpoint.
- A deployed integration still returning legacy 401/404 can show already-authorized registry data only with explicit incomplete-history warning. Other errors are not silently converted to success. No auth gate is relaxed.
- At most 240 EXISTING readings are selected for SVG rendering; full loaded readings remain available for sample totals/status distribution. Endpoints and actual object values are preserved, not interpolated/averaged. UI labels the representative sample count. Status distribution is sample frequency, not duration or whole-season certification.
- The fixed June shortcut remains a display/history query start, not proof of a season/account starting in June. No reading is fabricated for the missing July/August interval.

Authenticated browser UI evidence after removing the rewrite: current snapshot displayed 32.6 C / 83.4% / 100% at one observation; 24-hour range displayed 1,141 samples and history 4,697 samples. Today later showed 127 / total history 4,705; seven-day view 1,155 / 4,711; monthly view 1,156 / 4,712. Seven-day and month can coincide because actual October rows begin on October 14. These were separate observations during continued ingest, not a simultaneous equality/count assertion. The alert card was absent. No cookie/token was extracted; identity was visible through the existing UI session, not a fresh CLI login or live write E2E.

Final bounded-render UI check: full-history selection displayed 4,745 readings, 240 existing representative chart samples and 4,745 distribution/history samples. Exact alert-card heading count was zero. Screenshot: [tree-sensor-dashboard-final.png](tree-sensor-dashboard-final.png). The initial unbounded whole-history SVG caused a browser inspection timeout; bounded rendering was then added, tested and successfully inspected. No hardware measurement provenance is certified merely because a value appears on this screenshot.

## Commands and test results

| Location / command | Exit | Actual result |
| --- | --- | --- |
| Backend: `mvn -pl duriancare-farm-service -am test -q` | 0 | 88 tests, 0 failure/error/skip across eight Farm suites |
| Backend focused: `mvn -pl duriancare-farm-service -am test '-Dtest=TreeMutationContractTest,TreeServiceRecoveryGateTest,TreeDiagnosisServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' -q` | 0 | 5 new HTTP cases + 13 recovery/tree + 20 diagnosis = 38 |
| IoT: `node --test test/seed-history.test.js test/read-api.test.js` | 0 | 27 pass; includes 24 existing auth/scoping cases and 3 seed groups |
| IoT: `npm test` | 0 | 67 total, 65 pass, 2 explicit PostgreSQL fixture skips, 0 failure |
| IoT: `npm run check` | 0 | Existing source syntax checks pass |
| IoT: `node --check scripts/seed-iot-history.js` | 0 | Seed syntax pass |
| Web: `npm test -- --runInBand` | 0 | Final 73 pass, seven suites |
| Web: `npm run typecheck` | 0 | Pass; production build also typechecks |
| Web: `npm run lint` | 0 | 0 errors, 8 existing warnings outside edited sensor paths |
| Web: `npm run build` | 0 | Final rerun after bounded-render edit successful, including TypeScript and all 44 static pages |
| Web: `npx eslint next.config.ts src/components/charts/SensorCharts.tsx src/lib/iot/ranges.ts src/lib/iot/history.ts src/__tests__/iot-dashboard.test.ts src/__tests__/iot-history.test.ts` | 0 | Final changed-file lint pass |
| Mobile: `npm run typecheck` | 0 | Pass; corresponding API/types/removed-tree UI inspected, no Mobile edits |
| Seed CLI with only verified account date | 0 | DRY_RUN, 482 candidates, 0 writes |
| Backend/Web: `git diff --check` | 0 | Pass at review; CRLF conversion notices only |

An initial Maven invocation failed (exit 1) because PowerShell split an unquoted dotted -D property. The corrected quoted command above passed. This command-line failure is not counted as a passed test. Full Maven reactor, Mobile Expo export, AI and hardware tests were not rerun for this scoped task; no claim of fresh results is made for them. The two skipped IoT cases require explicit real-PostgreSQL fixture connection; no test was silently disabled.

New HTTP tests use real Spring MVC/controller/service with mock repositories and trusted-Gateway header fixtures: create healthy/no diagnosis, remove/retry/history preservation, missing/foreign owner denial, missing identity/unsupported method, and failed save. This is NOT authenticated live Gateway E2E. New Web tests cover UTC/Vietnam bounds, actual paginated URL/query generation, >1,000 samples, empty/error paths, duplicate/foreign/out-of-range rows, rewrite ordering, incomplete fallback and bounded chart data. One sensor source assertion checks widget/polling/race-guard presence; it is not a mounted React race simulation.

## Changed files this task

Backend:
- `duriancare-farm-service/src/main/java/com/duriancare/farm/controller/FarmApiExceptionHandler.java`
- `duriancare-farm-service/src/test/java/com/duriancare/farm/controller/TreeMutationContractTest.java`
- `duriancare-iot-service/scripts/seed-iot-history.js`
- `duriancare-iot-service/test/seed-history.test.js`
- `docs/IOT_READ_API.md` and this report

Web:
- `next.config.ts`
- `src/components/charts/SensorCharts.tsx`
- `src/lib/iot/ranges.ts`, `src/lib/iot/history.ts`
- `src/__tests__/iot-dashboard.test.ts`, `src/__tests__/iot-history.test.ts`

GitNexus queries/impact checks were requested before edits; FTS/caller coverage is degraded, with UNKNOWN results (not a LOW-risk guarantee). Manual caller tracing found SensorCharts used by sensors page and existing dashboard, the shared Farm exception advice used across Farm controllers, and direct CLI invocation for seed. No HIGH/CRITICAL result was suppressed. Existing HEALTHY/removeTree source changes and unrelated dirty files were preserved. Backend/Web/Mobile remain on duy/cleanup-fixes. No commit, push, reset, clean, stash, branch switch, DB reset, business deletion or volume deletion. No deployment executed; existing Web dev server picked up source/config changes.

Git status entry counts (including accumulated prior work): Backend 124 -> 127, Web 36 -> 40, Mobile 18 -> 18. These are not counts of changed files authored solely in this task. No database fingerprint comparison is claimed: live device ingestion continued and naturally changed telemetry throughout this read-only audit.

## Remaining gates

1. Operator must approve isolated Farm deployment with rollback image. Until then current Farm runtime still has the reported old defaults/missing operation. After deployment verify artifact identity and health, then use an explicitly approved disposable tree for authenticated create/remove/retry/refresh, never a real demo tree by default.
2. Operator must approve and provision a separate synthetic fixture schema/registry/farm-zone mapping before seed writes. Complete actual PostgreSQL seed/retry/rollback integration there; do not reuse real esp32-01 or infer a zone.
3. No live tree write E2E, real database failure injection, React-mounted race test, Mobile device UI/export, physical hardware or production deployment certification is claimed. Runtime read-only sensor UI is distinct from these gaps.

Final classification: **FIXED BUT NOT FULLY VERIFIED**.

## Authorized Farm-only deployment addendum - 2026-10-15 07:25 UTC

Operator explicitly approved deploying only the local Farm service after the preceding report. This supersedes the pending deployment-approval gate above, not the historical pre-deployment observations or the still-unexecuted business-write tests.

Deployment/artifact/readiness classification: **FIXED AND VERIFIED**. Overall workflow classification remains **FIXED BUT NOT FULLY VERIFIED** because no authenticated create/remove write was executed and no synthetic fixture seed was applied.

Preflight: branch/status/diff reviewed again; Docker context desktop-linux, Compose project duriancare, existing infrastructure/docker-compose.yml and infrastructure/.env. Parsed Compose environment was compared with the running Farm environment in memory: no changed configured environment names/values. No secret values were emitted. Existing network/ports were retained.

Actual commands/results:

| Command | Exit | Result |
| --- | --- | --- |
| `docker compose -f infrastructure/docker-compose.yml --env-file infrastructure/.env config --quiet` | 0 | Valid configuration |
| `docker image tag sha256:5a6feb45a41808e5e201ab846687c485c7b66866548789fb7640ab4574dbc83d duriancare/farm-service:rollback-tree-sensor-20261015` | 0 | Previous image retained and ID rechecked |
| `mvn -pl duriancare-farm-service -am test -q` | 0 | Fresh 88 tests, zero failures/errors/skips |
| `docker compose -f infrastructure/docker-compose.yml --env-file infrastructure/.env build duriancare-farm-service` | 1 (cancelled) | Compose announced dependency images; stopped before image export/recreate, switched to direct Farm Dockerfile build |
| `docker build -f duriancare-farm-service/Dockerfile -t duriancare/farm-service:local .` | 0 | Farm image built; container build packages parent/Farm only with tests skipped by existing Dockerfile, separate host tests above passed |
| `docker compose -p duriancare -f infrastructure/docker-compose.yml --env-file infrastructure/.env up -d --no-deps --no-build duriancare-farm-service` | 0 | Only Farm recreated |
| HTTP readiness retry against `http://localhost:8082/actuator/health` | 0 | HTTP 200 / UP at 07:23:00.006Z |
| Read-only `OPTIONS http://localhost:8082/api/trees/verification-nonexistent` | 0 | HTTP 200, Allow: DELETE,GET,HEAD,PUT,OPTIONS |
| Unauthenticated `GET http://localhost:8080/api/trees/verification-nonexistent` | 0 | HTTP 401 as required; no fake identity headers or JWT |
| JAR extraction/class inspection and `javap -c` | 0 | TreeController has removeTree and DeleteMapping; TreeService has removeTree; createTree and generateTrees load HEALTHY and do not load SUSPECTED |

New running image: `sha256:568364b73c9a088c69286740f0866892d96fa8a08d12f8cf0217ff481c459057`.
New Farm container: `9a8f89a3122de31ac282f30402f6f41a18740924a869eee269e7d5ce83e276b0`, started 07:22:43.590785517Z.
New running JAR SHA256: `bed9fe8fe8a4171137742bd869487288d61a2bd52d6c21f8a494214d30cada8b`.

Container-ID comparison before/after: exactly duriancare-farm-service changed; no container was added or lost. No other service restart/recreate was executed. In particular, IoT/UDP/camera were not deployed. Temporary inspection JAR/class copies were removed after verification, not existing project files or business records.

Read-only integrity evidence: [farm-tree-sensor-deployment-integrity.json](farm-tree-sensor-deployment-integrity.json). All existing non-system collection document fingerprints/counts for duriancare_farm and duriancare_cultivation matched between 07:22:06.969Z and 07:23:33.353Z, spanning the recreate. Canonical EJSON serialization is identical to the existing audit script. This comparison covers document content in those two Mongo databases only, not index metadata, PostgreSQL, telemetry, or unmeasured historical periods. No database reset, volume operation, record repair or business write test was issued.

Authenticated read-only UI smoke after readiness: temporary tab using the existing normal Web session loaded the Farmer farm, four zones, and Khu Bac Tree Map/list with 50 active trees and diagnosis metadata. No browser cookies/tokens were read/exported. Existing generated Hxx-Cxx trees still display their persisted SUSPECTED status: this is historical data preservation, not evidence that the new creation default is still wrong. No tree was created, removed or restored to make the UI appear fixed.

Rollback was retained but not exercised. If operational rollback is approved, retag `duriancare/farm-service:rollback-tree-sensor-20261015` as `duriancare/farm-service:local`, then repeat the Farm-only Compose up command above with --no-deps --no-build. Never remove volumes or alter database records. No rollback was needed for the observed readiness/read-only checks.

Remaining gates: explicitly approved disposable tree fixture for authenticated create/remove/retry/refresh E2E; isolated synthetic seed schema/registry and its actual PostgreSQL integration validation. Deployment approval alone did not authorize modifying the demo farm or repairing historical tree health.
