# Master Backend Acceptance - 2026-10-15

Latest evidence: [Final acceptance follow-up](FINAL_ACCEPTANCE_2026_10_15.md). It identifies the malformed
bootstrap email, deploys camera software corrections, executes isolated real-Mongo dispatch tests and
proves matching current-window Mongo/PostgreSQL fingerprints. Latest Maven: 313 pass, 1 debug skip,
no failure/error. Authenticated Gateway business E2E and physical hardware remain unverified;
historical fingerprint equality is not claimed. Verdict remains PARTIALLY_COMPLETE.

## 1. Overview

Verdict: **PARTIALLY_COMPLETE**. Automated backend regression and selected local deployments passed. Authenticated live business E2E, physical camera capture, and actual shipment evidence remain unverified. Health checks are not business E2E.

Repository: DurianCare-IoT-Backend; branch: duy/cleanup-fixes. Existing accumulated work was preserved. This report supersedes older runtime/test statements only where fresh evidence is recorded below. Web and Mobile were not changed or rebuilt in this backend acceptance run.

## 2. Findings and Resolution Evidence

| Finding | Root cause and correction | Evidence / remaining limit |
| --- | --- | --- |
| HIGH: whitespace JWT secret accepted | Auth and Gateway rejected only short secrets; now also reject blank secrets | Both new configuration tests failed before correction; final reactor passes |
| HIGH: insecure fallback configuration | Removed JWT/admin password fallbacks in local and central auth configuration; admin bootstrap defaults disabled | Compose validation passes; existing supplied environment values preserved; no credential values recorded |
| HIGH: unauthenticated internal search indexing | Internal POST now requires configured internal token, constant-time comparison; missing/wrong/unconfigured return 401/403/503 | Controller negative tests reproduced prior 202; runtime missing token returns 401; local internal token absent, HTTP indexing deliberately unavailable; Kafka indexing remains separate |
| MEDIUM: invalid search body returned 500 | Validation and unreadable-body exceptions mapped to safe 400 response | Regression reproduced 500; final search suite 13 passes |
| HIGH: malformed auth event exposed OTP payload in logs | Removed raw payload and exception details from malformed-event logs | Log-capture regression failed before fix; final listener suite passes |
| MEDIUM: notification OTP aliases bypassed rate limit | Added generate/validate aliases to existing Gateway OTP policies | Two negative tests reproduced forwarding instead of 429; final Gateway suite passes |
| MEDIUM: unbounded S3 download | Read at most configured maximum plus one byte; close body on success/failure/oversize | Bounded-read regression failed before fix; AI suite 26 passes; no live S3 object written |
| MEDIUM: auth health blocked by unused Mongo dependency | SQL auth module carried Mongo starter and attempted localhost Mongo connection | Removed unused starter; classpath regression added; deployed auth health 200/UP |
| HIGH: legacy telemetry GET bypassed registry ownership | Legacy latest/history now require actor, explicit device ID, registered device and farm/zone access | Three new tests reproduced unauthorized 200; IoT suite 42 passes; runtime no identity 401; device POST protocol unchanged |
| HIGH: OTP delivery exception silently acknowledged | Listener swallowed provider failure; now throws sanitized exception without provider cause | Regression reproduced missing exception; notification suite 31 passes; Kafka/provider outage not exercised live |
| Previous cultivation wiring failure | Production constructor explicitly selected | Cultivation wiring regression passes; deployed cultivation health remains UP |
| Dispatch read/retry scope | Existing request scope must match persisted scope and backend access guard | Controller/service fixture integration tests cover workflow and invalid relationships; no live shipment created |

GitNexus impact checks preceded symbol edits. Framework/dynamic entry points returned unknown or incomplete caller coverage; this is not evidence of zero blast radius. Index/graph findings were supplemented by source and executable regression tests, not suppressed.

Broader review covered auth and Gateway identity forwarding, farm access guards, care-plan/recovery checks, notification inbox ownership, harvest/release validation, traceability, IoT registration/read paths, and AI integrity. Passing tests do not prove every accumulated change is free of integration defects. Direct internal-service trusted headers still require network isolation; this run does not certify production hardening.

## 3. Files and Scope

New corrections are confined to auth/gateway JWT properties and their tests; auth POM and AuthDependencyHealthTest; auth local/central YAML and Compose bootstrap defaults; Gateway AuthRateLimitFilter and tests; SearchController, SearchApiExceptionHandler and SearchControllerTest; notification AuthEventListener and tests; IoT read-api.js and tests; AI s3_storage.py and test_s3_storage.py. Added scripts/master_backend_probe.py for allowlisted local official-login diagnostics. Documentation changes record evidence only.

Existing cultivation dispatch model/repository/service/controller, workflow integration tests, converter tests, dispatch harness and audit scripts remain part of the accumulated uncommitted implementation, not newly invented shipment data. The complete working tree includes unrelated earlier farm, AI, IoT and documentation changes; it was not reset or normalized.

## 4. Automated Tests

| Actual command | Exit | Result |
| --- | --- | --- |
| `mvn -B -ntp verify` (repository root, final run) | 0 | BUILD SUCCESS; 304 tests, 303 passed, 0 failure/error, 1 skipped |
| Auth Surefire XML | 0 | 71 total, 70 passed, 1 skipped |
| Farm Surefire XML | 0 | 83 passed |
| Cultivation Surefire XML | 0 | 94 passed |
| Gateway Surefire XML | 0 | 12 passed |
| Notification Surefire XML | 0 | 31 passed |
| Search Surefire XML | 0 | 13 passed |
| `npm run check` and `npm test` (IoT) | 0 | 42 tests passed, 8 suites |
| `npm ci --ignore-scripts --no-audit --no-fund` (chat), then `npm run test:security` | 0 | Restored absent local dependencies; Nest build and 10 security tests passed; no lockfile upgrade |
| `python -m unittest discover -s tests -v` (AI) | 0 | 26 passed |
| `python -m compileall -q duriancare-ai-service/app duriancare-ai-service/main.py scripts/master_backend_probe.py scripts/dispatch_e2e_harness.py` | 0 | Compile passed |
| `python scripts/severity_training/test_pipeline_guards.py` | 0 | 4 passed |
| Severity validation, actual Durian_Severity_Training dataset | 0 | 5 images, 0 approved, 0 annotation errors; artifacts/severity_training/master_validation.json |
| Severity training gate, same dataset | 1 (expected denial) | DO_NOT_TRAIN; protocol not finalized, approved 0 below 120, classes 0 below 2; master_training_gate.json |
| `python -m unittest discover -s scripts -p test_dispatch_e2e_harness.py` | 0 | 3 safety tests passed |
| `node --test scripts/audit-harvest-data.test.js` | 0 | 6 passed |
| Compose `config --quiet` with existing local env file | 0 | Configuration valid; values not printed |
| `git diff --check` | 0 | No whitespace errors |

AuthServiceDebugTest is deliberately skipped: it requires a real DB and rewrites OTP verification state, unsuitable for proving official authenticated E2E. Discovery, config and traceability modules have no executable unit tests in this reactor; their builds/health are not test coverage. Initial chat test failed because Nest was not installed; dependency restoration resolved that environment failure. Deliberately failing pre-fix regressions are not counted as unresolved final failures.

Frozen host/container model hashes matched:

- Leaf: ac2bd7f82f9fd1e054f496a90f21ce77eb9697b27a71a4ecdf6d7c47ac71408b
- Classifier: fc3dc5c92e43a9dbb196029fa56f9722ed91613d11ae7ccdadda1093fd1e5afe

No training, model replacement, invented severity or confidence-to-severity conversion occurred.

## 5. Runtime and Authenticated E2E

| Check | Actual result |
| --- | --- |
| Internal Docker-network health for config, auth, Gateway, notification, cultivation, farm, search, IoT, AI, traceability | PASS: all 10 returned 200/UP |
| Gateway GET /api/users/me, /api/search, /api/iot/devices, /api/v1/sensors/latest?device_id=esp32-01 without identity | PASS: 401 each |
| Direct legacy IoT GET without identity | PASS: 401 |
| Direct search invalid page | PASS: 400 |
| Direct internal search indexing without internal token | PASS: 401, no index record created |
| Official POST /api/auth/login using existing configured bootstrap context, memory-only | BLOCKED: HTTP 400, validationFields=[email]; no token acquired |
| Farmer dispatch harness | BLOCKED_AUTH_CONTEXT: host E2E credentials absent; no approved isolated evidence manifest |
| Authenticated dispatch create/confirm/aggregate | NOT TESTED live; fixture controller/service integration only |
| Real ESP32-CAM capture | HARDWARE_NOT_TESTED; heartbeat is not capture evidence |

master_backend_probe.py never prints credentials, headers or raw responses. It does not read browser cookies, forge tokens, create accounts or reset passwords. Login validation was not bypassed or guessed. Shared-session logout was not invoked. Write-mode variables were not enabled. There were no live create/confirm/accept/delete business requests.

## 6. Deployment and Rollback

Local Compose project duriancare, existing infrastructure/.env, existing database and volumes. Selected commands:

`docker compose -p duriancare --env-file infrastructure/.env -f infrastructure/docker-compose.yml build <selected-service>`

`docker compose -p duriancare --env-file infrastructure/.env -f infrastructure/docker-compose.yml up -d --no-deps --no-build <selected-service>`

Config was rebuilt first because central auth fallback configuration changed. Auth, Gateway, notification, search and IoT were rebuilt/recreated only for reviewed corrections. Notification was recreated again after the final sanitized-delivery-failure fix. AI was restarted using its existing image to reload the bind-mounted S3 module; model files were unchanged. Discovery, farm, cultivation, chat, traceability and data containers were not restarted in this master run.

Actual running image digests:

| Service | Digest (sha256) |
| --- | --- |
| config | b6e69a1df9a91605564e9ae1f702e11547c0a8fa05cb4c91f1ca6f0774c6d722 |
| auth | 08950430406e0b28ead1e1e06f4882ea75c7e6814e1753ea26432452d6d7a49c |
| Gateway | a3057cba90ced1944f1d7b5de11da4b602c53517a058bbd1638ca4e0310d3a4f |
| notification | 3a67c4913cf9bb7cd01cf18c1126094e0901dfee05a899fc9c7d8d7df10fb148 |
| search | 97ea227637429c00987c5f5654a1a568377f08605f420c769a9b2561706b8bd2 |
| IoT | 1b0eb894d3c005fece0205c86a6e22311de6f45687d937f40058f8904866794e |
| cultivation, preserved previous deployment | 754dabbf24756802ca2a5e6b681aa847854fbafceef8c80ca0c5bf8ad53a22cd |

IoT /app/src/read-api.js SHA256 exactly matches tested host source: ad2b71351cc4dde0a8bd8cc5dc14e39ebc0733fc4d2d96c47fe75c21417c2b75. JAR builds use container JDK 21 versus host JDK 22, so differing compiled byte hashes alone are not treated as a stale deployment finding.

Previous images retained as duriancare/rollback-master-{config-server,auth-service,gateway,notification-service,search-service,iot-service}:20261015. Rollback, if required, means retag the corresponding saved image to the service's existing local image tag and recreate only that service with --no-deps --no-build. No rollback was needed for final health; rollback execution was not tested and must account for central configuration compatibility. Never remove volumes or reset data.

## 7. Data Integrity

Before/after counts: farm 323 documents, cultivation 52 documents, SQL auth users 16. Previous/current reviewed harvest inventory: 2 harvest batches, 0 export releases, 0 dispatches. No business mutation was issued by this run. Failed official login and service startup may affect authentication/operational metadata, not authorized shipment evidence.

Final read-only full-collection fingerprints using sorted collection names and sorted _id arrays serialized with EJSON:

- Farm: 455691017e8d895f5d2f6cea27aada2985a214bea6f3771a67ddefe0f1278708
- Cultivation: 41ef681da75217be3e7aff94a84f8d86b1ce83f9eea6a33baef5247cc70cd545

These did not match the earlier captured fingerprints. The earlier serialization shape was not retained sufficiently to establish comparable hashing; equal counts do NOT prove content equality. No assertion of byte-for-byte unchanged database content is made. An initial unauthenticated mongosh read was denied; the authenticated read reused existing container environment without outputting values. Stronger integrity closure requires comparable before/after snapshots or an authorized audit-log review; no destructive corrective action was taken.

The 1850 KG lot remains provenance-unverified. The 1200 KG seed remains excluded for unsupported status/missing season-zone/safe-date problems, not repaired by assumption. No release/dispatch document proves physical shipment. Shipment summary must remain NO_VERIFIED_DATA with null totals when no valid confirmed record exists. Harvest quantities, dossier approval and season end dates are not shipped quantities.

## 8. Concrete Blockers and Next Steps

- Operator must provide a valid authorized farmer auth context locally, not in chat; existing configured bootstrap login fails email validation. No account or role is created/changed to work around this.
- Operator must approve isolated fixture farm/lot/season/zone and released dossier with evidence manifest before live dispatch writes. Harness checks identity/ownership first, defaults read-only, and has no destructive cleanup. Failed runs may leave isolated pending records requiring operator review.
- Provide source weighing, grading, packing, dispatch/delivery documents and release authority before recording actual shipping dates, customers or quantities. Provide grounded corrections for seed scope/date and provenance for 1850 KG.
- Provide registered device-to-farm/zone ownership for telemetry reads. A sole farm is not proof an unmapped device belongs to its user.
- Physical device credentials/ingress policy and ESP32-CAM capture remain separate production/hardware work. Prototype telemetry ingestion was not silently redesigned. Legacy IoT farming_batches identifiers are not verified cultivation shipment relationships.
- Kafka/SMTP outage and duplicate-delivery behavior need authorized integration fixtures; sanitized listener propagation is tested, not an exactly-once delivery guarantee.
- Production direct-service isolation and Gateway revocation fail-open policy remain explicit security decisions, not certified by local tests.
- Resolve fingerprint comparability before asserting complete database-content integrity. No unproven data repair is permitted.

## 9. Git Safety

No commit, push, reset, clean, stash, branch switch, DB reset, business deletion or Docker volume deletion. Existing dirty work preserved. Final diff check passed. Git status, branch and tracked diff were inspected; the accumulated tracked diff includes many earlier changes and untracked files, not solely this acceptance work. No actual credential value is added to this report. Existing insecure default literals were removed, not reproduced here.

Final verdict: **PARTIALLY_COMPLETE**.

## UDP Discovery Follow-up - 2026-10-15

See [MASTER_UDP_DISCOVERY_2026_10_15.md](MASTER_UDP_DISCOVERY_2026_10_15.md) for new source changes, 64 passing fixture-enabled IoT tests, cache/UDP/PostgreSQL integration evidence, the legacy test-isolation safety finding, and timestamped integrity comparisons. Source is not deployed; LAN/physical interoperability remains unverified. ADMIN, five missing diagnosis references and shipment evidence remain blocked. This addendum does not replace the historical results or final verdict above.

## Authorized Farm Deployment Follow-up - 2026-10-15

Operator approved local Farm-only deployment. See the deployment addendum in [MASTER_TREE_SENSOR_FIX_2026_10_15.md](MASTER_TREE_SENSOR_FIX_2026_10_15.md): fresh 88 Farm tests pass, running JAR now contains removeTree and HEALTHY creation defaults, HTTP health is 200/UP, and authenticated read-only Tree Map loads. Only Farm container ID changed; measured Farm/Cultivation Mongo document fingerprints match before/after. Rollback image retained. No tree business write or historical health correction was authorized/performed, and IoT/UDP/camera deployment remains unchanged. This does not constitute live create/remove E2E or project-wide completion.
