# Final Severity AI Report

Final status: `BLOCKED BY HUMAN ANNOTATION`.

## Summary

The severity AI pipeline has been advanced to the scientific boundary. Public datasets were researched and downloaded where legally available without bypassing access controls. A local annotation workflow now exists for the 6,002-image DLD dataset. Validation, duplicate detection, training gate, split guard, training guard, and inference placeholder all prevent fake severity modeling.

No severity model was trained because there are 0 `APPROVED` severity annotations.

## Datasets Researched

- Mobiusi Durian Plantation Disease Leaf Rot Detection Image Dataset.
- LeafNet75 Leaf Disease Segmentation Masks.
- Zenodo Plant Leaves Image Segmentation Dataset.
- Roboflow Durian Leaf Disease Semantic Seg.
- Kaggle Durian Leaf Disease Dataset.
- Roboflow Durian Diseases object-detection dataset.
- DiaMOS Plant severity dataset.

## Datasets Downloaded

- `dataset_001_mobiusi_durian_leaf_rot_hf`: 5 durian images with source JSON severity metadata.
- `dataset_002_leafnet_leaf_disease_segmentation_hf`: 588 image-mask rows in Parquet.
- `dataset_003_zenodo_plant_leaves_segmentation_overview`: overview CSV.
- `dataset_004_diamos_plant_severity_metadata`: dataset description PDF.

## Local Dataset State

- Staged severity images: 5.
- Annotation records: 5.
- Approved annotations: 0.
- DLD annotation candidates: 6,002.
- Exact duplicate groups in DLD candidates: 0.
- Near-duplicate groups in DLD candidates: 59.
- Validation errors: 0.

## Training Gate

Current decision: `DO_NOT_TRAIN`.

Blockers:

- PROTOCOL_NOT_FINALIZED
- APPROVED_COUNT_TOO_LOW: approved_count 0 < minimum 120
- CLASS_MINIMUM_TOO_LOW: severity_classes 0 < minimum 2

## Train / Val / Test

No split was created. `split_dataset.py` correctly refuses to create a fake split with 0 approved annotations.

## Model Architecture

No architecture was selected for training. The preferred first trainable path remains image-level severity classification only after expert-reviewed labels exist. If lesion masks become available, segmentation is scientifically stronger for affected-area ratio.

## Training Experiments

No experiments were run because the dataset failed the training gate.

## Evaluation

No model metrics exist. Evaluation is blocked until there is an untouched test set with approved ground truth.

## Model Export

No model was exported. Existing frozen files were not overwritten:

- `leaf_detector_best.pt`
- `mobilenetv2_classifier_high_acc.pth`

## Inference

`infer.py` returns `severity: null` until a validated severity artifact exists.

## Longitudinal Progression

No `IMPROVED`, `STABLE`, or `WORSENED` thresholds were implemented. Progression remains `UNCERTAIN` until thresholds are supported by expert tolerance, annotation variability, or validation error.

## Integration Status

AI service, backend, adaptive treatment, and notification integration are not activated for severity because no validated model exists. Future integration must be additive and return `severity = null` when unavailable.

## Exact Blocker

Human expert annotation is required. The remaining work is to use the local web UI to label images, finalize the expert severity protocol, and approve enough labels to satisfy the training gate.
