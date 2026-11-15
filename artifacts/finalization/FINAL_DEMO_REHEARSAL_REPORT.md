# DurianCare-IoT Final Demo Rehearsal Report

Date: 2026-10-11
Backend repo: `C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DurianCare-IoT-Backend`
Branch: `duy/cleanup-fixes`
Backend HEAD: `8fbdfa8`

## Executive Summary

Runtime readiness and safe Docker startup were verified successfully on the local demo environment. All Compose services built or reused local images, containers started, core infrastructure health checks passed, Java service actuator endpoints returned `UP`, and the AI service reported models/database readiness through `/actuator/health`.

An existing Farmer Web UI session is now visible and was inspected read-only. A Web proxy compatibility fix was added so `/api/users/**` is handled by the same authenticated Gateway proxy as `/api/backend/**`; unauthenticated checks now return `401` instead of the previous Web-host `404`. Authenticated API E2E is still blocked because the current browser automation environment cannot read the same-session JSON response body for `/api/users/me` without being stopped by the browser layer. Per demo safety rules, no authentication bypass, production account, generated JWT, guessed credential, forced data setup, or database reset was used.

Historical runtime-E2E conclusion for the earlier demo rehearsal phase: `BLOCKED`

Latest final requirements conclusion: `PARTIALLY_FIXED`

## Newer Executed Evidence: 2026-10-15 Web/Farmer Follow-Up

See [MASTER_WEB_FARMER_COMPLETION_2026_10_15.md](MASTER_WEB_FARMER_COMPLETION_2026_10_15.md)
and FINAL_ACCEPTANCE_2026_10_15.md section 13 for current evidence. This does not relabel the
historically blocked diagnosis/care-plan/dispatch rehearsal as a pass.

The existing Farmer browser session actually loaded Profile (1 farm / 4 zones / 100 trees), zone maps
(25 each, matching marker/table selection and no cross-zone retained tree), marked 8 own notifications
read, and registered real esp32-01 into the named owned farm after explicit operator confirmation.
Telemetry now displays from persisted data; no fabricated seed or online/physical-capture claim.
The standalone CLI harness still has no auth context and exits 1 with BLOCKED_AUTH_CONTEXT.
The new report separates browser runtime, mock tests, real Mongo fixtures, deployment health,
bounded integrity windows, legacy orphan care-plan findings and outstanding business evidence.

Current classification: **FIXED BUT NOT FULLY VERIFIED**. Camera remains deferred, not a new blocker
request for hardware; full care/dispatch live E2E, ADMIN approval and historical-data reconciliation
remain outside the proven runtime successes.

## Phase I - Final Risk Review And Demo Closure - 2026-10-14

Final Phase I verdict: `PARTIALLY_COMPLETE`

### Git And Diff Scope

All three repositories remain on `duy/cleanup-fixes` and all uncommitted work was preserved. No commit, push, reset, branch switch, database reset, Docker volume deletion, or credential exposure was performed.

Backend remains dirty across farm, cultivation, notification, gateway, AI, infrastructure, finalization artifacts, severity artifacts, and new tree-care files. Web remains dirty across authenticated proxy, traceability, camera, export, notifications, tree UI/client/types, and auth proxy files. Mobile remains dirty across package files, notification, traceability, tree UI/API/types, and generated export folders including new `dist-phase-i/`.

### GitNexus Critical Risk Review

GitNexus `detect_changes` over the accumulated Backend unstaged diff still reports `critical`: 120 changed symbols, 50 affected symbols, 25 changed files. This is not a single finding to suppress. It is caused by accumulated changes touching multiple demo/security-critical process groups:

| Risk area | Affected flows called out by GitNexus | Phase I review result |
| --- | --- | --- |
| Tree disease lifecycle | `CreateCarePlan`, `UpdateCarePlanStatus`, `SaveDiagnosis`, `CreateTree`, `GenerateTrees` | Found and fixed a real removed-tree mutation gap. Removed trees now remain readable for history but cannot receive new diagnosis, care recommendation, care plan, care-plan status, or manual health-status mutations. |
| Cultivation/export release | `CreateExportRelease`, `ApproveExportRelease`, `ReleaseExportRelease`, `AssessExportRelease`, `AssessSeasonCompliance`, `CreateCultivationSeason`, `ListCultivationSeasons` | Source review confirmed actor guards, harvest-batch eligibility checks, duplicate active export-release prevention, and DRAFT/UNDER_REVIEW/APPROVED transition checks are covered by service code and tests. No extra code changes made in Phase I. |
| Notification ownership | `NotificationInboxController`, `NotificationEventListener`, farm notification publisher/listener tests | Source review confirmed inbox operations use current receiver id and owned-notification lookup; care follow-up notification receiver is the authenticated actor/owner from the farm flow. Tests passed. |
| Gateway/auth forwarding | Gateway route config and `JwtAuthenticationFilter` | Source review confirmed internal identity headers are stripped then rebuilt from JWT claims; public path list does not include authenticated camera or farm/care routes. |
| IoT/camera/device auth | IoT read API, public camera registration/heartbeat, camera routes | Gateway protects `/api/v1/camera/**`; public camera registration/heartbeat are under `/api/v1/public/**` but require `X-Camera-Key`. IoT read/mutation APIs require gateway identity headers and farm authorization. No physical capture was tested. |

### Phase I Fix

One high-risk bug was corrected:

- `TreeService.transitionHealthStatus(...)` now requires an active tree before changing manual health state.
- Added `TreeService.requireActiveTreeForMutation(...)` for owner check plus active-status enforcement.
- `TreeDiagnosisService.saveDiagnosis(...)` now blocks new diagnosis on removed trees.
- `TreeCarePlanService` now blocks create care plan, create/update/accept recommendation, and care-plan status update on removed trees.
- Added regression tests for removed-tree health transition and removed-tree care workflow mutation rejection.

Historical diagnosis/care-plan reads remain allowed for removed trees, preserving thesis traceability and audit history.

### Runtime And E2E Status

Docker services are currently running and `docker compose -f infrastructure/docker-compose.yml ps` showed Gateway, Auth, Farm, Cultivation, Traceability, Notification, Search, AI, IoT, MongoDB, PostgreSQL, Redis, Kafka, EMQX, Elasticsearch, Chat, Discovery, and Config containers up. This is service readiness evidence only.

Authenticated read-only runtime E2E was attempted through `scripts/demo_e2e_harness.py`, but no safe auth context variables were present. The harness returned `BLOCKED_AUTH_CONTEXT` with `writeEnabled=false`. No live write E2E was executed and `DURIANCARE_E2E_ALLOW_WRITE` was not enabled.

Camera/ESP32 status remains `HARDWARE_NOT_TESTED`. Source and automated tests cover registration, heartbeat, offline handling, and access checks, but no real ESP32-CAM snapshot/capture was performed. Heartbeat success is not treated as image-capture evidence.

Manual health observations remain incomplete as a first-class workflow. Existing manual status transitions are narrower than a dedicated manual observation history and now respect removed-tree blocking.

### Phase I Automated Evidence

| Area | Command | Result |
| --- | --- | --- |
| Backend focused farm/care | `mvn -pl duriancare-farm-service "-Dtest=TreeServiceRecoveryGateTest,TreeCarePlanServiceTest,TreeDiagnosisServiceTest" test` | PASS: 46 tests |
| Backend full reactor | `mvn test` | PASS: 251 tests, 0 failures, 0 errors, 1 skipped; reactor `BUILD SUCCESS` |
| Web typecheck | `npm run typecheck` | PASS |
| Web lint | `npm run lint` | PASS with 8 warnings |
| Web Jest | `npm test -- --runInBand` | PASS: 1 suite, 23 tests |
| Web build | `npm run build` | PASS: 44 routes/pages generated |
| Mobile validation | `npm run validate` | PASS: typecheck plus Expo Doctor 21/21 |
| Mobile export | `npx expo export --platform web --output-dir dist-phase-i` | PASS: 75 static routes exported |
| AI compile | `python -m compileall -q .` from AI service | PASS |
| AI unittest | `python -m unittest discover -s tests` from AI service | PASS: 25 tests |
| AI integrity/safety | `python scripts/ai_validate.py --integrity-check --phase safety` | PASS: 15/15 |
| Severity validation | `python scripts/severity_training/validate_dataset.py --dataset-root ... --output artifacts/severity_training/phase_i_validation.json` | PASS: 5 images, 0 approved annotations, 0 annotation errors |
| Severity gate | `python scripts/severity_training/training_gate.py --dataset-root ... --output artifacts/severity_training/phase_i_training_gate.json` | Expected nonzero safety result: `DO_NOT_TRAIN` |
| Severity guard tests | `python -m unittest test_pipeline_guards.py` from `scripts/severity_training` | PASS: 4 tests |
| IoT service | `npm run check; npm test` from IoT service | PASS: syntax check plus 37 Node tests |

Severity gate reason codes remain `PROTOCOL_NOT_FINALIZED`, `APPROVED_COUNT_TOO_LOW`, and `CLASS_MINIMUM_TOO_LOW`. No severity model was trained or replaced.

### Remaining Blockers

- Authenticated live write E2E remains not executed. It requires an isolated disposable fixture and explicit operator confirmation.
- Real ESP32-CAM capture remains not tested.
- Manual health observation history remains not complete as a first-class workflow.
- Backend GitNexus risk remains `critical` over the accumulated dirty diff and requires human review before commit/merge, despite automated tests passing.

Phase I conclusion: `PARTIALLY_COMPLETE`

## Phase H - Finalization, Tree Removal, Regression, And Thesis Demo Readiness

Date: 2026-10-14

Executive verdict: `PARTIALLY_COMPLETE`

Phase H completed the safe implementable gap for physical tree removal and reran current regression evidence across Backend, Web, Mobile, AI, and severity guards. The system is stronger than the previous `PARTIALLY_FIXED` state, but it is not honest to claim `READY_FOR_THESIS_DEMO` because authenticated live write E2E, physical ESP32-CAM capture, broader manual observation workflow, and production-grade IoT provenance checks remain unverified or incomplete.

### Git And Scope

| Repo | Branch/status |
| --- | --- |
| Backend | `duy/cleanup-fixes...origin/duy/cleanup-fixes`; dirty working tree preserved |
| Web | `duy/cleanup-fixes...origin/duy/cleanup-fixes`; dirty working tree preserved |
| Mobile | `duy/cleanup-fixes...origin/duy/cleanup-fixes`; dirty working tree preserved |

No commit, push, branch switch, reset, database reset, Docker volume deletion, or live business write was performed.

GitNexus impact checks before backend edits:

| Symbol | Result |
| --- | --- |
| `TreeService` | `UNKNOWN` / lower-bound; no resolved callers, stale/incomplete scope metadata warning |
| `TreeController` | `UNKNOWN` / lower-bound; no resolved callers, stale/incomplete scope metadata warning |

Because GitNexus could not prove a complete caller set, source search was used as a cross-check before editing. No HIGH or CRITICAL symbol-specific pre-edit impact warning was returned, but the uncertainty was treated conservatively. Final `gitnexus_detect_changes` over the entire dirty backend tree reported `CRITICAL` because it includes all accumulated uncommitted Phase A-G changes across cultivation, farm/care-plan, notification, AI, and infrastructure flows, not only the Phase H tree-removal patch.

### Phase H Changes

| Area | Files | Purpose |
| --- | --- | --- |
| Backend tree lifecycle | `duriancare-farm-service/src/main/java/com/duriancare/farm/service/TreeService.java`, `duriancare-farm-service/src/main/java/com/duriancare/farm/controller/TreeController.java` | Added authenticated soft-removal using existing `TreeStatus.REMOVED`; changed active map/list query to `findByFarmZoneIdAndStatus(..., ACTIVE)` so removed trees do not reappear in active inventory |
| Backend tests | `duriancare-farm-service/src/test/java/com/duriancare/farm/service/TreeServiceRecoveryGateTest.java` | Added tests for active-only map list, authorized soft removal with diagnosis history preserved, idempotent repeated removal, unauthorized removal denial, and healthy default for new/generated trees |
| Web client/UI | `src/lib/trees/client.ts`, `src/components/trees/TreeDetailPanel.tsx`, `src/components/trees/ZoneTreesWorkspace.tsx` | Added `DELETE /api/trees/{treeId}` client call and a confirmed Vietnamese `Đã đốn cây` action; refreshes active tree list, zone detail, and safety summary after backend success |
| Mobile client/UI | `src/features/trees/treeApi.ts`, `src/features/trees/TreeDetailScreen.tsx`, `src/features/trees/ZoneTreesScreen.tsx` | Added matching remove-tree API, confirmation dialog, and list/safety refresh after backend success |
| Evidence artifacts | `artifacts/severity_training/phase_h_validation.json`, `artifacts/severity_training/phase_h_training_gate.json`, AI validation hard-example folders | Current validation outputs generated by non-destructive test commands |

Soft-removal behavior:

| Requirement | Phase H result |
| --- | --- |
| Authenticated farmer owns/manages tree | PASS by `requireTree(actor, treeId)` and unauthorized test |
| Removal despite diagnosis/care history | PASS at service-test level for diagnosis history; no destructive cascade added |
| Removed tree excluded from active Tree Map/list | PASS by backend query change and test |
| Counts/health aggregates exclude removed trees | PASS for existing `getZoneSafety`, which already filters ACTIVE; zone `activeTreeCount` already uses `countByFarmZoneIdAndStatus(ACTIVE)` |
| Historical detail/history preserved | PASS for direct tree detail and diagnosis-count response path; no records deleted |
| Repeat removal idempotent | PASS at service-test level; no duplicate save/side effect on already `REMOVED` tree |
| Web/Mobile refresh after success | PASS by typechecked implementation; not live-click tested in browser/device |
| Removed trees barred from future harvest eligibility | NOT TESTED directly; active inventory exclusion reduces risk, but harvest eligibility rules need a dedicated cross-service regression if tree-level harvest linkage is later added |

### Commands Executed In Phase H

No command below intentionally prints credentials or tokens. The full Maven reactor emitted normal Spring test logs; secret-like generated development passwords from framework logs must not be copied into demo materials.

| Command | Exit | Result |
| --- | ---: | --- |
| `git status --short --branch` in Backend/Web/Mobile | 0 | Dirty worktrees confirmed and preserved |
| `mvn -pl duriancare-farm-service "-Dtest=TreeServiceRecoveryGateTest,TreeCarePlanServiceTest" test` | 0 | PASS: 24 tests |
| `npm run typecheck` in Web | 0 | PASS |
| `npm run typecheck` in Mobile | 0 | PASS |
| `mvn -pl duriancare-farm-service test` | 0 | PASS: 81 tests |
| `npm run lint` in Web | 0 | PASS with 8 warnings, 0 errors |
| `npm run test -- --runInBand` in Web | 0 | PASS: 1 suite, 23 tests |
| `npm run validate` in Mobile | 0 | PASS: typecheck + Expo Doctor 21/21 |
| `npm run build` in Web | 0 | PASS: Next production build, 44 static pages |
| `npx expo export --platform web --output-dir dist-phase-h` in Mobile | 0 | PASS: exported 75 static routes to `dist-phase-h` |
| `mvn test` in Backend | 0 | PASS: full reactor `BUILD SUCCESS` across 10 modules |
| `python -m compileall -q duriancare-ai-service scripts\severity_training scripts\demo_e2e_harness.py` | 0 | PASS |
| `python -m pytest tests` in AI service | 1 | BLOCKED: active Python has no `pytest` module |
| `python -m unittest discover -s tests` in AI service | 0 | PASS: 25 tests |
| `python scripts\ai_validate.py --integrity-check --phase safety` | 0 | PASS: 15/15, model hashes match, runtime detector hash matches, non-leaf safety regression passes |
| `python scripts\severity_training\validate_dataset.py --dataset-root <severity dataset> --output artifacts\severity_training\phase_h_validation.json` | 0 | PASS: 5 images, 0 approved annotations, 0 annotation errors |
| `python scripts\severity_training\training_gate.py --dataset-root <severity dataset> --artifacts-root artifacts\severity_training --output artifacts\severity_training\phase_h_training_gate.json` | 1 | Expected gate block: `DO_NOT_TRAIN` |
| `python scripts\severity_training\test_pipeline_guards.py` | 0 | PASS: 4 tests |
| `gitnexus_detect_changes(scope=unstaged)` | 0 | Reported CRITICAL over accumulated dirty backend tree |

### Current Verification Matrix

| Area | Status | Evidence / limitation |
| --- | --- | --- |
| Backend compile/tests | PASS | Full Maven reactor `BUILD SUCCESS`; farm-service 81 tests |
| Web compile/tests | PASS WITH WARNINGS | Typecheck/build/Jest pass; lint has 8 pre-existing warnings |
| Mobile compile/export | PASS | Typecheck, Expo Doctor 21/21, web export pass |
| Tree removal backend | PASS | Service/controller implemented; active list filter and removal tests pass |
| Tree removal Web/Mobile UI | SOURCE_VERIFIED | Typecheck/build/export pass; live authenticated UI click not executed |
| Manual health observations | PARTIAL / NOT IMPLEMENTED | Existing recovery/status transitions remain; no full manual observation history model was added in Phase H |
| Care recommendation lifecycle | TEST_FIXTURE_PASS | Existing unit/integration coverage remains green; live write E2E still deferred |
| Harvest/export/traceability | TEST_FIXTURE_PASS | Prior fixes included in full Maven pass; no live write dossier E2E executed |
| Authenticated read-only inventory | HISTORICAL READ_ONLY_PASS | Phase D evidence exists; not rerun in Phase H because no new auth context was supplied in this turn |
| Live write E2E | NOT TESTED | Still requires explicit operator confirmation and isolated fixture |
| AI frozen baseline | PASS | `ai_validate.py` integrity/safety pass; model hashes match freeze |
| Severity model | DO_NOT_TRAIN | Gate blocks training due protocol not finalized, 0 approved annotations, <120 minimum |
| IoT telemetry provenance | PARTIAL / NOT VERIFIED | Existing software not fully reworked in Phase H; no real sensor/camera data claimed |
| Camera/hardware | HARDWARE_NOT_TESTED | No ESP32-CAM physical capture fixture was provided |

### Remaining Blockers Before Thesis Demo

1. Live authenticated write E2E still needs explicit operator approval and an isolated disposable farm/tree or approved demo-write plan.
2. Manual tree health observations are not yet a complete first-class workflow distinct from AI diagnoses.
3. IoT synthetic telemetry provenance and stale/unavailable UI semantics need a dedicated implementation/audit pass before claiming reliable sensor provenance.
4. ESP32-CAM physical capture was not tested; heartbeat or software health alone is not camera evidence.
5. Severity AI training remains correctly blocked by `DO_NOT_TRAIN`; expert-approved severity annotations and a finalized protocol are required.
6. GitNexus final change detection is CRITICAL over the whole accumulated dirty tree; all dirty areas need human review before commit/merge.

Phase H conclusion: `PARTIALLY_COMPLETE`

Latest project-completion increment conclusion: `PARTIALLY_COMPLETE`

## Phase G / Final Completion Increment - Verification Gaps And Critical Fixes

Date: 2026-10-14

This increment focused on the highest-safety remaining gaps that could be closed without live business writes, DB resets, credential handling, or destructive cleanup.

### Safety And Worktree Status

| Check | Status | Evidence |
| --- | --- | --- |
| Backend status checked | PASS | Branch `duy/cleanup-fixes`; dirty worktree preserved |
| Web status checked | PASS | Branch `duy/cleanup-fixes`; dirty worktree preserved |
| Mobile status checked | PASS | Branch `duy/cleanup-fixes`; dirty worktree preserved |
| No destructive data operation | PASS | No DB reset, volume delete, seed write, commit, push, or branch reset |
| Secrets handling | PASS WITH NOTE | Build/test logs emitted framework-generated development values; they are intentionally not reproduced in this report |

### Mobile Expo Doctor Fix

Root cause: Mobile was already on Expo SDK 57 / React Native 0.86.3, but seven Expo packages were behind the patch versions expected by `expo-doctor`.

Fix applied with the supported Expo command:

```powershell
npx expo install expo@~57.0.28 expo-camera@~57.0.6 expo-constants@~57.0.21 expo-document-picker@~57.0.3 expo-linking@~57.0.12 expo-router@~57.0.26 expo-splash-screen@~57.0.10
```

Changed Mobile files:

- `package.json`
- `package-lock.json`

Version updates:

| Package | Before | After |
| --- | --- | --- |
| `expo` | `^57.0.0` resolved to `57.0.25` | `~57.0.28` |
| `expo-camera` | `~57.0.5` | `~57.0.6` |
| `expo-constants` | `~57.0.19` | `~57.0.21` |
| `expo-document-picker` | `~57.0.2` | `~57.0.3` |
| `expo-linking` | `~57.0.11` | `~57.0.12` |
| `expo-router` | `~57.0.23` | `~57.0.26` |
| `expo-splash-screen` | `~57.0.9` | `~57.0.10` |

Mobile verification:

| Command | Exit | Result |
| --- | --- | --- |
| `npx expo install --check` | 0 | PASS, dependencies up to date |
| `npm run validate` | 0 | PASS, typecheck + Expo Doctor 21/21 checks |
| `npx expo export --platform web --output-dir dist-phase-g` | 0 | PASS, static export generated in `dist-phase-g/` |

`dist-phase-g/` is generated export output, not source code.

### Tree Creation Default Health Fix

Finding: backend tree creation paths defaulted new trees to `SUSPECTED`, which made brand-new Farmer-created trees appear as needing assessment without any diagnosis evidence.

Fix:

- `TreeService.createTree(...)` now creates trees with `TreeHealthStatus.HEALTHY`.
- `TreeService.generateTrees(...)` now creates every generated tree with `TreeHealthStatus.HEALTHY`.
- No diagnosis record is created by tree creation/generation.

Changed Backend files:

- `duriancare-farm-service/src/main/java/com/duriancare/farm/service/TreeService.java`
- `duriancare-farm-service/src/test/java/com/duriancare/farm/service/TreeServiceRecoveryGateTest.java`

Regression added:

| Test | Coverage |
| --- | --- |
| `createTreeDefaultsToHealthyAndCreatesNoDiagnosis` | Verifies one new tree defaults `HEALTHY` and no diagnosis is persisted |
| `generateTreesDefaultsEveryGeneratedTreeToHealthy` | Verifies generated grid trees default `HEALTHY` and no diagnosis is persisted |

GitNexus impact: `createTree` and `generateTrees` impact checks returned `UNKNOWN/lower-bound` with no HIGH/CRITICAL warning. Source search confirmed controller entry points; tests were added around the changed behavior.

### Harvest Lot / Export Release Re-Audit

Previously implemented rules were re-audited. An additional gap was found:

- `releaseExportRelease(...)` resolved ownership but did not re-check harvest-batch eligibility before issuing a released dossier/QR state.

Fix:

- Authenticated `releaseExportRelease(actor, ...)` now calls `requireExportReleaseEligibleBatch(...)`.
- Internal `releaseExportRelease(id, ...)` also resolves the persisted harvest batch and enforces eligibility.
- `recallExportRelease(...)` intentionally remains allowed without eligibility re-check so an operator can recall a dossier even if the lot later becomes unsafe/ineligible.

Additional regression coverage:

| Test | Coverage |
| --- | --- |
| `approveExportReleaseRejectsInvalidTransition` | Rejects approving from `DRAFT` |
| `releaseExportReleaseRejectsIneligibleResolvedHarvestBatch` | Rejects releasing a dossier whose batch is now `BLOCKED` |
| `releaseExportReleaseRejectsNonApprovedDossier` | Rejects releasing from `UNDER_REVIEW` |

Current verified rules:

- Persisted harvest lot ID is required.
- `APPROVED` and `HARVESTED` are the only eligible lot states.
- Authenticated endpoints resolve the lot and enforce farm/plot authorization.
- Compliance assessment rejects season mismatches.
- Active duplicate export-release dossiers are rejected.
- `DRAFT -> UNDER_REVIEW -> APPROVED -> RELEASED` transitions are enforced.
- `RECALLED` remains the non-active terminal state for duplicate checks.

### Notification Read/Open Audit

Notification lifecycle was re-audited in source and tests:

- Inbox list queries by current authenticated `X-Auth-User-Id`.
- Mark-read uses `findByIdAndReceiverId`, preserving recipient ownership.
- Mark-read only changes read/status timestamps, not recipient.
- Mark-all-read scopes to unread notifications for the same receiver.
- Existing regression `markAsReadPreservesReceiverAndKeepsNotificationReadableByOwner` covers the reported read/reopen/list behavior.

No notification code change was required in this increment.

### UI Placeholder / Mock Audit

Focused source searches were run across Web and Mobile for `backend`, `mock`, `demo`, `Chưa có API`, `TODO`, `Coming soon`, and related phrases.

Findings:

| Surface | Result |
| --- | --- |
| Farmer harvest/export/QR Web | Already cleaned in prior pass; no manual harvest ID entry remains in touched Farmer export flow |
| Public Web traceability route | Real-data-only route remains; no static mock certification fallback |
| Mobile traceability/export | Uses eligible synchronized lot selection; no manual lot ID entry in touched workflow |
| Web broader app | Remaining mock imports exist in dashboard, calendar, charts, authorization, and knowledge/admin surfaces; not all were changed because they require feature-specific backend/data work |
| Mobile broader app | Remaining developer-facing copy exists in authorization, cultivation, diagnosis, change-password, scanner, and knowledge screens; some are honest unsupported-feature notices, others need later UX cleanup |

No unsupported success state was added. Broader mock/placeholder cleanup remains a continuation item.

### E2E Preparation Status

Controlled authenticated write E2E was not executed in this increment.

Prepared safe sequence remains:

1. Use official login/auth flow in an isolated approved test environment.
2. Read `/api/users/me` and Farmer farm/zone/season/lot inventory.
3. Select an existing eligible `APPROVED`/`HARVESTED` lot.
4. Create export-release draft.
5. Verify persisted lot/season/farm relationships.
6. Submit and approve through allowed transitions.
7. Release and verify QR traceability references the same persisted dossier/lot.
8. Confirm draft, blocked, cancelled, nonexistent, cross-owner, and season-mismatched lots are rejected.
9. Confirm duplicate active dossiers are rejected.
10. Confirm unauthorized users cannot view/update another Farmer's dossier.

Execution remains blocked until an isolated disposable environment or explicit operator approval is provided. `DURIANCARE_E2E_ALLOW_WRITE` was not enabled.

### Regression Commands - 2026-10-14

| Area | Command | Exit | Result |
| --- | --- | --- | --- |
| Backend focused cultivation auth/export | `mvn -pl duriancare-cultivation-service -Dtest=CultivationCalendarAuthorizationServiceTest test` | 0 | PASS, 37 tests |
| Backend cultivation module | `mvn -pl duriancare-cultivation-service test` | 0 | PASS, 55 tests |
| Backend focused farm/tree/care | `mvn -pl duriancare-farm-service "-Dtest=TreeServiceRecoveryGateTest,TreeDiagnosisServiceTest,TreeCarePlanServiceTest" test` | 0 | PASS, 40 tests |
| Backend notification module | `mvn -pl duriancare-notification-service test` | 0 | PASS, 25 tests |
| Backend full Maven reactor | `mvn test` | 0 | PASS, reactor BUILD SUCCESS across all modules |
| Web typecheck | `npm run typecheck` | 0 | PASS after Next regenerated valid generated types |
| Web lint | `npm run lint` | 0 | PASS with 8 warnings in existing files |
| Web Jest | `npm run test -- --runInBand` | 0 | PASS, 1 suite / 23 tests |
| Web production build | `npm run build` | 0 | PASS |
| Mobile dependency check | `npx expo install --check` | 0 | PASS |
| Mobile validation | `npm run validate` | 0 | PASS, Expo Doctor 21/21 |
| Mobile web export | `npx expo export --platform web --output-dir dist-phase-g` | 0 | PASS |

Web typecheck note: the first run failed on a corrupt generated `.next/dev/types/validator.ts` fragment. The production build regenerated Next types successfully; rerunning typecheck then passed. No app source TypeScript error remained.

### Remaining Blockers / Continuation Checklist

| Area | Status | Required next step |
| --- | --- | --- |
| Safe tree deletion | NOT IMPLEMENTED | Needs backend soft-delete/archive design, dependency checks for diagnoses/care plans/traceability/audit, Web/Mobile UI, and tests |
| Farmer bulk manual health observations | NOT IMPLEMENTED | Needs explicit domain/API for observation-vs-diagnosis distinction, audit record, per-tree result semantics, Web/Mobile multi-select UX, and tests |
| Full authenticated Farmer data inventory for `minhdii1510@gmail.com` | BLOCKED_AUTH_CONTEXT | Requires safe authenticated API context; no credential/token extraction or bypass was performed |
| Live write E2E | BLOCKED_PENDING_OPERATOR_CONFIRMATION | Requires isolated approved environment or explicit operator approval |
| IoT synthetic telemetry generator | NOT IMPLEMENTED | Requires baseline telemetry audit and isolated fixture/database tests before any generator is applied |
| Hardware/camera E2E | NOT TESTED | Requires real device/camera fixture |
| AI model/severity revalidation in this increment | NOT RERUN | Existing safeguards remain, but this increment did not rerun AI/severity commands |
| Broader Web/Mobile placeholder cleanup | PARTIAL | Audit found remaining areas; large feature-specific cleanup remains |

Final conclusion for this increment: `PARTIALLY_COMPLETE`

## Final Requirements Pass - Harvest Lot IDs, Real Data, Farmer UI

Date: 2026-10-13

Scope of this pass:

- Preserve existing dirty worktrees in Backend, Web, and Mobile.
- Keep live write E2E disabled; `DURIANCARE_E2E_ALLOW_WRITE` was not enabled.
- Do not create, update, delete, seed, reset, or mutate demo business data.
- Replace fake/placeholder traceability behavior with real-data-only behavior where it affected the Farmer harvest/export flow.
- Enforce persisted harvest-lot IDs and reject ineligible harvest lots before export release creation/submission/approval.

### Preflight And Impact Review

| Check | Status | Evidence |
| --- | --- | --- |
| Backend branch/status checked | PASS | Branch `duy/cleanup-fixes`; dirty tree preserved |
| Web branch/status checked | PASS | Branch `duy/cleanup-fixes`; dirty tree preserved |
| Mobile branch/status checked | PASS | Branch `duy/cleanup-fixes`; dirty tree preserved |
| GitNexus impact before Backend edits | PASS WITH LIMITATION | Ran impact analysis for `CultivationCalendarService`, `createExportRelease`, `submitExportRelease`, and `approveExportRelease`; index returned unresolved/UNKNOWN rather than HIGH/CRITICAL, so source-search review was used as a conservative cross-check |
| No destructive data operation | PASS | No DB reset, volume deletion, seed write, commit, push, or branch reset was performed |

### Backend Findings And Fixes

| Area | Result |
| --- | --- |
| Harvest lot ID validation | Export release creation now resolves the persisted `HarvestBatch` by ID before persistence; free-form/nonexistent IDs are rejected |
| Eligibility rule | Only `APPROVED` and `HARVESTED` harvest batches are eligible for export release workflow |
| Season mismatch | Compliance assessment rejects a request when the supplied harvest batch belongs to a different cultivation season |
| Ownership/scope | Authenticated create/submit/approve paths resolve the batch first and use the resolved farm/plot scope for access checks |
| Duplicate active release | Creating another active export release for the same harvest batch is rejected unless previous releases are recalled |
| Status transitions | Submit is limited to `DRAFT`; approve is limited to `UNDER_REVIEW`; blocked dossiers cannot be approved |
| Orphan prevention | Rejected paths are covered by tests that verify repositories are not called for persistence |

Files changed in this pass include:

- `duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/service/CultivationCalendarService.java`
- `duriancare-cultivation-service/src/test/java/com/duriancare/cultivation/service/CultivationCalendarAuthorizationServiceTest.java`

No migration/schema change was required for this pass because the existing `HarvestBatch` and `ExportRelease` model already stores the relationships needed for validation.

### Web Findings And Fixes

| Area | Result |
| --- | --- |
| Farmer export release form | Replaced free-text harvest lot ID entry with selection from real loaded harvest batches |
| Eligibility in UI | UI only offers `APPROVED`/`HARVESTED` batches for export release creation |
| Fake public traceability fallback | Removed static/mock traceability page fallback; unresolved public traceability now returns `notFound()` instead of fabricated certification data |
| QR list | Farmer QR section now renders released export releases from backend data, not local mock crop lots |
| Dev-facing copy | Removed prominent `backend`, `demo`, and fake-data wording from the touched Farmer harvest/export surfaces |

Files changed in this pass include:

- `src/components/dashboard/CropQrBuilder.tsx`
- `src/app/traceability/[cropId]/page.tsx`
- `src/components/export/ExportCompliancePanel.tsx`

### Mobile Findings And Fixes

| Area | Result |
| --- | --- |
| Export release form | Mobile now selects from synchronized eligible harvest batches instead of allowing manual ID entry |
| Eligibility guard | Submit rejects any batch that is not present in the loaded eligible `APPROVED`/`HARVESTED` list |
| Farmer copy | Replaced several developer-facing `backend/demo/mock` messages in the touched traceability screen with product-facing copy |

Files changed in this pass include:

- `src/features/profile/DurianTraceabilityScreen.tsx`

### Real Data And Seed Strategy

| Item | Decision |
| --- | --- |
| Fake/mock traceability | Removed from the public Web traceability route touched in this pass |
| Hard-coded harvest lot IDs | Not added; create flows must use persisted IDs loaded from the system |
| Seed data | No seed script was added or run because the existing model and APIs are sufficient; if demo data is still needed, it should be an explicit, idempotent, dev-only seed in an isolated test DB, not a write to the current demo farm |
| Public QR contract | Still requires runtime verification against the real public traceability endpoint; the Web route no longer fabricates data when that endpoint is unavailable |

### Automated Regression Evidence

| Area | Command | Exit | Result |
| --- | --- | --- | --- |
| Backend focused cultivation tests | `mvn -pl duriancare-cultivation-service -Dtest=CultivationCalendarAuthorizationServiceTest test` | 0 | PASS, 34 tests |
| Backend cultivation module | `mvn -pl duriancare-cultivation-service test` | 0 | PASS, 52 tests |
| Backend full Maven reactor | `mvn test` | 0 | PASS, reactor `BUILD SUCCESS` across all modules |
| Web typecheck | `npm run typecheck` | 0 | PASS |
| Web lint | `npm run lint` | 0 | PASS with 8 warnings in pre-existing/adjacent files |
| Web Jest | `npm run test -- --runInBand` | 0 | PASS, 1 suite / 23 tests |
| Web production build | `npm run build` | 0 | PASS |
| Mobile typecheck | `npm run typecheck` | 0 | PASS |
| Mobile validate | `npm run validate` | 1 | Typecheck PASS; Expo doctor FAIL only on SDK patch-version mismatches listed below |

Mobile Expo doctor package mismatches:

| Package | Expected | Found |
| --- | --- | --- |
| `expo` | `~57.0.28` | `57.0.25` |
| `expo-camera` | `~57.0.6` | `57.0.5` |
| `expo-constants` | `~57.0.21` | `57.0.19` |
| `expo-document-picker` | `~57.0.3` | `57.0.2` |
| `expo-linking` | `~57.0.12` | `57.0.11` |
| `expo-router` | `~57.0.26` | `57.0.23` |
| `expo-splash-screen` | `~57.0.10` | `57.0.9` |

### Current Git Diff Summary

Backend diff remains broad from the accumulated finalization phases. Relevant changes for this pass are in cultivation export-release service/tests; other dirty files from prior phases were preserved and not reset.

| Repo | Branch | Diff summary |
| --- | --- | --- |
| Backend | `duy/cleanup-fixes` | 25 tracked files changed, plus untracked finalization docs/scripts/tree-care classes from prior phases |
| Web | `duy/cleanup-fixes` | 12 tracked files changed, plus new API proxy/auth helper files from prior phases |
| Mobile | `duy/cleanup-fixes` | 13 tracked files changed |

### Remaining Limitations

| Limitation | Status |
| --- | --- |
| Authenticated live runtime E2E for Farmer harvest/export/QR | NOT TESTED in this pass |
| Live creation of export release from a real approved harvest batch | NOT TESTED; write-path intentionally disabled |
| Public QR traceability endpoint end-to-end | NOT TESTED; route now requires real service data and returns 404 otherwise |
| Mobile Expo doctor | FAIL due SDK patch-version mismatches, not from TypeScript errors |
| Visual/manual UI verification | NOT TESTED in browser/device after this patch |

Final conclusion for this final requirements pass: `PARTIALLY_FIXED`

## Urgent Harvest Lot Selection And Season Status Fix - 2026-10-13

### Scope And Root Cause

The reported manual harvest lot ID issue was confirmed in the Web Farmer crops workflow. `CropQrBuilder` loaded real `HarvestBatch` records from `GET /api/v1/harvest-batches`, but the export-release form still exposed a free-text `harvestBatchId` input. That allowed a user to type an arbitrary identifier before the backend resolved and authorized it.

The related `Đang hoàn thiện hồ sơ` status was traced to static Web mock data in `src/constants/durianMockData.js` rendered by the QR tab, not to the live cultivation season lifecycle API. That mock card mixed a document/export workflow status with the season card context for the example season `DC-2026-RI6-012`. The Web QR tab was changed to render backend export releases and harvest batches instead of `cropLots`, keeping export-release document status separate from live season lifecycle fields (`status` / `effectiveStatus`) used elsewhere.

### Existing Harvest-Lot API

A canonical harvest-lot model already existed as `HarvestBatch`; no duplicate lot model was introduced.

| API | Result |
| --- | --- |
| `GET /api/v1/harvest-batches?cultivationSeasonId=&farmId=&plotId=` | Existing authenticated list endpoint; service filters results through `CultivationAccessGuard.canView` |
| `GET /api/v1/harvest-batches/{id}` | Existing authenticated get endpoint; service resolves persisted batch then checks farm/plot access |
| `POST /api/v1/export-releases` | Existing authenticated creation endpoint; now requires an existing eligible persisted batch |
| `GET /api/v1/export-releases/{id}/traceability` | Existing authenticated traceability snapshot endpoint; resolves release -> harvest batch -> owner scope |

### Backend Rules Added

Changed files:

- `duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/service/CultivationCalendarService.java`
- `duriancare-cultivation-service/src/test/java/com/duriancare/cultivation/service/CultivationCalendarAuthorizationServiceTest.java`

Rules now enforced:

- Export release creation resolves `request.harvestBatchId` through `getHarvestBatch(...)`, so nonexistent IDs are rejected by the existing not-found convention.
- Authenticated creation resolves the harvest batch first, applies `requireView`, then applies `requireUpdate` on the resolved farm/plot scope.
- Export release creation now rejects harvest batches whose lifecycle state is not `APPROVED` or `HARVESTED`.
- The unauthenticated/internal service overload also applies the same persisted-ID and lifecycle validation.
- Compliance assessment now rejects a supplied harvest batch whose `cultivationSeasonId` does not match the requested season.
- Saved export releases continue storing `batch.id()` as the canonical persisted ID and traceability snapshot source.

GitNexus impact note: `CultivationCalendarService` and `createExportRelease` impact queries returned `UNKNOWN` because the current index lacks completeness metadata and resolved no callers. Source search confirmed the controller endpoints and tests that call the service. No HIGH/CRITICAL GitNexus warning was returned.

### Web Fix

Changed file:

- `src/components/dashboard/CropQrBuilder.tsx`

Behavior now:

- The export-release form no longer permits free-text harvest lot IDs.
- A searchable control lists only backend harvest batches in `APPROVED` or `HARVESTED` status.
- Search covers canonical ID, batch code, season ID, farm ID, zone/plot ID, target market, and batch status.
- The submitted value is the canonical `HarvestBatch.id`, not a display label.
- Empty state explains that the Farmer must create/register a real harvest batch first.
- The QR tab no longer uses `cropLots` mock records; it displays QR cards only for backend export releases in `RELEASED` status and shows export-release document status separately from harvest/season data.
- QR values now point to the persisted export release traceability resource path instead of `/traceability/{mockCropId}`.

### Mobile Fix

Changed file:

- `src/features/profile/DurianTraceabilityScreen.tsx`

Behavior now:

- The create-release modal filters the existing backend `HarvestBatch[]` list to `APPROVED` / `HARVESTED`.
- The modal includes a search field and existing chip selector rather than a free-text ID field.
- Selecting a batch submits the canonical `HarvestBatch.id` and copies its expected destination market into the release draft.
- Blocked/draft/cancelled batches disable the "create release from this lot" action.
- Submit validation rejects any release draft whose selected batch is not in the currently loaded eligible backend list.

### Season Lifecycle Versus Document Status

Season lifecycle remains represented by the cultivation season API fields:

- Backend catalog response: `status` and `effectiveStatus`.
- Web/Mobile tree-zone season cards: `effectiveStatus ?? status ?? date-derived status`.

Document/export workflow status remains represented by:

- `HarvestBatch.status` for harvest-lot lifecycle.
- `ExportRelease.status` for export-release/document workflow.

The Web QR view no longer displays the static `Đang hoàn thiện hồ sơ` mock status as if it were a live season lifecycle status.

### Traceability Integrity

The QR and traceability workflow now uses persisted export releases and their persisted harvest batch association. Backend traceability access remains authenticated and resolves `ExportRelease.harvestBatchId` back to the harvest batch scope before returning the snapshot. Public QR/token contract is still not implemented as a separate public endpoint; the UI does not fabricate public traceability data.

### Tests Run

No secrets were printed and no live business records were created, modified, or deleted.

| Area | Command | Result |
| --- | --- | --- |
| Backend focused | `mvn -pl duriancare-cultivation-service -Dtest=CultivationCalendarAuthorizationServiceTest test` | PASS, 31 tests |
| Backend module | `mvn -pl duriancare-cultivation-service test` | PASS, 49 tests |
| Backend full reactor | `mvn test` | PASS, `BUILD SUCCESS` across 10 Maven modules |
| Web typecheck | `npm run typecheck` | PASS |
| Web lint | `npm run lint` | PASS with 8 pre-existing warnings outside `CropQrBuilder.tsx` |
| Web Jest | `npm run test -- --runInBand` | PASS, 1 suite / 23 tests |
| Web build | `npm run build` | PASS |
| Mobile typecheck | `npm run typecheck` | PASS |
| Mobile validate | `npm run validate` | FAIL only at Expo doctor SDK patch-version check after typecheck passed; 20/21 checks passed. Mismatches: `expo`, `expo-camera`, `expo-constants`, `expo-document-picker`, `expo-linking`, `expo-router`, `expo-splash-screen` |

### Remaining Manual Verification

- Verify the Web crops page visually with an authenticated Farmer session and live harvest batches.
- Verify the Mobile create-release modal on a device/emulator because only TypeScript/Expo doctor checks were run here.
- Runtime write-path E2E remains subject to the prior operator-confirmation rule; this fix did not enable `DURIANCARE_E2E_ALLOW_WRITE` and did not mutate demo-farm data.

Urgent harvest-lot selection conclusion: `FIXED`

## Urgent Farmer Season / Notification Authorization Fix Addendum

Date: 2026-10-13

This addendum covers the urgent audit/fix for Farmer cultivation season access, season lifecycle status, and notification read-state authorization. No live write E2E was executed, and no database or Docker volume reset was performed.

### Findings and Fixes

| Bug | Status | Root cause | Fix |
| --- | --- | --- | --- |
| Farmer cannot access own season/export workflow | FIXED | Backend farm ownership access already allowed a `FARMER` whose `userId` matches `farm.ownerUserId`; the visible blocker was Web role permission mapping: `FARMER` lacked `EXPORT_RELEASE_REVIEW`, so the client hid/blocked export-release creation even though the owner may submit export releases for their own farm through backend ownership checks. | Added `EXPORT_RELEASE_REVIEW` to the Web `FARMER` cultivation permission set. Backend ownership checks remain intact and still deny other users' farms. |
| Past season displayed as preparing | FIXED | `CultivationSeason` had no persisted or response status, and clients had to infer from dates. That left inconsistent fallback labels and no backend source of truth. | Added date-derived `effectiveStatus`/`status` to season responses: `PREPARING` when `startDate` is after runtime today, `IN_PROGRESS` when today is within/open-ended season, and `ENDED` when `endDate` is before runtime today. Uses `LocalDate.now(clock)` with an injectable clock for deterministic tests; no season records are mutated. Web and Mobile types/banner labels now consume the backend status with a local fallback. |
| Notification auth fails after mark-read | FIXED | Backend `markAsRead` already preserved `receiverId`, but Mobile notification calls sent a client-side `X-Auth-User-Id` header on top of the bearer token. That made identity handling fragile and inconsistent with the Gateway contract, where the Gateway overwrites internal auth headers from JWT claims. | Removed client-supplied `X-Auth-User-Id` from Mobile notification requests and kept bearer-token auth as the single source of identity. Added backend regression test proving mark-read preserves receiver ownership and the owner can still list/read the notification. |

### Files Changed In This Addendum

Backend:

- `duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/domain/CultivationSeasonStatus.java`
- `duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/api/CultivationCalendarDtos.java`
- `duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/service/CultivationSeasonCatalogService.java`
- `duriancare-cultivation-service/src/test/java/com/duriancare/cultivation/service/CultivationSeasonCatalogServiceTest.java`
- `duriancare-notification-service/src/test/java/com/duriancare/notification/service/impl/NotificationServiceImplTest.java`

Web:

- `src/features/cultivation-calendar/permissions.ts`
- `src/lib/cultivation/types.ts`
- `src/components/trees/ZoneTreesWorkspace.tsx`

Mobile:

- `src/features/cultivation/api/cultivationTypes.ts`
- `src/features/notification/notificationApi.ts`
- `src/features/notification/DurianNotificationInboxScreen.tsx`
- `src/features/notification/NotificationDetailScreen.tsx`
- `src/features/dashboard/DurianOperationsScreen.tsx`
- `src/features/more/DurianMoreScreen.tsx`
- `src/features/trees/ZoneTreesScreen.tsx`

### Test Evidence

| Command | Result |
| --- | --- |
| `mvn -pl duriancare-cultivation-service -Dtest=CultivationSeasonCatalogServiceTest test` | PASS, 4 tests |
| `mvn -pl duriancare-notification-service "-Dtest=NotificationServiceImplTest,NotificationInboxControllerTest" test` | PASS, 16 tests |
| `npm run typecheck` in Web | PASS |
| `npm run lint` in Web | PASS with 10 existing warnings, 0 errors |
| `npm test -- --runInBand` in Web | PASS, 1 suite / 23 tests |
| `npm run build` in Web | PASS |
| `npm run typecheck` in Mobile | PASS |
| `npm run validate` in Mobile | FAIL: Expo doctor passed 20/21 checks, but reports 7 Expo SDK patch-version mismatches (`expo`, `expo-camera`, `expo-constants`, `expo-document-picker`, `expo-linking`, `expo-router`, `expo-splash-screen`) |
| `gitnexus_detect_changes` on Backend unstaged changes | HIGH risk overall because the working tree also contains pre-existing high-risk tree care lifecycle changes; urgent addendum changes are scoped to cultivation season status/tests and notification regression test |

### Manual Verification Still Required

- Confirm in Web with a real Farmer owner session that the export-release panel allows creating/submitting an export release only for owned farm data.
- Confirm in Web/Mobile UI that a known past season renders `Đã kết thúc`, current season renders `Đang diễn ra`, and future season renders `Đang chuẩn bị`.
- Confirm on Mobile with a real notification that marking read and reopening the same notification no longer produces `Bạn không có quyền xem thông báo này`.
- Live authenticated E2E remains separate from this hotfix and was not rerun.

Urgent addendum conclusion: `FIXED`

## 2026-10-13 Existing Farmer Account Continuation

Requested account email: `minhdii1510@gmail.com`

The user confirmed that this is an existing Farmer account and that no duplicate registration should be attempted. Runtime services were still running and healthy enough for Gateway/API verification. Git status was rechecked across Backend, Web, and Mobile, and existing uncommitted work was preserved.

Authentication could not be completed because only the account email was provided. The login contract requires both `email` and `password`, and the required `userId` must be verified through authenticated API response, not guessed from database records or forged into a JWT. No password, access token, refresh token, OTP, or secret was written to this report.

### API Contract Audit

| Area | Gateway/API contract | Notes |
| --- | --- | --- |
| Login | `POST /api/auth/login` | Body is `email` + `password`; response includes `accessToken`, `refreshToken`, `userId`, `email`, `role`, `accountStatus`, `profile` |
| Current user | `GET /api/users/me` | Requires authenticated request; intended source of verified account/profile data after login |
| Farms | `GET /api/farms`, `GET /api/farms/{farmId}`, `GET /api/farms/{farmId}/zones` | Farm service filters/authorizes by authenticated `X-Auth-User-Id` emitted by Gateway |
| Trees | `GET /api/zones/{zoneId}/trees`, `GET /api/trees/{treeId}` | Requires authenticated owner/authorized actor |
| Diagnoses | `GET/POST /api/trees/{treeId}/diagnoses`, `GET /api/trees/{treeId}/diagnoses/latest` | Diagnosis save expects `imageUrl`, `diseaseCode`, optional disease details, confidence, bounding box, source |
| Recommendation | `POST /api/trees/{treeId}/care-recommendation`, `GET /api/trees/{treeId}/care-recommendation/latest` | Can use latest diagnosis or supplied `diagnosisId`, subject to ownership |
| Care plan | `POST /api/trees/{treeId}/care-plans`, `GET /api/trees/{treeId}/care-plans`, `PATCH /api/care-plans/{planId}/status` | Requires authenticated owner and business transition rules |
| Tasks | `GET /api/care-plans/{planId}/tasks` | Requires access to the owning care plan |
| Recovery | `POST /api/trees/{treeId}/evaluate-recovery`, `PATCH /api/trees/{treeId}/health-status` | Recovery gate must pass before `RECOVERED` transitions |
| Notifications | `GET /api/v1/notification/notifications`, `/unread`, `/count` | Requires authenticated user ID propagated by Gateway |
| AI prediction | `POST /api/v1/predict` | Multipart `image` through Gateway to AI service; prediction alone is not a persisted tree diagnosis until saved to Farm service |

### Runtime Recheck

| Check | Result |
| --- | --- |
| Backend branch/status | PASS: branch `duy/cleanup-fixes`, HEAD `8fbdfa8`, dirty worktree preserved |
| Web branch/status | PASS: branch `duy/cleanup-fixes`, HEAD `abaa3ca`, dirty worktree preserved |
| Mobile branch/status | PASS: branch `duy/cleanup-fixes`, HEAD `cde1765`, dirty worktree preserved |
| Docker services | PASS: all Compose services running |
| Gateway health | PASS: `GET /actuator/health` returned HTTP 200 `UP` |
| Farm health | PASS: `GET /actuator/health` returned HTTP 200 `UP` |
| Notification health | PASS: `GET /actuator/health` returned HTTP 200 `UP` |
| AI health | PASS: `GET /actuator/health` returned HTTP 200 `UP`; AI reported model/classifier/detector/diagnosis/database readiness |

### Existing Account/Data Audit Table

| Entity | Existing count | Needed for demo | Missing / inconsistent | Minimal safe action |
| --- | ---: | ---: | --- | --- |
| Authenticated Farmer account | BLOCKED | 1 | Cannot verify via API without password/OTP | Provide valid demo password or approved auth flow for `minhdii1510@gmail.com` |
| Verified user ID | BLOCKED | 1 | Cannot derive from unauthenticated state | Login through Gateway and read login response or `/api/users/me` |
| Farm | BLOCKED | 1+ | Requires verified token | After login, call `GET /api/farms` |
| Zone | BLOCKED | 1+ | Requires a known owned farm | After farm selection, call `GET /api/farms/{farmId}/zones` |
| Tree | BLOCKED | 1+ | Requires a known owned zone | After zone selection, call `GET /api/zones/{zoneId}/trees` and `GET /api/trees/{treeId}` |
| Diagnosis | BLOCKED | 1+ for disease demo | Requires authenticated tree ownership and valid test image if creating a new diagnosis | Reuse existing diagnosis if present; otherwise run AI prediction and save diagnosis through Gateway |
| Recommendation | BLOCKED | 1 for diseased-tree demo | Requires diagnosis tied to owned tree | Use latest diagnosis or explicit `diagnosisId` through recommendation endpoint |
| Care plan | BLOCKED | 1 for treatment demo | Requires recommendation or valid manual care plan request | Accept recommendation or create minimal valid care plan through API |
| Tasks | BLOCKED | 1+ | Requires care plan | Verify via `GET /api/care-plans/{planId}/tasks` |
| Notification | BLOCKED | 1+ if care-plan/follow-up emits notification | Requires authenticated user and event persistence | Verify inbox/count after care-plan action |

No database query was used as a substitute for authenticated ownership verification, and no duplicate Farmer account registration was attempted.

### Current E2E Status

| Step | Status | Evidence / Blocker |
| --- | --- | --- |
| Runtime health | PASS | Gateway/Farm/Notification/AI health endpoints returned `UP` |
| Login existing Farmer | BLOCKED | Email is known, but password/OTP was not provided |
| Verify user ID via API | BLOCKED | Requires successful login |
| Audit account-owned farm/zone/tree data | BLOCKED | Requires verified token |
| Create/repair demo data safely | BLOCKED | Requires verified token and current entity counts |
| AI diagnosis through Gateway | BLOCKED | Requires authenticated demo tree and approved image fixture |
| Persist diagnosis to tree | BLOCKED | Requires authenticated tree ownership |
| Recommendation/care plan/tasks/notification | BLOCKED | Requires authenticated diagnosis/tree flow |
| Web/Mobile consistency | BLOCKED | Requires authenticated demo session |

Additional condition from the earlier blocked attempt: login had to be completed interactively or through an approved secure test-auth flow without exposing secrets in chat/history. This was partially superseded by the existing Web login inspected below, but API `userId` verification remains blocked.

### Authenticated Web Session Audit From Existing Login

The user then confirmed the Farmer demo account was already logged in on the Web app. The existing browser session was inspected read-only. No password, OTP, access token, refresh token, cookie value, or secret was requested or logged.

| Check | Status | Evidence |
| --- | --- | --- |
| Web session visible | PASS | Browser page `http://localhost:3000/dashboard/client` rendered the Farmer dashboard for `Huynh Le Minh Duy` with role `Chủ vườn` |
| Browser-stored token exposure | PASS | `document.cookie` returned empty text and local/session storage keys were empty; session appears protected through HttpOnly/server-side Web proxy storage |
| Direct `/api/users/me` on Web host | BLOCKED | `http://localhost:3000/api/users/me` is not a Next route and returned the Web app 404 page |
| Web proxy `/api/auth/session` | BLOCKED | Direct navigation from the automation tab to `http://localhost:3000/api/auth/session` was blocked by the browser automation layer before a JSON response could be captured |
| Web proxy `/api/backend/farms` | BLOCKED | Direct navigation from the automation tab to `http://localhost:3000/api/backend/farms` was blocked by the browser automation layer before a JSON response could be captured |
| Verified `userId` from `/api/users/me` | BLOCKED | No actual `/api/users/me` JSON body was captured; no database lookup or guessed avatar/object ID was used as a substitute |

Conclusion for authentication: the Web UI session is visibly authenticated, but the required API proof `GET /api/users/me` with a verified `userId` is still not available from the current automation context. The rehearsal must not be upgraded to authenticated API E2E until that JSON response is captured safely.

### Existing Web Data Observed Through The Authenticated UI

| Entity | Observed data | Status for demo audit |
| --- | --- | --- |
| Farmer identity shown by Web UI | `Huynh Le Minh Duy`, role `Chủ vườn` | PASS for UI session display; API `userId` still BLOCKED |
| Dashboard farm summary | `TRANG TRẠI MINH PHÁT`, 440 managed trees, 4 cultivation zones, 12/12 IoT active | PASS as UI evidence only |
| Farm list | `Vườn sầu riêng Cai Lậy`, location `Tiền Giang, Cai Lậy`, `4 vùng`, `4.2 ha`, farm id link `farm-khoa-luan-2026` | PASS |
| Zones under `farm-khoa-luan-2026` | `Khu Bắc`/`KHU-BAC`, `Khu Nam`/`KHU-NAM`, `Khu Đông`/`KHU-DONG`, `Khu Tây`/`KHU-TAY`; each displayed with 25 trees | PASS |
| Zone detail | `Khu Bắc`, code `KHU-BAC`, 25 total trees, 25 evaluated, 22 healthy, 3 needing attention, 0 unevaluated, 88% safe | PASS |
| Tree map | 25 tree buttons `DC-T001` through `DC-T025` rendered for `Khu Bắc` | PASS |
| Diseased tree fixture candidate | `DC-T009`, alias `Cây Chín`, variety `Ri6`, status `Bệnh`, 1 diagnosis, last diagnosis `22/9/2026` | PASS |
| Diseased diagnosis detail | `DC-T009` diagnosis `ALLOCARIDARA_ATTACK`, disease text `Bọ chích hút lá sầu riêng`, confidence `61%`, timestamp `17:00:00 22/9/2026`, source `MOBILE` | PASS |
| Care plan on `DC-T009` | UI shows `Chưa có kế hoạch chăm sóc nào` | MISSING for care-plan rehearsal; do not create until API identity/ownership are verified |
| AI recommendation on `DC-T009` | UI shows action `Tạo đề xuất từ AI/KB`; no existing recommendation snapshot was visible | MISSING for recommendation rehearsal; do not create until API identity/ownership are verified |
| Recovery fixture candidate | `DC-T010`, alias `Cây Mười`, variety `Monthong`, 2 diagnoses; UI recovery analysis said `Đã hồi phục`, previous `LEAF_BLIGHT` 87%, current `HEALTHY_LEAF` 93% | PASS as existing UI evidence, but recovery status update was not triggered |
| Notification inbox | 7 unread CARE notifications were visible, including follow-up notifications for `DC-T077`, `DC-T005`, `DC-T097`, `DC-T008`, `DC-T086`, `DC-T076` | PASS for existing notification data; not proof of a new E2E-created notification |

Ownership note: Web source inspection showed the Web proxy attaches backend auth from server-side cookies, and Farm service source filters/checks farms by authenticated owner user id. The UI therefore gives strong owner-scoped evidence for the logged-in session. However, because `/api/users/me` did not produce a captured JSON body, ownership has not yet been independently proven by comparing a verified `userId` to API responses.

### Updated Data Gap Before Any Record Creation

| Required item | Current status | Safe next step |
| --- | --- | --- |
| Verified authenticated `userId` from `/api/users/me` | MISSING | Capture sanitized response body only, without headers/cookies/tokens |
| Owned farm/zone/tree via direct Gateway/API JSON | MISSING | After `userId` is verified, call the Web proxy or Gateway in a same-session test context and compare owner-scoped responses |
| Existing diseased tree for recommendation demo | AVAILABLE in UI | Candidate: `DC-T009` / `ALLOCARIDARA_ATTACK` |
| Existing care plan on diseased tree | MISSING in UI for `DC-T009` | Create only after API identity and ownership are verified |
| Existing recommendation snapshot on diseased tree | MISSING in UI for `DC-T009` | Generate only after API identity and ownership are verified |
| Existing notifications | AVAILABLE in UI | 7 unread CARE notifications exist; new notification idempotency still untested |
| Hardware/camera capture | NOT TESTED | Requires real hardware or approved fixture; heartbeat alone is insufficient |

Safe ways to unblock the API identity check without exposing secrets:

1. Use browser DevTools Network on the already logged-in Web app and copy only the JSON response body for `/api/auth/session` or `/api/backend/users/me`; do not copy request/response headers, cookies, bearer tokens, or refresh tokens.
2. Run a local same-session Playwright/browser test that reads the response body inside the logged-in browser context and writes a sanitized report containing only `userId`, email, role, and account status.
3. Add a temporary local-only diagnostic route/command only with explicit approval, returning sanitized session identity and no token material; remove it after verification.

### 2026-10-13 Auth Session Proxy Fix

Phase A root cause findings:

| Finding | Evidence | Result |
| --- | --- | --- |
| Backend identity endpoint exists | Auth service defines `GET /api/users/me` in `UserProfileController` and requires authenticated principal | PASS |
| Gateway route exists | Gateway maps `/api/users/**` to auth-service and injects `X-Auth-User-*` headers after bearer validation | PASS |
| Web internal proxy exists | Web route `/api/backend/[...path]` forwards HttpOnly cookie-backed bearer auth to Gateway | PASS |
| Web direct `/api/users/me` route was missing | Before the fix, `http://localhost:3000/api/users/me` hit no Next route and rendered the app 404 page | BUG FOUND |
| Browser automation cannot read top-level API body | After the fix, opening `http://localhost:3000/api/users/me` in the in-app browser is stopped with `ERR_BLOCKED_BY_CLIENT` before body capture | TOOLING BLOCKER |

Minimal Web fix applied:

| File | Change |
| --- | --- |
| `C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DurianCare-IoT-Web-Client\src\lib\auth\gatewayProxy.ts` | Added shared server-only authenticated Gateway proxy helper; it reads HttpOnly auth cookies server-side, refreshes tokens when needed, forwards bearer auth to Gateway, strips inbound cookie/host/origin headers, and never exposes token material in the response |
| `C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DurianCare-IoT-Web-Client\src\app\api\backend\[...path]\route.ts` | Reused the shared proxy helper; behavior should remain compatible with existing `/api/backend/**` clients |
| `C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DurianCare-IoT-Web-Client\src\app\api\users\[...path]\route.ts` | Added `/api/users/**` Web alias so `/api/users/me` forwards to Gateway `/api/users/me` with the active Web session |

Runtime checks after the fix:

| Request | Status | Evidence |
| --- | --- | --- |
| `GET http://localhost:3000/api/users/me` without browser cookies | PASS | HTTP `401`, JSON body `{"status":401,"error":"Unauthorized","message":"Phiên đăng nhập đã hết hạn."}` |
| `GET http://localhost:3000/api/backend/users/me` without browser cookies | PASS | HTTP `401`, same JSON unauthorized body |
| `GET http://localhost:8080/api/users/me` without bearer token | PASS | HTTP `401`, Gateway JSON message `Missing bearer token` |
| `GET http://localhost:3000/api/users/me` inside logged-in browser session | BLOCKED | Browser automation stopped top-level API navigation with `ERR_BLOCKED_BY_CLIENT`; no authenticated JSON body was captured |

Regression after the fix:

| Area | Result |
| --- | --- |
| Web typecheck | PASS: `npm run typecheck` |
| Web Jest | PASS: `npm test -- --runInBand`, 1 suite / 23 tests |
| Web lint | PASS WITH EXISTING WARNINGS: `npm run lint`, 0 errors / 10 warnings |
| Web build | PASS: `npm run build`, route manifest includes `ƒ /api/users/[...path]` |

No backend, mobile, AI, database, or fixture data was modified in this phase. No care plan, recommendation, task, diagnosis, notification, account, farm, zone, or tree record was created.

Updated E2E gate:

| Step | Status | Evidence / blocker |
| --- | --- | --- |
| Fix Web `/api/users/me` route compatibility | PASS | Web host now has `/api/users/[...path]`; unauthenticated request returns `401`, not `404` |
| Verify authenticated `userId` from HTTP response body | BLOCKED | Same-session browser body capture remains blocked by automation; no safe token/cookie extraction was attempted |
| Direct authenticated farm/zone/tree audit via Gateway JSON | BLOCKED | Requires verified identity/session JSON first |
| Create missing DC-T009 recommendation/care plan | BLOCKED | Correctly deferred until identity and ownership are proven by authenticated API response |
| Authenticated business E2E | BLOCKED | No write-path E2E can proceed without verified identity and ownership |

### 2026-10-13 Phase B Attempt: Same-Session API Verification

Phase B was attempted without reading or logging password, bearer token, refresh token, session cookie, request headers, or browser storage.

| Check | Result | Evidence |
| --- | --- | --- |
| Re-read Web proxy implementation | PASS | `gatewayProxy.ts` reads HttpOnly cookies server-side, refreshes access tokens if needed, strips inbound `cookie`, `host`, `origin`, `referer`, `content-length`, and forwards `Authorization: Bearer <server-side token>` only to Gateway |
| Re-read `/api/backend/[...path]` | PASS | Route now delegates to `proxyAuthenticatedGatewayRequest(request, /api/<path>)` |
| Re-read `/api/users/[...path]` | PASS | Route now delegates to `proxyAuthenticatedGatewayRequest(request, /api/users/<path>)` |
| Same-page `fetch` from logged-in dashboard | BLOCKED | In the in-app browser Playwright page evaluation scope, `typeof fetch` was `undefined` |
| Same-page `XMLHttpRequest` from logged-in dashboard | BLOCKED | In the same evaluation scope, `typeof XMLHttpRequest` was `undefined`; constructing XHR failed |
| Top-level API navigation | NOT USED | Avoided per Phase B instruction; previous top-level attempts were already blocked by browser layer |
| Profile page identity rendering | PARTIAL | `/profile` rendered `Huynh Le Minh Duy`, role `Chủ vườn`, and masked email was discoverable in DOM; no trustworthy user UUID was rendered |
| Verified `userId` through HTTP JSON | BLOCKED | No authenticated `/api/users/me` or equivalent JSON response body was captured |

Phase B conclusion: authenticated identity remains unverified at the required API evidence level. UI session evidence is strong enough to show a logged-in Farmer page, but it is not a substitute for `userId` from an authenticated HTTP JSON response. Therefore no real-data write-path action was performed.

Read-only runtime data status after Phase B:

| Area | Status | Notes |
| --- | --- | --- |
| Farm/zone/tree UI | PASS VIA UI ONLY | Existing UI evidence still shows `farm-khoa-luan-2026`, 4 zones, and `Khu Bắc` with 25 rendered trees |
| `DC-T009` diagnosis UI | PASS VIA UI ONLY | Existing UI evidence still shows `ALLOCARIDARA_ATTACK` at 61% confidence from source `MOBILE` |
| Direct API farm/zone/tree JSON | BLOCKED | Requires authenticated identity JSON first |
| Recommendations/care plans/tasks direct JSON | BLOCKED | Requires verified identity and ownership first |
| Notifications direct JSON | BLOCKED | Existing UI showed 7 unread CARE notifications, but direct API recipient verification remains blocked |

Write-path E2E status after Phase B:

| Step | Status | Reason |
| --- | --- | --- |
| Create/get recommendation for `DC-T009` | BLOCKED | Identity and ownership were not verified through authenticated API JSON |
| Accept recommendation | BLOCKED | No safe recommendation write may run before identity/ownership proof |
| Verify care plan/tasks/notification persistence | BLOCKED | Depends on accepted recommendation |
| Retry/idempotency check | BLOCKED | Depends on safe write-path fixture |
| Unauthorized access check | NOT TESTED | No second approved account/fixture was available; no owner mutation was attempted |
| Status transition/recovery gate | NOT TESTED | No safe write fixture was available; no tree status was changed |

Additional safe unblock options, still without exposing secrets:

1. Run a same-session browser automation environment that supports normal page `fetch`/XHR and capture only sanitized JSON fields from `/api/users/me`.
2. Add an explicitly approved temporary local-only rendered diagnostics page that calls `readSession` server-side and displays only `userId`, masked email, role, and account status; remove it after verification.
3. Have the user copy only the response body from browser DevTools Network for `/api/users/me` or `/api/auth/session`, excluding headers, cookies, Authorization, localStorage, and sessionStorage.

### Phase B Regression Results

| Area | Command | Result |
| --- | --- | --- |
| Backend Maven reactor | `mvn test` | PASS: reactor `BUILD SUCCESS`, total time `02:19`; modules `discovery-server`, `config-server`, `duriancare-gateway`, `duriancare-auth-service`, `duriancare-farm-service`, `duriancare-cultivation-service`, `duriancare-traceability-service`, `duriancare-notification-service`, `duriancare-search-service` all `SUCCESS` |
| Backend focused lifecycle tests inside reactor | `mvn test` | PASS: `TreeCarePlanServiceTest` 12 tests, `TreeDiagnosisServiceTest` 20 tests, `TreeServiceRecoveryGateTest` 6 tests; all failures/errors 0 |
| Web typecheck | `npm run typecheck` | PASS |
| Web Jest | `npm test -- --runInBand` | PASS: 1 suite, 23 tests |
| Web lint | `npm run lint` | PASS WITH WARNINGS: 0 errors, 10 existing warnings |
| Web build | `npm run build` | PASS; route manifest includes `ƒ /api/users/[...path]` |
| Mobile typecheck | `npm run typecheck` | PASS |
| Mobile Expo export | `npx expo export --platform android --output-dir <temp>` | PASS: Android bundle exported to a temp directory outside the repo; first attempted command containing a temp cleanup step was rejected by local safety policy before running, then rerun without deletion |
| AI Python compile | `python -m compileall duriancare-ai-service scripts\severity_training` | PASS |
| Frozen AI model hash check | Python SHA256 command for `leaf_detector_best.pt` and `mobilenetv2_classifier_high_acc.pth` | PASS: `leaf_detector_best.pt` size `113925667`, SHA256 `ac2bd7f82f9fd1e054f496a90f21ce77eb9697b27a71a4ecdf6d7c47ac71408b`; `mobilenetv2_classifier_high_acc.pth` size `9173070`, SHA256 `fc3dc5c92e43a9dbb196029fa56f9722ed91613d11ae7ccdadda1093fd1e5afe` |
| Severity guard tests | `python test_pipeline_guards.py` from `scripts/severity_training` | PASS: 4 tests; an earlier root-level invocation failed due `PYTHONPATH`/sibling import path, then passed from the correct script directory |
| Severity validation | `python scripts\severity_training\validate_dataset.py --dataset-root <Durian_Severity_Training>` | PASS: wrote validation summary; `Images=5`, `approved_annotations=0`, `annotation_errors=0` |
| Severity training gate | `python scripts\severity_training\training_gate.py --dataset-root <Durian_Severity_Training>` | EXPECTED BLOCK: exit code 1 with `DO_NOT_TRAIN`; blockers `PROTOCOL_NOT_FINALIZED`, `APPROVED_COUNT_TOO_LOW`, `CLASS_MINIMUM_TOO_LOW`; no training was run |

Regression conclusion: automated tests/builds are healthy, and severity remains correctly blocked from training. These results still do not constitute authenticated runtime business E2E because the required HTTP identity proof is blocked.

### 2026-10-13 Phase C: Authenticated Integration Harness

Phase C searched for a supported non-browser API verification path. Existing Postman/Newman/Insomnia/API collection harnesses were not found in the repos. Existing env inspection was limited to variable names only; no values were printed. The only matching environment variable present was `CODEX_SESSION_ID`, which is not a DurianCare auth context.

Authentication/session architecture confirmed for the harness:

| Layer | Finding |
| --- | --- |
| Gateway | Requires real bearer token for protected routes; injects `X-Auth-User-Id`, `X-Auth-Email`, and `X-Auth-Role` only after JWT validation |
| Auth service | Provides `POST /api/auth/login`, `POST /api/auth/refresh`, and authenticated `GET /api/users/me` |
| Web proxy | Stores access/refresh/session cookies as HttpOnly and forwards bearer server-side through `/api/backend/**` and the new `/api/users/**` alias |
| Browser session | Cannot be reused by a separate terminal harness without reading cookies/tokens, which is explicitly forbidden |

Minimal safe harness added:

| File | Purpose |
| --- | --- |
| `C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DurianCare-IoT-Backend\scripts\demo_e2e_harness.py` | Gateway-only integration harness. It reads auth context only from env (`DURIANCARE_E2E_ACCESS_TOKEN` or `DURIANCARE_E2E_EMAIL` + `DURIANCARE_E2E_PASSWORD`), never prints secrets, masks email, defaults to read-only, and requires `DURIANCARE_E2E_ALLOW_WRITE=true` before any write-path recommendation/care-plan action |

Harness behavior:

| Capability | Status |
| --- | --- |
| Auth source | Env-only; no browser cookie extraction, no DevTools token extraction, no hard-coded credentials |
| Identity verification | Calls `GET /api/users/me` after real Gateway login/token |
| Read-only ownership audit | Calls farms, zones, trees, tree detail, diagnosis history, latest diagnosis, care plans, latest recommendation, notifications |
| Target tree | Defaults to `DC-T009`, configurable through `DURIANCARE_E2E_TREE_CODE` |
| Write path | Disabled by default; only runs if `DURIANCARE_E2E_ALLOW_WRITE=true` and identity/ownership/latest diagnosis are already verified |
| Logging | Prints sanitized JSON only; no headers/cookies/tokens/passwords |

Phase C harness execution:

| Command | Exit code | Result |
| --- | ---: | --- |
| `python -m py_compile scripts\demo_e2e_harness.py` | 0 | PASS |
| `python scripts\demo_e2e_harness.py` | 2 | `BLOCKED_AUTH_CONTEXT`; no auth env existed, so no login/API audit/write-path was attempted |

Sanitized harness output summary:

| Field | Value |
| --- | --- |
| `status` | `BLOCKED_AUTH_CONTEXT` |
| `apiBase` | `http://localhost:8080` |
| `treeCode` | `DC-T009` |
| `writeEnabled` | `false` |
| `steps` | `[]` |
| `identity` | `null` |
| `ownership` | `null` |
| `writePath` | `NOT_TESTED` |
| `blocker` | Missing local auth context; set `DURIANCARE_E2E_ACCESS_TOKEN` or `DURIANCARE_E2E_EMAIL` + `DURIANCARE_E2E_PASSWORD` in the operator environment |

Phase C E2E status:

| Step | Status | Evidence / blocker |
| --- | --- | --- |
| Locate existing supported API harness | BLOCKED | No reusable Postman/Newman/API collection or auth-capable E2E harness was found |
| Add safe Gateway integration harness | PASS | `scripts/demo_e2e_harness.py` added and compiled |
| Verify identity through authenticated HTTP JSON | BLOCKED_AUTH_CONTEXT | No approved auth env was present; browser session was not harvested |
| Read farm/zone/tree/diagnosis API data | BLOCKED_AUTH_CONTEXT | Requires authenticated `userId` first |
| Run recommendation/care-plan write path | NOT TESTED | Correctly skipped because identity and ownership were not verified |
| Hardware/camera E2E | NOT TESTED | No real hardware capture fixture was used |

Phase C conclusion: the project now has a safe repeatable integration harness, but live authenticated API E2E remains blocked until an operator provides auth context through environment variables or another approved non-secret mechanism.

### 2026-10-13 Phase D: Safe Auth Context Setup And Read-Only Harness

Phase D re-audited `scripts/demo_e2e_harness.py` before attempting any runtime API verification. The harness was inspected without printing or storing any secret values.

Harness auth and safety behavior:

| Area | Finding |
| --- | --- |
| Auth env inputs | Supports `DURIANCARE_E2E_ACCESS_TOKEN`, or `DURIANCARE_E2E_EMAIL` + `DURIANCARE_E2E_PASSWORD` |
| Official login flow | Supported through `POST /api/auth/login`; token is kept in memory only |
| Default Gateway | `DURIANCARE_E2E_API_URL`, default `http://localhost:8080` |
| Target tree | `DURIANCARE_E2E_TREE_CODE`, default `DC-T009` |
| Write-path guard | Disabled unless `DURIANCARE_E2E_ALLOW_WRITE` is explicitly truthy; Phase D did not enable it |
| HTTP timeout | Uses a 30 second timeout for Gateway calls |
| HTTP error handling | `safe_call` captures non-2xx status without throwing; required `call` raises sanitized errors with method/path/status only |
| Refresh token | Not used by the harness; acceptable for the short read-only audit, but a longer rehearsal should add in-memory refresh handling if token expiry is observed |
| Secret filtering | Output is sanitized JSON; it records method, path, status, selected non-sensitive fields, masked email, and summarized business objects only |
| Forbidden sources | Does not read browser cookies, DevTools state, local browser session stores, or generated JWTs |

Local environment inspection checked only variable names. No `DURIANCARE_E2E_*` auth variable was present in this terminal environment, and no values were printed.

Phase D harness execution:

| Command | Exit code | Result |
| --- | ---: | --- |
| `python -m py_compile scripts\demo_e2e_harness.py` | 0 | PASS |
| `python scripts\demo_e2e_harness.py` | 2 | `BLOCKED_AUTH_CONTEXT`; no auth env existed, so no login, authenticated GET, ownership audit, or write-path was attempted |

Sanitized harness output summary:

| Field | Value |
| --- | --- |
| `status` | `BLOCKED_AUTH_CONTEXT` |
| `apiBase` | `http://localhost:8080` |
| `treeCode` | `DC-T009` |
| `writeEnabled` | `false` |
| `steps` | empty |
| `identity` | `null` |
| `ownership` | `null` |
| `readOnlyAudit` | `null` |
| `writePath` | `NOT_TESTED` |

Operator setup needed to run the read-only harness safely:

1. In a local terminal only, configure either `DURIANCARE_E2E_ACCESS_TOKEN` or `DURIANCARE_E2E_EMAIL` + `DURIANCARE_E2E_PASSWORD`.
2. Do not paste the password, token, cookie, refresh token, or authorization header into chat or the report.
3. Optionally set `DURIANCARE_E2E_API_URL=http://localhost:8080` and `DURIANCARE_E2E_TREE_CODE=DC-T009`.
4. Do not set `DURIANCARE_E2E_ALLOW_WRITE` for Phase D.
5. Run `python scripts\demo_e2e_harness.py` and retain only the sanitized JSON result.

Phase D E2E status:

| Step | Status | Evidence / blocker |
| --- | --- | --- |
| Re-audit harness auth inputs | PASS | Env names and login path verified from `scripts/demo_e2e_harness.py` |
| Verify harness secret filtering | PASS | Output excludes headers, cookies, password, bearer token, and refresh token |
| Compile harness | PASS | `python -m py_compile scripts\demo_e2e_harness.py` exit code `0` |
| Run read-only Gateway harness | BLOCKED_AUTH_CONTEXT | No approved auth env was present in the terminal environment |
| Verify authenticated identity | BLOCKED_AUTH_CONTEXT | Requires valid operator-provided auth context |
| Audit farm/zone/tree/diagnosis ownership | BLOCKED_AUTH_CONTEXT | Requires verified identity first |
| Write-path recommendation/care-plan flow | NOT TESTED | Explicitly out of scope for Phase D and not enabled |

Phase D conclusion: the harness is ready for a safe read-only authenticated run, but the live Gateway E2E remains blocked until the operator provides valid demo/test auth context through local environment variables. No business data was created, modified, or deleted.

### 2026-10-13 Phase E: Controlled Care Workflow Preflight

Phase E was run as a preflight only. No write-path flag was enabled, no diagnosis was created, no care recommendation was created, no care plan/task/notification record was created, and no tree health status was changed.

The operator reported a successful Phase D `READ_ONLY_PASS` through the real Gateway with these sanitized facts:

| Item | Reported Phase D result |
| --- | --- |
| Farmer identity | Verified through Gateway |
| Farm/zone/tree ownership | Verified through authenticated API |
| Target tree | `DC-T009` |
| Latest diagnosis | `diag-t009-pest` |
| Disease code | `ALLOCARIDARA_ATTACK` |
| Existing care plans | None on `DC-T009` |
| Latest recommendation | HTTP 404 / not present |
| Write-path | Disabled |

The local terminal used for this Phase E preflight did not contain any `DURIANCARE_E2E_*` variables, so authenticated Gateway write/read validation was not rerun from this process. The harness was run without write access and correctly returned `BLOCKED_AUTH_CONTEXT` with `writeEnabled=false`.

#### Contract And Business Rule Findings

| Area | Finding |
| --- | --- |
| Recommendation endpoint | `POST /api/trees/{treeId}/care-recommendation` resolves the actor through Gateway headers, verifies tree access with `treeService.requireTree`, resolves supplied/latest diagnosis, and rejects diagnoses that do not belong to the tree/farm |
| Recommendation idempotency | `TreeCareRecommendationRepository.findByDiagnosisIdAndSource(diagnosisId, "AI_RECOMMENDED")` returns the existing recommendation instead of saving another draft |
| AI/KB support for target disease | `ALLOCARIDARA_ATTACK` exists in `duriancare-ai-service/knowledge_base/diseases/allocaridara-attack.md` and direct AI runtime `GET /api/v1/recommendations/ALLOCARIDARA_ATTACK` returned HTTP 200 |
| Gateway protection | Gateway route `/api/v1/recommendations/**` is protected; unauthenticated `GET http://localhost:8080/api/v1/recommendations/ALLOCARIDARA_ATTACK` returned HTTP 401 |
| Accept endpoint | `POST /api/trees/{treeId}/care-recommendation/{recommendationId}/accept` verifies actor/tree ownership, verifies recommendation belongs to the same tree/farm, saves one care plan, creates task records, updates recommendation to `ACCEPTED`, and publishes follow-up notification metadata |
| Accept idempotency | `TreeCarePlanRepository.findByRecommendationId(recommendationId)` returns the existing care plan on retry and skips duplicate task/notification creation |
| Task failure handling | If task persistence fails after plan save, service deletes the newly saved plan and does not mark the recommendation accepted or publish notification |
| Notification owner | Follow-up notification is published with `receiverId = actor.userId()` and metadata includes `treeId`, `treeCode`, `farmId`, `zoneId`, `carePlanId`, `followUpDate`, and `targetUrl` |
| Notification idempotency | Notification service deduplicates by `sourceEventId`; care follow-up source event id is `CARE_FOLLOWUP:{planId}:{followUpDate}` |
| Status transitions | Existing service allows `PLANNED -> IN_PROGRESS/CANCELLED`, `IN_PROGRESS -> COMPLETED/CANCELLED`, and idempotent same-status responses |
| Severity/confidence safety | The care recommendation flow uses diagnosis `diseaseCode`; Phase E did not infer severity from confidence and did not invent medicine, dosage, or PHI |

#### Runtime AI Recommendation Evidence

| Check | Result |
| --- | --- |
| Direct AI runtime `GET http://localhost:8000/api/v1/recommendations/ALLOCARIDARA_ATTACK` | PASS: HTTP 200 |
| Disease code | `ALLOCARIDARA_ATTACK` |
| Vietnamese name | `Bọ chích hút lá sầu riêng` |
| Biological treatments | 7 entries |
| Organic treatments | 5 entries |
| Chemical treatments | 2 entries, with label/registration/PHI warnings in the KB |
| References | 10 entries |
| Gateway unauthenticated recommendation route | PASS auth boundary: HTTP 401 |

#### Phase E Test Results

| Command | Exit code | Result |
| --- | ---: | --- |
| `mvn -pl duriancare-farm-service -Dtest=TreeCarePlanServiceTest test` | 0 | PASS: 12 tests, 0 failures, 0 errors |
| `mvn -pl duriancare-notification-service "-Dtest=NotificationEventListenerTest,NotificationServiceImplTest" test` | 0 | PASS: 14 tests, 0 failures, 0 errors |
| `$env:PYTHONPATH=(Get-Location).Path; python tests\test_recommendation_service.py` from `duriancare-ai-service` | 0 | PASS: 3 tests |
| `python -m py_compile scripts\demo_e2e_harness.py` | 0 | PASS |
| `python scripts\demo_e2e_harness.py` without auth env | 2 | `BLOCKED_AUTH_CONTEXT`, `writeEnabled=false`; no API write attempted |

Initial AI test invocations using module-style imports failed because the `tests` directory is not an importable package and `PYTHONPATH` was not set. They were rerun with the correct local module path and passed.

#### Controlled Write-Path Proposal

Do not run this until the operator explicitly confirms that writing to the demo farm is acceptable.

Planned calls through authenticated Gateway:

| Step | Endpoint | Expected effect |
| --- | --- | --- |
| 1 | `POST /api/trees/{treeId}/care-recommendation` with body `{"diagnosisId":"diag-t009-pest"}` | Create or return one `AI_RECOMMENDED` draft recommendation for diagnosis `diag-t009-pest` |
| 2 | `GET /api/trees/{treeId}/care-recommendation/latest` | Verify snapshot source, disease code, KB-backed actions, warnings, and recommendation id |
| 3 | `POST /api/trees/{treeId}/care-recommendation/{recommendationId}/accept` with empty or minimal approved body | Create one care plan, create planned tasks from recommendation drafts, mark recommendation accepted, publish one CARE follow-up event |
| 4 | `GET /api/trees/{treeId}/care-plans` | Verify one plan appears for `DC-T009` with `diagnosisId=diag-t009-pest` and `diseaseCode=ALLOCARIDARA_ATTACK` |
| 5 | `GET /api/care-plans/{planId}/tasks` | Verify task records are tied to the new plan, same tree/farm/zone, and no duplicates |
| 6 | `GET /api/v1/notification/notifications?size=10` | Verify a CARE notification for the authenticated farmer with matching metadata |
| 7 | Repeat accept endpoint once | Verify idempotency: same care plan returned, no duplicate tasks/notification |

Expected persisted records if the write-path is approved:

| Collection/domain | Expected record count |
| --- | --- |
| Tree care recommendation | 1 new record if none exists for `diag-t009-pest` + `AI_RECOMMENDED`; otherwise existing draft returned |
| Tree care plan | 1 new record for accepted recommendation |
| Tree care tasks | One task per recommendation draft generated from KB snapshot |
| Notification | One CARE follow-up notification/event for the authenticated farmer, deduplicated by source event id |
| Tree health status | No expected change |
| Diagnosis | No expected change |

Rollback/containment notes:

| Area | Plan |
| --- | --- |
| Preferred safety path | Use a dedicated fixture tree or disposable test farm before using `DC-T009` on the demo farm |
| Demo farm write | If approved, treat the created recommendation/care plan/tasks/notification as demo evidence and record all ids from the sanitized harness output |
| Cleanup | No destructive DB deletion should be performed from this rehearsal. If cleanup is required, use an approved business/admin cleanup path or a dedicated seeded fixture environment |
| Failure handling | If accept fails during task persistence, service-level tests verify the new plan is deleted and notification is not published |
| Remaining risk | Repository-level idempotency is proven for normal retry; simultaneous concurrent duplicate writes are reduced by lookup guards but not proven by a unique database constraint in this preflight |

Phase E status:

| Gate | Status | Evidence |
| --- | --- | --- |
| Read-only Phase D prerequisite | READ_ONLY_PASS | Reported by operator with verified identity/ownership and target diagnosis `diag-t009-pest` |
| Preflight source/contract review | PASS | Recommendation, accept, task, notification, ownership and idempotency code paths reviewed |
| Fixture/unit regression | TEST_FIXTURE_PASS | Farm 12 tests, Notification 14 tests, AI recommendation 3 tests all passed |
| Live write E2E | NOT TESTED | Correctly deferred pending operator confirmation |
| Current blocker | BLOCKED_PENDING_OPERATOR_CONFIRMATION | Need explicit approval before setting `DURIANCARE_E2E_ALLOW_WRITE` or creating demo-farm records |

Phase E conclusion: `TEST_FIXTURE_PASS`; live write E2E is `NOT TESTED` and remains blocked until the operator confirms the controlled write-path plan.

## Environment Readiness

| Check | Result | Evidence |
| --- | --- | --- |
| Backend git branch/status checked | PASS | Branch `duy/cleanup-fixes`, HEAD `8fbdfa8`, dirty working tree preserved |
| Web git branch/status checked | PASS | Branch `duy/cleanup-fixes`, HEAD `abaa3ca`, dirty working tree preserved |
| Mobile git branch/status checked | PASS | Branch `duy/cleanup-fixes`, HEAD `cde1765`, dirty working tree preserved |
| Docker Compose config syntax | PASS | `docker compose --env-file infrastructure\.env -f infrastructure\docker-compose.yml config --quiet` returned success |
| Required no-default Compose env vars | PASS | Required variable names were present and non-empty in local env file; values were not logged here |
| Optional/default Compose vars | PASS WITH LIMITATION | Missing optional/default keys: `AI_DEBUG`, `AI_GIT_COMMIT`, `AWS_S3_AVATAR_PREFIX`, `ENABLE_YOLO_CROP`, `INTERNAL_SERVICE_TOKEN`, `KNOWLEDGE_DB_SCHEMA`, `REDIS_DB`, `YOLO_FALLBACK_MODEL_PATH`, `YOLO_MODEL_PATH`; Compose supplied defaults where configured |
| Port availability before startup | PASS | Ports checked free: `5432`, `27018`, `6379`, `9200`, `9092`, `1883`, `8084`, `18083`, `8761`, `8888`, `8082`, `18084`, `3001`, `8000`, `3002`, `8083`, `8085`, `8086`, `8080` |
| Existing Docker data safety | PASS | Existing containers were stopped/exited before startup; no volume reset or destructive cleanup was run |

## Services And Versions

| Service | Image / Version | Runtime status |
| --- | --- | --- |
| postgres | `postgres:16-alpine` | Up, Docker healthy |
| mongodb | `mongo:7.0` | Up, Docker healthy |
| redis | `redis:7-alpine` | Up, Docker healthy |
| kafka | `confluentinc/cp-kafka:7.7.1` | Up, Docker healthy |
| elasticsearch | `docker.elastic.co/elasticsearch/elasticsearch:8.17.0` | Up, Docker healthy |
| emqx | `emqx/emqx:5.8.4` | Up, Docker healthy |
| discovery-server | `duriancare/discovery-server:local` | Up, actuator `UP` |
| config-server | `duriancare/config-server:local` | Up, actuator `UP` |
| gateway | `duriancare/gateway:local` | Up, actuator `UP` |
| auth-service | `duriancare/auth-service:local` | Up |
| farm-service | `duriancare/farm-service:local` | Up, actuator `UP` |
| cultivation-service | `duriancare/cultivation-service:local` | Up, actuator `UP` |
| notification-service | `duriancare/notification-service:local` | Up, actuator `UP` |
| search-service | `duriancare/search-service:local` | Up, actuator `UP` |
| traceability-service | `duriancare/traceability-service:local` | Up, actuator `UP` |
| ai-service | `duriancare/ai-service:local` | Up, `/actuator/health` returned `UP`, models loaded, classifier loaded, detector ready, diagnosis ready, database ready |
| iot-service | `duriancare/iot-service:local` | Up |
| chat-service | `duriancare/chat-service:local` | Up |

## Commands Run

No command below contains secret values.

```powershell
git rev-parse --abbrev-ref HEAD
git rev-parse --short HEAD
git status --short
docker compose --env-file infrastructure\.env -f infrastructure\docker-compose.yml config --quiet
Get-NetTCPConnection -LocalPort <demo ports>
docker compose --env-file infrastructure\.env -f infrastructure\docker-compose.yml ps -a
docker compose --env-file infrastructure\.env -f infrastructure\docker-compose.yml up -d --build postgres mongodb redis kafka discovery-server config-server duriancare-auth-service duriancare-farm-service duriancare-notification-service duriancare-ai-service duriancare-gateway
docker compose --env-file infrastructure\.env -f infrastructure\docker-compose.yml build --progress plain duriancare-notification-service
docker compose --env-file infrastructure\.env -f infrastructure\docker-compose.yml ps
docker compose --env-file infrastructure\.env -f infrastructure\docker-compose.yml images
Invoke-WebRequest http://localhost:<port>/actuator/health
Invoke-WebRequest http://localhost:8080/api/farms
Invoke-WebRequest http://localhost:8080/api/trees/demo-tree-id
Invoke-WebRequest http://localhost:8080/api/auth/login -Method POST -Body <fake credentials>
```

## Startup Notes

The first full startup attempt failed during `duriancare-notification-service` image build with a Maven dependency-resolution failure. A targeted rebuild of `duriancare-notification-service` immediately afterward completed with Maven `BUILD SUCCESS`, indicating a transient build/dependency retrieval issue rather than a demonstrated source regression.

The second startup completed with exit code `0`. Compose built or reused all local service images, recreated containers, waited for configured dependency health checks, and started Gateway last.

Current Docker services were left running for demo inspection. No database reset, Docker volume deletion, branch reset, commit, or push was performed.

## Runtime Health Evidence

| Endpoint | Result |
| --- | --- |
| `http://localhost:8080/actuator/health` | HTTP 200, `{"status":"UP"}` |
| `http://localhost:8888/actuator/health` | HTTP 200, `{"status":"UP"}` |
| `http://localhost:8761/actuator/health` | HTTP 200, `{"status":"UP"}` |
| `http://localhost:8082/actuator/health` | HTTP 200, `{"status":"UP"}` |
| `http://localhost:8085/actuator/health` | HTTP 200, `{"status":"UP"}` |
| `http://localhost:8086/actuator/health` | HTTP 200, `{"status":"UP"}` |
| `http://localhost:8083/actuator/health` | HTTP 200, `{"status":"UP"}` |
| `http://localhost:18084/actuator/health` | HTTP 200, `{"status":"UP"}` |
| `http://localhost:8000/actuator/health` | HTTP 200, AI runtime `UP`, model/classifier/detector/diagnosis/database ready |
| `http://localhost:8000/health` | HTTP 404; expected because AI service exposes `/actuator/health` |

Gateway/auth smoke checks:

| Check | Result |
| --- | --- |
| `GET /api/farms` without token | HTTP 401 |
| `GET /api/trees/demo-tree-id` without token | HTTP 401 |
| `POST /api/auth/login` with fake credentials | HTTP 401 |

These smoke checks prove startup, public health readiness, and auth enforcement at the Gateway boundary. They do not prove authenticated business E2E.

## E2E Rehearsal Matrix

| Step | Status | Evidence / Blocker |
| --- | --- | --- |
| Start Docker runtime safely | PASS | Compose startup exit code `0`; containers up |
| Verify infra readiness | PASS | Postgres, MongoDB, Redis, Kafka, Elasticsearch, EMQX healthy |
| Verify service health/readiness | PASS | Gateway, Config, Discovery, Farm, Notification, Search, Traceability, Cultivation, AI health endpoints returned `UP` |
| Verify Gateway auth boundary | PASS | Protected Gateway routes returned HTTP 401 without token |
| Login with farmer demo account | BLOCKED | No valid, explicitly authorized farmer demo credential was provided |
| Open farm, zone, Tree Map via backend data | BLOCKED | Requires authenticated farmer account and seeded/known demo farm data |
| Open Tree Detail and verify `treeId`/`treeCode` | BLOCKED | Requires authenticated farmer account and known test tree |
| Submit diagnosis image through Gateway/API/UI | BLOCKED | Requires authenticated farmer account, known test tree, and approved fixture image |
| Verify recommendation from actual disease code and Knowledge Base | BLOCKED | Depends on authenticated diagnosis result |
| Accept recommendation and persist care plan/tasks/notification | BLOCKED | Depends on authenticated recommendation flow |
| Retry accept recommendation for idempotency | BLOCKED | Depends on authenticated recommendation flow |
| Verify invalid status transitions at runtime | BLOCKED | Requires authenticated plan owned by demo farmer |
| Follow-up/re-diagnosis progression | BLOCKED | Requires authenticated fixture that supports follow-up |
| Web UI consistency | BLOCKED | Backend is running, but no authorized demo login/data was available |
| Mobile UI consistency | BLOCKED | Backend is running, but no authorized demo login/data was available |
| Camera / ESP32 hardware capture | NOT TESTED | No hardware capture fixture was provided; heartbeat alone is not camera-capture evidence |

## Regression Status

No code changes were made during this demo rehearsal, so full regression was not rerun after startup. The immediately preceding high-risk review in this same finalization sequence recorded:

| Area | Prior result |
| --- | --- |
| Backend focused tests | 38 PASS |
| Backend full Maven reactor | BUILD SUCCESS |
| Web lint/typecheck/build/Jest | PASS, with 10 lint warnings |
| Mobile typecheck and Expo export | PASS |
| AI compile and model hash verification | PASS |
| Severity validation/guard/training gate | `DO_NOT_TRAIN` |

Because this phase was environment/startup focused and no source files were changed, these prior regression results remain the latest automated regression evidence. They are not a substitute for authenticated runtime E2E.

## Git Status Summary

Backend dirty working tree was preserved. Notable modified/untracked areas include:

- Backend service changes under `duriancare-farm-service`, `duriancare-notification-service`, `duriancare-gateway`, `duriancare-ai-service`, and `infrastructure/docker-compose.yml`.
- Finalization and severity artifacts under `artifacts/finalization/` and `artifacts/severity_training/`.
- New tree care recommendation/care task service classes and tests.
- This report: `artifacts/finalization/FINAL_DEMO_REHEARSAL_REPORT.md`.

Web dirty working tree was preserved:

- `src/components/camera/CameraSection.tsx`
- `src/components/export/ExportCompliancePanel.tsx`
- `src/components/notifications/NotificationInbox.tsx`
- `src/components/trees/TreeDetailPanel.tsx`
- `src/components/trees/ZoneTreesWorkspace.tsx`
- `src/lib/trees/client.ts`
- `src/lib/trees/types.ts`

Mobile dirty working tree was preserved:

- `app/(main)/_layout.tsx`
- `src/features/notification/notificationTargetResolver.ts`
- `src/features/trees/TreeDetailScreen.tsx`
- `src/features/trees/ZoneTreesScreen.tsx`
- `src/features/trees/treeApi.ts`
- `src/features/trees/treeTypes.ts`

## Known Limitations

- Authenticated Web UI inspection is available through the existing logged-in Farmer session, but authenticated API E2E remains blocked until `/api/users/me` or an equivalent sanitized session endpoint returns a verified `userId` without exposing token/cookie material.
- The current UI already exposes controlled farm/zone/tree data, including candidate diseased tree `DC-T009`; direct owner/user comparison through API JSON has not yet been captured.
- No production credentials or data were used.
- No generated JWT, default admin shortcut, database mutation, or auth bypass was used.
- Severity model training remains gated by `DO_NOT_TRAIN`; no training was run and frozen model files were not changed.
- Hardware/camera/ESP32 flow was not tested with real hardware.
- Health checks and Gateway 401 checks prove readiness and auth enforcement only; they do not prove diagnosis, recommendation, care plan, task, notification, or recovery lifecycle correctness at runtime.

## Conditions Required To Finish Authenticated E2E

Provide or enable all of the following in a safe demo/test environment:

1. A sanitized `GET /api/users/me` or `/api/auth/session` response body from the already logged-in Web session, containing only identity/profile fields and no headers/cookies/tokens.
2. A same-session Web proxy or Gateway JSON response for farms/zones/trees to compare against the verified `userId`.
3. Approval to use existing UI fixture candidate `DC-T009` for the recommendation/care-plan rehearsal, or another named owned tree.
4. An approved test image fixture for disease diagnosis if a new diagnosis must be created.
5. Confirmation that local Docker data can be used as-is, without destructive resets.
6. Optional hardware fixture details if ESP32/camera capture must be rehearsed.

Historical runtime-E2E conclusion for the earlier demo rehearsal phase: `BLOCKED`

Latest final requirements conclusion: `PARTIALLY_FIXED`
