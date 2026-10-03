# Web / Mobile Parity Matrix

**Last updated:** 2026-10-03  
**Methodology:** Code audit + static analysis + MongoDB direct query (farmer JWT NOT AVAILABLE for full runtime UI test)

Legend: ✅ PASS | ⚠️ PARTIAL | ❌ FAIL | 🔒 NOT TESTABLE (requires farmer JWT)

---

## Core Health Status

| Feature | Backend | Web | Mobile | Notes |
|---------|---------|-----|--------|-------|
| `TreeHealthStatus` enum (all 5 values) | ✅ HEALTHY/SUSPECTED/DISEASED/TREATING/RECOVERED | ✅ `types.ts` union type | ✅ `treeTypes.ts` union type | All 5 defined in all repos |
| Health status from DB (not computed from frontend) | ✅ `listTreesForMap()` reads `durian_trees.healthStatus` | ✅ `treeClient.listTrees()` → UI | ✅ `listTrees()` → UI | Backend is source of truth |
| No fake/local health state in frontend | ✅ — | ✅ No `localStorage` health | ✅ No cached health state | Verified: neither client stores health locally |

---

## Tree Map (RECOVERED Color Fix)

| Feature | Backend | Web | Mobile | Notes |
|---------|---------|-----|--------|-------|
| RECOVERED marker color (#0891b2 cyan) | — | ✅ `TreeMapCanvas.HEALTH_COLORS.RECOVERED` | ✅ `ZoneTreesScreen.HEALTH_COLORS.RECOVERED` | Fixed this session (was gray before) |
| RECOVERED label ("Đã hồi phục") | — | ✅ `TreeMapCanvas.HEALTH_LABELS.RECOVERED` | ✅ `ZoneTreesScreen.HEALTH_LABELS.RECOVERED` | Fixed this session |
| RECOVERED background color (#e0f2fe) | — | ✅ `ZoneTreesWorkspace.HEALTH_BADGE_CLASS.RECOVERED` | ✅ `ZoneTreesScreen.HEALTH_BG.RECOVERED` | Fixed this session |
| RECOVERED in legend (auto from HEALTH_LABELS) | — | ✅ `Object.entries(HEALTH_LABELS)` | ✅ `Object.entries(HEALTH_LABELS)` | Both legends auto-include all entries |
| Tree Map refresh from DB after TREATING→RECOVERED | ✅ `listTreesForMap()` returns DB state | ✅ `onDiagnosisSaved` → `treeClient.listTrees()` | ✅ `onDiagnosisSaved` → `listTrees()` | Both refresh from API, not local state |
| Full runtime test (reload, logout/login) | — | 🔒 NOT TESTABLE — FARMER JWT REQUIRED | 🔒 NOT TESTABLE — FARMER JWT REQUIRED | Code audit PASS; DB has 3 RECOVERED trees |

---

## Tree Detail Panel / Screen

| Feature | Backend | Web | Mobile | Notes |
|---------|---------|-----|--------|-------|
| RECOVERED health badge styling | — | ✅ `HEALTH_COLORS.RECOVERED = cyan` | ✅ `HEALTH_COLORS.RECOVERED = #0891b2` | Both have correct cyan styling |
| RecoveryPanel only for TREATING | — | ✅ `needsRecovery = health === "TREATING"` | ✅ `needsRecovery = health === "TREATING"` | |
| TreatmentPanel only for DISEASED/SUSPECTED | — | ✅ `needsTreatment = health === "DISEASED" \|\| "SUSPECTED"` | ✅ `needsTreatment = health === "DISEASED" \|\| "SUSPECTED"` | |
| CarePlanSection for DISEASED/TREATING/SUSPECTED | — | ✅ | ✅ | Both show correctly |
| RECOVERED tree shows no action panels | — | ✅ (no needsRecovery, no needsTreatment, no needsCarePlan) | ✅ | |

---

## Recovery Evaluation (Phase 8)

| Feature | Backend | Web | Mobile | Notes |
|---------|---------|-----|--------|-------|
| `POST /api/trees/{treeId}/evaluate-recovery` | ✅ Compares 2 latest diagnoses by diagnosedAt DESC | ✅ `treeClient.evaluateRecovery()` | ✅ `evaluateRecovery()` in treeApi | |
| evaluateRecovery called before showing confirm | — | ✅ `handleExpand()` fires API call on open | ✅ `handleExpand()` fires API call on open | Fixed this session |
| Recovery gate: WORSENED/STABLE/UNCERTAIN → blocked | ✅ outcome logic | ✅ `RECOVERY_OUTCOME_ELIGIBLE` set check | ✅ `RECOVERY_OUTCOME_ELIGIBLE` set check | |
| "Chưa đủ điều kiện xác nhận hồi phục" shown | — | ✅ rendered when `!eligible` | ✅ rendered when `!eligible` | |
| Recovery gate: RECOVERED/IMPROVED → allowed | ✅ | ✅ | ✅ | |
| DC-T026 evaluation (TREATING, latest=PHOMOPSIS) | WORSENED (current=PHOMOPSIS, prev=HEALTHY_LEAF, different codes) | 🔒 NOT TESTABLE — FARMER JWT | 🔒 NOT TESTABLE — FARMER JWT | Expected: gate blocks confirm |
| DC-T010 evaluation (TREATING, latest=HEALTHY_LEAF) | RECOVERED (current is healthy) | 🔒 NOT TESTABLE — FARMER JWT | 🔒 NOT TESTABLE — FARMER JWT | Expected: gate allows confirm |
| Backend PATCH enforced (gate at service layer) | ✅ `TreeService.enforceRecoveryGate()` blocks WORSENED/STABLE/UNCERTAIN → HTTP 409 | — | — | Fixed: `FarmConflictException` thrown when not eligible |

---

## Recovery Confirmation Flow

| Feature | Backend | Web | Mobile | Notes |
|---------|---------|-----|--------|-------|
| Confirm saves RECOVERED_BY_FARMER diagnosis | ✅ `POST /diagnoses` with code=RECOVERED_BY_FARMER | ✅ `treeClient.saveDiagnosis()` | ✅ `saveDiagnosis()` | |
| Health status inferred as HEALTHY on RECOVERED_BY_FARMER | ✅ `inferHealthStatus("RECOVERED_BY_FARMER")` → HEALTHY | — | — | |
| History preserved (no overwrite) | ✅ always inserts, never updates old records | ✅ | ✅ | All 3 tiers: history is additive |

---

## Diagnosis History

| Feature | Backend | Web | Mobile | Notes |
|---------|---------|-----|--------|-------|
| History never overwritten | ✅ `diagnosisRepository.save(newRecord)` always | ✅ shows timeline of all records | ✅ shows timeline | |
| Diagnosis count per tree | ✅ `diagnosisRepository.countByTreeId()` | ✅ `diagnosisCount` in TreeDetail | ✅ `diagnosisCount` | |
| Confidence stored as 0-1 (not %) | ✅ `Double confidence` field (0.0-1.0) | ✅ `prediction.confidence / 100` before save | ✅ `prediction.confidence / 100` before save | |
| DC-T026 diagnosis count | ✅ 2 records in DB | 🔒 | 🔒 | PHOMOPSIS + HEALTHY_LEAF |

---

## Care Plan

| Feature | Backend | Web | Mobile | Notes |
|---------|---------|-----|--------|-------|
| Care plan linked to treeId, diagnosisId, diseaseCode | ✅ `TreeCarePlan` has all 3 | ✅ | ✅ | |
| Care plan visible for DISEASED/TREATING/SUSPECTED | — | ✅ | ✅ | |
| Create care plan via API | ✅ `POST /api/trees/{treeId}/care-plans` | ✅ `treeClient.createCarePlan()` | ✅ `createCarePlan()` | |
| DB has real care plans | ✅ 2 care plans in `tree_care_plans` collection | — | — | Both for DC-T008 (DISEASED) |
| `diagnosisId` correctly linked | ✅ Second care plan: `diagnosisId: "6abfac4b..."` | — | — | Real diagnosis ID, not hardcoded |

---

## Follow-Up Notification

| Feature | Backend | Web | Mobile | Notes |
|---------|---------|-----|--------|-------|
| CARE notification created when care plan has followUpDate | ✅ Kafka `CARE_FOLLOWUP` → notification-service | — | — | |
| `treeId` in notification metadata (not null) | ✅ `metadata: {treeId: "tree-008", ...}` | — | — | Verified from DB |
| Notification `type = CARE` | ✅ `NotificationType.CARE` | — | — | |
| Notification routes to correct tree on click | ⚠️ metadata has `treeId` but UI routing NOT VERIFIED | 🔒 NOT TESTABLE | 🔒 NOT TESTABLE | `treeId` present in metadata; click handler not tested |

---

## Re-diagnosis (Tree Context)

| Feature | Backend | Web | Mobile | Notes |
|---------|---------|-----|--------|-------|
| treeId always in context | ✅ `@PathVariable String treeId` required | ✅ AIPanel receives `treeId` prop | ✅ AIPanel receives `treeId` route param | |
| Re-diagnosis adds to history, no overwrite | ✅ | ✅ | ✅ | |
| treeId null scenario impossible | ✅ Validated at controller level | ✅ AIPanel only renders when tree is loaded | ✅ | |

---

## Source of Truth Audit

| Feature | Result | Evidence |
|---------|--------|----------|
| Tree health from API (not localStorage) | ✅ PASS | Code audit: no localStorage health in either client |
| Tree health from API (not computed from diagnoses in frontend) | ✅ PASS | Backend computes healthStatus from `durian_trees.healthStatus`; frontends just display it |
| No hardcoded treeId/farmId in runtime flow | ✅ PASS | Grep found no hard-coded IDs in business logic |
| No mock/demo data in runtime flow | ✅ PASS | Care plans use real diagnosisIds; notifications use real treeIds |
| RECOVERED trees in DB reflect DB writes | ✅ PASS | 3 RECOVERED trees in DB (tree-005, tree-071, tree-072) |

---

## DB Summary (as of 2026-10-03)

| Status | Count |
|--------|-------|
| HEALTHY | 51 |
| SUSPECTED | 23 |
| DISEASED | 14 |
| TREATING | 9 (DC-T001, T010, T026, T073-T075, T077-T079) |
| RECOVERED | 3 (DC-T005, T071, T072) |
| **Total** | **100** |

## Known Limitations

1. ~~**Backend PATCH endpoint unprotected**~~ — **FIXED**: `TreeService.enforceRecoveryGate()` now evaluates the top 2 diagnoses inline when `newStatus == RECOVERED`. WORSENED/STABLE/UNCERTAIN → `FarmConflictException` (HTTP 409). TREATING transition remains unrestricted.
2. **N+1 query in `listTreesForMap`**: For each tree, a separate query fetches latest diagnosis. Acceptable for current scale; monitor for zones with 100+ trees.
3. **Mobile imageUrl is local file URI**: `photo.uri` from expo-image-picker is device-local. Backend stores it but cannot serve it externally.
4. **DC-T005 data inconsistency**: healthStatus=RECOVERED but latest diagnosis is ALGAL_LEAF_SPOT. Farmer manually set RECOVERED on 2026-10-02 via direct PATCH, overriding disease diagnosis. This is a valid use of the endpoint but illustrates the UI gate bypass risk.
