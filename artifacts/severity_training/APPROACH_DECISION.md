# Severity Modeling Approach Decision

## Compared Options

Option A: lesion segmentation

- Output: pixel-level lesion mask.
- Best metric for affected-area ratio.
- Requires mask annotations.
- Current local data does not provide masks.

Option B: lesion detection

- Output: lesion bounding boxes.
- Useful for lesion count and approximate affected area.
- Requires lesion boxes.
- Current local data does not provide lesion boxes.

Option C: image-level severity classification

- Output: NONE / MILD / MODERATE / SEVERE.
- Simpler annotation workflow.
- Requires expert/manual severity labels.
- Current local data does not provide severity labels.

Option D: regression

- Output: continuous severity or affected-area score.
- Useful for longitudinal delta.
- Requires numeric labels or validated affected-area annotations.
- Current local data does not provide numeric labels.

## Selected Current Approach

No model is selected for training yet. The available durian severity seed data is 5 images, so it is suitable only for pipeline validation and expert-review protocol testing.

## Recommended First Trainable Approach

Start with Option C if expert/manual labels can be approved faster than masks. Use the 6,002 DLD images as annotation candidates only; do not infer severity from disease class folders.

If the project can obtain lesion masks, prefer Option A because it supports affected-area ratio and clearer longitudinal comparison.

## Input

Durian leaf image after existing leaf-quality gates.

## Output

Pending annotation type:

- classification: severity label
- segmentation/regression: affected-area evidence plus optional label bucket

## Metric

Pending annotation type. See `EVALUATION_REPORT.md`.

## Limitations

The current classification dataset cannot support severity/progression training.
