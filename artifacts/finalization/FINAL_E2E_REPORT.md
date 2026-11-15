# DurianCare-IoT Final E2E Report

Date: 2026-10-11

## What Was Verified In This Session

- Backend service modules compile and tests pass.
- Web client builds production routes and passes tests.
- Mobile app typechecks and exports for web.
- AI Python source compiles.
- Severity pipeline validation and guard scripts run.
- Existing AI model files are present and hash-verified against `artifacts/AI_FREEZE.md`.

## What Was Not Runtime-Verified In This Session

- End-to-end HTTP flow across gateway, auth, farm, notification, AI, and database.
- Browser-driven authenticated flows against a live backend.
- Mobile app against a live backend.
- Physical IoT gateway and camera hardware route.
- Fresh database persistence inspection.

Reason: Docker Compose services were not running. No database reset or service bootstrap was performed because the final prompt explicitly forbids destructive reset behavior.

## Existing E2E Evidence

The tree disease lifecycle implementation has prior evidence in:

- `docs/architecture/TREE_DISEASE_LIFECYCLE.md`
- `docs/architecture/TREE_DISEASE_LIFECYCLE_E2E.md`
- `docs/TREE_AI_CARE_PLANNING.md`

Use those documents as implementation evidence, and use this report as the final source/regression verification evidence.

## Phase H E2E Status - 2026-10-14

Final live E2E verdict: `PARTIALLY_COMPLETE`

| E2E Layer | Status | Evidence |
| --- | --- | --- |
| Authenticated read-only inventory | HISTORICAL READ_ONLY_PASS | Phase D report previously verified farmer identity/ownership through Gateway; not rerun in Phase H |
| Tree removal write path | SOURCE_AND_TEST_VERIFIED | Backend soft-removal service/controller added; Web/Mobile action added; farm-service tests pass |
| Live tree removal against demo data | NOT TESTED | No destructive live business write was executed |
| Care recommendation live write path | NOT TESTED | Still requires operator confirmation and isolated fixture |
| Export-release live write path | NOT TESTED | Prepared/covered by tests, but no live dossier write was executed |
| AI runtime safety | PASS | `ai_validate.py --integrity-check --phase safety` returned 15/15 pass |
| Severity live model | NOT AVAILABLE | Training gate remains `DO_NOT_TRAIN` |
| Hardware camera E2E | HARDWARE_NOT_TESTED | No physical ESP32-CAM capture was available |

Operator confirmation is still required before any live write-enabled E2E run.

## Phase I E2E Status - 2026-10-14

Final live E2E verdict: `PARTIALLY_COMPLETE`

| E2E Layer | Status | Evidence |
| --- | --- | --- |
| Service readiness | PARTIAL PASS | `docker compose -f infrastructure/docker-compose.yml ps` showed Gateway, Auth, Farm, Cultivation, Traceability, Notification, Search, AI, IoT, data stores, Kafka, EMQX, Discovery, Config, and Chat containers up. |
| Authenticated read-only API | BLOCKED_AUTH_CONTEXT | `scripts/demo_e2e_harness.py` ran with write mode disabled and returned missing auth context. No tokens/cookies/passwords were printed or stored. |
| Live write E2E | NOT TESTED | `DURIANCARE_E2E_ALLOW_WRITE` was absent/false. No business records were created, updated, accepted, deleted, or reset. |
| Tree removal source/test E2E | SOURCE_AND_TEST_VERIFIED | Removed-tree lifecycle was reviewed and regression-tested: active lists exclude removed trees; historical reads remain; new diagnosis/care/recovery mutations are blocked. |
| Care recommendation live write | NOT TESTED | Requires isolated disposable fixture and explicit operator confirmation. |
| Export-release live write | NOT TESTED | Automated validation passed, but no live export dossier write was executed. |
| Notification runtime delivery | NOT TESTED | Source/tests verify receiver ownership and listener behavior; no live Kafka notification delivery was asserted with an authenticated user. |
| AI runtime safety | PASS | AI validation returned 15/15 PASS and model integrity matched. |
| Severity model | NOT AVAILABLE | Training gate remains `DO_NOT_TRAIN`; no severity model training or replacement occurred. |
| Physical ESP32-CAM capture | HARDWARE_NOT_TESTED | No real hardware snapshot/capture was performed. Camera heartbeat/registration tests are not image-capture evidence. |

Phase I did not produce `READY_FOR_THESIS_DEMO` because authenticated live write E2E and physical camera E2E remain unverified.
