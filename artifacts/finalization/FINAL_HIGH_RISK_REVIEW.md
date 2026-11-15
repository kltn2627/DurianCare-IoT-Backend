# Final High-Risk Review — DurianCare-IoT

Date: 2026-10-11

## Verdict

HIGH-RISK REVIEW PASSED

This means the reviewed high-risk source flows have no remaining proven blocking issues after the fixes and regression tests below. It does not claim a fresh live Gateway runtime E2E passed, because Docker runtime verification is blocked by environment.

## Reviewed Flows

- `CreateCarePlan`
- `UpdateCarePlanStatus`
- `TreeCarePlanService`
- Diagnosis -> Recommendation -> Care Plan -> Tasks -> Notification
- Recovery evaluation and `RECOVERED` tree transition gate

GitNexus `detect_changes(scope=all)` still reports `risk_level=high`, with 43 changed symbols and these affected processes:

- `CreateCarePlan -> FarmNotFoundException`
- `CreateCarePlan -> FarmAccessDeniedException`
- `CreateCarePlan -> Publish`
- `CreateCarePlan -> Status`
- `UpdateCarePlanStatus -> FarmNotFoundException`
- `UpdateCarePlanStatus -> FarmAccessDeniedException`
- `UpdateCarePlanStatus -> Status`

The high risk is expected because these are core farm/tree lifecycle flows.

## Findings

| ID | Severity | Status | Finding |
| --- | --- | --- | --- |
| HR-001 | High | Fixed | Manual care-plan creation with the same `diagnosisId` could create duplicate plans and duplicate follow-up notification attempts on request retry. |
| HR-002 | High | Fixed | `updateStatus` accepted arbitrary enum transitions, including direct `PLANNED -> COMPLETED` and transitions out of terminal states. |
| HR-003 | Medium | Fixed | If recommendation acceptance saved a plan and task persistence then failed, a plan without tasks could remain. The service now deletes the saved plan before rethrowing. |
| HR-004 | Medium | Verified | `diagnosisId` ownership is guarded by resolving the tree through the authenticated actor and checking diagnosis `treeId` and `farmId`. |
| HR-005 | Medium | Verified | When `diagnosisId` is provided, `diseaseCode` is taken from the persisted diagnosis; conflicting client disease code is rejected. |
| HR-006 | Medium | Verified | Recommendation acceptance is idempotent by `recommendationId`; existing plan is returned and tasks/notifications are not recreated. |
| HR-007 | Medium | Verified | Notification retry/idempotency uses deterministic `sourceEventId` and notification service returns existing records for already processed source events. |
| HR-008 | Medium | Verified | `RECOVERED` tree status remains gated by latest two diagnoses and rejects `WORSENED`, `STABLE`, and `UNCERTAIN`. |
| HR-009 | Open | Environment | Live Gateway runtime E2E was not run. No Docker services were running and backend `.env` is absent. |

## Fixes Applied

- Added `TreeCarePlanRepository.findByDiagnosisIdAndSource(...)`.
- `createCarePlan(...)` now returns the existing manual plan for repeated `diagnosisId + MANUAL` requests and does not republish follow-up notification for the duplicate request.
- `updateStatus(...)` now validates allowed transitions:
  - `PLANNED -> IN_PROGRESS`
  - `PLANNED -> CANCELLED`
  - `IN_PROGRESS -> COMPLETED`
  - `IN_PROGRESS -> CANCELLED`
  - same-status retry is idempotent
  - `COMPLETED` and `CANCELLED` are terminal
- `acceptRecommendation(...)` now deletes the saved plan if task persistence fails before recommendation acceptance and notification publication.
- Added regression tests for duplicate manual care-plan request, invalid status transition, valid status transition, same-status retry, and task persistence failure rollback.

## Non-Issues / Verified By Review

- Unauthorized tree access is still blocked by `treeService.requireTree(actor, treeId)`.
- A diagnosis from a different tree or farm is rejected before plan creation.
- Status API response is built from the saved `TreeCarePlan`.
- Plan status updates do not directly set tree `RECOVERED`; tree recovery remains controlled by the separate recovery gate.
- Farm notification publisher catches Kafka publish failures, so notification-service outage does not abort care-plan persistence.

## Tests

### Backend

| Command | Result |
| --- | --- |
| `mvn -pl duriancare-farm-service "-Dtest=TreeCarePlanServiceTest,TreeServiceRecoveryGateTest,TreeDiagnosisServiceTest" test` | PASS, 38 tests |
| `mvn test` | PASS, full reactor `BUILD SUCCESS` |

### Web

| Command | Result |
| --- | --- |
| `npm run lint` | PASS with 10 warnings |
| `npm run typecheck` | PASS |
| `npm run build` | PASS, 44 static pages |
| `npm test -- --runInBand` | PASS, 23 tests |

### Mobile

| Command | Result |
| --- | --- |
| `npm run typecheck` | PASS |
| `npx expo export --platform web` | PASS, 75 static routes |

### AI / Severity

| Command | Result |
| --- | --- |
| AI Python compile | PASS |
| AI model hash verification | PASS |
| `python scripts\severity_training\test_pipeline_guards.py` | PASS, 4 tests |
| `python scripts\severity_training\validate_dataset.py ...` | PASS |
| `python scripts\severity_training\training_gate.py ...` | EXPECTED BLOCKER: `DO_NOT_TRAIN` |

Severity gate reasons:

- `PROTOCOL_NOT_FINALIZED`
- `APPROVED_COUNT_TOO_LOW`
- `CLASS_MINIMUM_TOO_LOW`

## Runtime E2E

Not performed.

Reason:

- `docker compose -f infrastructure\docker-compose.yml ps` showed no running services.
- Backend repo has no `.env`.
- Compose requires environment variables such as database, Mongo, Redis, JWT, Kafka, mail, and AI settings.
- No valid farmer credentials were available for authenticated Gateway smoke.

No Docker volume reset, DB reset, auth bypass, or fake runtime pass was performed.

## Git Status Summary

Backend remains dirty with prior lifecycle/AI/notification/gateway changes, new severity artifacts, and this high-risk review/finalization documentation.

Backend `git diff --stat`:

```text
18 files changed, 653 insertions(+), 63 deletions(-)
```

Backend touched in this review:

- `duriancare-farm-service/src/main/java/com/duriancare/farm/repository/TreeCarePlanRepository.java`
- `duriancare-farm-service/src/main/java/com/duriancare/farm/service/TreeCarePlanService.java`
- `duriancare-farm-service/src/test/java/com/duriancare/farm/service/TreeCarePlanServiceTest.java`
- `artifacts/finalization/FINAL_HIGH_RISK_REVIEW.md`

Web status still contains existing tree/notification UI changes plus earlier final lint fixes:

Web `git diff --stat`:

```text
7 files changed, 441 insertions(+), 36 deletions(-)
```

- `src/components/camera/CameraSection.tsx`
- `src/components/export/ExportCompliancePanel.tsx`
- `src/components/notifications/NotificationInbox.tsx`
- `src/components/trees/TreeDetailPanel.tsx`
- `src/components/trees/ZoneTreesWorkspace.tsx`
- `src/lib/trees/client.ts`
- `src/lib/trees/types.ts`

Mobile status still contains existing tree/notification route changes:

Mobile `git diff --stat`:

```text
6 files changed, 431 insertions(+), 33 deletions(-)
```

- `app/(main)/_layout.tsx`
- `src/features/notification/notificationTargetResolver.ts`
- `src/features/trees/TreeDetailScreen.tsx`
- `src/features/trees/ZoneTreesScreen.tsx`
- `src/features/trees/treeApi.ts`
- `src/features/trees/treeTypes.ts`

## Remaining Issues

- Live runtime E2E through Gateway remains blocked by missing `.env`, stopped services, and no valid farmer credentials.
- Severity AI training remains intentionally blocked until human annotation requirements are met.
- Web lint has 10 warnings.
- Expo doctor was not rerun in this pass; prior run reported SDK patch-version mismatches.

HIGH-RISK REVIEW PASSED
