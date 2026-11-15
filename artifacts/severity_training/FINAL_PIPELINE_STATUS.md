# Final Pipeline Status

Final status: `BLOCKED BY HUMAN ANNOTATION`.

## 1. Dataset Size

- Staged severity dataset images: 5
- DLD annotation candidates: 6,002
- Raw external dataset folders: 4

## 2. Approved Annotation Count

0

## 3. Per-Class Count

No approved severity labels exist, so per-class approved counts are empty.

## 4. Duplicate Status

- Exact duplicate groups: 0
- Near duplicate groups: 59
- Duplicate group manifest exists at `DataSet/Durian_Severity_Training/reports/duplicate_groups.csv`

## 5. Split Status

No split exists. Split creation correctly refuses when approved annotations are absent.

## 6. Training Gate

`DO_NOT_TRAIN`

Reason codes:

- `PROTOCOL_NOT_FINALIZED`
- `APPROVED_COUNT_TOO_LOW`
- `CLASS_MINIMUM_TOO_LOW`

## 7. Model Trained?

No.

## 8. Model Path

No severity model path exists.

## 9. Model SHA256

No severity model SHA256 exists.

## 10. Test Metrics

No test metrics exist because no model and no approved test split exist.

## 11. Integration Status

Severity integration is inactive. Existing diagnosis behavior should continue returning `severity = null` or equivalent unavailable evidence until a validated model exists.

## 12. Longitudinal Progression Support

Research helper exists for label-level comparison, but production use remains blocked until approved severity evidence and finalized thresholds exist.

## 13. Remaining Blocker

Human expert annotation and protocol approval.

## 14. Exact Next Action

Run the annotation web UI, review DLD candidates, keep labels provisional until the protocol is finalized, then approve enough labels to satisfy the training gate.
