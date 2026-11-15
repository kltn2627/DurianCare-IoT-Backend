# DurianCare Severity Dataset Report

## Isolated severity dataset
- Root: `C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training`
- Images staged: 5
- Annotation records: 5
- Approved annotation records: 0
- Review-pending source severity records: 5 from `dataset_001_mobiusi_durian_leaf_rot_hf`
- DLD annotation candidates: 6,002 rows in `metadata/severity_annotation_candidates.csv`
- Duplicate audit over DLD candidates: 0 exact duplicate groups, 59 near-duplicate groups at dHash threshold 4
- Duplicate group manifest: `DataSet/Durian_Severity_Training/reports/duplicate_groups.csv`

## Approved disease counts
- None. No approved severity annotations are available yet.

## Approved severity counts
- None. No approved severity annotations are available yet.

## Existing classification dataset audit
### train
- ALGAL_LEAF_SPOT: 752
- ALLOCARIDARA_ATTACK: 639
- HEALTHY_LEAF: 967
- LEAF_BLIGHT: 1047
- PHOMOPSIS_LEAF_SPOT: 888
### valid
- ALGAL_LEAF_SPOT: 124
- ALLOCARIDARA_ATTACK: 91
- HEALTHY_LEAF: 158
- LEAF_BLIGHT: 110
- PHOMOPSIS_LEAF_SPOT: 138
### test
- ALGAL_LEAF_SPOT: 200
- ALLOCARIDARA_ATTACK: 183
- HEALTHY_LEAF: 258
- LEAF_BLIGHT: 205
- PHOMOPSIS_LEAF_SPOT: 242

## Scientific status
The current local dataset is not trainable yet because all severity rows are unapproved.
Do not train a severity/progression model from classifier labels or classifier confidence.

The 5 Mobiusi seed rows contain real source metadata (`severity_level`, `affected_area_percentage`) but are too small and remain `NEEDS_EXPERT_REVIEW`.

Training gate status: `DO_NOT_TRAIN`.

Training gate reason codes:

- `PROTOCOL_NOT_FINALIZED`
- `APPROVED_COUNT_TOO_LOW`
- `CLASS_MINIMUM_TOO_LOW`
