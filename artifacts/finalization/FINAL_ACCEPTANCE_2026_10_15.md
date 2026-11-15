# Final Backend Acceptance - 2026-10-15 Follow-up

Latest checkpoint: section 10, Final Blocker Closure. Earlier test/deployment counts below remain
historical results; the checkpoint distinguishes checks actually rerun from prior evidence.

Latest scoped follow-up is section 11: Auth E2E and Harvest Dispatch Closure. Camera is deferred by
operator request in that follow-up; it is not an actionable blocker for the scoped work in section 11.

Latest evidence for bootstrap change control and positive release regression is section 12. Camera remains
deferred. No account repair or additional deployment is authorized or claimed by section 12.

## 1. Verdict

**PARTIALLY_COMPLETE**. Three camera software defects were reproduced, fixed, tested and deployed locally. Official login validation is correct: the supplied bootstrap email contains two at-signs. Real-Mongo dispatch persistence is now tested in isolated databases. Authenticated live Gateway business E2E and physical capture remain unverified. No actual shipment is claimed.

Continues MASTER_BACKEND_ACCEPTANCE_2026_10_15.md. Historical results there are not silently reclassified. This follow-up changed production code only in two IoT camera files. Existing dirty Backend work was preserved; Web/Mobile were not edited or rebuilt.

## 2. Findings and Files

| Finding | Reproduction / root cause | Correction or blocker |
| --- | --- | --- |
| Login HTTP 400 | Memory-only diagnostic found two @ characters in configured bootstrap email; official API rejects email | Environmental identity blocker. @NotBlank/@Email/length and password checks unchanged; no account or configured email rewritten |
| capture-now always 502 | HTTP regression: resolveCaptureUrl(...).replace is not a function | cameraRoutes.js awaits the Promise before replace |
| Invalid response persisted as image | Three failing tests: empty 200, HTML 200, truncated JPEG accepted | cameraService.js validates image/jpeg and JPEG start/end markers before file/DB persistence |
| Invalid snapshot cached as success | First HTML response 502, next cached response 200 | cameraRoutes.js caches only after content-type/JPEG validation |
| Intermediate patch regression caught in final diff | Cache assignment accidentally landed in stored-image handler; expanded test reproduced image GET 500 | Moved assignment into validated snapshot path; stored-image GET and valid cached-frame checks now pass; rebuilt/redeployed IoT again |
| Dispatch persistence previously mocked | Controller fixture tests did not use Mongo | New opt-in tests use actual Spring Data repositories/service in random UUID databases |
| Historical hashes incomparable | Algorithm/serialization shape and raw historical snapshot not retained | Versioned canonical EJSON and fixed SQL fingerprints now prove equality for this follow-up's measurement window only |

Changed production files: duriancare-iot-service/src/camera/cameraService.js and cameraRoutes.js.
Added tests: auth controller/LoginContractTest.java; cultivation api/HarvestDispatchMongoIntegrationTest.java; IoT test/camera-capture.test.js.
Added scripts: database-fingerprint.js, database-fingerprint.test.js, database-fingerprint.sql, verify-deployed-source.ps1.
Added metadata-only JSON/JSONL evidence; updated this report, master report and operational-data-audit.md.

GitNexus upstream impact was checked before edits; results UNKNOWN, incomplete index coverage, not LOW. Manual callers: IoT main mounts createCameraRouter; capture-now and scheduler call captureAndStore -> downloadImageBuffer. Snapshot has a separate buffered download/cache path. No authentication policy was weakened.

## 3. Tests and Commands

| Executed command/check | Exit | Actual final result |
| --- | --- | --- |
| `mvn -B -ntp -pl duriancare-auth-service,duriancare-gateway test` | 0 | Auth 77 total: 76 pass/1 skip; Gateway 12 pass |
| `mvn -B -ntp verify`, with verified local fixture URI supplied in process environment | 0 | BUILD SUCCESS; 314 total: 313 pass, 0 failure/error, 1 skip |
| Final Surefire XML: Auth/Farm/Cultivation/Gateway/Notification/Search | 0 | 77 (1 skip) / 83 / 98 / 12 / 31 / 13 |
| `mvn -B -ntp -pl duriancare-cultivation-service -Dtest=HarvestDispatchMongoIntegrationTest test`, first successful targeted run | 0 | 3 pass before concurrent case was added |
| HarvestDispatchMongoIntegrationTest in final reactor | 0 | 4 pass, 0 skip; actual Mongo persistence |
| `npm run check` (IoT) | 0 | Syntax checks pass |
| `node --test test/camera-capture.test.js` after fixes | 0 | 6 pass; pre-fix runs reproduced invalid-body, async URL and invalid-cache defects |
| `npm test` (IoT), dedicated PostgreSQL camera fixture schema | 0 | 48 runner tests, 8 suites, all pass; registration script additionally reports its 8 internal cases pass |
| `node --test scripts/database-fingerprint.test.js scripts/audit-harvest-data.test.js` | 0 | 9 pass |
| `python -m unittest discover -s scripts -p test_dispatch_e2e_harness.py` | 0 | 3 pass |
| `python -m unittest discover -s tests -v` (AI) | 0 | 26 pass |
| `python scripts/severity_training/test_pipeline_guards.py` | 0 | 4 pass |
| `python -m compileall -q duriancare-ai-service/app duriancare-ai-service/main.py scripts/master_backend_probe.py scripts/dispatch_e2e_harness.py` | 0 | Pass |
| Compose `config --quiet` with existing local env file | 0 | Valid; values not printed |
| `git diff --check` | 0 | No whitespace errors |

AuthServiceDebugTest remains deliberately skipped: it rewrites OTP verification state and cannot prove official authentication. New Mongo tests default to skip without DURIANCARE_FIXTURE_MONGO_URI; the final reactor DID supply the local URI and executed all four. A preceding reactor without that env skipped the then-three Mongo cases; it is not the final result above.

Initial Mongo run used incorrect host port 27017 and was interrupted after connection failure. Docker inspection identified host 27018 -> container 27017; rerun passed. An initial read-only SQL inventory had a shell quoting error; stdin corrected it. Neither failure was called a pass or repaired by editing business data.

Frozen AI hashes were checked again on host/container and match the master report. No model or training setting changed. DO_NOT_TRAIN remains the recorded gate decision; guards, not training, ran here. Chat/Web/Mobile were not rerun because untouched in this follow-up. Discovery/config/traceability still have no Maven unit tests.

## 4. Login and Live API Evidence

Trace: Web src/app/api/auth/login/route.ts forwards parsed JSON unchanged through backendRequest to Gateway /api/auth/login. AuthController validates LoginRequest before AuthServiceImpl normalizes email, queries the user, checks password/status and issues tokens. GlobalExceptionHandler returns 400 for invalid fields and 401 for incorrect credentials. There is no evidence the proxy corrupted the configured bootstrap email.

New controller tests cover valid contract reaching official service, double-@/malformed email rejection, missing email/password, and incorrect credentials. Success uses a mocked AuthService, NOT a real authenticated session.

Actual Gateway requests:

| Request/context | Result |
| --- | --- |
| POST /api/auth/login, existing configured bootstrap context in memory | 400; email validation |
| POST /api/auth/login, synthetic double-@ email | 400 |
| POST /api/auth/login, missing email | 400 |
| POST /api/auth/login, missing password | 400 |
| POST /api/auth/login, valid-format random nonexistent identity under example.invalid | 401; no account created |
| GET /api/users/me and /api/search?q=durian, forged identity headers without JWT | 401 each |
| GET /api/v1/notification/notifications and /api/iot/devices, same context | 401 each |
| GET /api/v1/harvest-batches/dispatch-summary?farmId=fixture-nonexistent and /api/v1/camera/snapshot?device_id=fixture-nonexistent, same context | 401 each |

The negative login does not guess passwords for an existing account. No JWT was acquired/fabricated, no browser cookies read, no account provisioned/reset. Host E2E access-token/email/password variables and both write flags were absent. `python scripts/dispatch_e2e_harness.py` returned BLOCKED_AUTH_CONTEXT, steps=[], identity=null, exit 1 in this follow-up. master_backend_probe.py returns BLOCKED_CONFIGURED_AUTH despite process exit 0: that exit code does not mean login passed.

Authenticated Auth/Notification/Search/IoT/cultivation E2E: **BLOCKED**. Real cross-owner farm/zone/recipient testing requires valid authorized identities; fixture tests do not replace it.

## 5. Isolated Dispatch Fixtures

Reviewed HarvestBatch, ExportRelease, HarvestDispatch, repositories, controller, HarvestDispatchService and CultivationAccessGuard. Scope comes from persisted lot/season, actor from Gateway identity, with farm/zone permissions. Dispatch requires HARVESTED lot, matching season, RELEASED dossier for that lot, source refs, consistent weights/unit and chronological constraints. Pending records cannot contribute; lot ID enforces one dispatch and versioning protects confirmation updates.

New tests use dispatch_acceptance_fixture_<UUID> databases, empty before inserts, separate from farm/cultivation business databases. IDs, source refs and receiver strings explicitly say TEST/FIXTURE. External FarmAccessClient is stubbed for exact owner/farm/zone; this is real Mongo integration, NOT cryptographic identity or live farm-service/Gateway E2E.

Four executed cases:

- Record -> persisted read -> pending NO_VERIFIED_DATA/null -> confirm -> owner/aggregate checks -> idempotent retries.
- Reject invalid weight, chronology and missing confirmation; pending cannot contribute.
- Reject other actor, wrong release and persisted farm/zone/season corruption.
- Two simultaneous identical submissions return the same lot ID and leave exactly one dossier.

Existing 7 controller workflow and 18 service tests cover additional forged body actor/scope, bad relations and authorization. Cleanup deletes only the known IDs in that test's UUID database; no database was dropped. Final audit: seven fixture databases, 0 remaining documents. Empty namespaces are retained. No global write flag was enabled.

Camera registration used camera_acceptance_fixture_<UUID> with an empty cloned camera_devices table. Its broad offline helper therefore could not touch actual camera records. All 8 internal cases passed; cleanup count was 0 fixture rows. Empty schema/table retained; no production table altered or dropped.

## 6. Database Integrity

New read-only scripts output only names/counts/digests. canonical-ejson-v1 retains BSON types with non-relaxed EJSON, recursively sorts object keys, preserves array order, sorts serialized documents and SHA256-hashes the complete serialization. Three tests verify order independence, detection of value/array/duplicate changes, and filtered output.

SQL fixes UTC/ISO rendering and fingerprints sorted row JSON hashes across 42 public/duriancare_auth/duriancare_iot tables. MD5 is a non-adversarial change detector, not a cryptographic security signature.

Retained evidence:

- acceptance_mongo_before.json / acceptance_mongo_after.json: exact equality across captured farm/cultivation collections after final fixture cleanup.
- acceptance_postgres_before.jsonl / acceptance_postgres_after.jsonl: all 42 tables have equal counts and digests.
- acceptance_integrity_result.json: mongoEqual=true, postgresEqual=true, historicalEqualityProven=false.

This proves the sampled scope across THIS measurement window only. Reads were not a transaction spanning databases; writes that were reversed between samples could be missed. Redis/Kafka/Elasticsearch, other Mongo databases, external stores and time before the first sample are outside this proof.

The previous hashes had insufficient serialization/snapshot provenance; no trustworthy earlier backup was established. Their mismatch is not automatically attributed to harmless reordering. Historical content equality remains unproven; no data was modified to force matching hashes.

Actual cultivation remains 2 harvest lots, 0 releases, 0 dispatches. The 1850 KG lot stays unverified; invalid 1200 KG seed stays excluded. Test fixtures cannot raise production totals. NO_VERIFIED_DATA/null remains correct without valid confirmed business records.

## 7. Camera and Deployment

Firmware arduino/DurianCare_ESP32_CAM exposes /capture JPEG and /stream MJPEG. Backend capture has 10-second socket timeout; snapshot 12 seconds. Timeout regression verifies rejection/request destruction before DB writes. JPEG marker checks do not decode the image; the HTTP positive fixture uses marker-only bytes, not physical/image-quality evidence.

Read-only registry query found 0 cameras online and seen within 24 hours. No physical request was sent to guessed/default addresses or used to auto-register a device. **HARDWARE_NOT_TESTED**. Heartbeat, registration and simulations are not physical capture success.

Verified existing local Compose project duriancare and env/config. Executed, exit 0:

```text
docker tag duriancare/iot-service:local duriancare/rollback-final-camera:20261015
docker compose -p duriancare --env-file infrastructure/.env -f infrastructure/docker-compose.yml config --quiet
docker compose -p duriancare --env-file infrastructure/.env -f infrastructure/docker-compose.yml build duriancare-iot-service
docker compose -p duriancare --env-file infrastructure/.env -f infrastructure/docker-compose.yml up -d --no-deps --no-build duriancare-iot-service
```

Only IoT was rebuilt/recreated, including a second build after the intermediate patch regression was corrected and all 48 IoT tests rerun. Final running image sha256:5fee64a9b759802714d9c5cb541e61e62e2e04992462b3025dd634323b147627. Other image digests unchanged from master report. Final internal health for config/auth/Gateway/notification/cultivation/farm/search/IoT/AI/traceability: 10 x 200/UP. Bounded startup-log scans found no APPLICATION FAILED TO START, MongoTimeoutException or Connection refused markers for auth/Gateway/notification/search/IoT; this does not certify all historical logs error-free.

Rollback saved, not executed: retag duriancare/rollback-final-camera:20261015 to duriancare/iot-service:local, then recreate only IoT with --no-deps --no-build. No volume/DB reset involved.

`pwsh -NoProfile -File scripts/verify-deployed-source.ps1` exit 0; acceptance_deployed_source.json records actual deployed-JAR comparisons:

| Service | Byte-identical host class/config entries | Exception |
| --- | --- | --- |
| Config | 9 | None |
| Gateway | 10 | None |
| Notification | 45 | None |
| Search | 15 | None |
| Cultivation | 121 | None |
| Auth | 141 | KnowledgeArticleService.class byte difference; deployed/host javap -c -p output equal |

IoT host/container source SHA256 matches:

- cameraService.js: 9248df0c0879e6b99ecd2c1153dc3be8f6c5cb1126355487b74159219590d461
- cameraRoutes.js: 69e1892a2f86a8181ec7b31df3c90c3ff4dc92af762c578dc4cdcbf42beb8905
- read-api.js: ad2b71351cc4dde0a8bd8cc5dc14e39ebc0733fc4d2d96c47fe75c21417c2b75

Coverage is enumerated host entries, not a full reproducible-image attestation. AI frozen hashes match master on host/container: leaf ac2bd7f82f9fd1e054f496a90f21ce77eb9697b27a71a4ecdf6d7c47ac71408b; classifier fc3dc5c92e43a9dbb196029fa56f9722ed91613d11ae7ccdadda1093fd1e5afe.

## 8. Remaining Inputs

- Authorized valid Farmer credentials/session context supplied locally, not in chat. Malformed bootstrap identity cannot be corrected by weakening @Email or silently rewriting an existing account.
- Approved isolated farm/season/zone/lot/release and second authorized user for live cross-owner Gateway checks. Current Mongo fixtures deliberately do not provision real identities.
- Source weighing/grading/packing/dispatch/delivery documents and authorized confirmation for actual shipment quantities/dates/receiver. Provenance and grounded corrections for the two old lots remain missing.
- Reachable authorized ESP32-CAM with recent registration and actual capture/stream access.
- Earlier comparable snapshot/audit trail if historical DB-content equality must be proven.

## 9. Git Safety

Branch duy/cleanup-fixes; HEAD 8fbdfa8b8803813750e9194452d01013e0ba36fc. No commit/push/reset/clean/stash/branch switch, business-data edit, truncation, database drop or volume deletion. Existing accumulated dirty work retained. Git status had 102 entries including pre-existing work; tracked diff covers 48 files and is not solely this follow-up. Cleanup touched only newly created isolated fixture records; empty namespaces remain. Reports contain no actual credential, token, raw row or expanded secret configuration. A bounded scan of eight new code/report files found zero matches against configured password/JWT values held only in memory; this is not a universal secret-scanner guarantee.

Final verdict: **PARTIALLY_COMPLETE**.

## 10. Final Blocker Closure - Fresh Read-only Checkpoint

No application source/config changed and no deployment was needed in this checkpoint. Branch remains
duy/cleanup-fixes, HEAD 8fbdfa8b8803813750e9194452d01013e0ba36fc, 102 Git status entries including earlier
work. No commit/push/reset/clean/stash/branch switch, business write, account creation, bootstrap edit,
fixture DB write, volume deletion or service restart was performed.

### Fresh Status and Evidence

| Item | Status | New evidence |
| --- | --- | --- |
| Service readiness | PASS | Config/Auth/Gateway/Notification/Cultivation/Farm/Search/IoT/AI/Traceability: 10 x HTTP 200/UP |
| Camera source deployed | PASS | Running IoT digest 5fee64a9b759802714d9c5cb541e61e62e2e04992462b3025dd634323b147627; cameraRoutes/cameraService SHA256 still match section 7 |
| Login validation | PASS | LoginRequest still @NotBlank/@Email/length constrained; existing official bootstrap attempt still HTTP 400, email validation |
| Authenticated Gateway E2E | BLOCKED | Access-token/email/password/isolated-manifest env variables absent; dispatch harness steps=[], identity=null, BLOCKED_AUTH_CONTEXT |
| Camera hardware capture/stream | BLOCKED | Registry sample: 1 device, offline. Direct registered and explicitly configured /capture requests failed URLError from host; configured request from running IoT container hit 4-second TimeoutError. No valid frame obtained; stream not attempted after failed capture prerequisite |
| Dispatch validation/authorization fixtures | PASS | Fresh 31 Maven cases: LoginContractTest 6; HarvestDispatchWorkflowIntegrationTest 7; HarvestDispatchServiceTest 18, no failure/error/skip |
| Camera HTTP simulation | PASS | Fresh 6 tests pass; marker-only positive bytes, stored-image read/cache checks and timeout fixture, NOT hardware evidence |
| Business shipment evidence | BLOCKED | Read-only Mongo count: 2 lots, 0 releases, 0 dispatches; no valid confirmed physical shipment |
| Current-window data fingerprints | PASS | closure_mongo_before/after.json match for farm/cultivation; closure_postgres_before/after.jsonl match all 42 tables; closure_integrity_result.json retained |
| Historical database equality | BLOCKED | No comparable historical snapshot established; no retroactive unchanged-DB claim |
| Git whitespace/safety | PASS | git diff --check exit 0; existing changes preserved |

Commands actually rerun:

```text
mvn -B -ntp -pl duriancare-auth-service,duriancare-cultivation-service -Dtest=LoginContractTest,HarvestDispatchServiceTest,HarvestDispatchWorkflowIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
node --test test/camera-capture.test.js
python scripts/master_backend_probe.py
python scripts/dispatch_e2e_harness.py
git diff --check
```

Maven, camera tests and diff check exited 0. The dispatch runner printed BLOCKED_AUTH_CONTEXT and the
PowerShell command returned nonzero (1); source returns exit_code=2 for missing auth. Treat the JSON
blocker as authoritative, not the shell's remapped native exit number. The bootstrap probe exits 0 but
explicitly reports BLOCKED_CONFIGURED_AUTH, not success. Full reactor 313 pass/1 skip, full IoT 48 and
AI 26 are preceding-run results, NOT rerun totals here. No new Mongo write tests were needed for docs-only
work. Read-only fingerprint commands reuse scripts/database-fingerprint.js/.sql with existing container
credentials held internally, without expanded values in logs. closure_harvest_inventory.json retains the
projected source audit, not a verified shipment statement: its recorded 1850 KG is still unverified.

### A. Auth Context Checklist

Responsible: local/dev environment administrator and the authorized demo Farmer/account owner.

1. Supply an EXISTING active FARMER account with valid email and official password, or a valid session
   context issued through official auth. No production identity, fabricated JWT, OTP rewrite, password
   reset or bootstrap-account correction is authorized by this checkpoint.
2. Supply owned isolated farm/zone/season/HARVESTED lot and RELEASED dossier, plus a second authorized
   test account outside that farm for negative access tests. Farmer must pass VIEW_CARE_SCHEDULE,
   CREATE_CARE_SCHEDULE and UPDATE_CARE_SCHEDULE on the fixture farm/zone as applicable. Confirmation
   requires UPDATE; client-supplied confirmer/farm/zone fields do not grant permission.
3. In a new, untranscribed LOCAL PowerShell terminal at repo root, leave both write flags absent. Do not
   paste credentials into chat, files or command literals. Example interactive official-login/read-only run:

```powershell
if ((Test-Path Env:DURIANCARE_E2E_ALLOW_WRITE) -or
    (Test-Path Env:DURIANCARE_DISPATCH_E2E_ALLOW_WRITE) -or
    (Test-Path Env:DURIANCARE_E2E_ACCESS_TOKEN)) { throw 'Use a fresh read-only test terminal.' }
$env:DURIANCARE_E2E_API_URL = 'http://localhost:8080'
$env:DURIANCARE_E2E_EMAIL = Read-Host 'Authorized demo Farmer email'
$secure = Read-Host 'Password (not logged)' -AsSecureString
try {
    $env:DURIANCARE_E2E_PASSWORD = [System.Net.NetworkCredential]::new('', $secure).Password
    python scripts/demo_e2e_harness.py
} finally {
    Remove-Item Env:DURIANCARE_E2E_PASSWORD, Env:DURIANCARE_E2E_EMAIL -ErrorAction SilentlyContinue
    $secure = $null
}
```

Expected: official POST /api/auth/login succeeds, GET /api/users/me supplies actual FARMER userId, then
ownership audit READ_ONLY_PASS. Stop on login, role or ownership failure. This command is NOT executed
here with credentials. A successful login must not be inferred from a mocked controller response.

4. Fixture custodian must provide an existing manifest path via DURIANCARE_DISPATCH_E2E_MANIFEST.
   It must contain approvedIsolatedFixture=true, farmId and harvestBatchId starting dispatch-e2e-, and
   recordRequest.exportReleaseId plus recordRequest.evidence fields listed below. Prefix alone is NOT
   proof of isolation: custodian must approve actual fixture scope. In the same credential-protected
   try/finally context above, run `python scripts/dispatch_e2e_harness.py` with both write flags absent.
   Expected READ_ONLY_PASS/BLOCKED_WRITE_PATH after identity, farm membership, lot and empty-summary checks.
5. Only after those gates pass, separate explicit operator approval can enable the dispatch-specific
   write flag on the approved disposable fixture and rerun that command. Do NOT enable the generic
   DURIANCARE_E2E_ALLOW_WRITE, which controls a different tree-care workflow. The dispatch harness creates,
   reads, confirms, aggregates and retries; it does not test every cross-owner endpoint or delete records.
   After failure inspect fixture records before retry; never clean a demo farm. Second-identity access
   checks must use the same fixture IDs and official session, not forged trusted headers.

### B. Camera Checklist

Responsible: ESP32-CAM hardware/firmware owner and local network/Docker administrator.

1. Power the actual device; verify flashed arduino/DurianCare_ESP32_CAM firmware, Wi-Fi registration and
   actual DHCP address/port. Confirm /capture and /stream match firmware configuration. Authorize the
   specific endpoint and ensure it is reachable from BOTH operator host and running IoT container.
   Host mDNS success alone does not prove Docker DNS/routing success. Do not substitute a guessed IP.
2. From the authorized host, perform direct firmware GETs (not backend capture-now, which persists):

```powershell
$camera = (Read-Host 'Approved camera base URL').TrimEnd('/')
if (([uri]$camera).UserInfo) { throw 'Do not place credentials in a URL.' }
$tag = [guid]::NewGuid().ToString('N')
$frame = Join-Path $env:TEMP "duriancare-acceptance-frame-$tag.jpg"
$stream = Join-Path $env:TEMP "duriancare-acceptance-stream-$tag.bin"
curl.exe --noproxy '*' --connect-timeout 3 --max-time 10 --fail --output $frame --write-out '%{http_code} %{content_type} %{size_download}\n' "$camera/capture"
if ($LASTEXITCODE -ne 0) { throw 'Capture transport failed; do not decode an earlier file.' }
python -c "from PIL import Image; import sys; im=Image.open(sys.argv[1]); assert im.format=='JPEG'; im.verify(); print('JPEG_DECODE_PASS')" $frame
if ($LASTEXITCODE -ne 0) { throw 'Capture JPEG validation failed.' }
curl.exe --noproxy '*' --connect-timeout 3 --max-time 5 --max-filesize 5242880 --fail --output $stream --write-out '%{http_code} %{content_type} %{size_download}\n' "$camera/stream"
python -c "from PIL import Image; import sys,io; b=open(sys.argv[1],'rb').read(5242880); a=b.find(b'\xff\xd8'); z=b.find(b'\xff\xd9',a+2); assert a>=0 and z>a; im=Image.open(io.BytesIO(b[a:z+2])); assert im.format=='JPEG'; im.verify(); print('STREAM_FRAME_DECODE_PASS')" $stream
```

Capture requires HTTP 200, image/jpeg, nonempty decoded JPEG. Stream requires HTTP 200, expected
multipart MJPEG type and at least one decoded frame. A bounded stream timeout (curl 28) or size bound
(63) alone is NOT a pass or necessarily a device failure; examine HTTP type/body/frame. Do not run decode
against a stale file after a failed download. Repeat reachability from Docker using approved address;
then authorize any backend capture/AI persistence separately. These future commands were not run here.

### C. Evidence and Data Reconciliation Checklist

Responsible: farm operator/harvest owner, warehouse/packing-dispatch custodian, quality/compliance
reviewer and authorized release approver; environment administrator supplies isolated fixture context.

| Source to supply | Fields to reconcile against persisted data |
| --- | --- |
| Harvest/weighing record | harvestSourceRef, actual lot ID/code, farmId/plotId/season linkage, harvestedAt, harvestedQuantity matching lot.quantity, quantityUnit |
| Grading/quality record | gradingSourceRef, qualifiedQuantity, rejectedQuantity; sum must not exceed harvested quantity; ungraded is not assumed rejected |
| Packing record | packingSourceRef, packedQuantity <= qualified, actual packedAt >= harvest; no invented packaging quantity/date |
| Dispatch/delivery record | dispatchSourceRef, shippedQuantity > 0 and <= packed, matching unit, actual shippedAt, actual destination/receiver evidence |
| Reviewed/released export dossier | existing exportReleaseId for same lot, RELEASED and releasedAt <= shippedAt, traceability/compliance review source |
| Authorized source confirmation | authenticated actor with UPDATE on actual farm/zone, sourceDocumentsChecked=true; server records confirmedBy/confirmedAt |
| 1850 KG provenance | original weighing/harvest source and actual source relationships; stays unverified without these |
| 1200 KG seed corrections | grounded season existence, actual zone/farm linkage, recognized status and harvest/safe-date chronology; no corrections inferred from a seed ID |

Actual confirm endpoint: POST /api/v1/harvest-batches/{lotId}/dispatch/confirm with
sourceDocumentsChecked=true; record POST /api/v1/harvest-batches/{lotId}/dispatch supplies the release ID
and evidence only. GET same dispatch and GET /api/v1/harvest-batches/dispatch-summary?farmId=...&cultivationSeasonId=...
must verify persisted actor/scope and totals. These live writes remain BLOCKED; fixture-only harness is the
next command after approved inputs, not permission to populate production from this checklist.

Code review confirms create/confirm authorize persisted scope, reject bad relations/weights/time and
summary revalidates evidence/chronology and excludes pending/unconfirmed records. Summary trusts the
server-persisted confirmer produced by the guarded API; it does not authenticate document contents or
prove the confirmer's historical permission against a separate immutable authorization ledger. Controlled
DB write access and source-document custody remain required. API safeguards PASS in fresh fixtures;
real authorized business confirmation and evidence remain BLOCKED. No valid dossiers means
NO_VERIFIED_DATA with null quantities, not a fabricated zero or yield/quality percentage.

Checkpoint verdict: **PARTIALLY_COMPLETE**. No COMPLETE claim until required authenticated E2E,
physical camera and mandatory operational evidence are actually verified.

## 11. Auth E2E and Harvest Dispatch Closure - 2026-10-15

Scope: Auth, farm ownership, harvest/release/dispatch only. Camera completely deferred by operator
request: no camera endpoint, firmware test, camera software test or camera change in THIS follow-up.
Earlier camera results remain historical. Verdict remains PARTIALLY_COMPLETE for Auth/business evidence.

### New Root-cause Evidence

PASS - privacy-filtered comparison establishes all three:

- infrastructure/.env explicitly defines the bootstrap email with two @ characters.
- Its value matches DEFAULT_ADMIN_EMAIL in the running Auth container.
- A read-only hashed-email equality lookup finds one persisted matching account, ADMIN/ACTIVE.

Thus the malformed identity exists in BOTH configuration and the account table; it is not an invented
request or a proven frontend serialization fault. AdminBootstrapProperties checks nonblank values, not
the LoginRequest @Email contract; AdminBootstrapInitializer creates/looks up that identity directly.
Official login still correctly returns 400 at email validation before password checking. No address,
password, token or email hash was printed. No config/account was corrected and validation was not relaxed.
Auth service startup/email provisioning hardening requires an operator decision on this existing invalid
bootstrap context; deploying fail-fast validation without that decision would knowingly prevent startup.

Read-only role inventory: 13 ACTIVE Farmers, 1 PENDING_VERIFICATION Farmer, 2 ACTIVE Admins. Account
existence is not authorization to obtain/change its credentials. No Farmer credential or token was supplied.
DURIANCARE_E2E_ACCESS_TOKEN, EMAIL, PASSWORD, DISPATCH_E2E_MANIFEST and both write flags remain absent.
dispatch_e2e_harness.py again reports BLOCKED_AUTH_CONTEXT, steps=[], identity=null. Bootstrap probe
reports BLOCKED_CONFIGURED_AUTH with POST /api/auth/login 400. Authenticated live E2E remains BLOCKED.

Search performed: scripts/demo_e2e_harness.py, dispatch_e2e_harness.py, test_dispatch_e2e_harness.py;
auth src/test including JwtServiceTest, AuthServiceImplTest, LoginContractTest and disabled AuthServiceDebugTest;
repository filename inventory for manifest/fixture/Postman/Newman; operational/finalization E2E documentation.
No supplied live fixture manifest was found. Mocks and isolated Mongo tests cannot issue a legitimate
Farmer session; the disabled debug helper rewrites OTP verification and is not an acceptable bypass.
An additional names-only scan of local Compose env configuration found no nonempty FARMER or
DURIANCARE_E2E_/DURIANCARE_DISPATCH_E2E_ context keys. Matching credential-provider searches in
scripts/infrastructure/auth tests found the existing demo harness, not an issued test identity.

### Proven Code Bug and Correction

PASS after correction - authenticated export-release overloads authorized the farm but forwarded body
submittedBy/userId/reviewerId into persisted audit fields without checking the authenticated actor.
Five parameterized regressions reproduced acceptance of forged actor IDs for create/submit/approve/
release/recall: 5 failures, 0 errors before fix. Added requireSameActor to exactly those five overloads
in CultivationCalendarService. Mismatches now reject before persistence with IllegalArgumentException
(existing API handler maps to 400). Valid same-actor requests, farm permission gates, DTOs and status
transitions remain unchanged. No schema/migration/backfill, no account changes, no camera changes.

Files changed: CultivationCalendarService.java and CultivationCalendarAuthorizationServiceTest.java,
plus this report and operational audit. Existing dirty changes preserved. GitNexus upstream analysis was
disambiguated by symbol UID for all five overloads: UNKNOWN/no resolved callers, incomplete coverage.
Manual call path is CultivationCalendarController -> actor overload -> existing farm guard -> existing
state/persistence method. No claim of zero blast radius from the incomplete graph.

Fresh commands/results:

| Command | Result |
| --- | --- |
| mvn -B -ntp -pl duriancare-auth-service,duriancare-gateway,duriancare-farm-service,duriancare-cultivation-service test, before new regression | Exit 0; 270 total, 265 pass, 5 skip |
| mvn -B -ntp -pl duriancare-cultivation-service -Dtest=CultivationCalendarAuthorizationServiceTest#exportReleaseRejectsForgedAuditActor test, before fix | Exit 1; 5 fail demonstrating bug |
| Same four-module Maven command after fix | Exit 0; 275 total, 270 pass, 5 skip, 0 failure/error |
| mvn -B -ntp -pl duriancare-cultivation-service -Dtest=HarvestDispatchMongoIntegrationTest test, local fixture URI supplied privately | Exit 0; 4 pass, 0 skip; separate real Mongo run |
| python -m unittest discover -s scripts -p test_dispatch_e2e_harness.py | Exit 0; 3 pass |
| node --test scripts/audit-harvest-data.test.js scripts/database-fingerprint.test.js | Exit 0; 9 pass |

Four-module final breakdown: Gateway 12, Auth 77 (1 debug skip), Farm 83, Cultivation 103 (4 opt-in Mongo
skips). The separate real-Mongo command executed those four cases with actual repositories in fresh
dispatch_acceptance_fixture_UUID databases, localhost:27018 verified beforehand; cleanup deletes only
known fixture IDs. Final namespace audit after this run: 11 fixture DBs, 0 remaining fixture documents.
No global write flag set. These are not authenticated live Gateway tests. Camera/full IoT/AI/full reactor
were not rerun here because outside scope/unchanged; old totals are not presented as new results.

### Why Two Lots But No Release/Dispatch

PASS - direct DB reads currently show 2 harvest lots, 0 export releases, 0 dispatches, 0 compliance
assessments and 0 ExportRelease audit entries. That establishes absence of persisted workflow evidence,
not that no request ever failed or no physical shipment ever happened. No historical HTTP error/request
trail proves a specific attempted submission, so a missing release is NOT attributed to an unproven API
failure or permission denial. The actor-spoofing bug above affects attribution, not the arithmetic reason
these collections are empty.

- harvest-2025-khu-dong: farm, zone and season exist and match, status HARVESTED; 1850 KG remains
  PERSISTED_ORIGIN_UNVERIFIED. Valid relationships do not authenticate weighing or shipment evidence.
- demo-harvest-batch-2026-001: farm exists, referenced zone and season do not; raw PENDING_INSPECTION
  status is unsupported, and preceding audit also records harvest-before-safe-date. 1200 KG remains excluded.
- A dispatch requires the same-lot RELEASED dossier, which does not exist for either lot. Actual source
  references, weights, packing/shipping times and destination evidence were not supplied. No release,
  dispatch, compliance assessment or source evidence was created to make the report nonempty.

Guard/aggregation review: create/read/confirm use persisted lot scope; confirm requires UPDATE and
records actor/time server-side; reporting requires VIEW and per-zone visibility, revalidates lot/season/
release/weights/chronology and excludes pending or invalid dossiers. Same-lot dispatch ID, duplicate-key
handling and versioning remain in place. Empty valid set -> NO_VERIFIED_DATA and null quantity/month
totals. API document references do not prove document authenticity or physical delivery.

### Required Inputs and Exact Resume Procedure

Environment administrator + authorized demo Farmer: supply an EXISTING ACTIVE FARMER's official local
login context through a private, untranscribed terminal; never chat or a committed credential file. Follow
section 10.A's interactive secure-password command with both write flags absent. First command:
`python scripts/demo_e2e_harness.py`, Gateway http://localhost:8080. Require real /api/users/me identity,
FARMER role and successful farm/zone/tree ownership read. Bootstrap ADMIN is not a substitute.

Fixture custodian: provide an approved existing isolated farm/zone/season/HARVESTED lot, same-lot
RELEASED dossier, and second authorized identity outside that farm for negative tests. Manifest is a
local UTF-8 JSON file, no credentials. Exact keys/types expected by scripts/dispatch_e2e_harness.py:

| JSON field | Required content |
| --- | --- |
| approvedIsolatedFixture | boolean true, backed by actual custodian approval |
| farmId / harvestBatchId | strings of existing approved fixture IDs, each starting dispatch-e2e- |
| recordRequest.exportReleaseId | string ID of existing same-lot RELEASED dossier |
| recordRequest.evidence.harvestSourceRef / gradingSourceRef / packingSourceRef / dispatchSourceRef | actual authorized fixture evidence references; no fabricated production documents |
| recordRequest.evidence.harvestedQuantity / qualifiedQuantity / rejectedQuantity / packedQuantity / shippedQuantity | measured fixture numbers, required hierarchy and exact lot harvested quantity |
| recordRequest.evidence.quantityUnit | same stored unit as lot |
| recordRequest.evidence.packedAt / shippedAt | actual fixture ISO-8601 instants satisfying harvest/release chronology |
| recordRequest.evidence.destination | authorized fixture receiver/destination, never invented production customer |

Inside the credential-protected try/finally context from section 10.A, set only the manifest path:

```powershell
$env:DURIANCARE_DISPATCH_E2E_MANIFEST = Read-Host 'Absolute path to custodian-approved isolated manifest'
python scripts/dispatch_e2e_harness.py
```

Expected with writes OFF: READ_ONLY_PASS / BLOCKED_WRITE_PATH after real identity/ownership and empty
fixture summary. This does not validate physical source authenticity. Only a separately approved live
fixture run may set the dispatch-specific write flag; generic DURIANCARE_E2E_ALLOW_WRITE stays absent.
Harness then calls POST /api/v1/harvest-batches/{lot}/dispatch, GET summary (pending excluded), POST
same dispatch /confirm with sourceDocumentsChecked=true, GET persisted dispatch, GET summary and
repeat POST to verify same-ID replay. It never creates the source lot, release, account or documents.

Business farm/warehouse/quality-release owners: section 10.C lists exact required source documents and
field reconciliation. After source validation and authenticated permission verification, official dossier
flow is POST /api/v1/export-releases/assess -> POST /api/v1/export-releases -> POST /{id}/submit ->
POST /{id}/approve -> POST /{id}/release. Create requires releaseCode, actual harvestBatchId,
targetMarketCode, submittedBy matching actor; submit/approve/release body userId must match actor.
Farm/zone UPDATE is required; state transitions and compliance still gate progress. Dispatch record/
confirm/summary follow only with valid source evidence. No such live writes were run here.

### Local Deployment / Integrity Evidence

Local Docker endpoint verified npipe dockerDesktopLinuxEngine, Compose project duriancare, existing
environment/config validated. Only Cultivation is eligible for redeploy because its actor guard changed.
Rollback image retained as duriancare/rollback-dispatch-audit-actor:20261015 (previous running image
754dabbf24756802ca2a5e6b681aa847854fbafceef8c80ca0c5bf8ad53a22cd).

PASS - executed local build and recreate, exit 0:

```text
docker compose -p duriancare --env-file infrastructure/.env -f infrastructure/docker-compose.yml config --quiet
docker tag duriancare/cultivation-service:local duriancare/rollback-dispatch-audit-actor:20261015
docker compose -p duriancare --env-file infrastructure/.env -f infrastructure/docker-compose.yml build duriancare-cultivation-service
docker compose -p duriancare --env-file infrastructure/.env -f infrastructure/docker-compose.yml up -d --no-deps --no-build duriancare-cultivation-service
```

Only Cultivation was rebuilt/recreated. Actual running image:
sha256:23a691b1f6dfee9d8ba9431f0f8f8c47e876260991efe64fbad8db2c959d6e32.
Final health: all 10 previously listed services 200/UP, including Cultivation. Bounded Cultivation log
scan: startup completed, no APPLICATION FAILED TO START or MongoTimeoutException marker. Actual
deployed JAR comparison matches all 121 Cultivation host class/config entries, including changed
CultivationCalendarService (auth_dispatch_deployed_source.json). Auth's previously noted unrelated
KnowledgeArticleService byte difference remains, not a new deployment assertion.

Post-deploy regression command:
`mvn -B -ntp -pl duriancare-cultivation-service -Dtest=CultivationCalendarAuthorizationServiceTest,HarvestDispatchWorkflowIntegrationTest,HarvestDispatchServiceTest test`
exited 0: 67 pass (42 authorization, 7 workflow, 18 dispatch service), 0 failure/error/skip. Gateway
runtime GET users/me, GET dispatch-summary, POST empty export-release and POST approve with fake
identity headers but no JWT all returned 401. No business record was written; valid Farmer live E2E
is still BLOCKED, not replaced by these negative checks.

Rollback available but not exercised: retag duriancare/rollback-dispatch-audit-actor:20261015 to
duriancare/cultivation-service:local and recreate only Cultivation with the same --no-deps --no-build
command. Do not reset DB or volumes.

PASS - fresh versioned fingerprints:

- Mongo duriancare_farm/duriancare_cultivation: equal snapshots, 2026-10-15 02:10:34 -> 02:19:24 +07:00.
- PostgreSQL public/duriancare_auth/duriancare_iot: 42 table digests/counts equal, 02:10:35 -> 02:19:25 +07:00.
- Files: auth_dispatch_mongo_before/after.json, auth_dispatch_postgres_before/after.jsonl and
  auth_dispatch_integrity_result.json. historicalEqualityProven=false; no proof about unobserved earlier
  periods or stores outside scope. Nontransactional cross-DB samples retain the section 6 limitations.
- Existing business lots/releases/dispatches were not edited or seeded. New test-only Mongo writes
  were confined to verified UUID fixture databases, then cleaned by exact IDs; no database dropped.

Final git diff --check exit 0; branch/HEAD unchanged, 102 status entries including earlier dirty work.
No commit, push, reset, clean, stash, branch switch, credential exposure or unauthorized data cleanup.

Camera remains deferred, not tested or changed. Authenticated E2E BLOCKED, real shipment evidence BLOCKED.
Scoped final verdict: PARTIALLY_COMPLETE.

## 12. Bootstrap Auth and Business Evidence - Controlled Resolution Checkpoint

Status: **PARTIALLY_COMPLETE**. Only tests/docs were changed in this checkpoint. No runtime code/config,
ADMIN record/password, role, source lot, release, dispatch or camera was changed; no service redeployed.

### Bootstrap Provenance and Usage

PASS - source chain reviewed: infrastructure/.env -> infrastructure/docker-compose.yml DEFAULT_ADMIN_EMAIL
-> Auth container environment -> duriancare.bootstrap.admin.email in both Auth application.yml and
Config Server's duriancare-auth-service.yml -> AdminBootstrapProperties -> AdminBootstrapInitializer.
Both YAML sources use the same environment placeholder. Running value and local env match and contain
two @ characters; a filtered hashed lookup confirms the matching persisted account is ADMIN/ACTIVE.
No real email, hash, password or token was output. The account was not renamed or recreated.

Root-cause boundary: bootstrap checks nonblank email and normalizes trim/lowercase, but does not apply
LoginRequest's @Email validation. User's JPA email column enforces nonnull/unique/length, not email syntax.
Initializer directly finds or creates the normalized identity, so this source path can persist a malformed
address which official login correctly rejects with 400. Normalization does NOT repair duplicate @.
The reviewed source and matching config/account explain the reachable failure mechanism; no retained
creation audit proves who entered the typo or whether this exact row was created by bootstrap versus a
historical import/manual action. That historical attribution is not guessed.

BLOCKED - whether this ADMIN is actively being used cannot be established. Users table/model has no
last_login/last_login_at/last_sign_in_at. Non-sensitive Redis SCARD/TTL checks were attempted without
reading token/session members; existing available credentials did not authorize the metadata read.
Unavailable metadata is NOT zero sessions. Even a zero refresh set would not exclude an existing access
JWT or establish human ownership. No browser cookie store/session was inspected and nobody was logged
out. Account owner/environment administrator must confirm usage and a maintenance window.

### Approved Repair Plan - NOT Executed

Responsible: named account owner, environment administrator, authorized DBA/security reviewer.

Required approval inputs: exact account UUID through a private admin channel; verified replacement
email and mailbox ownership (never infer by deleting one @); target local/dev instance/database;
confirmation of usage/maintenance impact; authorization for a same-UUID email-only update; a verified
existing alternate administrator for recovery; protected before-image/config version and rollback owner.
No password, JWT or OTP should be supplied in chat. No new administrator is proposed.

1. Verify a working alternate EXISTING administrator via official auth, and obtain account-owner sign-off.
   Freeze the affected identity during the approved maintenance period; establish comparable DB/table
   fingerprints and protected, access-controlled backup evidence. Do not put account rows/password hashes
   or full .env contents in reports or Git.
2. With explicit approval, disable DEFAULT_ADMIN_ENABLED before changing the email and recreate only
   Auth with the reviewed local Compose config. Changing the enabled bootstrap email first is unsafe:
   findByEmailIgnoreCase(newEmail) could return empty and create a second ADMIN. Never use that as repair.
3. Validate the approved canonical replacement using the unchanged login email/length rules. Check
   case-insensitive uniqueness and inventory UUID foreign keys and any denormalized email/OTP references.
   Do not blindly rewrite business records or move OTP verification to a different mailbox.
4. Authorized DBA performs a parameterized transaction: lock the exact users row by approved UUID;
   compare expected old email/role/status; ensure no case-insensitive target collision; update ONLY its
   email plus normal audit timestamp; require exactly one affected row. Preserve UUID, password hash,
   role/status, profiles/preferences and ownership relationships. Abort on any mismatch. No executable
   account mutation script was added or run in this checkpoint.
5. Update only the approved email configuration consistently with the preserved UUID. Prefer keeping
   bootstrap disabled for steady-state existing accounts. Do not restore an entire old .env over other
   work. Recreate only Auth if needed; verify effective non-secret metadata, health, official login with
   the SAME operator-supplied password, /api/users/me UUID/role and database invariants.
6. Coordinate session handling with the owner. Current logout revokes its presented access token and
   all user's refresh sessions, not every independently issued access JWT. Where full invalidation is
   required, use approved existing controls and account for effective access-token expiry; do not rotate
   the global JWT secret or restore old tokens as a shortcut. No session operation was executed here.
7. Rollback before commit means SQL ROLLBACK. After commit, use an approved compensating email-only
   transaction on the SAME UUID with expected-current checks, then restore only the matched configuration
   values. Do not reset the DB or overwrite concurrent changes. Restoring the old malformed email restores
   the previous login defect, NOT a successful acceptance state. Revoked sessions are not restored;
   alternate-admin recovery must be confirmed before proceeding.

Until these approvals exist: real ADMIN repair BLOCKED. No automatic startup validation change was
deployed against the known malformed configuration; future bootstrap email hardening must be coordinated
with the configuration/account decision rather than knowingly breaking service startup.

### New Fixture Coverage and Results

GitNexus impact requested before test edits: new AdminBootstrapSafetyTest not indexed, existing
CultivationCalendarAuthorizationServiceTest has UNKNOWN caller completeness. Source callers/fixture
boundaries reviewed manually; production methods were not edited.

Added AdminBootstrapSafetyTest (3 cases, all in-memory mocks):

- Disabled bootstrap never accesses repositories/encoder.
- Existing malformed fixture account is not renamed, rehashed or recreated, even with different config.
  This protects the no-unapproved-repair boundary, not endorsement of malformed account creation.
- Missing valid fixture identity is normalized to lowercase/trimmed email and gets ADMIN/ACTIVE using
  the mocked encoder/repositories. No account or credential is created in an actual database.

Added matchingAuthenticatedActorCanCreateReadSubmitApproveReleaseAndRecallFixture in cultivation
authorization tests: DRAFT -> read -> UNDER_REVIEW -> APPROVED -> RELEASED -> RECALLED; actor audit
fields match the supplied authenticated fixture actor, duplicate active dossier creation is rejected,
and only one fixture record remains. Existing five forged-actor regressions still pass. These are fixture
service tests, NOT principal/JWT/Gateway authenticated E2E. Body actor fields retain the compatibility
contract but must equal the Gateway-derived actor; they are not independent authority.

| Fresh executed command | Result |
| --- | --- |
| mvn -B -ntp -pl duriancare-auth-service,duriancare-gateway,duriancare-farm-service,duriancare-cultivation-service test | Exit 0; 279 total, 274 pass, 5 skip; 0 failure/error |
| Gateway/Auth/Farm/Cultivation breakdown | 12 / 80 (1 skip) / 83 / 104 (4 skips) |
| AdminBootstrapSafetyTest / cultivation authorization suite | 3 pass / 43 pass, included above |
| python -m unittest discover -s scripts -p test_dispatch_e2e_harness.py | Exit 0, 3 pass |
| node --test scripts/audit-harvest-data.test.js scripts/database-fingerprint.test.js | Exit 0, 9 pass |
| git diff --check | Exit 0 |

One Auth debug helper remains skipped because it rewrites OTP state against a live DB; four opt-in Mongo
tests skipped because their explicit URI was not supplied. Their earlier isolated success is historical,
not a new pass. New fixtures are in-memory and discarded with the process: no DB cleanup required or run.
Production health freshly checked: Auth/Gateway/Farm/Cultivation 200/UP, existing images unchanged.

### Remaining Auth and Business Gates

BLOCKED - Farmer official-login E2E: host credential/token/manifest variables still absent. Official
bootstrap attempt remains 400; dispatch runner BLOCKED_AUTH_CONTEXT with no HTTP steps. Existing
mock/token unit helpers and isolated repository fixtures cannot legitimately issue a live Farmer identity.
Environment/account owner must supply an EXISTING ACTIVE FARMER context locally using section 10.A's
untranscribed secure-password procedure, not source/chat. Next read-only command:
`python scripts/demo_e2e_harness.py` with Gateway http://localhost:8080 and generic write flag absent.

BLOCKED - live dispatch E2E: approved fixture custodian must supply the section 11 UTF-8 manifest,
existing isolated farm/zone/season/HARVESTED lot/same-lot RELEASED dossier and second authorized user.
Set DURIANCARE_DISPATCH_E2E_MANIFEST to that approved local path in the credential-protected context;
run `python scripts/dispatch_e2e_harness.py` first with BOTH write flags absent. Only separately approved
isolated writes may use the dispatch-specific flag. No real demo-farm creation/write permission inferred.

BLOCKED - business evidence: source references/measurements/chronology/destination and release review
must be supplied by farm/weighing, grading/quality, packing, warehouse/dispatch and release custodians.
Section 10.C and 11 give exact evidence fields and official API order. Reconcile harvested quantity to
the actual lot; qualified+rejected <= harvest, packed <= qualified, 0 < shipped <= packed; exact unit;
harvest <= packing <= shipping, shipping >= release and not future; valid farm/zone/season and safe date.
Do not treat an ungraded balance as rejected. Authenticated UPDATE source confirmation is required;
summary excludes pending/invalid dossiers and returns NO_VERIFIED_DATA/null when none qualify.
The 1850 KG lot remains unverified and 1200 KG invalid seed excluded. Fixture transitions do not supply
source documents or raise production totals. No release/dispatch/customer/date/weight was created here.

### Current-window Integrity and Safety

PASS - bootstrap_resolution_mongo_before/after.json match in duriancare_farm and duriancare_cultivation;
bootstrap_resolution_postgres_before/after.jsonl match all 42 tables in public/duriancare_auth/duriancare_iot.
Measurement: Mongo 2026-10-15 02:22:18 -> 02:26:53 +07:00; SQL 02:22:20 -> 02:26:57 +07:00.
bootstrap_resolution_integrity.json retains exact timestamps; historicalEqualityProven=false.
No transaction-wide/global snapshot or historical-content claim; previous fingerprint limitations stand.

Only new bootstrap test, existing cultivation authorization test, evidence metadata and the two requested
reports changed here. No production source/config change, deployment, password/identity/role edit, token
fabrication, bypass, camera work, DB reset/volume deletion, commit/push/reset/clean/stash or branch switch.
Existing uncommitted changes preserved. Current ADMIN usage and actual repair require approval; Farmer
auth context, isolated manifest and verified business documents remain required external inputs.

Final verdict: **PARTIALLY_COMPLETE**.

## 13. Master Web/Farmer Follow-Up: New Runtime Evidence

Current detailed evidence: [MASTER_WEB_FARMER_COMPLETION_2026_10_15.md](MASTER_WEB_FARMER_COMPLETION_2026_10_15.md).
Classification for this follow-up: **FIXED BUT NOT FULLY VERIFIED**; prior-window statements above
remain historical and do not negate these new explicitly authorized changes.

- FIXED AND VERIFIED: notification proxy Origin/Referer forwarding caused Gateway 403, not a wrong
  bulk endpoint. Existing authenticated Farmer UI mark-all changed unread 8 -> 0; Mongo confirms 8 read.
  Backend now uses owner-scoped conditional bulk update with real-Mongo retry/concurrency tests.
- FIXED AND VERIFIED: Profile now agrees with canonical Farm/Zone/Tree APIs: 1 farm, 4 zones, 100 active
  trees, 25 per zone. Map/list selection opens the same DC-T009; Khu Nam has its own 25 markers and
  does not retain DC-T009. Invalid normalized positions are excluded. No real-tree CRUD/health write.
- FIXED AND VERIFIED: operator explicitly authorized esp32-01 registration into farm-khoa-luan-2026.
  Official authenticated Web -> Gateway POST created registry 2634d1d5-e6a2-4930-aa39-73ada0ee217a,
  zone=null. Sensors now display persisted 30.6 C / 87.2% air / 100% soil, last measured Oct 14 16:52Z.
  Connectivity remains UNKNOWN; no online or fresh physical-telemetry claim. Polling remains 8 seconds.
- PASS: Maven full reactor 325 cases (318 pass, 7 skip), separately 2 real Mongo notification fixtures
  pass with exact cleanup; Web 55 tests, typecheck, lint (8 existing warnings), build pass; Node audit/
  fingerprint 13 pass; IoT non-camera read API 24 pass; dispatch-harness unit 3 pass. Commands/exits
  and intermediate corrected failures are recorded in the detailed report.
- PASS: deployed only Notification to image sha256:698b78cfad997c309814c84021182963cbdc7005842d06036d59ca8dddbf110b;
  retained rollback tag before-master-web-fix-20261015. Other container IDs unchanged. Latest notification,
  Gateway, farm, cultivation, search and config health: six 200/UP. No camera endpoints/tests performed.
- PASS (bounded integrity): farm/cultivation Mongo hashes match during Oct 15 05:28:17 -> 05:30:32 UTC.
  Separate 42-table PostgreSQL window around approved device registration shows ONLY iot_devices
  count/hash changed 0 -> 1; other 41 tables, including users and telemetry, match. This does not claim
  no data changed: eight own notifications were marked read and one approved device registry created.
- BLOCKED: five existing target-farm care plans reference globally missing diagnosis IDs, enumerated
  in the detailed report. They were preserved, not deleted/reattached or supplied synthetic diagnoses.
- BLOCKED: real ADMIN repair still lacks approved replacement/control, current-use assessment and
  tested backup-admin access. Added read-only dry-run SQL and guarded backup/transaction/rollback
  runbook; fixture-only approval text used for syntax checks is NOT actual authorization.
- BLOCKED_AUTH_CONTEXT applies to the standalone CLI harness (exit 1, steps=[]), not the browser:
  the existing browser session successfully exercised Profile, garden, notifications and device creation.
  No fresh login, cross-user live fixture or dispatch write E2E is claimed. Business source documents
  remain absent; 1850 KG unverified, 1200 KG excluded, NO_VERIFIED_DATA/null unchanged.
- NOT CHANGED: no seed was necessary for existing complete garden/season layout. Unsafe legacy
  seed scripts were not run. Chart history is capped and starts in August; no missing June data fabricated.

Overall project remains **PARTIALLY_COMPLETE**; this follow-up is **FIXED BUT NOT FULLY VERIFIED**.

## 14. Farmer-Controlled Export and Approved Synthetic Fixtures (2026-10-16)

See [detailed report](FARMER_EXPORT_SELF_VERIFICATION_2026_10_16.md) for commands, lifecycle rules,
safe seed gates, deployment/rollback images, measured integrity window and verification limits.

- PASS: Farmer self-inspection / release permission, guarded inspection API and independent season
  production lifecycle; additive delivery evidence with DISPATCHED/DELIVERED status.
- PASS: Cultivation 110 / 4 skip; Farm 88; Web 84; seed 3; Web lint/typecheck/build; Mobile typecheck
  and Android/iOS/Web export. Web lint has 8 existing warnings, not zero warnings.
- PASS: approved Cultivation-only local deployment, retained rollback image, health 200/UP and
  127 identical tested/deployed classes. Other container IDs unchanged in this measured deployment.
- PASS: exactly two DEMO_SYNTHETIC lot/release/dispatch chains inserted (six documents); repeated
  seed inserts zero. These are not real shipments, laboratory results or certification.
- PASS (bounded): existing Farm/Cultivation non-fixture records match before/after counts/hashes.
  New harvest_dispatches collection is separately disclosed, not hidden behind an unchanged-DB claim.
- PASS (fixtures): verified totals exclude synthetic records; NO_VERIFIED_DATA/null remains correct
  absent valid operational evidence. The old 1850 KG and 1200 KG records remain untouched.
- NOT TESTED: authenticated live write lifecycle and physical Mobile/QR scan. Read-only Web loaded
  both labelled release cards and persisted DISPATCHED/DELIVERED states after re-login. Monthong
  QR wrong-first-row selection was reproduced, fixed and verified against its actual snapshot.
- BLOCKED: operational source evidence remains external. No actual export/delivery is certified.

Overall verdict remains **PARTIALLY_COMPLETE**, not COMPLETE or verified real-world export success.
