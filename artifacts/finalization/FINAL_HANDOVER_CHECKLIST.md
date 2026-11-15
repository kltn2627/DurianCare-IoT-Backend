# DurianCare-IoT Final Handover Checklist

Date: 2026-10-11

## Complete

- [x] Backend compile and tests verified.
- [x] Web lint/typecheck/build/tests verified.
- [x] Mobile typecheck and export verified.
- [x] AI source compile verified.
- [x] AI frozen model hashes verified.
- [x] Severity validation and guard scripts verified.
- [x] Final reports added under `artifacts/finalization`.

## Still Needed Before Public Demo

- [ ] Start live services without resetting data.
- [ ] Run authenticated web E2E against live backend.
- [ ] Run mobile app against live backend.
- [ ] Confirm notification delivery through the running stack.
- [ ] Confirm camera/gateway route with real hardware or simulator.
- [ ] Resolve Expo doctor patch-version mismatches or document acceptance.
- [ ] Review and commit intended changes.

## Phase H Handover - 2026-10-14

- [x] Preserved all dirty worktrees; no commit/push/reset/branch switch.
- [x] Added backend soft tree-removal endpoint using existing `TreeStatus.REMOVED`.
- [x] Excluded removed trees from active Tree Map/list queries.
- [x] Added backend tests for authorized removal, unauthorized denial, active-list exclusion, idempotency, and history preservation.
- [x] Added Web and Mobile remove-tree actions with confirmation and refresh after backend success.
- [x] Reran backend full Maven reactor: PASS.
- [x] Reran Web typecheck/lint/Jest/build: PASS with 8 lint warnings.
- [x] Reran Mobile typecheck/Expo Doctor/export: PASS.
- [x] Reran AI unittest and integrity/safety validation: PASS.
- [x] Reran severity validation/guard/gate: validation/guards PASS, gate `DO_NOT_TRAIN`.
- [ ] Live write E2E approved and executed.
- [ ] Physical ESP32-CAM capture verified.
- [ ] Manual observation workflow completed.
- [ ] Full dirty diff reviewed by human before commit/merge.

Final handover verdict: `PARTIALLY_COMPLETE`

## Phase I Handover - 2026-10-14

- [x] Preserved dirty Backend/Web/Mobile worktrees; no commit/push/reset/branch switch.
- [x] Inspected current branch/status/diff scope across Backend, Web, and Mobile.
- [x] Ran GitNexus impact checks before backend symbol edits and reran `detect_changes` after fixes.
- [x] Identified concrete GitNexus CRITICAL drivers: tree disease/care lifecycle, cultivation/export release, notification ownership, gateway auth, and IoT/camera/device paths.
- [x] Fixed removed-tree mutation gap while preserving historical diagnosis/care-plan reads.
- [x] Added regression coverage for removed-tree health transition and removed-tree care workflow rejection.
- [x] Reviewed tree removal end to end: owner checks, `TreeStatus.REMOVED`, active map/list exclusion, active safety aggregates, historical preservation, Web/Mobile confirmation and refresh.
- [x] Reviewed care-plan/recovery gates, notification ownership, harvest/export validation, traceability exposure, gateway auth forwarding, and IoT device/camera auth.
- [x] Reran Backend focused and full Maven tests: PASS.
- [x] Reran Web typecheck/lint/Jest/build: PASS with 8 lint warnings.
- [x] Reran Mobile validation and export: PASS.
- [x] Reran AI compile/unittest/integrity/safety: PASS.
- [x] Reran severity validation, guard tests, and training gate: validation/guards PASS; gate `DO_NOT_TRAIN`.
- [x] Reran IoT syntax/tests: PASS.
- [x] Checked runtime service status: containers are up.
- [x] Attempted safe read-only harness: blocked by missing auth context, no secrets exposed.
- [ ] Authenticated live write E2E approved and executed against isolated disposable fixtures.
- [ ] Physical ESP32-CAM capture verified.
- [ ] Manual health observation workflow completed or explicitly scoped for thesis demo.
- [ ] Full accumulated dirty diff reviewed by human before commit/merge.

Final Phase I verdict: `PARTIALLY_COMPLETE`
