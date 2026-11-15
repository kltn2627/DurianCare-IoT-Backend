# DurianCare-IoT Final Diff Audit

Date: 2026-10-11

## Backend Working Tree

The backend tree contains prior disease lifecycle, AI recommendation, notification, gateway, Docker, documentation, and severity pipeline changes. These were not committed in this session.

GitNexus `detect_changes(scope=all)` result:

- Changed symbols: 43
- Affected symbols/process nodes: 7
- Changed files detected by GitNexus: 18
- Risk level: HIGH
- Affected processes: `CreateCarePlan -> FarmNotFoundException`, `CreateCarePlan -> FarmAccessDeniedException`, `CreateCarePlan -> Publish`, `CreateCarePlan -> Status`, `UpdateCarePlanStatus -> FarmNotFoundException`, `UpdateCarePlanStatus -> FarmAccessDeniedException`, `UpdateCarePlanStatus -> Status`

The high risk is expected because care-plan controller/service/notification paths are core disease lifecycle flows. Full backend Maven tests passed after these changes.

New finalization output:

- `artifacts/finalization/INITIAL_AUDIT.md`
- `artifacts/finalization/FINAL_TEST_REPORT.md`
- `artifacts/finalization/FINAL_PROJECT_STATUS.md`
- `artifacts/finalization/FINAL_E2E_REPORT.md`
- `artifacts/finalization/FINAL_SECURITY_AUDIT.md`
- `artifacts/finalization/FINAL_AI_REPORT.md`
- `artifacts/finalization/FINAL_DEMO_GUIDE.md`
- `artifacts/finalization/FINAL_KNOWN_LIMITATIONS.md`
- `artifacts/finalization/FINAL_HANDOVER_CHECKLIST.md`
- `artifacts/finalization/FINAL_THESIS_CLAIMS.md`

## Web Working Tree

Final fixes made during this pass:

- Escaped user-facing quote characters in JSX text for camera and export pages.
- Deferred tree-detail effect state updates to satisfy React lint rules.
- Stabilized timestamp use in zone tree workspace to avoid impure render lint errors.

Existing web tree disease and notification changes remain present.

## Mobile Working Tree

Existing mobile tree and notification route changes remain present. No mobile source edits were made during the final pass.

## Generated Files

Build/export outputs were produced by verification commands. No generated output was intentionally added as source evidence.
