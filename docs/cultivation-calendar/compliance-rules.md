# Compliance Rules

Compliance is evaluated in `ComplianceEngine` and orchestration logic in `CultivationCalendarService`.

## Chemical Treatment

When creating `CHEMICAL_TREATMENT`, at least one agricultural input is required. Inputs marked `PROHIBITED` are rejected.
Inputs with `CHEMICAL` or `RESTRICTED_CHEMICAL` biological level require:

- target pest or disease;
- recorded biological/IPM reason;
- approval user;
- default `PENDING_APPROVAL` activity status.

## Safe Harvest Date

Each input usage calculates `completedDate + preHarvestIntervalDays`.
The season safe harvest date is the latest calculated safe date across all actual input usages.

## Risk Assessment

Blocking reasons include prohibited input use, market restriction, PHI violation, missing active ingredient, lab failure,
pending lab result and missing MRL. Warnings include missing batch number and other advisory conditions.

## Lab Result Comparison

The lab comparator returns:

- `NOT_DETECTED` when no value is reported or the value is below detection limit;
- `BELOW_LIMIT_OF_QUANTIFICATION` when detected but below quantification limit;
- `NO_STANDARD_FOUND` when no MRL is available;
- `PASS` when value is within MRL;
- `FAIL` when value exceeds MRL.

## Export Eligibility

Export release is blocked when compliance has blocking reasons, PHI is not met, inputs are prohibited, target-market
restrictions are violated, or required lab results fail/pending.

## Audit

The module records audit logs for plan/activity changes, chemical approval/rejection, activity completion, MRL creation,
lab result recording, safe-harvest override, export approval, release and recall.
