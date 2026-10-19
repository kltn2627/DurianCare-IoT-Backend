# Final Team Push Review - 2026-10-16

Verdict: **PARTIAL**. Source fixes, automated regression and authenticated read-only Web checks PASS.
Authenticated live write E2E and native-device checks are NOT TESTED. No commit/push or new deployment
was performed; this is a review package, not a production-readiness certification.

## Workspace and References

Verified repositories under C:/Users/minhd/OneDrive/Desktop/KhoaLuanTotNghiep_26-27:

| Repository | Branch | Initial status entries |
| --- | --- | ---: |
| DurianCare-IoT-Backend | duy/cleanup-fixes | 138 |
| DurianCare-IoT-Web-Client (actual Web name) | duy/cleanup-fixes | 45 |
| DurianCare-IoT-Mobile-App | duy/cleanup-fixes | 21 |

Complete current modified/untracked paths, HEADs and tracked diff stats are in
[Git inventory](../artifacts/finalization/TEAM_PUSH_GIT_INVENTORY_2026_10_16.json).
Status counts collapse untracked directories and are not authored-file counts. Inventory is a generated
review artifact, not an instruction to stage everything.

Read the existing MASTER_TREE_SENSOR_FIX_2026_10_15.md (including authorized Farm deployment
addendum), FARMER_EXPORT_SELF_VERIFICATION_2026_10_16.md, operational-data-audit.md, Backend merge
audit, Mobile/frontend auth/error/search docs and Mobile traceability/parity docs. Exact files
docs/SEARCH_PRODUCTION_REVIEW.md, docs/NOTIFICATION_PRODUCTION_REVIEW.md and docs/AUTH_PRODUCTION_AUDIT.md
were not found in current repos; no replacement histories were invented. No Backend docs/testing directory
was found. Existing Postman collection is in Mobile docs/frontend and is historical, not evidence that
newer private cultivation endpoints were executed.

## Findings and Resolutions

| Priority / Item | Root Cause and Action | Evidence / Status |
| --- | --- | --- |
| High: legacy release gate bypass | APPROVED release did not rerun compliance/PHI, unlike INSPECTED_PASSED. Both now require current compliance and harvest date/PHI; invalid future dates fail. Existing statuses/routes retained; RELEASED retry stays no-write. | PASS: new legacy compliance rejection, PHI rejection and successful retry fixtures; prior workflow suite still passes. Not deployed this turn. |
| Medium: synthetic harvest could complete operational season | A matching synthetic harvested lot could satisfy production transition. Excluded synthetic lots and reject missing season start date safely. No season records changed. | PASS: missing/synthetic harvest rejection and existing ordered/owner transitions. Not deployed this turn. |
| Medium: stale/wrong QR snapshot | The prior wrong-first-row fix existed, but a failed reload could leave an old snapshot and overlapping replies could overwrite selection. Web clears before load, invalidates on selection and validates response releaseId; Mobile guards request sequence/identity and shows modal errors. | PASS: selected-ID/missing-ID tests, wrong-response/error tests and authenticated Ri6 -> Monthong read-only smoke. Mounted React race/failure injection remains NOT TESTED. |
| Medium: generic tree error display | Historical root cause was stale Farm artifact missing DELETE plus broad 500 mapping, already fixed/deployed previously; current clients still displayed raw English generic errors on real failures. Added Vietnamese status/network presentation, preserving HTTP errors/body and never returning success. | PASS: 400/401/403/404/409/500/503, network and successful repeat client tests; existing Farm persistence/owner/missing/save-failure fixtures PASS. No real tree removed. |
| Medium: fixture presentation / summary | Long internal names and repeated warnings cluttered normal release cards. Added origin-aware display titles plus concise per-card simulation labels, keeping a shared explicit warning and technical identity in audit/QR detail. Mobile uses the same rule and excludes synthetic release counts. Web reads actual dispatch summary instead of deriving totals from delivery statuses. | PASS: presentation/provenance tests, summary null test and authenticated Web shows no verified operational data. No marker/code/record was changed. |
| Existing sensor fixes | Vietnam-calendar bounds, fixed-to pagination, ID/device/date checks, bounded rendering, 8s polling, scoped fallback and removed IoT alert widget are present. | PASS: 27 IoT read/seed tests plus Web sensor tests/read-only smoke. No new telemetry or camera/UDP change. |

GitNexus query/impact was requested before production-symbol edits. Backend FTS search is degraded;
new symbols/Web are absent from its index, and Mobile reports no resolved callers with risk UNKNOWN.
UNKNOWN is not LOW. Manual paths reviewed: Gateway -> TreeController -> TreeService -> repository;
release controller/actor guard -> service -> compliance/PHI -> save; SeasonProductionController ->
service -> owned harvest; CropQrBuilder -> authenticated proxy client -> snapshot/dispatch/summary;
Mobile traceability/detail and tree removal clients. No HIGH/CRITICAL result was suppressed, and these
partial impact results do not certify the entire accumulated dirty diff.

## Files Authored or Extended This Turn

Backend (paths relative to repo):

- duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/service/CultivationCalendarService.java
- duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/service/SeasonProductionService.java
- duriancare-cultivation-service/src/test/java/com/duriancare/cultivation/service/FarmerExportWorkflowTest.java
- duriancare-cultivation-service/src/test/java/com/duriancare/cultivation/api/FarmerDispatchLifecycleTest.java
- docs/cultivation-calendar/api.md; this readiness report; generated Git inventory JSON.

Web:

- src/components/dashboard/CropQrBuilder.tsx
- src/lib/cultivation/client.ts; new src/lib/cultivation/presentation.ts
- src/lib/trees/client.ts; new src/lib/trees/errors.ts
- src/__tests__/farmer-export.test.ts; new export-presentation.test.ts and tree-errors.test.ts

Mobile:

- src/features/profile/DurianTraceabilityScreen.tsx
- src/features/trees/TreeDetailScreen.tsx
- docs/mobile/08_TRACEABILITY.md

All other dirty files existed at inventory and were preserved, not implicitly approved for team push.
No manifest, dependency, camera/UDP implementation, auth policy, seed script or migration was modified
in this turn. Build output was directed outside source for the new Mobile export.

## Verification Results

| Command / Scope | Exit | Actual Result |
| --- | ---: | --- |
| Backend mvn test -q, full reactor | 0 | 343 total: 336 PASS, 7 SKIP, 0 FAIL/error. Auth 79/1 skip; Gateway 12; Farm 88; Cultivation 113/4 skip; Notification 31/2 skip; Search 13. Counts from current Surefire XML. |
| mvn -pl duriancare-cultivation-service,duriancare-farm-service -am test -q | 0 | Cultivation 113 PASS / 4 SKIP; Farm 88 PASS. |
| Web npm test -- --runInBand | 0 | 101 PASS / 10 suites. |
| Web npm run typecheck | 0 | PASS. |
| Web npm run lint | 0 | PASS, 0 errors / 8 existing warnings in unrelated AI/camera/export components. |
| Web npm run build | 0 | PASS, including TypeScript / 44 generated pages. |
| Mobile npm run typecheck | 0 | PASS after final UI edits. No Mobile unit-test script exists in package.json. |
| Mobile npx expo export --platform all --output-dir C:/Users/minhd/.codex/tmp/duriancare-team-review-mobile-20261016 | 0 | PASS: Android/iOS/Web bundles, 75 static routes. Not a native-device interaction test. |
| IoT node --test test/read-api.test.js test/seed-history.test.js | 0 | 27 PASS; excludes camera/UDP suites. Writer rollback/idempotency cases are mocked, not new real DB writes. |
| node --test scripts/seed-export-dispatches.test.js | 0 | 3 PASS; deterministic IDs, owner/zone/season/future guards, quantity/reference collision rejection. |
| AI python -m unittest discover -s tests -p test_s3_storage.py | 0 | 3 PASS; bounded S3 read/cleanup regression. No model modification/training. |
| Existing mongosh export fixture seed, default dry-run only | 0 | 6 candidate records, 6 existing, 0 pending, 0 inserted. APPLY was NOT set. |
| python scripts/demo_e2e_harness.py | 1 | BLOCKED_AUTH_CONTEXT; writeEnabled=false, steps=[], identity/ownership=null. Not a passed E2E. |
| git diff --check in each repository | 0 | PASS; pre-existing CRLF notices only. |

Skipped Maven cases: one explicit Auth debug/environment case, four opt-in Cultivation Mongo cases,
two opt-in Notification Mongo cases. They were not silently disabled this turn. An initial new Mockito
test stub failed (WrongTypeOfReturnValue from nested mock access); changed the stub to doReturn and
reran successfully. An initial new Web test fixture failed typecheck because it lacked required fields;
supplied the full typed fixture and reran. These failed attempts are not counted as passes.

## Tree and Sensor Contract Review

Tree removal persists REMOVED rather than deleting the tree/diagnoses/plans; retry returns stored state.
Tree Map uses ACTIVE repository filtering. Ownership is resolved from stored tree/farm/zone, not request
IDs alone. Existing mutation and recovery tests cover denied access and protected history. Missing identity
at direct MVC header boundary remains 400; Gateway returns 401 before forwarding unauthenticated calls.
Unsupported method is 405, not generic 500. Save failure still returns an error, never fabricated success.

Both createTree and generateTrees default HEALTHY/ACTIVE without generating a diagnosis. CreateTreeRequest
does not expose an initial health-status field; the explicit-status creation case is not a supported contract
and no permissive new field was added. Existing status update/diagnosis paths and historical trees are unchanged.

Sensor Today/month use UTC+7 calendar boundaries; 24h/seven-day windows remain elapsed intervals.
Pagination fixes to once, rejects duplicate IDs/out-of-device/out-of-range rows and stops explicitly at
20,000 rows rather than silently reporting a truncated total. Missing deployed legacy integration is
labelled incomplete, not full history. Chart subsampling selects existing rows only, not generated points.
The removed alert card stays absent; backend alerts and other notification screens remain untouched.
Source review/static race assertions are not a mounted component race test.

## Data and Authenticated Read-Only Evidence

Direct read-only reconciliation verified the exact fixture chains, not matches by variety/weight:

| Lot ID | Release ID | Existing Zone / Season | Persisted Shipping |
| --- | --- | --- | --- |
| demo-synthetic-export-2026-ri6-lot | demo-synthetic-export-2026-ri6-release | zone-d-khu-tay / season-2026-khu-tay | 3500 KG / DISPATCHED |
| demo-synthetic-export-2026-monthong-lot | demo-synthetic-export-2026-monthong-release | zone-c-khu-dong / season-2026-khu-dong | 5000 KG / DELIVERED |

Both dispatch IDs equal their lot IDs; release -> lot, dispatch -> release, farm, embedded zone, season
and confirmedBy -> farm owner relationships match. Origin remains DEMO_SYNTHETIC / realLabEvidence=false.
No lab result/customer/source evidence was generated. The 1850 KG unverified record and invalid 1200 KG
seed were not changed or reused. No seed apply, DB reset, cleanup/delete, account creation or owner change.
No new database fingerprint window was measured; historical integrity reports retain only their original windows.

Authenticated browser session read the official Web proxy/Gateway paths without cookie/token extraction.
Release cards display Lo Ri6 / Lo Monthong with Mo phong labels; delivery status is read from dispatch records.
Actual summary reads show no verified operational shipment data, not 0 KG or 8500 KG. Ri6 private snapshot
matched zone D / Ri6 lot / tay season; switching to Monthong cleared the snapshot before loading and matched
zone C / Monthong lot / dong season. These are real runtime reads of synthetic fixtures, not real shipments.
No business action buttons were submitted.

Sensor read-only smoke loaded esp32-01, 34.7 C / 70.6% air / 100% soil at one observation, 202 loaded
24h samples and 5019 whole-history samples. Counts change during live ingestion. Selecting Today cleared
the prior displayed range while loading. No physical-camera success or telemetry provenance certification
is inferred from these readings; no sensor backfill was executed.
After Today finished loading, the UI showed 62 actual range samples and 5045 history samples at that
later polling observation, with last measurement 14:56:36 on Oct 16. This is a separate time point, not
a simultaneous comparison to the earlier 202/5019 counts.

## Auth, Search, Notification and S3

Reviewed accumulated changed controllers/config guards and existing regression suites without refactoring:
JWT settings reject short/placeholder secrets; Gateway strips browser-supplied internal identity headers
and rebuilds identity after JWT validation. OTP generation/verification rate limits include notification
routes. Search validates paging/sort and protects internal indexing with configured internal-token comparison.
Notification ownership and atomic owner-scoped mark-all remain tested, including duplicate-suppression/error
fixtures. S3 bounded-read/close behavior passed its focused tests. No live OTP, mail, S3 outage or Kafka outage
was injected. AuthEventPublisher uses bounded synchronous retries but no durable outbox; ambiguous broker
ack/crash windows remain a known reliability risk, not solved by adding architecture in this audit.

## Runtime and Deployment

No service was built/recreated/restarted this turn. Gateway 8080, Farm 8082 and Cultivation 18084 return
HTTP 200/UP. Unauthenticated Gateway tree and export-release reads return 401. Health is readiness only.
Farm container remains 9a8f89a3122d; Cultivation remains 25d03bf4fdcc. The prior deployment/rollback
images remain documented in the previous reports. Current new release/production gate fixes are SOURCE
ONLY until a separately reviewed Cultivation-only deployment; do not infer them from current health checks.

## Review Before Staging

Do not use git add . indiscriminately. Review these accumulated groups together:

1. Farm tree mutation/recovery/care recommendation and related DTO/repository/event tests. Keep schema/API
   changes with their callers; new care source files are untracked and must not be accidentally omitted.
2. Cultivation domain/dispatch/season/converters/controllers/services and their tests/docs; include the two
   gate fixes in this turn. Inspect legacy status compatibility and standalone Mongo partial-failure limits.
3. Auth/Gateway/config-server configuration/rate-limit/security tests; validate required environment names
   before deployment. No secrets should be committed.
4. Notification controller/repository/listeners plus Web/Mobile proxy/client/UI regression files.
5. Sensor read API/history helpers/tests and Web proxy rewrite fix. Keep camera/UDP/discovery changes in
   a separate owner review; they were present at entry and deliberately untouched here.
6. Web/Mobile cultivation/QR/presentation/tree error changes and contract docs, separate from old dependency
   lockfile/Expo upgrades and unrelated dashboard/community/UI changes.

Generated/unreviewed material that should not be blindly pushed:

- Mobile dist-farmer-export, dist-phase-g, dist-phase-h and dist-phase-i: pre-existing export bundles, not
  source. Preserved, not deleted or hidden by a broad .gitignore change. New export is outside the repos.
- Backend artifacts/finalization and artifacts/severity_training: review reports/screenshots/JSON/logs
  individually for PII/runtime data, size and provenance before selecting anything.
- Backend AI datasets/hard_examples/2026-10-14_*: images/metadata need data-owner/provenance review,
  not automatic source staging. Frozen models and severity gate were not changed or bypassed.
- Runtime logs, target/, .next, .expo, local .env and credentials: generated/private, not source. Existing
  ignore rules were inspected; no broad ignore rule was introduced.

A limited content scan of changed source/config/docs (excluding build/artifact folders and files >2 MB)
found no JWT/private-key/AWS-key literal. Six Compose credential-URI regex hits were confirmed to use
environment interpolation, not literal credential pairs; no values were printed. This is not a complete
secret/PII scan of all artifacts or history. That manual review remains required before staging.

## Remaining Gates and Verdict

- PARTIAL (priority review): export-release creation still uses a repository check-then-save for active
  dossiers, without a demonstrated database uniqueness/transaction guarantee for simultaneous requests.
  Sequential create rejection and same-ID release retries are tested; concurrent creation is not certified.
  Inspection/release assessment, save and audit are also not newly atomic across collections. Resolve with
  an approved schema/index strategy and isolated concurrency/failure fixtures before claiming production
  idempotency. No unapproved index migration or live failure injection was introduced in this audit.
- PARTIAL: source/backend correctness is tested; fresh gate fixes are not deployed, and browser evidence
  exercises read-only API/UI rather than the new write gates in the running container.
- BLOCKED: CLI authenticated E2E has no process DURIANCARE_E2E_ACCESS_TOKEN/EMAIL/PASSWORD and no
  approved isolated dispatch manifest. An operator must supply demo-only auth in a local terminal and
  an approved dispatch-e2e-* farm/lot manifest, not paste secrets into chat. Write flags remain unset.
- NOT TESTED: authenticated create/inspect/release/dispatch/confirm/deliver live chain, live cross-user
  denial, injected DB/Kafka/S3 failures, physical Mobile/QR scanning and camera/hardware. No camera work.
- PARTIAL: dispatch/delivery and season-production have API support but not complete new Web/Mobile
  entry forms. No complete native delivery UI is claimed.
- BLOCKED: actual harvest/grading/packing/dispatch/delivery/PHI/lab evidence and counterparties remain
  external inputs before any operational quantities can be verified. Synthetic records cannot close this.
- PARTIAL: full accumulated diff (including camera/UDP, dependencies, AI data and old reports) requires
  the relevant owners' review. GitNexus coverage is incomplete; automated passes alone are not approval.

When isolated fixture/auth inputs are approved, first run python scripts/demo_e2e_harness.py read-only;
then review DURIANCARE_DISPATCH_E2E_MANIFEST and run python scripts/dispatch_e2e_harness.py without
write flags. Any later write-enabled run needs separate explicit scope/cleanup approval. No admin email
repair, new identity or fake JWT was used to close the current lack of CLI auth context.

Final review state: **PARTIAL**. No commit, push, reset, clean, stash, branch switch, business deletion,
volume operation or secret output was executed. Existing unrelated uncommitted work remains present.

Final Git status entries: Backend 140, Web 49, Mobile 22; untracked file expansion is 186 / 23 / 354
respectively, including old generated exports/artifacts. Exact paths and unchanged branch/HEAD values
are recorded in the linked inventory. These counts are not source files approved for staging.

## Follow-Up: Export Presentation Only (2026-10-16)

Scope result: **PASS** for automated presentation/summary regression and authenticated read-only Web
smoke. Physical Mobile UI remains **NOT TESTED**. The broader project verdict above stays PARTIAL.
This section supersedes the earlier mixed-list presentation description, not its historical evidence.

### What Changed

Web and Mobile default to a separate Van hanh view. Du lieu minh hoa is an explicit second view;
synthetic batches/releases never enter the operational list. The illustrative view is read-only: create,
self-inspection and release actions are not offered for fixture records. There is one concise scope
notice, with short Mo phong labels rather than repeated warning paragraphs on every business card.

Normal Ri6/Monthong cards show human-readable titles, actual season names and formatted 3.500 kg /
5.000 kg quantities. The Web resolves season names via its existing authorized catalog API. Missing
names stay unavailable rather than fabricated. Internal fixture codes, IDs and raw snapshot payloads
are available only in explicitly opened technical/source details; no provenance is deleted or relabelled.
Mobile list cards no longer show actor UUIDs or shortened synthetic IDs as business names. The obsolete
public-QR claim is removed; private snapshot access still uses the existing authenticated contract.

The source record dataOrigin, reserved codes, PHI, lab/evidence fields, counterparties and shipping
states were not changed. Backend summary exclusion was not modified. Mobile operational counters
exclude fixtures; illustrative counters are not presented as operational totals. The existing Web
dispatch-summary API still reports NO_VERIFIED_DATA with null quantities in this local environment;
there is no invented 0 KG total or 8500 KG operational shipment.

### Actual Files in This Follow-Up

Web repository:

- src/components/dashboard/CropQrBuilder.tsx
- src/lib/cultivation/presentation.ts
- src/__tests__/export-presentation.test.ts

Mobile repository:

- src/features/profile/DurianTraceabilityScreen.tsx
- src/features/profile/exportPresentation.ts (new, pure display/provenance helpers)
- tests/export-presentation.test.cjs (new, Node tests of those helpers)

Backend: only this report was edited. No backend production symbol, database schema, API, seed script,
auth module or unrelated UI was changed in this follow-up. Existing dirty files remain untouched.
GitNexus upstream impact was requested for touched presentation symbols; results remain UNKNOWN
with incomplete caller coverage. Manual tracing limited the blast radius to the export screens and
their local display helpers. No HIGH/CRITICAL warning was suppressed.

### Commands and Evidence

| Command | Exit / Result |
| --- | --- |
| Web npm test -- --runInBand | 0; 103 PASS / 10 suites, including separate-origin filtering, unchanged provenance, readable lot/quantity labels, QR selection/error contracts and null verified summary. |
| Web npm run typecheck | 0. |
| Web npm run lint | 0; 0 errors, the same 8 pre-existing warnings outside scope. |
| Web npm run build | 0; TypeScript and 44 generated pages PASS. |
| Mobile node --test tests/export-presentation.test.cjs | 0; 2 PASS: origin-marker recognition, fixture/operational separation, unchanged source, labels and quantities. Helper tests, not mounted native UI tests. |
| Mobile npm run typecheck | 0 after final presentation edits. |
| Mobile npx expo export --platform all --output-dir C:/Users/minhd/.codex/tmp/duriancare-export-presentation-20261016 | 0; Android/iOS/Web and 75 static routes. New output is outside source repositories. |
| mvn -pl duriancare-cultivation-service -am test '-Dtest=HarvestDispatchServiceTest,FarmerDispatchLifecycleTest' '-Dsurefire.failIfNoSpecifiedTests=false' -q | 0; 18 + 5 = 23 PASS, no skips/failures/errors. Includes synthetic release exclusion and NO_VERIFIED_DATA/null aggregation, owner/scope and persisted dispatch lifecycle. Mock/service fixtures, not new live writes. |
| Web/Mobile git diff --check | 0; pre-existing CRLF notices only. |

Mobile has no configured lint script; no Mobile ESLint pass is claimed. An intermediate Mobile
typecheck caught incomplete local prop/type updates; completed the presentation changes against the
actual DTO (which has no HarvestBatch.variety field), then reran typecheck/export successfully.

Authenticated Web smoke used the existing Farmer session without extracting credentials. Van hanh
showed no fixture QR and the actual no-verified-data summary. Du lieu minh hoa showed Lo Ri6 / Lo
Monthong, actual Khu Tay/Khu Dong season names and 3.500 kg / 5.000 kg without internal codes in the
normal card text. QR modals expose codes only under collapsed technical details. Monthong snapshot
matched its monthong lot / zone-c / dong season; switching to Ri6 first cleared the snapshot and then
loaded its ri6 lot / zone-d / tay season. Expanded source details retained DEMO_SYNTHETIC and
realLabEvidence=false. Request-error handling remains covered by Web client regression; no live
network-failure injection or physical QR scan was performed.

No seed apply, deploy, commit, push, reset, clean, stash, branch switch, business deletion, credential
change or volume operation was executed. No actual shipment or certification is claimed. Native-device
visual validation and live write E2E remain outside this presentation-only completion evidence.

## Follow-Up: Unified Operational Lists (2026-10-16)

Supersedes the separate-origin presentation described immediately above. PASS for the scoped
presentation change; this does not upgrade overall project readiness or verify real shipments.

- Web CropQrBuilder and Mobile DurianTraceabilityScreen now include fixtures in the same
  scope-authorized harvest/release lists. Removed origin tabs, separate notices and simulation
  captions. Existing farm/zone/season filters remain; there is no special filter needed for fixtures.
- Friendly existing variety names, catalog season names and Vietnamese quantities remain.
  Missing variety uses a neutral label, not an invented cultivar. Technical provenance remains
  in collapsed details. No API, database, evidence, dataOrigin, PHI or dispatch status was changed.
- Fixture creation/transition protections moved from the removed view switch to per-record guards:
  synthetic lots are excluded from create-release choices; synthetic releases cannot be submitted,
  inspected or released through ordinary UI controls. Mobile keeps its empty synthetic action list.
- Verified counters still exclude synthetic records. Backend summary remains authoritative;
  NO_VERIFIED_DATA/null remains unknown, not zero. Displayed list size is not verified shipment mass.
- QR request sequencing, selection checks, clear-before-load and error behavior are unchanged.

Files edited in this follow-up:

- Web: src/components/dashboard/CropQrBuilder.tsx; src/lib/cultivation/presentation.ts;
  src/__tests__/export-presentation.test.ts.
- Mobile: src/features/profile/DurianTraceabilityScreen.tsx;
  src/features/profile/exportPresentation.ts; tests/export-presentation.test.cjs.
- Backend: this report only; existing service source was not edited.

Actual reruns (all exit 0):

| Command | Result |
| --- | --- |
| Web npm test -- --runInBand | 104 PASS, 10 suites. Includes existing QR/client error/null-summary tests and added source-contract guard check. |
| Web npm run typecheck | PASS. |
| Web npm run lint | 0 errors, 8 existing warnings in diagnosis/camera/export-compliance files outside this scope. |
| Web npm run build | PASS; 44 pages generated. |
| Mobile node --test tests/export-presentation.test.cjs | 3 PASS. Pure helper/source-contract checks, not rendered native tests. |
| Mobile npm run typecheck | PASS. |
| Mobile npx expo export --platform all --output-dir C:/Users/minhd/.codex/tmp/duriancare-merged-export-ui-20261016 | PASS Android/iOS/Web; 75 static routes; output outside repositories. |
| mvn -pl duriancare-cultivation-service -am test '-Dtest=HarvestDispatchServiceTest,FarmerDispatchLifecycleTest' '-Dsurefire.failIfNoSpecifiedTests=false' -q | 23 PASS (18 service + 5 MockMvc lifecycle); no failures/errors/skips. Synthetic exclusion and null summary unchanged. No live business writes. |
| git diff --check in all three repositories | Exit 0; existing CRLF conversion notices only. |

Authenticated read-only Web smoke at localhost:3000/dashboard/client/crops: the default QR list
contains both Ri6 3.500 kg (Da xuat xuong) and Monthong 5.000 kg (Da giao hang), with actual
Khu Tay/Khu Dong season names, no origin selector or simulation caption. The verified summary
still reports no verified operational shipment data. Expanded Ri6 snapshot matched ri6-lot /
zone-d / tay-season. Selecting Monthong cleared the previous snapshot (Chua tai snapshot)
before loading monthong-lot / zone-c / dong-season. Both retained DEMO_SYNTHETIC and
realLabEvidence=false in technical details. No credentials were extracted.

Limitations: no native-device/emulator visual test, physical QR scan or live network-failure
injection in this run. Mobile has no configured lint script. Source-contract tests do not substitute
for mounted UI behavior tests. GitNexus Web repository is not indexed; Mobile JSX caller coverage
is incomplete (UNKNOWN impact), so code callers were manually cross-checked.

All repositories retain extensive pre-existing dirty work: Backend auth/farm/AI/IoT/notification/
cultivation/infrastructure/scripts; Web proxies, camera/sensors/trees/notifications and cultivation;
Mobile navigation, notifications/trees, dependency files and prior export output directories.
None was reverted or included as new work in this follow-up. No seed apply, deployment, commit,
push, reset, clean, stash, branch switch, data deletion or volume operation was performed.
