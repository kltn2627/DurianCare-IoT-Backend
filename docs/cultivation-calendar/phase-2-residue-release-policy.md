# Phase 2 residue and export release policy

This document describes the implemented application rules. It does not supply or certify regulatory MRL values. An administrator must enter and verify standards from an accountable source.

## Standard selection

- A lab result must be linked to a harvest batch. Its batch supplies the market; its cultivation season supplies the commodity/crop. The ingredient code is normalized to uppercase. A release target market must equal the batch's expected destination market.
- The applicable date for a lab result is the UTC calendar date of `LabSample.sampledAt`. Compliance checks Usage standard availability on the UTC harvest date. A season-only preview without a batch uses the current UTC date.
- Candidates must match market, commodity and ingredient code; be active and verified; have a nonnegative MRL and a unit; and satisfy `effectiveFrom <= applicable date <= effectiveTo` when `effectiveTo` is present. The candidate with the latest effective-from date wins; a tie on that date is treated as ambiguous and no standard is selected. Mongo `@Version` is only an optimistic-lock field.
- A missing candidate yields `NO_STANDARD_FOUND` for a new lab result and `NO_APPLICABLE_MRL` in batch compliance. No MRL is inferred from pesticide quantity.

## Measurement and evidence

- The client supplies observed value, unit, LOD and LOQ. The server ignores legacy request fields `resultStatus`, `applicableMrl`, `applicableMrlSource` and `verifiedBy` when computing and saving a result.
- Only exact compatible units are compared. Supported exact units are `mg/kg`, `ug/kg` (including micro-sign spellings), and `ppm`; no conversion between them is performed. Unsupported or mismatched units produce `PENDING_REVIEW`.
- A non-detect result without an LOD, or with an LOD above the MRL, cannot clear the result. A value below an LOQ above the MRL also remains `PENDING_REVIEW`.
- Results save the standard ID, MRL and unit, source/reference, effective dates, measured value/unit, computed status, actor, timestamp, and `SERVER_MRL_V1` evidence marker. Compliance recomputes the status against the currently applicable verified standard rather than trusting the stored status.
- Existing results without `SERVER_MRL_V1` are treated as unverified evidence and block a batch assessment. Existing samples without an expected panel remain pending. There is no automatic rewrite or guessed migration of historical values.

## Lab lifecycle and missing lab

- New samples begin `COLLECTED`. The send action moves `COLLECTED -> SENT`. Only a sent sample accepts results; `CANCELLED` cannot be sent or receive results.
- The expected panel is the union of explicitly supplied ingredient codes and codes in recorded Usage for the season at sample creation. One result per ingredient code is allowed in a sample. The sample becomes `RESULT_RECORDED` only after every expected code has a result. At assessment, current Usage codes must still be covered by results.
- If a batch has no sample, compliance emits `LAB_NOT_ASSESSED` as a warning and the traceability snapshot states `NOT_ASSESSED`. This application has no sourced market policy that requires every batch to have a lab test, so absence alone does not block export. The warning must never be presented as a lab PASS.
- A present but incomplete sample, unverified legacy evidence, failed result, missing applicable standard or incompatible unit blocks export.

## Export workflow and authorization

- Valid transitions are `DRAFT -> UNDER_REVIEW -> APPROVED -> RELEASED` and `RELEASED -> RECALLED`. `BLOCKED` has no ordinary submit transition; an explicit rebuild flow would be needed after remediation.
- Approval and final release each recompute compliance from current batch, Usage, samples, results and standards. Both reject when `eligibleForExportRelease` is false. Successful approval and release replace the snapshots with freshly assessed evidence. The final release snapshot includes raw lab records and separately resolved live lab evidence.
- The gateway supplies identity headers from JWT claims. Standard creation/import and audit-log listing require `ADMIN`. Lab-result entry and export approve/release require `EXPERT` or `ENGINEER` plus farm update permission. Sample entry/send, export create/submit/recall require farm update permission. Actor IDs written to audit/release/result records come from the authenticated identity, not request bodies.
- The compose deployment does not publish the cultivation service port on the host. Requests should enter through the authenticated gateway.
- Recall preserves review metadata and obtains `recalledAt` from the recall audit event. The traceability response includes current status and recall reason alongside the historical snapshot.

## Manual end-to-end check

1. As an administrator, create a sourced, verified, active standard for the batch market, season commodity and ingredient with dates covering the sample date. Use a test environment and a value supplied by the project's regulatory owner.
2. Complete a chemical activity with Usage and PHI, create a harvest batch whose farm/zone match the season, and create a batch-linked sample with an expected panel. Send the sample.
3. As an authorized expert or engineer, submit an observed value above the standard. Confirm the response is `FAIL` even when legacy client status/MRL fields claim `PASS` and a high MRL.
4. Confirm compliance blocks export, and a `BLOCKED` release cannot be submitted. Keep the failed evidence intact; this phase does not define an adjudication flow that overrides it.
5. On a separate eligible batch, create a release, submit it and approve it. Add a new failing result or Usage that changes PHI before final release; confirm the release action rejects and leaves the record short of `RELEASED`.
6. On a clean eligible batch, complete approval and release; inspect the refreshed assessment and resolved lab evidence in traceability. Recall the release and confirm the traceability response reports `RECALLED` and its reason.
