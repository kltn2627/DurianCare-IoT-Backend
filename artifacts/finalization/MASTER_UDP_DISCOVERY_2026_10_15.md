# UDP discovery follow-up - 2026-10-15

Overall classification: **FIXED BUT NOT FULLY VERIFIED**. Source and isolated integration tests pass; the new implementation is not deployed, and LAN/device interoperability is not verified. Historical acceptance results remain historical.

## Findings and implementation

| Item | Classification | Evidence / remaining condition |
| --- | --- | --- |
| Missing UDP discovery | FIXED AND VERIFIED | New module, mocked beacon tests, real localhost datagram tests |
| Snapshot could return cached frame before checking updated DB origin | FIXED AND VERIFIED | DB origin now checked before cache; HTTP integration verifies replacement origin |
| Arbitrary UDP sender could impersonate a registered camera | FIXED BUT NOT FULLY VERIFIED | Fail-closed per-device authenticated envelope and farm binding; approved sender provisioning remains required |
| Startup/shutdown and degraded UDP | FIXED AND VERIFIED | Discovery starts after HTTP listen; lifecycle fixtures cover signals, failed HTTP bind, socket faults and repeated stop |
| Actual LAN broadcast / ESP32 interoperability | BLOCKED | Docker Desktop bridge is not a LAN broadcast transport; approved network arrangement and sender are needed |
| Deployment | NOT CHANGED | Running image lacks the new module; no container rebuilt/restarted |
| Five missing diagnosis references | BLOCKED | Fresh read-only audit confirms five missing references out of eight plans; source evidence/approval missing |
| Malformed ADMIN email | BLOCKED | Existing ADMIN/ACTIVE UUID and two-at-sign email confirmed; no approved replacement/repair authorization |
| Export / dispatch evidence | BLOCKED | Two harvest batches, zero releases, zero dispatches; no source documents supplied |

GitNexus upstream impact was requested before existing-symbol edits. Results were UNKNOWN with incomplete caller coverage, not LOW risk. Call paths were manually traced. No HIGH/CRITICAL impact result was suppressed.

## Protocol and security

`src/discovery/udpDiscoveryService.js` selects validated non-loopback IPv4, excludes known virtual interfaces and avoids advertising container eth0 as a physical LAN address. Optional `HOST_OVERRIDE_IP` is validated. The actual bound HTTP port is used (existing fallback 3001).

Beacon: `DURIANCARE_SERVER:http://<IPv4>:<port>` immediately after bind and every 3000 ms, broadcast enabled, destination `255.255.255.255:4210`. Camera listener: UDP 4211. Start/stop are idempotent; shutdown closes sockets/timer and drains accepted updates before closing HTTP/resources. UDP errors produce sanitized degraded status without terminating HTTP.

Bare `DURIANCARE_CAM:<ID>:<URL>` syntax is parsed but is NOT sufficient authorization to write. Authorized updates require the JSON HMAC envelope and explicit per-device farm/key binding documented in `docs/IOT_UDP_DISCOVERY.md`. No production key was generated or installed. Existing unsigned firmware cannot automatically update URLs under this policy. This is an explicit interoperability blocker, not a claimed hardware success.

Validation rejects oversized packets, unknown devices, wrong farm, invalid signature/time, credentials/query/hash/base paths, non-HTTP schemes, public or non-peer literal IPs. The current DB stores origin components, not arbitrary base paths. Rate limits, duplicate suppression, per-device serialization and conditional old-origin/farm SQL guard prevent unnecessary writes and races. Timestamp replay protection is process-local, not durable exactly-once delivery across restarts.

`updateCameraUrl` updates existing records only, preserving ownership/online metadata. Snapshot reads DB origin on every request; a changed origin gets a new cache object, preventing older in-flight fetches from poisoning it. There is no existing `/live-stream` route: Web preview polls snapshot. No new route or relaxed HTTP authentication was introduced.

## Files in this follow-up

- `duriancare-iot-service/src/discovery/udpDiscoveryService.js`: discovery and validation.
- `duriancare-iot-service/src/main.js`: HTTP/discovery lifecycle.
- `duriancare-iot-service/src/camera/deviceService.js`: guarded URL update.
- `duriancare-iot-service/src/camera/cameraRoutes.js`: origin-aware snapshot cache.
- `duriancare-iot-service/package.json`: additional syntax checks, unchanged test runner.
- `duriancare-iot-service/test/udp-*.test.js`, `test-support/`: unit, lifecycle, HTTP, UDP and isolated PostgreSQL integration fixtures.
- `duriancare-iot-service/tests/camera-registration.test.js`: legacy DB test isolation fix.
- `infrastructure/docker-compose.udp-discovery.yml`: optional UDP overlay, not activated.
- `scripts/audit-care-plan-references.js` and test: read-only reconciliation audit.
- `docs/IOT_UDP_DISCOVERY.md`, `docs/care-plan-reference-reconciliation.md` and this report: operation/evidence gates.

## Commands and actual results

Commands below ran from the IoT directory unless noted. Credential values are intentionally omitted.

| Command | Exit | Result |
| --- | --- | --- |
| `npm run check` | 0 | Seven source syntax checks pass |
| `npm test` with explicit process-local fixture PG URI | 0 | 64 tests pass, 0 fail, 0 skip; legacy wrapper includes nine cases |
| `npm test` without fixture URI | 0 | 62 pass, 0 fail, 2 explicit PostgreSQL fixture skips; 64 total |
| Root: `node --test scripts/audit-care-plan-references.test.js scripts/audit-harvest-data.test.js scripts/database-fingerprint.test.js` | 0 | 12 pass |
| Root: `docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.udp-discovery.yml --env-file infrastructure/.env config --quiet` | 0 | Overlay parses; not deployed |
| Root: `git diff --check` | 0 | No whitespace errors |

Lifecycle fixtures initially failed due to missing fixture upload configuration; the fixture was corrected and the entire suite rerun successfully. No failing case was suppressed.

Real localhost integration: signed UDP -> existing service repository -> PostgreSQL row in unique transactional schema using actual V9 DDL -> next HTTP snapshot uses new origin. Schema rollback and namespace absence were verified. Separate listener smoke bound `127.0.0.1:4211`, then stopped cleanly with beacon deliberately disabled. These demonstrate local socket/integration behavior, not LAN broadcast, real image capture, authenticated Gateway E2E or physical camera success. Lifecycle signal tests dispatch signals in child-process fixtures, not deployed-container shutdown tests.

Web/Mobile/Maven/AI suites were not rerun in this IoT-only follow-up; earlier report results are not presented as fresh runs. Models and training gate were untouched.

## Legacy test safety finding and integrity scope

The initial baseline `npm test` passed 48 tests but unexpectedly auto-executed a legacy registration script loading the business environment. It created/deleted its own two prefixed rows and invoked a global stale-camera update. This was unsafe and was disclosed, not silently treated as an isolated baseline.

Before subsequent runs the script was changed to require explicit `DURIANCARE_CAMERA_FIXTURE_PG_URI`, use a UUID-named schema and actual migration DDL within one transaction, clean only its fixture IDs, roll back and assert schema absence. Without explicit opt-in it skips. The full fixture-enabled suite subsequently exercised it successfully.

`udp-discovery-sql-integrity.json` compares 42 PostgreSQL tables at 06:15:39.707Z and 06:29:40.416Z on 2026-10-15: no differences. This baseline is AFTER the initial unsafe legacy run, before isolated regression runs. A retained 05:38:51.215Z camera-table fingerprint also matches the current table (one row, MD5 `68a3d1822576d9cf4f78fb5f144a29b4`). It supports no net persisted camera-table difference at those measured points, not proof that no temporary writes occurred. `udp-discovery-mongo-integrity.json` records equal comparable retained/current farm and cultivation snapshots; it is not an immediate before/after baseline for the initial command.

No business repair, Mongo write, volume deletion or schema migration was applied. Fixture DDL was rolled back. No claim of universal database immutability across unmeasured historical windows is made.

## Operator prerequisites and next safe commands

1. Discovery: review `docs/IOT_UDP_DISCOVERY.md`; approve LAN transport and authenticated sender provisioning. Supply per-device farm/key bindings securely in local environment, never chat/source/report. The optional Compose overlay only publishes UDP; it does not solve outbound broadcast on Windows bridge networking. Source remains undeployed (running image `sha256:5fee64a9b759802714d9c5cb541e61e62e2e04992462b3025dd634323b147627`; `/app/src/discovery/udpDiscoveryService.js` absent). No hardware/firmware was changed or tested.
2. Care history: data custodian supplies original diagnosis/backup/import evidence and per-plan approval following `docs/care-plan-reference-reconciliation.md`. Read-only command after approved scope variables are supplied: `node scripts/audit-care-plan-references.js`. No diagnosis replacement or historical status edits were made; a legacy demo seed delete/reinsert path is not proof of causality for this farm.
3. ADMIN: authorized administrator confirms replacement email, current use, backup access and DB/config change approval per `docs/admin-email-repair-runbook.md`. Run the documented SQL dry-run with approved inputs; current no-input run returns `BLOCKED_ADMIN_ID_REQUIRED` (exit 0 is not repair success). No account/password/role/status was changed.
4. Auth E2E: authorized operator supplies valid Farmer context locally and runs `python scripts/demo_e2e_harness.py` read-only. Relevant credential/manifest/write environment variables were absent; no authenticated runtime E2E was claimed or write flag enabled.
5. Business evidence: custodian supplies weighing, grading, packing, release authority and dispatch/delivery documents with lot/farm/zone/season and quantity/date/customer reconciliation. Approve a separate isolated manifest before any dispatch write test. The 1850 kg lot remains unverified; the 1200 kg seed remains excluded. No valid shipment means `NO_VERIFIED_DATA` and null totals, not assumed zero shipped mass.

Backend/Web branch remains `duy/cleanup-fixes`. Backend status grew from 110 to 124 entries during this work; this is an accumulated dirty workspace, not 124 newly edited files. Web remained at 36 entries with no edits by this follow-up; Mobile was untouched. Existing unrelated changes were preserved. Reports/artifacts may be ignored by Git but are saved in the workspace. No commit, push, reset, clean, stash, branch switch, deployment or destructive database operation was executed.
