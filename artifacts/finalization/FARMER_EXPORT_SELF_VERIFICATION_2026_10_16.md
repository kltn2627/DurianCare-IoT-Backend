# Farmer-Controlled Export Workflow

Report date: 2026-10-16 (operator timezone Asia/Bangkok). Execution timestamps below are the UTC timestamps returned by the local host, not inferred business event dates.

## Scope and Approval

The operator explicitly approved local Cultivation-only deployment and exactly two DEMO_SYNTHETIC chains in farm-khoa-luan-2026. No existing harvest lot, season, tree, diagnosis, account, source document or customer was changed. All three repositories remain on duy/cleanup-fixes with accumulated uncommitted work preserved. No commit, push, reset, branch switch or volume deletion was performed.

## Findings and Changes

| Finding | Resolution / Evidence |
| --- | --- |
| Backend already used farm/zone access guards, but Web FARMER lacked EXPORT_RELEASE_APPROVE | Granted the Farmer UI permission; ownership is still enforced server-side, not by the UI permission alone. |
| No explicit dated Farmer self-inspection | Added POST /api/v1/export-releases/{id}/inspect. Source reference, inspection timestamp, PHI check, pest check and quality check are required for a passed inspection; optional measured Brix is validated, not invented. Actor comes from Gateway identity. |
| Dossier approval and season completion could be confused | Added a separate optional season productionStatus field, independent of existing date-derived status/effectiveStatus and export/dispatch state. No existing season was backfilled or changed. |
| Delivery had no persisted evidence/state | Added optional delivery evidence to HarvestDispatch; DRAFT/DISPATCHED/DELIVERED are derived from persisted confirmation/delivery. Optimistic delivery conflicts return 409. |
| Synthetic shipments could pollute verified totals | HarvestBatch and ExportRelease expose dataOrigin. Dispatch summary excludes synthetic batches/releases, preserving NO_VERIFIED_DATA and null quantities/monthly totals when no operational record qualifies. Regression test covers this. |
| QR targeted an unproxied relative API path and was described as public | Web QR now points to the same-origin authenticated Web proxy. Cards and QR warn that DEMO_SYNTHETIC is not certification/shipment evidence. QR is private, not a new public certification mechanism. |
| Read failures previously looked like empty lists | CropQrBuilder now surfaces failed list requests; dispatch 404 remains missing data, other failures show an error. Delivery is loaded from the dispatch API rather than inferred from RELEASED. |
| Reproduced live: Monthong QR's snapshot button loaded the first row (Ri6) | Persisted the selected release ID in UI state and clear the previous snapshot when switching. New pure selection regression covers second-row selection and missing IDs. Retested in the authenticated browser: Monthong snapshot now contains the Monthong lot, zone C and matching season. |
| Seed dry-run failed in mongosh while Node tests passed | Replaced optional method chaining in the variety guard with explicit string conversion. Reran the actual mongosh dry-run successfully before any insertion. |

GitNexus upstream impact was requested before symbol edits. New symbols and frontend symbols were absent from the Backend index; returned risk was UNKNOWN, not LOW. Manual controller/service/repository/client call paths were reviewed. This does not close earlier project-wide accumulated-diff risk findings.

## State and API Contract

All business endpoints remain behind Gateway authentication. Controllers obtain identity from Gateway headers and services resolve farm/plot/season scope from persisted records. New inspection/delivery requests do not accept a creator/approver ID. Legacy userId fields must match the authenticated actor.

| API | Purpose / Guard |
| --- | --- |
| POST /api/v1/export-releases | Existing persisted eligible harvest lot only; farm/zone create permission; active duplicate protection retained. |
| POST /api/v1/export-releases/{id}/inspect | DRAFT/UNDER_REVIEW/BLOCKED/NOT_ELIGIBLE/INSPECTION_PENDING/INSPECTED_PASSED only. Requires update permission, HARVESTED lot, dated source, Brix 0..100 when supplied. Fresh compliance and harvest PHI date are checked. |
| POST /api/v1/export-releases/{id}/release | Existing owner/delegation guards retained. New INSPECTED_PASSED path reruns compliance and PHI checks; RELEASED retry does not write again. Legacy APPROVED workflow remains compatible. |
| GET /api/v1/export-releases/{id}/traceability | Existing authenticated farm/zone-scoped snapshot; not public certification. |
| POST /api/v1/harvest-batches/{id}/dispatch | Requires a matching RELEASED dossier, HARVESTED lot, season/farm/zone consistency, measured quantity balance and dated source references. One record per lot; identical submission is idempotent, conflicting payload rejected. |
| POST /api/v1/harvest-batches/{id}/dispatch/confirm | Farm/zone update permission, explicit document confirmation and all persisted relationship/quantity/date gates. Repeated confirmation returns the persisted record. |
| POST /api/v1/harvest-batches/{id}/dispatch/delivered | Requires confirmed dispatch, sourceRef and shippedAt <= deliveredAt <= now. Identical retry does not write; conflicting evidence is rejected; optimistic write race returns 409. |
| GET /api/v1/harvest-batches/{id}/dispatch | Read ownership guard and persisted scope validation; returns independent shipping status. |
| GET /api/v1/harvest-batches/dispatch-summary?farmId=... | Authorized farm/zone reads; only valid confirmed operational records count. Synthetic records are explicitly excluded. |
| PATCH /api/v1/cultivation-seasons/{id}/production-status | Explicit IN_PROGRESS -> HARVESTED -> COMPLETED only; owner/update access and matching dated positive harvested lot required. Release/dispatch never calls this transition implicitly. |

INSPECTION_PENDING means inspection checks are incomplete; NOT_ELIGIBLE means the compliance/PHI gate failed. Neither changes season productionStatus. Farmer autonomy removes the mandatory external approver, not ownership, PHI, evidence or market compliance requirements. Brix is an optional measured field, not a universal invented legal threshold. Legacy release APIs/states are retained, not silently migrated.

Schema changes are additive optional fields in existing Mongo documents (season productionStatus and dispatch delivery), plus export status enum values. No SQL migration or destructive Mongo migration was run. Existing constructors/date-based API fields remain compatible. Self-inspection writes a current assessment/snapshot and audit event; it is not a multi-collection transaction.

## Approved Synthetic Data

Script: scripts/seed-export-dispatches.js; tests: scripts/seed-export-dispatches.test.js.

| Scenario | Existing scope | Synthetic event dates | Quantity / Shipping |
| --- | --- | --- | --- |
| DEMO-SYNTHETIC-2026-RI6 | zone-d-khu-tay / season-2026-khu-tay | Harvest/inspection Aug 28; dispatch Aug 29, 2026 | 3500 KG / DISPATCHED |
| DEMO-SYNTHETIC-2026-MONTHONG | zone-c-khu-dong / season-2026-khu-dong | Harvest/inspection Sep 14; dispatch Sep 15; delivery Sep 16, 2026 | 5000 KG / DELIVERED |

Farm ownership was checked against persisted owner 9ffc2a41-eaa6-4567-ad6d-d6741acafeb7. Existing season variety/date and embedded zone matches were checked. Each chain adds one harvest_batches, one export_releases and one harvest_dispatches document, with deterministic IDs prefixed demo-synthetic-export-2026-. Both release records are RELEASED; shipping is an independent dispatch property.

The fixtures contain scenario-only PHI 21 days/Brix 31.5 and explicitly realLabEvidence=false. No real no-residue result, buyer company, laboratory record, source certificate or shipment was fabricated. Destination and document references are labelled DEMO_SYNTHETIC/demo://. They demonstrate rendering, not real-world compliance. UI warning remains required even though fixture statuses resemble completed workflows.

Execution uses authenticated mongosh with existing container environment credentials, never printed. Required non-secret gates: DURIANCARE_EXPORT_FIXTURE_ENV=local-demo; DURIANCARE_EXPORT_FIXTURE_OWNER_ID=<approved owner>; dry-run by default. Applying also requires DURIANCARE_EXPORT_FIXTURE_APPLY=true and DURIANCARE_EXPORT_FIXTURE_ACK=SYNTHETIC_NOT_BUSINESS_EVIDENCE. DURIANCARE_E2E_ALLOW_WRITE was not enabled.

Dry-run: 6 candidates, 0 existing, 0 inserted. First approved apply: 6 inserted. Second apply: 6 existing, 0 inserted. Existing IDs, unique codes, ownership/scope and selected immutable quantity/reference fields are preflighted; writes use $setOnInsert and never overwrite existing records. Standalone Mongo does not make six inserts atomic: an interrupted run must be audited and resumed using the same IDs, not cleaned up with broad deletion.

The 1850 KG unverified lot and invalid 1200 KG seed were not reused, corrected or included as verified evidence.

## Actual Commands and Results

| Command | Exit / Result |
| --- | --- |
| mvn -pl duriancare-cultivation-service,duriancare-farm-service -am test -q | 0; Cultivation 110 PASS / 4 opt-in Mongo SKIP; Farm 88 PASS. XML totals 114 and 88 respectively. |
| node --test scripts/seed-export-dispatches.test.js | 0; 3 PASS, including deterministic chains, invalid owner/zone/season/future scenario and collision/no-overwrite. |
| Web npm test -- --runInBand | 0; 84 PASS / 8 suites. Includes Farmer permission, inspect payload without actor claims, error propagation, state labels, persisted dispatch GET/missing data and QR-selected release regression. |
| Web npm run typecheck | 0. |
| Web npm run lint | 0; 0 errors, 8 existing warnings in unrelated AI/camera/export components. |
| Web npm run build | 0; 44 routes generated. |
| Mobile npm run typecheck | 0. |
| Mobile npx expo export --platform all --output-dir dist-farmer-export | 0; Android, iOS and Web bundles / 75 static routes. Native-device interaction was not tested. |
| docker build -f duriancare-cultivation-service/Dockerfile -t duriancare/cultivation-service:local . | 0; Docker build packages with -DskipTests; host Maven test execution above is separate evidence. |
| docker compose -p duriancare -f infrastructure/docker-compose.yml --env-file infrastructure/.env up -d --no-deps --no-build duriancare-cultivation-service | 0; only Cultivation recreated. |
| mongosh seed dry-run, approved apply, repeated apply | All 0 after fixing the dry-run compatibility issue; 0 / 6 / 0 inserts. |
| git diff --check | 0; pre-existing CRLF warnings only. |

An earlier new lifecycle test expected 409 for an invalid production transition; the established exception handler contract returns 400. The assertion was corrected and Maven rerun to the successful results above; production validation was not weakened to satisfy the test.

FarmerExportWorkflowTest has 6 HTTP/service fixture tests: self-inspect/release/retry, foreign actor, forged actor, compliance failure, incomplete inspection, invalid date/Brix and changed compliance. FarmerDispatchLifecycleTest has 4 fixture tests: record/confirm/deliver/retry, foreign access and invalid dates, synthetic total exclusion, and explicit season transitions. These use MockMvc identity headers and controlled repositories; they are NOT live JWT/Gateway write E2E.

## Local Deployment and Integrity

Compose project duriancare, Docker context desktop-linux, port 18084 -> 8084. Desired environment values were compared in memory to the previous container; changed keys=[]; no secret values were output.

- Previous image: sha256:23a691b1f6dfee9d8ba9431f0f8f8c47e876260991efe64fbad8db2c959d6e32.
- Rollback tag: duriancare/cultivation-service:rollback-farmer-export-20261015.
- Deployed image: sha256:8d17e54b3fdbe75dd26227e36755a459a39b526de91ca232a450faa28a89ab54.
- Container ID: 25d03bf4fdcc. Parsed before/after inventory shows only duriancare-cultivation-service changed.
- Health: HTTP 200, JSON status UP (08:51:54Z local-host execution timestamp), repeated successfully afterward.
- Deployed JAR: 127 Cultivation class files exactly match host-tested class files; no differences. New season controller/service/status and dispatch service classes are present.
- Non-fixture Mongo comparison: 08:49:23Z -> 08:52:22Z. Farm and Cultivation collection counts/hashes match when excluding only _fixture=export-dispatch-2026-v1. A new harvest_dispatches collection has zero non-fixture records; this is an expected new collection, not an existing-document change. No PostgreSQL unchanged claim is made for this window.
- Raw bounded evidence is in duriancare-cultivation-service/target/farmer-export-nonfixture-before.json, farmer-export-nonfixture-after.json and farmer-export-containers-before.txt. These generated files contain counts/hashes/container IDs, not credentials.

Rollback is an operator-controlled re-tag of the retained old image and the same Cultivation-only no-deps compose recreation. No rollback was needed or executed. Do not delete fixtures or volumes as a deployment rollback step; fixture audit/resumption is separate.

## Runtime Evidence and Limits

PASS: after deployment/seed, the existing authenticated Web page loaded both DEMO-SYNTHETIC release cards, the matching lot IDs/seasons and harvest dates, with explicit no-certification warnings. No business action buttons were submitted during that read-only check.

The next fresh tab initially reported authentication failure. The operator subsequently signed in again; no browser token/cookie was extracted. Authenticated UI then loaded both dispatch API records: Ri6 displayed "Da xuat xuong" / 3500 KG; Monthong displayed "Da giao hang" / 5000 KG. These labels come from GET dispatch status, not a fabricated inference from RELEASED. Opening Monthong QR and loading its snapshot initially reproduced the wrong-first-row bug described above. After the minimal fix, the same actions loaded dataOrigin=DEMO_SYNTHETIC, harvestBatchId=demo-synthetic-export-2026-monthong-lot, plotId=zone-c-khu-dong and cultivationSeasonId=season-2026-khu-dong, with realLabEvidence=false and demoDispatchStatus=DELIVERED. This is PASS for authenticated read-only rendering of synthetic data, not live write E2E or physical QR scanning.

## Files Changed for This Scope

- Backend: CultivationCalendarController / CultivationCalendarDtos / HarvestDispatchController / SeasonProductionController; ExportReleaseStatus / ExportRelease / HarvestBatch / HarvestDispatch / CultivationSeason / SeasonProductionStatus; CultivationCalendarService / HarvestDispatchService / CultivationSeasonCatalogService / SeasonProductionService; FarmerExportWorkflowTest and FarmerDispatchLifecycleTest.
- Backend scripts: seed-export-dispatches.js and seed-export-dispatches.test.js. The existing Mongo converter, authorization tests and unrelated dirty modules were preserved; accumulated diff is not attributed entirely to this task.
- Web: CropQrBuilder, new FarmerInspectionDialog, cultivation client/types, releaseSelection helper, cultivation permission/labels and farmer-export.test.ts. No unrelated camera/AI/dashboard refactor.
- Mobile: DurianTraceabilityScreen, new FarmerInspectionModal, cultivation types/labels. Generated dist-farmer-export is build output, not source or real shipment evidence.
- Documentation: this report, operational-data-audit.md and acceptance section 14.

NOT TESTED: authenticated live create -> inspect -> release -> dispatch -> confirm -> delivered through Gateway. Direct fixture seeding and MockMvc are not that E2E. Cross-account live denial, concurrent release/inspection races and physical mobile/QR scanning remain unverified. Delivery optimistic locking is supported, but inspection/release multi-record operations are not newly transactional. Full dispatch entry and season-production controls are API-based; this change does not claim complete new Web/Mobile forms for all such actions.

BLOCKED (operational evidence): real source inspection/PHI/market lab evidence, harvest/grading/packing/dispatch/delivery references, actual counterparties and measured quantities must be provided before operational shipments can count. No synthetic fixture is a substitute.

Verdict: implementation, automated fixture tests, local deployment and approved idempotent synthetic seed PASS; project-wide live workflow completion is NOT claimed.
