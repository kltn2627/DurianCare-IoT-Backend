# Codex Takeover Audit

**Date:** 2026-10-06  
**Scope:** Backend, Web Client, Mobile App takeover audit only. No feature implementation, no DB reset, no seeding, no push.

## Audit Status Legend

- **PASS**: Verified from source and/or automated test in this session.
- **PARTIAL**: Implemented or documented, but an important runtime path or integration remains unverified.
- **FAIL**: Verified broken in this session.
- **NOT TESTABLE**: Could not be exercised safely in this session, usually because required runtime services, farmer JWT, hardware, or DB containers were unavailable.

## A. Current Architecture

| Area | Status | Current Architecture |
|---|---:|---|
| Backend monorepo | PASS | Spring Boot services: gateway, auth, farm, cultivation, notification, search, traceability, config, discovery; Node.js IoT/chat services; FastAPI AI service. |
| Gateway routing | PASS | `duriancare-gateway/src/main/resources/application.yml` routes auth/knowledge, farm/tree, cultivation/harvest, IoT/camera/public, AI, chat, traceability, notification, search. |
| Farm / Tree domain | PASS | `farm-service` stores farms/zones/trees in MongoDB; tree map reads `DurianTree.healthStatus` from DB and enriches latest diagnosis/count. |
| Tree diagnosis | PASS | `TreeDiagnosisService.saveDiagnosis()` appends `TreeDiagnosisRecord` and updates tree health from diagnosis code. History is additive, not overwritten. |
| Recovery | PASS | `TreeDiagnosisService.evaluateRecovery()` compares two latest diagnoses; `TreeService.transitionHealthStatus()` enforces the same gate before RECOVERED. |
| Care Plan | PARTIAL | `TreeCarePlan` exists in `farm-service` with tree/diagnosis/disease/follow-up fields and Kafka notification event. Runtime scheduling/click-through was not verified. |
| Knowledge Base | PARTIAL | Auth-service `knowledge_articles` exists; search includes title/excerpt/tags. Runtime article contents/counts not verified because DB was not running. |
| Cultivation / Harvest | PARTIAL | Cultivation service has seasons/plans/activities/harvest-batches routes; code/schema present, runtime API not verified this session. |
| Camera / IoT | PARTIAL | IoT service has self-registration, heartbeat, DB-backed camera devices, capture/history/schedule routes. Hardware/runtime capture not verified. |
| AI | PARTIAL | Frozen disease-diagnosis baseline documented; code still points at generic leaf detector + MobileNetV2 classifier. Runtime service not running. |

## B. Current Branch / Commit For Each Repo

| Repo | Branch | HEAD | Tracking | Working Tree |
|---|---|---:|---|---|
| Backend | `duy/cleanup-fixes` | `8fbdfa8` | `origin/duy/cleanup-fixes` | Clean before report creation |
| Web Client | `duy/cleanup-fixes` | `abaa3ca` | `origin/duy/cleanup-fixes` | Clean |
| Mobile App | `duy/cleanup-fixes` | `cde1765` | `origin/duy/cleanup-fixes` | Clean |

## C. Recent Claude Changes

| Repo | Recent Commits | Status |
|---|---|---:|
| Backend | `8fbdfa8 fix(backend): enforce evaluateRecovery gate on PATCH /health-status → RECOVERED`; `4894a9e feat(backend): tree care plan + recovery evaluation + care followup notification`; `25ed321 feat(farm-service): add PATCH /api/trees/{id}/health-status` | PASS |
| Web | `162f009 fix(web): gate RecoveryPanel behind evaluateRecovery + add RecoveryEvaluationResponse type`; `24ac3c0 feat(web): add CarePlanSection + care plan types and API client methods`; `8a225b0 fix(web): wire TREATING/RECOVERED health-status transitions + fix KB search` | PASS |
| Mobile | `cde1765 fix(mobile): add RECOVERED state to tree map + gate RecoveryPanel behind evaluateRecovery`; `170839d feat(mobile): add CarePlanSection and fix needsRecovery scope`; `33bce11 fix(mobile): use disease.code (not name) for KB article search` | PASS |

No unpushed local-only commits were detected from `git branch -vv`; each current branch points at its matching origin branch.

## D. What Is Actually Implemented

| Feature | Status | Evidence |
|---|---:|---|
| Farm/zone/tree map DB model | PASS | `DurianTree`, `Farm`, `FarmZone`, `TreeService.listTreesForMap()`, and `GenerateTreesRequest` exist. Tree map returns DB `healthStatus`. |
| Auto tree generation | PASS | `POST /api/zones/{zoneId}/trees/generate` creates row/column-positioned trees with unique generated tree codes. |
| Health states | PASS | Backend/Web/Mobile all model `HEALTHY`, `SUSPECTED`, `DISEASED`, `TREATING`, `RECOVERED`. |
| Diagnosis save/history | PASS | Backend `POST/GET /api/trees/{treeId}/diagnoses`; Web/Mobile call same endpoints with tree context. |
| Recovery backend gate | PASS | `TreeService.enforceRecoveryGate()` blocks RECOVERED unless outcome is `RECOVERED` or `IMPROVED`. |
| Recovery frontend gates | PASS | Web and Mobile RecoveryPanel call `evaluateRecovery()` and allow confirmation only for `RECOVERED`/`IMPROVED`. |
| Care plans | PASS | Backend CRUD-lite endpoints exist; Web/Mobile create/list care plans from tree detail. |
| Follow-up notification event | PARTIAL | Backend publishes `CARE_FOLLOWUP` with `treeId`, `carePlanId`, `followUpDate`; notification-service maps it to `NotificationType.CARE`. Runtime persistence not verified. |
| Camera self-registration | PASS | `POST /api/v1/public/cameras/register` and `/heartbeat` exist with `X-Camera-Key`; DB table `camera_devices` stores IP/online/tree_id. |
| AI frozen baseline | PASS | `artifacts/AI_FREEZE.md` freezes detector/classifier SHA256, labels, thresholds, and explicitly says not to retrain/change baseline. |

## E. Code-Implemented But Runtime-Unverified

| Area | Status | Reason |
|---|---:|---|
| Full Tree Health E2E through Gateway/UI | NOT TESTABLE | Docker containers were not running; no farmer JWT was available. |
| Current DB counts and data freshness | NOT TESTABLE | `docker ps` returned no running containers. I did not start the stack or mutate DB state. |
| Care Plan notification persistence | NOT TESTABLE | Kafka/notification-service/MongoDB runtime path not active. |
| Web browser flow | PARTIAL | Web typecheck passes; no browser/session/JWT runtime test was run. |
| Mobile device flow | PARTIAL | Source audited; typecheck has two dependency/type errors, so runtime parity is not fully proven. |
| Camera capture/preview/heartbeat | NOT TESTABLE | No IoT service container or ESP32-CAM hardware/runtime available. |
| AI prediction/chat runtime | NOT TESTABLE | AI service container was not running in this session. |

## F. Broken APIs

| API | Status | Finding |
|---|---:|---|
| `PATCH /api/trees/{treeId}/health-status` | PASS | Code/test verified. Direct RECOVERED bypass is blocked for WORSENED/STABLE/UNCERTAIN. |
| `POST /api/trees/{treeId}/evaluate-recovery` | PASS | Source present and shared by Web/Mobile clients. |
| Tree/care plan APIs | PARTIAL | Source/client mappings exist. Runtime HTTP not exercised. |
| Cultivation/harvest APIs | PARTIAL | Gateway routes and services exist. Runtime HTTP not exercised. |
| Camera APIs | PARTIAL | Routes exist. Runtime HTTP/capture not exercised. |
| Known current API failures | NOT TESTABLE | No live API stack was running to test 404/500 behavior. |

## G. Broken Frontend Screens

| Screen | Status | Finding |
|---|---:|---|
| Web Tree Map / Tree Detail | PASS | `npm run typecheck` passed. Code uses DB-backed tree list and re-fetches after diagnosis/status changes. |
| Web RecoveryPanel | PASS | Calls evaluateRecovery before confirm and checks eligible outcomes. |
| Web CarePlanSection | PARTIAL | Typecheck passes; runtime save/list not tested. |
| Mobile Tree Map / Tree Detail | PARTIAL | Source has RECOVERED styling and DB-backed tree list, but project typecheck fails on unrelated dependency/type resolution. |
| Mobile RecoveryPanel | PARTIAL | Source calls evaluateRecovery and checks eligible outcomes; runtime not tested. |
| Mobile Camera screen | PARTIAL | Uses a fixed UI `DEVICE_ID = esp32-cam-01` pattern via camera client calls; runtime device registry/capture not tested. |

Mobile typecheck failures observed:

```text
app/(main)/_layout.tsx(2,40): Cannot find module 'expo-router/build/react-navigation/bottom-tabs'
src/features/chat/useChatRealtime.ts(2,33): Cannot find module 'socket.io-client'
```

## H. DB Missing Data

| Data Area | Status | Finding |
|---|---:|---|
| Current DB counts | NOT TESTABLE | No running MongoDB/PostgreSQL containers. |
| Historical docs claim demo data | PARTIAL | Prior docs report 1 farm, 4 zones, 100 trees, diagnoses, cultivation, harvest, notifications. This audit did not re-verify live DB. |
| Knowledge articles | NOT TESTABLE | Prior recovery reports conflict over whether auth DB had 0 articles before later fixes. Live DB required. |
| Camera devices | NOT TESTABLE | Schema and routes exist; current rows unknown. |

## I. Recovery Backend Gate Status

| Check | Status | Evidence |
|---|---:|---|
| Invalid WORSENED direct PATCH RECOVERED blocked | PASS | Unit test `worsenedEvaluationBlocksRecoveredTransition()` passes. |
| Invalid UNCERTAIN direct PATCH RECOVERED blocked | PASS | Unit test `uncertainEvaluationBlocksRecoveredTransition()` passes. |
| Invalid STABLE direct PATCH RECOVERED blocked | PASS | Unit test `stableEvaluationBlocksRecoveredTransition()` passes. |
| Valid RECOVERED direct PATCH allowed | PASS | Unit test `recoveredEvaluationAllowsRecoveredTransition()` passes. |
| Valid IMPROVED direct PATCH allowed | PASS | Unit test `improvedEvaluationAllowsRecoveredTransition()` passes. |
| TREATING transition remains unrestricted | PASS | Unit test `treatingTransitionRequiresNoEvaluation()` passes. |
| HTTP status mapping | PASS | `FarmApiExceptionHandler` maps `FarmConflictException` to HTTP 409. |
| Runtime API/DB verification | NOT TESTABLE | No services running/JWT unavailable. |

Test command run:

```powershell
mvn -pl duriancare-farm-service -Dtest=TreeServiceRecoveryGateTest test
```

Result: **6 tests run, 0 failures, 0 errors.**

## J. Care Plan Status

| Check | Status | Evidence |
|---|---:|---|
| Domain model | PASS | `TreeCarePlan` has `treeId`, `farmId`, `diagnosisId`, `diseaseCode`, `knowledgeArticleId`, `treatment`, `startDate`, `followUpDate`, `status`. |
| Backend endpoints | PASS | `POST/GET /api/trees/{treeId}/care-plans`, `GET/PATCH /api/care-plans/{planId}` exist. |
| Web implementation | PASS | Web `TreeDetailPanel` lists and creates care plans. |
| Mobile implementation | PARTIAL | Mobile source lists and creates care plans; typecheck has unrelated dependency errors. |
| Link to cultivation schedule | PARTIAL | Care plans are separate from cultivation schedules; no verified scheduler integration beyond notification event. |
| Runtime DB data | NOT TESTABLE | DB not running. |

## K. Follow-Up Notification Status

| Check | Status | Evidence |
|---|---:|---|
| Event emitted on care plan follow-up date | PASS | `TreeCarePlanService.publishFollowUpNotification()` emits `CARE_FOLLOWUP`. |
| Notification carries tree id | PASS | Metadata includes `"treeId"`, `"carePlanId"`, `"followUpDate"`. |
| Notification type mapping | PASS | Notification listener maps `CARE_FOLLOWUP` to `NotificationType.CARE`. |
| Actual persisted notification | NOT TESTABLE | Kafka and notification MongoDB were not running. |
| UI click routes to tree | NOT TESTABLE | Requires live notification data/session. |

## L. Camera Status

| Check | Status | Evidence |
|---|---:|---|
| Self-registration endpoint | PASS | `POST /api/v1/public/cameras/register` exists and upserts camera IP/metadata. |
| Heartbeat endpoint | PASS | `POST /api/v1/public/cameras/heartbeat` updates `ip_address`, `port`, `online`, `last_seen`, or auto-registers. |
| DB schema | PASS | `V9__add_camera_devices.sql` creates `camera_devices` with `ip_address`, `online`, `last_seen`, `tree_id`, `zone_id`, `farm_id`. |
| Capture and preview routes | PARTIAL | `cameraRoutes.js` has capture, snapshot, image, history, devices, config, schedule routes. Runtime not tested. |
| Hard-coded IP risk | PARTIAL | Docker env still has fallback defaults (`ESP32_CAMERA_URL`, `DEFAULT_ESP32_IP`), but runtime route resolves DB device URL first and uses fallback only when missing. |
| Physical ESP32 path | NOT TESTABLE | No hardware/container runtime in this session. |

## M. AI Status

| Check | Status | Evidence |
|---|---:|---|
| Disease diagnosis model scope | PASS | Frozen classes are `ALGAL_LEAF_SPOT`, `ALLOCARIDARA_ATTACK`, `HEALTHY_LEAF`, `LEAF_BLIGHT`, `PHOMOPSIS_LEAF_SPOT`. |
| Pest detection separation | PASS | Docs explicitly treat future pest detection as separate Phase O; no retraining performed. |
| Frozen baseline protected | PASS | No model files, thresholds, labels, or AI runtime config were changed. |
| Current runtime health | NOT TESTABLE | AI container was not running. |
| Current model checksums | NOT TESTABLE | I did not hash large model files in this audit because source docs already record them and no AI implementation was requested. |

## N. Web / Mobile Parity

| Feature | Backend | Web | Mobile | Status |
|---|---:|---:|---:|---:|
| 5 health states | PASS | PASS | PASS | PASS |
| Tree map reads DB healthStatus | PASS | PASS | PASS | PASS |
| RECOVERED visible on map/detail | PASS | PASS | PASS | PASS |
| Diagnosis save with tree context | PASS | PASS | PASS | PASS |
| Recovery gate before RECOVERED | PASS | PASS | PASS | PASS |
| Care plan create/list | PASS | PASS | PARTIAL | PARTIAL |
| Follow-up notification | PARTIAL | N/A | N/A | PARTIAL |
| Runtime E2E | NOT TESTABLE | NOT TESTABLE | NOT TESTABLE | NOT TESTABLE |
| Typecheck | PASS for scoped farm test | PASS | FAIL | PARTIAL |

## O. Exact Next Actions

1. **Runtime DB audit first**: start the existing Docker stack only when approved, without `down -v`; query MongoDB/PostgreSQL for current farms, zones, trees, diagnoses, care plans, notifications, camera devices, cultivation seasons/schedules, and harvest batches.
2. **Gateway API verification with farmer JWT**: login/provide token for `minhdii1510@gmail.com`; verify tree list, tree detail, invalid RECOVERED PATCH, valid RECOVERED PATCH, care plan create, notification persistence, cultivation/harvest APIs.
3. **Fix Mobile typecheck blockers**: resolve `expo-router/build/react-navigation/bottom-tabs` import and `socket.io-client` type/dependency issue, then re-run `npm run typecheck`.
4. **Verify Care Plan → Follow-up notification runtime**: create a care plan with `followUpDate`, confirm notification-service persists a CARE notification with non-null `metadata.treeId`.
5. **Verify Web/Mobile parity with real account**: use the same tree and care plan from DB; confirm tree map reload, tree detail history, recovery panel, and care plan list match on both clients.
6. **Camera runtime audit after Tree Health baseline**: test ESP32 registration, heartbeat, DB IP update, snapshot, capture-now, timeout behavior, and tree assignment.
7. **AI runtime audit without retraining**: run health/predict smoke tests against frozen baseline; do not change model weights, thresholds, or class labels.

## Commands Run In This Audit

| Command | Result |
|---|---|
| `git status --short` in all 3 repos | Clean before report creation |
| `git branch -vv` and `git log --oneline --decorate -20` in all 3 repos | Branch/commit data captured |
| `mvn -pl duriancare-farm-service -Dtest=TreeServiceRecoveryGateTest test` | PASS, 6/6 |
| Web `npm run typecheck` | PASS |
| Mobile `npm run typecheck` | FAIL, 2 dependency/type errors listed above |
| `docker ps` | No running containers |
| GitNexus MCP `list_repos` | Backend not visible in MCP registry; Mobile index stale by 37 commits |

## Audit Conclusion

The backend recovery bypass gap appears **closed at source/test level**: direct API PATCH to RECOVERED is now gated and covered by focused tests. The project is **not fully runtime-verified in this session** because no Docker services were running and no farmer JWT/hardware was available. The most defensible next phase is runtime database/API verification of Tree Health + Care Plan + notification before any Pest Detection or AI training work.
