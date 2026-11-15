# DurianCare-IoT Final Known Limitations

Date: 2026-10-11

| Area | Limitation | Impact |
| --- | --- | --- |
| Runtime E2E | Services were not running in Docker Compose during final verification. | Live API/browser/mobile E2E must be rerun before demo. |
| Mobile | Expo doctor reports SDK patch-version mismatches. | Non-blocking for typecheck/export, but should be normalized before release. |
| Severity AI | Human annotation count is 0. | No severity model training or accuracy claim is allowed. |
| Hardware | Camera and IoT gateway routes need physical or simulator validation. | Hardware demo requires a prepared test rig. |
| Web lint | 10 warnings remain. | Build is not blocked, but cleanup is recommended. |
| Git state | Working trees are dirty. | Review diffs before committing. |

## Phase H Known Limitations - 2026-10-14

| Area | Limitation | Required next action |
| --- | --- | --- |
| Live write E2E | No write-enabled authenticated workflow was run in Phase H. | Use an isolated disposable farm/tree and explicit operator confirmation before enabling writes. |
| Manual observations | Manual health observation history remains incomplete as a first-class workflow distinct from AI diagnoses. | Add a dedicated manual observation model/API/UI or formally document the narrower status-transition behavior. |
| IoT provenance | Synthetic-vs-real telemetry provenance was not fully implemented or runtime-verified in Phase H. | Audit IoT service and dashboard with source/isSynthetic/stale semantics. |
| Camera hardware | ESP32-CAM physical capture was not tested. | Test with real hardware and preserve tree context through capture/diagnosis. |
| Severity AI | 0 approved severity annotations; gate returns `DO_NOT_TRAIN`. | Finalize expert protocol and collect >=120 approved annotations with at least two severity classes. |
| Git risk | GitNexus reports CRITICAL over the entire accumulated dirty backend tree. | Human review the full dirty diff before commit/merge. |
| Web lint | 8 warnings remain. | Clean unused imports/expressions and image warnings when time permits. |

## Phase I Known Limitations - 2026-10-14

| Area | Limitation | Current evidence | Required next action |
| --- | --- | --- | --- |
| Live write E2E | No authenticated live write workflow has been run. | Harness returned `BLOCKED_AUTH_CONTEXT`; write flag remained disabled. | Provide safe auth context plus isolated disposable fixture and operator confirmation before writes. |
| Physical camera | No real ESP32-CAM capture was tested. | IoT/camera automated tests pass, but no hardware image was captured. | Test a physical ESP32-CAM snapshot and capture-to-AI path. |
| Manual observations | Manual health observations are still not a complete first-class workflow. | Manual health transitions exist and now block removed trees, but no observation history model/API/UI exists. | Either implement a distinct manual observation workflow or document the current narrower behavior for thesis demo. |
| GitNexus risk | Backend accumulated diff remains `critical`. | `detect_changes`: 120 changed symbols, 50 affected symbols, 25 changed files. | Human review full dirty diff before commit/merge; do not treat green tests as a risk waiver. |
| Severity AI | Training remains blocked. | Gate status `DO_NOT_TRAIN`, 0 approved annotations. | Finalize expert protocol and collect enough approved data before training. |
| Runtime auth | Read-only runtime API cannot run without a safe auth context. | No E2E auth env vars were present. | Configure `DURIANCARE_E2E_ACCESS_TOKEN` or local email/password env vars outside chat. |
| Generated artifacts | Mobile export added `dist-phase-i/`; AI validation added hard-example folders and severity Phase I JSON outputs. | Worktree dirty status shows these untracked/generated artifacts. | Decide which generated artifacts to keep before commit. |
