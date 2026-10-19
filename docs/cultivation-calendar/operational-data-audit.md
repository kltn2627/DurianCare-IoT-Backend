# Harvest And Shipment Source Audit

Verified on 2026-10-14 against the running local Docker containers. All database operations were reads.
No business records were inserted, updated, deleted or relabelled. No seed was executed.

## Observed Sources

| Source | Observation | Interpretation |
| --- | --- | --- |
| Mongo cultivation_seasons | 5 documents | Calendar ranges; no persisted harvest-completion event |
| Mongo harvest_batches | 2 documents | Persisted quantities require provenance review |
| Mongo export_releases | 0 documents | No recorded release dossiers |
| Mongo lab_samples / lab_residue_results | 0 / 0 | No measured laboratory evidence |
| Mongo cultivation_audit_logs filtered by HarvestBatch / ExportRelease | 0 / 0 | No matching domain audit evidence found |
| PostgreSQL duriancare_iot.farming_batches | 0 rows | IoT compliance prototype cannot supply shipment history |

`harvest-2025-khu-dong` records 1850 KG, harvested 2025-10-15, status HARVESTED.
Its season, farm and embedded farm zone references match `season-2025-khu-dong`,
`farm-khoa-luan-2026`, `zone-c-khu-dong`. This is a recorded quantity, not independently verified
production: no matching source document was found in the inspected seeds/docs, and matching audit
entries were absent. Do not infer that the seed origin is disproven or that a shipment occurred.

`demo-harvest-batch-2026-001` records 1200 KG. Its identifier and the existing
`scripts/seed-cultivation-dev.js` establish a seed lineage, although deployed IDs differ from that script.
Its referenced season `demo-season-2026-vu1` is absent. Its plot `demo-zone-a-vung-trong-demo`
is absent from the farm's embedded zones. Its raw status PENDING_INSPECTION is not a supported enum.
Harvest date 2026-09-15 precedes stored safe date 2026-09-20. It is excluded from the report totals.
Repair requires a verified source mapping and review; this audit does not rewrite it.

## Repeatable Read-Only Report

From repository root, with the existing local Mongo container running:

```powershell
Get-Content -Raw scripts/audit-harvest-data.js | docker exec -i duriancare-mongodb sh -c 'mongosh --quiet --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --file /dev/stdin'
```

Authentication uses the existing container environment; no credential value is emitted by the script.
The report uses allowlisted fields, omitting users, secrets and arbitrary snapshots.
It is an operator database audit, not evidence of authenticated Farmer API ownership.
It does not establish farm/plot existence itself; the embedded-zone reference check above was a separate read.
Run only against an authorized local container. No DB URI or credentials belong in source control.

The report retains original raw statuses. Only valid HARVESTED batches with matching season scope,
nonnegative finite quantity/unit and valid harvest/safe dates contribute to recorded totals.
Quantities aggregate separately per stored unit. Monthly buckets use UTC harvestedAt.
The same grouping can be restricted to cultivationSeasonId for a season report.
JavaScript numeric aggregates are rounded to six fractional digits; source quantity strings are retained.
No cross-unit conversion or agricultural yield estimate is applied.

Current result: recorded harvest 1850 KG, UTC month 2025-10. No released lot is recorded.
Qualified/rejected/packed/shipped quantities, confirmed operational total, completed-harvest season count
and quality-pass rate are null, not invented zero measurements.
When verified grading inputs exist, quality rate is qualified / harvested * 100 within the same unit;
the result remains null for a zero denominator or missing measurements.

## Schema And Dashboard Gaps

HarvestBatch stores total quantity/unit, harvestedAt, scope, market, safe date, risk and status.
It has no grading quantities, packing quantity/date, actual dispatch weight/date, receiver or source evidence.
ExportRelease stores dossier decisions and releasedAt; neither APPROVED nor RELEASED proves a physical
dispatch of the entire harvest quantity. Season ENDED is derived from calendar dates and does not prove
completed harvest. PostgreSQL EXPORTED is a separate prototype lifecycle, not a join to Mongo harvest lots.
Do not sum these two domains or join IoT by device/name without verified farm/season references.

No production dashboard totals were added because required event/source fields are absent.
The current Web CropQrBuilder and ExportCompliancePanel were inspected for lifecycle use; their existing
labels are not evidence of physical shipments. No frontend code changed in this audit.

Before adding schema or operational data, supply validated harvest weighing records, grading records,
packing records and dispatch confirmations, with source reference, unit, timestamps, lot IDs and actor.
A minimal future extension should persist those actual events under the existing harvest lot, include
provenance, support partial shipments, and sum verified event weights. Validate grading <= harvest,
dispatch <= available packed weight, and dispatch >= harvest/packing dates before writes. Define returns,
recalls and corrections before claiming net shipped totals. No default grade ratio or full-lot dispatch
assumption is authorized by the current data.

## Fix And Verification

The Mongo status converter previously promoted every unrecognized stored harvest status to APPROVED.
It now maps unknown values to DRAFT, preserving all supported statuses. Existing stored documents remain
unchanged. This prevents legacy data from acquiring export eligibility during deserialization.
GitNexus impact was UNKNOWN with no resolved callers; Spring Mongo invokes the converter dynamically.
GitNexus concept search was degraded by an unavailable FTS extension, so source inspection supplemented it.

Commands executed successfully:

- `node --test scripts/audit-harvest-data.test.js`: exit 0, 6 pass, 0 fail/skip.
- `mvn -pl duriancare-cultivation-service test`: exit 0, 64 pass, 0 fail/error/skip,
  including 9 converter cases and 37 authorization/harvest/export tests.
- Read-only report through mongosh: exit 0, 5 seasons / 2 batches / 0 releases; 3 issues reported.

Report tests cover missing season/scope, invalid weight/date, safe dates, unit separation,
approval versus release, duplicate/orphan/premature releases and repeatability without input mutation.
No API write E2E, seed idempotency against a database, production verification or physical shipment
verification was performed. The running service image has not been rebuilt/deployed with the converter fix.

Status: PARTIALLY_COMPLETE. Data reconciliation and the unsafe converter fix are verified.
Confirmed grading, packing and shipment reporting is blocked by missing source events and schema fields.

## Evidence Dossier Implementation Follow-up

Continued from the inventory above on 2026-10-14 without re-running the audit or repairing business data.
The new implementation is additive; existing HarvestBatch and ExportRelease constructors/contracts are unchanged.

### Minimal Schema

New Mongo document `harvest_dispatches`, implemented by `HarvestDispatch`:

- `_id` is the existing harvest batch ID, providing one immutable source dossier per lot and atomic duplicate prevention.
- `exportReleaseId` references an existing RELEASED ExportRelease for that lot.
- `farmId`, `plotId`, `cultivationSeasonId` are copied from the persisted lot, never accepted from the client.
- `evidence` holds harvest/grading/packing/dispatch source-document references, measured harvested/qualified/
  rejected/packed/shipped quantities, the existing lot unit, actual packing/dispatch times and destination.
- Server-generated `recordedBy/At` and `confirmedBy/At` identify recording and explicit document confirmation.
- Mongo optimistic `version` protects confirmation writes. No separate status enum is added: null confirmedAt
  means pending verification. A record with source references alone does not qualify for aggregation.

No migration, backfill, automatic seed, repair or write was run. Mongo creates the collection on its first
authorized insert; `_id` uniqueness is native. Current old lots remain unverified, with no inferred shipments.
The new DTO is distinct from the old release dossier, because release permission is not a physical dispatch.

### APIs

All routes use the existing Gateway cultivation route and trusted identity headers from JWT authentication.
Requests must go through Gateway; the service uses the existing FarmAccessClient/CultivationAccessGuard.
Missing identity is 401; denied farm/zone permission is 403; missing lot/dossier is 404; invalid evidence is 400.

| Method and route | Purpose / required fields | Persistence |
| --- | --- | --- |
| GET /api/v1/harvest-batches/{id}/dispatch | View a dossier; lot view permission | Read only |
| POST /api/v1/harvest-batches/{id}/dispatch | `exportReleaseId`, `evidence`; create permission | Insert pending dossier; identical retry returns existing; different duplicate rejected |
| POST /api/v1/harvest-batches/{id}/dispatch/confirm | `sourceDocumentsChecked: true`; update permission | Revalidate, record confirming actor/time; confirmed retry returns existing |
| GET /api/v1/harvest-batches/dispatch-summary?farmId=...&cultivationSeasonId=... | Farm view permission, optional season filter, per-zone visibility | Read only |

No client-supplied actor or verification timestamp is trusted. Source confirmation is an authenticated
operator attestation that the referenced documents were checked, not an independent document-signature
validation, government certificate or second-person approval. An actor may confirm their own dossier if
their existing farm permission permits update; no new supervisory role is invented.

Validation resolves persisted lot -> season and release -> lot; checks matching farm/zone/season,
HARVESTED status, a RELEASED dossier with release time, source references, positive harvest and dispatch
weight, nonnegative measured weights and exact matching units. Harvest evidence must equal the stored lot
quantity. Qualified + rejected <= harvest, packed <= qualified, shipped <= packed. Packing >= harvest,
dispatch >= packing/release, dispatch <= current time, harvest within season and not before safe date.
The FarmAccess service remains responsible for validating actual farm/zone membership and permission.

Recording/confirmation does not edit the harvest lot, release, season, seed, notifications or IoT records.
There are no Kafka events or additional task/notification side effects. Single-document insertion and
confirmation avoid a multi-collection partial-write sequence; a failure does not leave generated plans/tasks.
No update/delete/correction endpoint is introduced for immutable source evidence.

### Reporting Semantics

The summary revalidates confirmed records against current persisted relationships, lot/release eligibility,
source references, measurements and chronology. Changed/orphan/invalid dossiers are excluded and reported
by ID; invisible farm-zone records are omitted. No pending dossier or old lot alone contributes.

If none qualify: `status: NO_VERIFIED_DATA`, `quantitiesByUnit: null`, `shippedByMonth: null`.
The actual count of confirmed lots is 0; this is not a claim of measured zero shipped weight.
Otherwise status is `OPERATOR_CONFIRMED_RECORDS` with sums per stored unit, BigDecimal arithmetic,
UTC dispatch-month totals, and qualified / harvested * 100 rounded to four decimal places (null for a zero
denominator). Totals cover only qualifying dossiers, not all farm production or all ended seasons.
No remaining/ungraded weight is automatically treated as rejected.

The original mongosh audit script still inventories raw harvest/release data. Its 1850 KG recorded total
is not this new evidence-confirmed summary, and its unknown shipment fields remain unknown.
Frontend integration has not been implemented or claimed; consumers of the new summary should display
"Chưa có dữ liệu được xác minh" for NO_VERIFIED_DATA, not a zero-weight chart.

Limitations: one consolidated dispatch per lot; partial weight is supported in that record but a second
dispatch is intentionally rejected. Multi-trip shipments, returns, recall accounting and corrections need
an explicit event-ledger workflow before use. A recalled release currently fails revalidation and is excluded;
the summary is not an all-time gross or net shipment ledger. Document authenticity and physical delivery
remain operator responsibilities; the API does not create their evidence.

### Tests, Local Readiness And Deployment

- `mvn -pl duriancare-cultivation-service test`: exit 0, 86 tests, 0 failures/errors/skips.
  Includes 18 new service tests, 4 MVC tests and the 9 converter cases.
- `node --test scripts/audit-harvest-data.test.js`: exit 0, 6 pass, 0 fail/skip.
- `git diff --check` for cultivation/docs: exit 0; Windows line-ending warnings only.
- Local count verification: 2 harvest batches, 0 releases, 0 dispatch records. Added/updated business rows: 0.
- Existing container health: HTTP 200, UP. Compose project `duriancare`, image
  `duriancare/cultivation-service:local`, container port 8084 -> host 18084.
- Container Mongo URI matched the local `mongodb:27017/duriancare_cultivation` Compose database
  without printing its credentials. Current image ID:
  `sha256:5690354e8e30bf62b704b51d9670f18bb066890da7c9a9fc21bf1252caa5109d`.
- Unauthenticated GET through the real Gateway at port 8080 for the new summary path returned 401.
  This proves Gateway rejection only; the new controller is not deployed.

Converter deployment is still PENDING. The current container was not rebuilt/replaced. Its health is not
evidence it runs the fix. Rebuilding from this workspace would include existing uncommitted cultivation
DTO/service changes as well as this additive feature, so no automatic deployment was performed.
GitNexus reports UNKNOWN for new unindexed symbols; source/test review defines the scope here.

After reviewing the full cultivation diff and local configuration, proposed commands (NOT executed):

```powershell
docker compose -p duriancare --env-file infrastructure/.env -f infrastructure/docker-compose.yml build duriancare-cultivation-service
docker compose -p duriancare --env-file infrastructure/.env -f infrastructure/docker-compose.yml up -d --no-deps duriancare-cultivation-service
Invoke-RestMethod http://localhost:18084/actuator/health
```

Record the previous image ID before deployment and retain it for rollback; recreate only this service.
No volume removal, database reset or business seed is required. Recheck image identity, health and
authenticated read-only Gateway responses after restart. Existing service ports are exposed to the host;
as with the existing trusted-header APIs, block untrusted direct service access before broader rollout.
No live authenticated new-API/write E2E or deployment has been claimed.

Still required from the operator: source weighing document for the 1850 KG lot; verified corrections
for the 1200 KG seed season/zone/safe-date conflicts; actual grading/packing/dispatch documents,
weights, unit, receiver/destination and times; valid review/release dossier through the existing workflow.
Absent evidence remains unknown. Status remains PARTIALLY_COMPLETE pending those sources and runtime verification.

## Review And Local Deployment Result (2026-10-14 / 2026-10-15)

This section supersedes the earlier PENDING deployment status. Existing business data was not modified.

### Review Findings And Fixes

1. Read and duplicate-create paths authorized the current harvest lot but did not check stored dispatch
   scope before returning it. Both paths, including the insert-race retry, now require matching lot ID,
   farm, zone and season. Confirmation and reporting reuse this same scope guard.
2. Existing accumulated season changes introduced two constructors without a selected Spring injection
   constructor. A new Spring wiring test reproduced BeanCreationException / No default constructor found
   (1 test, 1 error, Maven exit 1). Added @Autowired to the production constructor; clock-injected unit
   tests are preserved. This was a demonstrated deployment blocker, not a speculative refactor.

GitNexus impact was run before edits. Results were UNKNOWN: new dispatch symbols are not indexed, and
season construction is reached through Spring injection. Callers were traced manually: controller get/record
and repository insert-race replay reach the new scope check; confirmation and summary validate the same scope.

Authorization review: GET requires VIEW on the persisted lot scope; POST record requires CREATE;
POST confirm requires UPDATE; summary requires farm VIEW plus per-zone visibility. Farm/zone/season
and recorded/confirming actors come from persisted sources and Gateway identity, not request body fields.
Existing FarmAccessCheckService checks owner or active delegated permission and embedded-zone membership.
The current trust boundary still requires Gateway JWT authentication and controlled direct service access;
401 without headers does not prove cryptographic authentication of arbitrary X-Auth headers at the service.

### Executed Tests

| Check | Actual result | Evidence scope |
| --- | --- | --- |
| mvn -pl duriancare-cultivation-service test, before deployment | Exit 0, 94 pass / 0 failure/error/skip | Module regression |
| Same Maven command after deployment | Exit 0, 94 pass / 0 failure/error/skip | Host tests rerun; not live E2E |
| HarvestDispatchWorkflowIntegrationTest | 7 pass, included above | MockMvc -> real controller/service/access guard -> in-memory repository fixture; FarmAccessClient is mocked |
| CultivationSeasonWiringTest | 1 pass after fix | Actual Spring bean construction with mocked external dependencies |
| python -m unittest discover -s scripts -p test_dispatch_e2e_harness.py | Exit 0, 3 pass | No credential/no fixture/non-local guards stop HTTP writes |
| python -m py_compile scripts/dispatch_e2e_harness.py | Exit 0 | Syntax |
| node --test scripts/audit-harvest-data.test.js | Exit 0, 6 pass / 0 fail/skip | Original read-only report regression |
| python scripts/dispatch_e2e_harness.py | Native exit 2, BLOCKED_AUTH_CONTEXT, no API steps | Actual harness run; authenticated live E2E blocked |

The workflow fixture tests exercise create -> GET -> pending summary -> confirmation -> confirmed summary
and retry, unauthorized create/read/confirm/report, view-only actor denied confirmation, forged body scope/
confirmedBy ignored, wrong lot/release or season, excess weight, invalid chronology, stored scope corruption
and missing identity. Fixture documents/receiver are test-only constants; no fixture was inserted into Mongo.
The integration tests do not perform login, JWT validation or real Mongo persistence.

### Actual Deployment

Confirmed local Compose project duriancare, local Mongo database and existing Dockerfile/configuration.
.dockerignore excludes .env files and secrets from the build context. Reviewed cultivation changes include
season status DTO, export eligibility/transition guards, converter, additive dispatch APIs and constructor fix.

Executed successfully, exit 0:

```powershell
docker tag sha256:5690354e8e30bf62b704b51d9670f18bb066890da7c9a9fc21bf1252caa5109d duriancare/cultivation-service:pre-dispatch-review-20261014
docker compose -p duriancare --env-file infrastructure/.env -f infrastructure/docker-compose.yml build duriancare-cultivation-service
docker compose -p duriancare --env-file infrastructure/.env -f infrastructure/docker-compose.yml up -d --no-deps --no-build duriancare-cultivation-service
```

Only cultivation image was built and only cultivation container was recreated. Other containers retained
their pre-deployment uptimes. Volumes and existing records were preserved. Docker build's -DskipTests
does not replace the separately executed passing host Maven tests.

Running image: `sha256:754dabbf24756802ca2a5e6b681aa847854fbafceef8c80ca0c5bf8ad53a22cd`.
Container started at 2026-10-14T16:59:30.832804572Z (23:59:30 local).
Health GET localhost:18084/actuator/health returned HTTP 200, UP.
Copied deployed app.jar to ignored target/deployed-review.jar for inspection: SHA-256 of embedded
MongoConfig$StringToHarvestBatchStatusConverter.class and HarvestDispatchService.class matched the
host compiled classes. Converter fix is now deployed and its 9 automated regression cases pass.

Unauthenticated GET dispatch-summary returned 401 at both localhost:18084 (service) and localhost:8080
(Gateway). No forged identity or fabricated JWT was used in runtime checks. No authenticated write test
ran against this container. Post-deployment DB counts remain 2 harvest batches, 0 releases, 0 dispatches.

### Authenticated E2E Prerequisites

Added scripts/dispatch_e2e_harness.py. It reuses official login or operator-provided access-token handling
from demo_e2e_harness.py. The current process has none of DURIANCARE_E2E_ACCESS_TOKEN,
DURIANCARE_E2E_EMAIL or DURIANCARE_E2E_PASSWORD. Do not send their values in chat.

The runner accepts local HTTP Gateway addresses only. It verifies /api/users/me, FARMER role, owned farm
and lot scope. It requires an operator-created manifest path via DURIANCARE_DISPATCH_E2E_MANIFEST,
with approvedIsolatedFixture=true, farmId and harvestBatchId prefixed dispatch-e2e-, and recordRequest
containing the actual authorized fixture's release ID and source evidence. No manifest, account, farm,
lot, release, credential or business document is generated by the harness.

Default execution is read-only. After identity/ownership and empty-summary checks, live fixture writes
require DURIANCARE_DISPATCH_E2E_ALLOW_WRITE=1, explicitly set by the operator. It was NOT set here.
The global DURIANCARE_E2E_ALLOW_WRITE was not enabled. The runner then checks pending exclusion,
persisted confirmation actor/scope, exact shipped total and same-ID replay. It issues no cleanup/deletion.
Failure may leave a pending or confirmed record in that isolated fixture; inspect it before retrying.

Status: deployment and fixture integration PASS; authenticated live write E2E BLOCKED_AUTH_CONTEXT and
BLOCKED_ISOLATED_FIXTURE. No live business shipment is claimed. The 1850 KG lot remains unverified,
and the invalid 1200 KG seed remains excluded. Source weighing, grading, packing, dispatch and release
documents plus grounded seed corrections are still needed before operational shipment totals exist.

### Master Backend Acceptance - 2026-10-15

See [master acceptance evidence](../../artifacts/finalization/MASTER_BACKEND_ACCEPTANCE_2026_10_15.md).
Final Maven reactor: 304 tests, 303 pass, 1 deliberate debug skip, no failure/error.
All 10 checked application services returned internal health 200/UP. Cultivation's previous
deployment was preserved; selected auth/Gateway/notification/search/IoT/config fixes were deployed.
Official configured login returned HTTP 400 with email validation; authenticated live dispatch E2E
remains BLOCKED, not passed. No write mode was enabled and no shipment was created or confirmed.
Farm/cultivation document counts remain 323/52 and auth user count 16. Final fingerprints differ
from earlier fingerprints whose serialization shape was not retained: content equality is NOT proven
by equal counts. The master report records this integrity limitation rather than claiming a match.
The 1850 KG lot stays unverified; invalid 1200 KG seed stays excluded. No actual export/dispatch
evidence exists to replace NO_VERIFIED_DATA/null shipment totals. Verdict: PARTIALLY_COMPLETE.

### Final Acceptance Follow-up - 2026-10-15

See [final acceptance evidence](../../artifacts/finalization/FINAL_ACCEPTANCE_2026_10_15.md).
Official login 400 was traced to an existing bootstrap email containing two at-signs; validation is
correct and was not weakened. No identity/password/business record was rewritten to obtain a token.
Final Maven reactor with actual local Mongo fixture enabled: 313 pass, 1 debug skip, no failure/error.
Four isolated Mongo persistence tests now cover pending/confirmed summaries, validation/authorization,
scope, retry and concurrent identical submissions. All fixture documents were removed only from their
UUID databases; seven empty namespaces remain, no existing database was dropped. These are NOT live
JWT/Gateway E2E tests. Both live write flags remain off; farmer auth context is still unavailable.

Versioned canonical fingerprints match before/after across current farm/cultivation collections and all
42 captured PostgreSQL tables. This proves the current measurement window, not prior historical content.
No release/dispatch exists in the business database; 1850 KG remains unverified and invalid 1200 KG seed
excluded. Real source documents are still required; shipment totals remain NO_VERIFIED_DATA/null.
Three camera software defects were reproduced and corrected; IoT 48 tests pass and only IoT was
redeployed. No recently online registered camera was found: HARDWARE_NOT_TESTED. All 10 checked
application services returned 200/UP. Final verdict remains PARTIALLY_COMPLETE.

### Final Blocker Closure Checkpoint - 2026-10-15

See section 10 of [final acceptance](../../artifacts/finalization/FINAL_ACCEPTANCE_2026_10_15.md) for fresh
evidence and executable operator checklists. No application code/config or deployment changed here.
Fresh tests: LoginContract 6 + dispatch service 18 + workflow 7 = 31 PASS; camera HTTP simulation 6 PASS.
Prior full reactor 313 pass/1 skip, full IoT 48 and AI 26 are historical runs, not new totals for this checkpoint.
Runtime: 10 services 200/UP; unchanged IoT/cultivation images; source camera hashes still match deployment.

Authenticated E2E BLOCKED: credentials and isolated manifest absent, official bootstrap login still 400.
Environment administrator/demo Farmer must supply an existing active authorized Farmer context locally;
no account was created, no bootstrap email corrected, no authentication bypassed. Test helpers use mocks
or isolated Mongo and cannot supply a legitimate live identity. Both write flags remain absent.

Physical camera BLOCKED: one registered camera offline; direct /capture at registered/configured endpoints
failed from host, and configured endpoint timed out after 4 seconds from IoT container. No valid frame,
no stream success, no backend capture write. Hardware/network owner must provide a reachable approved
device, actual DHCP/port/firmware and Docker routing; section 10 includes bounded capture/decode/stream
commands. Software simulation does not close hardware acceptance.

Read-only inventory remains 2 lots, 0 releases, 0 dispatches. Shipment evidence BLOCKED. Farm/warehouse/
quality-release custodians must supply harvest/weighing, grading, packing, dispatch/delivery references,
actual weights/unit/dates/destination and the same-lot RELEASED dossier. Confirmation must come from an
authenticated actor with UPDATE on the persisted farm/zone. Missing evidence is not data. 1850 KG remains
unverified; the 1200 KG seed remains excluded pending grounded season/zone/status/safe-date corrections.
The raw audit's recorded harvest quantity is not verified shipment. NO_VERIFIED_DATA/null remains correct.

Fresh closure_mongo_before/after.json match across two business databases; closure_postgres_before/after.jsonl
match all 42 captured tables. closure_integrity_result.json explicitly keeps historicalEqualityProven=false.
No claim about earlier unobserved DB changes, no record repair to force matching fingerprints. Git branch,
HEAD and dirty work preserved; no commit/push/reset, no DB/volume deletion. Verdict PARTIALLY_COMPLETE.

### Auth and Dispatch-focused Closure - 2026-10-15

Camera is deferred by operator request: no camera request/test/change in this follow-up. See section 11
of FINAL_ACCEPTANCE_2026_10_15.md for fresh evidence and exact secure resume/manifest procedure.

New provenance proof: malformed two-@ bootstrap email is defined in infrastructure/.env, matches the
running Auth environment AND one stored ADMIN/ACTIVE account (hashed lookup, no full email exposed).
13 ACTIVE Farmer accounts exist, but no credential/session or approved manifest was provided. Do not
alter existing accounts or OTP state to obtain access. Authenticated live E2E remains BLOCKED.

Found and reproduced a real attribution bug: authenticated export-release create/submit/approve/release/
recall methods accepted client audit actor IDs unrelated to authenticated actor. Five regressions failed
before fix. Reused requireSameActor on those five methods; no DTO/state/farm permission relaxation.
Four-module Auth/Gateway/Farm/Cultivation regression after fix: 270 pass, 5 skip, no failure/error. Four
opt-in Mongo cases separately passed on dedicated UUID databases; 11 fixture namespaces now empty.

Current DB evidence: 2 lots, 0 releases/dispatches/compliance assessments/ExportRelease audit entries.
This is absence of persisted workflow evidence, not proof of historical requests or physical deliveries.
1850 KG lot farm/zone/season links are valid but provenance stays unverified. 1200 KG seed references
missing season/zone, unsupported status and unsafe chronology; excluded, not repaired. Missing same-lot
RELEASED dossier and source evidence prevent dispatch eligibility. No aggregation bug demonstrated;
NO_VERIFIED_DATA/null remains the proper empty-verified-set contract. No business record was created.

Environment/account owner must supply official Farmer auth locally. Fixture custodian must supply an
existing approved isolated manifest (keys/types and commands in section 11). Farm/warehouse/quality
custodians must supply actual weighing/grading/packing/dispatch/delivery evidence and release approval;
no date/weight/customer is invented. Only the changed Cultivation service may be redeployed locally
after passing tests; final image/health/fingerprint evidence is recorded in section 11. PARTIALLY_COMPLETE.

Actual follow-up deployment: ONLY Cultivation rebuilt/recreated, image
23a691b1f6dfee9d8ba9431f0f8f8c47e876260991efe64fbad8db2c959d6e32. 121 deployed class/config entries
match host; health 200/UP, all 10 service health checks UP. Post-deploy authorization/workflow/dispatch
regression: 67 PASS, no failure/error/skip. Gateway rejects missing-JWT/fake-header checks with 401.
Rollback retained at duriancare/rollback-dispatch-audit-actor:20261015; not exercised, no DB reset needed.
Current-window fingerprints match: two Mongo business DBs 02:10:34 -> 02:19:24 +07:00 on 2026-10-15;
42 PostgreSQL tables 02:10:35 -> 02:19:25. auth_dispatch_integrity_result.json retains scope/times and
historicalEqualityProven=false. Auth live E2E/source documents remain BLOCKED; camera deferred.

### Bootstrap Change Control and Positive Release Regression - 2026-10-15

See FINAL_ACCEPTANCE_2026_10_15.md section 12. Only tests/docs changed; no production deployment or
camera work. Reviewed env -> Compose -> local/central Auth YAML -> bootstrap properties -> initializer.
Bootstrap normalizes but does not apply login's @Email rule; JPA uniqueness is not email-format validation.
Known malformed config matches one persisted ADMIN/ACTIVE. Exact historical creator/typo origin remains
unproven. Whether it is currently used is BLOCKED: no last-login column, session metadata read unavailable,
and no session/token contents were read. Do not infer an unused account from this absence of evidence.

Repair approval needs exact UUID, verified corrected mailbox/email, account owner/usage confirmation,
maintenance window, existing alternate-admin recovery and authorized DBA/rollback owner. Disable bootstrap
before changing its email to avoid creating a second ADMIN; use an approved guarded transaction to change
email on the SAME UUID, preserve password/role/status/ownership, then align config and verify official login.
Keep backups protected outside reports/Git; rollback must compare current state and only compensate the
approved row/config fields, never reset the DB. Session invalidation needs separate owner approval and
access-token expiry handling. No account mutation procedure was executed or credential invented.

New tests PASS: three bootstrap safety fixtures (disabled/no touch, existing account/no reset, valid fixture
normalization/create) and one complete same-actor release lifecycle/read/duplicate test. Existing five
forged-actor tests still pass. Fresh Auth/Gateway/Farm/Cultivation run: 274 PASS, 5 SKIP, 0 failure/error;
Auth 80 total, Gateway 12, Farm 83, Cultivation 104. Skips: one live-DB OTP debug helper, four opt-in Mongo
cases (not rerun here). New fixture state is in-memory only; no cleanup touched actual data. Harness safety
3 PASS and read-only audit/fingerprint scripts 9 PASS. Auth/Gateway/Farm/Cultivation health 200/UP unchanged.

Fresh fingerprints match two Mongo business DBs (02:22:18 -> 02:26:53 +07:00) and 42 PostgreSQL tables
(02:22:20 -> 02:26:57 on 2026-10-15); bootstrap_resolution_integrity.json records scope/times and no
historical equality claim. Farmer credential/manifest remain absent, official bootstrap login 400, live E2E
BLOCKED. Resume locally with python scripts/demo_e2e_harness.py, then approved-manifest read-only
python scripts/dispatch_e2e_harness.py; both write flags stay absent until separate fixture approval.
Actual weighing/grading/packing/dispatch/delivery/release documents are still required; 1850 KG stays
unverified, invalid 1200 KG excluded, NO_VERIFIED_DATA/null unchanged. Verdict PARTIALLY_COMPLETE.

## 2026-10-15: Canonical Garden, Notification and Approved Device Follow-Up

See [new execution evidence](../../artifacts/finalization/MASTER_WEB_FARMER_COMPLETION_2026_10_15.md)
and acceptance section 13. Existing historical results above retain their original measurement windows.

The target FARMER/ACTIVE UUID 9ffc2a41-eaa6-4567-ad6d-d6741acafeb7 owns farm-khoa-luan-2026,
four embedded zones and 100 ACTIVE persisted trees (25 each), with five correctly scoped seasons.
Read-only reconciliation found no duplicate tree codes/active normalized positions or invalid positions.
No polygon exists; the SVG map uses normalized positionX/Y rather than geographical coordinates.
No extra farm/zone/tree/season records were seeded. The old tree-map seed deletes documents and was
NOT run; audit repeatability does not certify that old script's idempotency.

Profile's legacy cultivation-area request had hidden errors as empty arrays. It now uses canonical
catalog plus scoped tree APIs and shows 1/4/100, matching the database and maps. Counts in zone list
and detail are 25, marker/table select the same tree, and changing zones does not retain old selection.
No tree/health/diagnosis/care plan was modified during UI checks. Unknown stage/age stays unknown.

The user-reported notification 403 was reproduced and fixed by stripping browser Origin/Referer at
the Web proxy, retaining bearer/session and backend owner checks. Logged-in mark-all changed the
target's 8 unread notifications to read. Separate real Mongo fixtures prove owner-scoped bulk update,
retry/empty behavior and concurrent modified counts; those fixtures were removed by exact IDs only.

After explicit operator confirmation of ownership and destination farm, official authenticated device
registration created esp32-01 registry 2634d1d5-e6a2-4930-aa39-73ada0ee217a in farm-khoa-luan-2026.
Zone is intentionally null pending installation-location evidence. No sensor measurements were seeded.
Sensors now load existing telemetry; latest timestamp is 2026-10-14T16:52:17.067Z, not a new physical
capture. last_seen_at is null and connectivity UNKNOWN. Current 4,578 rows start Aug 12, not June;
1,000-row season view is labeled as loaded samples with a cap, not verified whole-season elapsed time.

New unresolved historical-data finding: five of eight care plans in this farm reference missing diagnosis
IDs (one cancelled, three in-progress, one planned). Their exact IDs are in the new report. All eight
referenced trees exist; the other three diagnoses match their trees/farm. Source evidence/custodian
approval is required before any historical correction. No synthetic replacement or cleanup was done.

Farm/cultivation Mongo hashes match only the newly recorded 05:28:17 -> 05:30:32 UTC window.
PostgreSQL's separate 42-table before/after registration comparison shows only iot_devices changed;
users and telemetry hashes match. Notification mutation is separately disclosed, not hidden behind
a blanket unchanged-database claim. Evidence JSON paths are linked in the detailed report.

Current regression: full Maven 318 pass / 7 skip; 2 additional opt-in Mongo notification cases pass;
Web 55 pass + typecheck/lint/build pass (8 old warnings); IoT non-camera API 24 pass; read-only audit/
fingerprint 13 pass; harness unit 3 pass. CLI live-auth harness remains BLOCKED_AUTH_CONTEXT,
while existing authenticated browser UI did run the explicitly described operations.

Malformed ADMIN remains unchanged. Read-only preflight and secured backup/transaction/rollback
runbook are prepared; replacement email approval/control, current usage and usable backup login are
still external prerequisites. No shipping/release/dispatch/evidence data was created. The 1850 KG lot
remains unverified, invalid 1200 KG seed excluded, NO_VERIFIED_DATA/null contract unchanged.
Camera, firmware and physical-camera tests were deferred and untouched in this execution.

Classification: **FIXED BUT NOT FULLY VERIFIED**; no project-wide COMPLETE claim.

## Farmer Export Self-Verification Follow-Up (2026-10-16)

The operator approved Cultivation-only local deployment and two explicitly DEMO_SYNTHETIC chains,
not real shipping evidence. See [execution report](../../artifacts/finalization/FARMER_EXPORT_SELF_VERIFICATION_2026_10_16.md).
Farmer self-inspection now has a dated source endpoint, ownership guards and fresh compliance/PHI
gates. Season productionStatus is independent of release/dispatch; no existing season was transitioned.
Dispatch delivery evidence is additive; confirmed shipping and delivery are separate from RELEASED.

Exactly six documents were inserted: two lots, two releases, two dispatches. Ri6 is 3500 KG, Aug 28/29,
zone-d / season-2026-khu-tay, DISPATCHED; Monthong is 5000 KG, Sep 14/15/16,
zone-c / season-2026-khu-dong, DELIVERED. Both are scenario-only, clearly prefixed DEMO-SYNTHETIC,
with no real laboratory evidence, customer or source certificate. Repeat execution inserted zero.
No operational record was overwritten; 1850 KG remains unverified and the invalid 1200 KG seed excluded.
The dispatch summary excludes synthetic batches/releases. NO_VERIFIED_DATA/null remains the contract
when no valid operational record qualifies; a demo release is not a verified shipped quantity.

Cultivation 110 tests pass / 4 opt-in Mongo skipped; Farm 88 pass; Web 84 pass, typecheck/lint/build pass
(8 old warnings); Mobile typecheck and all-platform export pass; seed tests 3 pass. Only Cultivation
was recreated; health 200/UP; 127 deployed class files match tested host output. Farm/Cultivation
non-fixture Mongo hashes match only the measured 08:49:23Z -> 08:52:22Z local-host execution window.
The new harvest_dispatches collection contains two fixtures and zero operational records.

Direct DB fixture seeding and MockMvc tests do not constitute authenticated live write E2E. Hardware
and real business evidence are not certified by this update. Existing unrelated changes are preserved.

After the operator re-logged in, authenticated Web reads showed Ri6 DISPATCHED / 3500 KG and Monthong
DELIVERED / 5000 KG from the persisted dispatch API. A reproduced wrong-first-row QR snapshot bug
was fixed and retested: the selected Monthong QR now loads the Monthong lot / zone C / matching
season, retaining DEMO_SYNTHETIC and realLabEvidence=false. No live business write was used for this check.
