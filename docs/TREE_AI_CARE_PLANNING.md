# Tree AI Care Planning

## Architecture

Tree disease care planning is owned by the farm service. Web and Mobile call farm-service endpoints through Gateway; they do not call the AI service directly.

Flow:

1. Farmer saves a tree diagnosis.
2. Farm service resolves the diagnosis and disease code from DB.
3. Farm service requests a structured recommendation from the AI service.
4. Farm service persists a recommendation snapshot for that diagnosis.
5. Farmer accepts or edits the draft.
6. Farm service creates a `TreeCarePlan` and persisted `TreeCareTask` records.
7. Farm service publishes a follow-up notification with a deterministic source event id.
8. Farmer re-diagnoses the tree and existing recovery evaluation compares previous/latest diagnoses.

## Recommendation Source

The primary source is AI-service structured KB via `GET /api/v1/recommendations/{diseaseCode}`. The response is the existing structured `DiseaseRecommendation` payload from AI service KB tables.

Farm service snapshots only the render/action subset needed by Tree Detail:

- disease code and names
- summary
- care actions
- monitoring actions
- warnings
- references
- generated timestamp
- source

## Fallback

If AI service or structured KB is unavailable, farm service creates a conservative monitoring recommendation. This fallback does not include pesticide names, dosage, concentration, safety interval, certification, or legal claims.

## Snapshot

Snapshots are stored in `tree_care_recommendations` and copied into accepted `tree_care_plans`. This prevents old diagnosis recommendations from changing when KB content changes later.

Idempotency key:

```text
diagnosisId + source
```

Calling the recommendation endpoint repeatedly for the same diagnosis/source returns the existing draft.

## Acceptance And Edit

Draft endpoint:

```text
POST /api/trees/{treeId}/care-recommendation
GET  /api/trees/{treeId}/care-recommendation/latest
PUT  /api/trees/{treeId}/care-recommendation/{recommendationId}
POST /api/trees/{treeId}/care-recommendation/{recommendationId}/accept
```

Farmers may edit task title, description, scheduled date, ordering, treatment text, and follow-up date. Integrity fields are server-owned: diagnosis id, tree id, farm id, and disease code are never trusted from client edits.

Accepting a draft is idempotent via `recommendationId`. If a care plan already exists for the recommendation, farm service returns the existing plan.

## Automatic Tasks

Care tasks are persisted in `tree_care_tasks`, not frontend state. Each task stores:

- carePlanId
- diagnosisId
- treeId
- treeCode
- scheduledDate
- title
- description
- status
- source
- sortOrder

The implementation intentionally avoids `cultivation_schedules` because that schema requires material, dosage, and safety interval. Those fields are not always present in KB and must not be invented.

## Notification Idempotency

Follow-up notification source event id:

```text
CARE_FOLLOWUP:{carePlanId}:{followUpDate}
```

Notification-service stores `sourceEventId`, so retries, refreshes, duplicate Kafka delivery, or service restarts return/reuse the existing notification instead of creating duplicates.

## Follow-Up

The care plan always receives a follow-up date. If the recommendation lacks a structured interval, farm service uses the configured conservative default:

```text
duriancare.tree-care.default-follow-up-days=7
```

This default is a system rule, not an AI prediction.

## Recovery Loop

Recovery remains unchanged:

```text
previous diagnosis + latest diagnosis -> recovery evaluation
```

Confidence delta is not used. The system does not create fake recovered diagnoses. The existing RECOVERED gate still controls farmer confirmation.

## Limitations

- The current task model is tree-care specific and intentionally separate from cultivation schedules.
- Structured follow-up interval extraction is conservative; absent KB intervals fall back to the configured default.
- Chemical details are only surfaced when present in structured KB. The system does not generate dosage, concentration, PHI, or safety claims on its own.
