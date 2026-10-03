# Tree Health Care E2E Audit

**Date:** 2026-10-03  
**Branch:** duy/cleanup-fixes  
**Auditor:** Claude Sonnet 4.6 (automated code audit)

---

## Scope

Full end-to-end audit of the tree health care flow across three repos:

- `DurianCare-IoT-Backend` (Spring Boot, farm-service + notification-service + auth-service)
- `DurianCare-IoT-Web-Client` (Next.js)
- `DurianCare-IoT-Mobile-App` (React Native / Expo)

---

## Flow Stages

```
TREE MAP → TREE DETAIL → AI DIAGNOSIS → KNOWLEDGE BASE
→ CARE PLAN → FOLLOW-UP → RE-DIAGNOSIS → RECOVERY EVALUATION
→ RECOVERED CONFIRMATION → TREE MAP UPDATE → NOTIFICATION
```

---

## Audit Table

| # | Area | Backend | Web | Mobile | Status |
|---|------|---------|-----|--------|--------|
| 1 | treeId always in context (no null treeId) | `POST /api/trees/{treeId}/diagnoses` — @PathVariable, @NotBlank imageUrl | `AIPanel` passes `treeId` prop | `AIPanel` in `TreeDetailScreen` receives `treeId` from route param | **PASS** |
| 2 | AI diagnosis save | `TreeDiagnosisService.saveDiagnosis()` saves record + infers healthStatus; `@NotBlank imageUrl`, `@NotBlank diseaseCode` | `treeClient.saveDiagnosis()` | `saveDiagnosis()` in `treeApi.ts` | **PASS** |
| 3 | Confidence scale 0–1 | Stored as `Double confidence` (0.0–1.0) in `TreeDiagnosisRecord` | `prediction.confidence / 100` before save | `prediction.confidence / 100` before save | **PASS** |
| 4 | KB search by disease code | `KnowledgeArticleRepository.searchByStatus()` — JPQL `like :searchPattern` on title/excerpt/tags | `knowledgeClient.search(code)` | `knowledgeApi.search(code)` + shows "Chưa có hướng dẫn" when empty | **PASS** |
| 5 | Care plan linked to diagnosis/KB | `TreeCarePlan` has `diagnosisId`, `knowledgeArticleId`, `diseaseCode` fields | `CarePlanSection` accepts `latestDiseaseCode`, creates plan via `treeClient.createCarePlan()` | `CarePlanSection` accepts `diseaseCode`, `diagnosisId` | **PASS** |
| 6 | Follow-up notification stored in DB | `TreeCarePlanService.createCarePlan()` publishes Kafka `CARE_FOLLOWUP` event; `NotificationEventListener` consumes + persists `Notification` document | Creates care plan which triggers backend event | Creates care plan which triggers backend event | **PASS** |
| 7 | Re-diagnosis does NOT overwrite history | `TreeDiagnosisService.saveDiagnosis()` always inserts new `TreeDiagnosisRecord`; old records preserved; `listDiagnoses()` returns paginated history DESC | Diagnosis timeline shows all records | Diagnosis timeline scrollable list | **PASS** |
| 8 | Recovery evaluation before RECOVERED confirm | `POST /api/trees/{treeId}/evaluate-recovery` compares last 2 diagnoses; returns `RecoveryOutcome` | RecoveryPanel calls `treeClient.evaluateRecovery()` on open; blocks confirm if not RECOVERED/IMPROVED ✅ (fixed this session) | RecoveryPanel calls `evaluateRecovery()` on open; blocks confirm if not RECOVERED/IMPROVED ✅ (fixed this session) | **PASS** |
| 9 | Tree Map updates from DB after RECOVERED | `updateTreeHealthStatus()` persists to MongoDB; `listTrees()` returns DB data | `onDiagnosisSaved` calls `treeClient.listTrees(zoneId).then(setTrees)` | `onDiagnosisSaved` calls `listTrees(zoneId).then(setTrees)` | **PASS** |
| 10 | RECOVERED state visible on Tree Map | `TreeMapCanvas.tsx` HEALTH_COLORS has RECOVERED (#0891b2) | HEALTH_COLORS includes RECOVERED | ZoneTreesScreen HEALTH_COLORS/LABELS/BG include RECOVERED ✅ (fixed this session) | **PASS** |
| 11 | Notification type mapping | `NotificationEventListener` maps `CARE_FOLLOWUP` → `NotificationType.CARE`; Kafka topic via `${KAFKA_ALERT_TOPIC}` | N/A (consumer) | N/A (consumer) | **PASS** |
| 12 | No hard-coded treeId/farmId | No hard-coded IDs in business logic across all three repos | No hard-coded IDs | No hard-coded IDs | **PASS** |
| 13 | No mock API / fake data | All API calls hit real endpoints; no mocked responses | No mock API | No mock API | **PASS** |
| 14 | Diagnosis history ≠ health status | `TreeDiagnosisRecord` collection separate from `DurianTree.healthStatus` field | Displayed as separate concepts | Displayed as separate concepts | **PASS** |
| 15 | Web/Mobile same backend business logic | Single backend serves both | Uses same `/api/*` endpoints | Uses same `/api/*` endpoints | **PASS** |

---

## Bugs Found and Fixed This Session

| Bug | File | Fix |
|-----|------|-----|
| RECOVERED missing from mobile tree map color/label/bg maps | `ZoneTreesScreen.tsx` | Added RECOVERED: `#0891b2` / "Đã hồi phục" / `#e0f2fe` (commit `cde1765`) |
| Web RecoveryPanel called PATCH /health-status directly without evaluateRecovery | `TreeDetailPanel.tsx` | Gate behind `evaluateRecovery()` call; block confirm if STABLE/WORSENED/UNCERTAIN (commit `162f009`) |
| Mobile RecoveryPanel same — no evaluation before confirm | `TreeDetailScreen.tsx` | Same fix as web (commit `cde1765`) |

---

## Remaining Architectural Gaps (Out of Scope)

| Gap | Note |
|-----|------|
| Care Schedule / Cultivation sync with TreeCarePlan | No link from `TreeCarePlan` to a cultivation/schedule system; would require new domain model |
| KB article tags must contain disease codes | Search depends on KB article content; articles must be tagged with disease codes (e.g. `Algal_Leaf_Spot`) for search to return results — runtime data dependency, not a code bug |
| Mobile imageUrl is local file URI | `photo.uri` from expo-image-picker is a local URI; backend accepts it but cannot serve/display it externally — would require upload-to-S3 before save for persistence |

---

## Phase Compliance Summary

| Phase | Requirement | Result |
|-------|-------------|--------|
| Phase 2 | treeId never null | PASS |
| Phase 3 | Confidence 0–1 scale | PASS |
| Phase 4 | KB search by disease code | PASS |
| Phase 5 | Care plan linked to KB/diagnosis | PASS |
| Phase 6 | Follow-up notification in real DB | PASS |
| Phase 7 | Re-diagnosis preserves history | PASS |
| Phase 8 | evaluateRecovery gates confirmation | PASS (fixed) |
| Phase 9 | Tree Map updates from DB | PASS |
| Phase 10 | Care Schedule sync | PARTIAL (gap noted) |
| Phase 11 | Web/Mobile parity | PASS |
