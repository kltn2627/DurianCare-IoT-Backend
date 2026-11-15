# Training Report

No severity/progression model was trained.

Reason: the current local data has no approved severity labels, lesion masks, lesion boxes, or affected-area ground truth.

Current isolated dataset state after collection:

- 5 staged Mobiusi durian leaf-rot images with source-provided severity metadata.
- 0 `APPROVED` severity annotations.
- 6,002 DLD classification images prepared as human annotation candidates.
- Training guard verified: no model is trained.

Training gate:

- Decision: `DO_NOT_TRAIN`
- Approved images: 0
- Minimum approved images before first baseline: 120
- Minimum severity classes: 2
- Minimum per severity class: 20
- Reason codes: `PROTOCOL_NOT_FINALIZED`, `APPROVED_COUNT_TOO_LOW`, `CLASS_MINIMUM_TOO_LOW`

Split status: no split created. `split_dataset.py` refuses with no approved annotations.

The guarded training entrypoint is:

```powershell
python scripts/severity_training/train.py --dataset-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training"
```

Expected current behavior:

```text
No APPROVED severity annotations found. Refusing to train. Create/review real annotations first; confidence is not severity.
```

## Planned Experiments After Annotation Approval

1. Baseline severity classification or affected-area regression, depending on annotation type.
2. Reasonable augmentation preserving lesion geometry and color realism.
3. Class-balancing strategy if severity labels are imbalanced.

No experiment should be selected from training accuracy alone. Selection must use validation/test metrics.
