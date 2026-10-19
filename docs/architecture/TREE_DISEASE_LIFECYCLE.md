# Tree Disease Lifecycle / Treatment Follow-Up Integration

**Date:** 2026-10-06  
**Branch:** `duy/cleanup-fixes`  
**Scope:** Backend farm-service, Web Client, Mobile App

## 1. End-to-End Lifecycle

```text
Tree Map
  -> Tree Detail
  -> AI diagnosis saved to backend
  -> Diagnosis history is appended
  -> Tree health summary is recalculated from latest diagnosis
  -> Knowledge Base search uses disease code
  -> Care plan is created from latest diagnosis
  -> Follow-up notification is published with tree navigation metadata
  -> Farmer re-opens the exact tree from notification
  -> Farmer re-diagnoses the tree
  -> Backend evaluates previous diagnosis vs current diagnosis
  -> UI explains outcome and recommended next action
  -> Farmer may confirm RECOVERED only when backend gate allows it
  -> Tree Map refreshes from persisted DB state
```

## 2. Status and History Are Separate

`TreeDiagnosisRecord` is immutable history: every diagnosis creates a new record. It is not overwritten by recovery confirmation.

`DurianTree.healthStatus` is the current map/status summary. It is updated from either:

- AI diagnosis save: `TreeDiagnosisService.saveDiagnosis()`
- Farmer status transition: `TreeService.transitionHealthStatus()`

This keeps the Tree Map fast to render while preserving the full disease timeline for audit and follow-up.

## 3. Confidence Rule

Model confidence is stored and displayed only as model confidence.

It must not be interpreted as:

- disease severity
- recovery progress
- worsening level
- treatment success

Recovery evaluation now compares the implied health status and disease identity of the two most recent diagnoses. A confidence increase or decrease alone does not make a tree improved or worsened.

## 4. Recovery Outcome Rules

`TreeDiagnosisService.evaluateRecovery()` and `TreeService.enforceRecoveryGate()` use the same outcome helper.

| Previous diagnosis | Current diagnosis | Outcome | Meaning |
| --- | --- | --- | --- |
| Any disease/suspected state | Healthy/current implied healthy | `RECOVERED` | Tree can be confirmed recovered |
| `DISEASED` | `SUSPECTED` with same disease code | `IMPROVED` | Continue monitoring; backend allows recovered confirmation only through explicit farmer action |
| Same non-healthy disease state and code | Same non-healthy disease state and code | `STABLE` | Continue treatment/follow-up |
| `HEALTHY` | Non-healthy | `WORSENED` | New disease signal appeared |
| `SUSPECTED` | `DISEASED` with same disease code | `WORSENED` | Disease evidence strengthened by health classification, not by confidence |
| Different non-healthy disease codes | Different non-healthy disease codes | `UNCERTAIN` | Re-check image/diagnosis before changing treatment |

Allowed `RECOVERED` transition:

```text
PATCH /api/trees/{treeId}/health-status RECOVERED
  -> TreeService.enforceRecoveryGate()
  -> allowed only for RECOVERED or IMPROVED outcome
  -> otherwise HTTP 409
```

## 5. Backend Integration Points

### Diagnosis

- `POST /api/trees/{treeId}/diagnoses`
- Appends a `TreeDiagnosisRecord`
- Infers `impliedHealthStatus`
- Updates current tree disease summary fields
- Does not delete or mutate previous diagnosis records

### Recovery evaluation

- `POST /api/trees/{treeId}/evaluate-recovery`
- Reads the latest two diagnoses
- Returns:
  - previous disease/code/confidence
  - current disease/code/confidence
  - outcome
  - reason
  - evaluatedAt

### Care plan

- `POST /api/trees/{treeId}/care-plans`
- Receives the latest diagnosis id from Web/Mobile when available
- Persists `diagnosisId`, `diseaseCode`, `knowledgeArticleId`, `followUpDate`
- Publishes a `CARE_FOLLOWUP` notification when `followUpDate` is present

### Follow-up notification metadata

The care follow-up notification now carries enough context to return the farmer to the exact tree.

```json
{
  "treeId": "...",
  "treeCode": "...",
  "farmId": "...",
  "zoneId": "...",
  "carePlanId": "...",
  "followUpDate": "YYYY-MM-DD",
  "targetUrl": "/dashboard/client/farms/{farmId}/zones/{zoneId}?treeId={treeId}"
}
```

## 6. Web Flow

Primary files:

- `src/components/trees/ZoneTreesWorkspace.tsx`
- `src/components/trees/TreeDetailPanel.tsx`
- `src/components/notifications/NotificationInbox.tsx`

Flow:

1. Tree Map loads persisted tree status from backend.
2. Query param `?treeId=` opens the matching tree detail panel.
3. Tree Detail shows diagnosis history and care plan section.
4. Care plan creation sends the latest diagnosis id.
5. When two or more diagnoses exist, Tree Detail calls `evaluateRecovery()`.
6. The evaluation card explains outcome and next action.
7. Recovery confirmation calls `PATCH /health-status` with `RECOVERED`.
8. Backend gate remains authoritative and may return HTTP 409.
9. After successful save/transition, Tree Map refreshes from backend.

Notification routing:

- Prefer backend-provided `metadata.targetUrl`
- Fallback to `farmId + zoneId + treeId`
- Opens `/dashboard/client/farms/{farmId}/zones/{zoneId}?treeId={treeId}`

## 7. Mobile Flow

Primary files:

- `src/features/trees/ZoneTreesScreen.tsx`
- `src/features/trees/TreeDetailScreen.tsx`
- `src/features/notification/notificationTargetResolver.ts`
- `app/(main)/trees/[treeId].tsx`

Flow:

1. Zone Tree Map loads persisted tree status from backend.
2. Notification metadata with `treeId` navigates to `/(main)/trees/[treeId]`.
3. Tree Detail shows diagnosis history, evaluation, recovery, and care plan sections.
4. Care plan creation sends the latest diagnosis id.
5. Recovery confirmation calls the backend health-status endpoint with `RECOVERED`.
6. Backend gate remains authoritative and may reject confirmation.
7. Tree Map refreshes from backend after diagnosis/status changes.

## 8. Non-Goals

This integration does not:

- retrain or change the disease detection model
- claim the model is more accurate
- reset or seed the database
- create demo-only data
- bypass authorization
- infer disease severity from confidence

## 9. Verification Targets

Code-level verification should include:

- Backend unit tests for recovery outcomes and backend gate
- Backend unit test for care follow-up notification metadata
- Web typecheck
- Mobile typecheck
- Runtime E2E when services, database, and a valid farmer JWT are available

