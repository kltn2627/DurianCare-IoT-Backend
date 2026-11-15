# Dataset Card: DurianCare Severity Training

## Purpose

Create an isolated dataset for disease burden / severity evidence and longitudinal progression research.

## Current Status

No approved severity training set exists yet.

The available local dataset is:

- Path: `C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DLD_Dataset`
- Source: Roboflow `DurianCare-Classification`
- License: CC BY 4.0
- Images: 6,002
- Annotation type: folder-level disease class
- Severity labels: none
- Lesion masks: none
- Lesion boxes: none

## Why It Cannot Train Severity Yet

Folder class labels identify disease category only. They do not identify lesion area, affected-area percentage, severity label, or clinical disease burden.

Classifier confidence must not be converted into severity.

## Required Annotation Schema

Use `DataSet/Durian_Severity_Training/metadata/annotations.csv` with:

- `image_id`
- `image_path`
- `disease`
- `annotation_type`
- `annotation_source`
- `annotator`
- `review_status`
- `severity_label`
- `severity_score`
- `affected_area_ratio`
- `source_dataset`
- `tree_group_id`
- `session_id`
- `notes`

Only records with `review_status=APPROVED` may be used for training.

## Leakage Rule

Images from the same `tree_group_id` or same `session_id` must remain in the same split.
