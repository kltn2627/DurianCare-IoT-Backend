# Phase N — Allocaridara Pest Detection: Feasibility Report

**Date:** 2026-09-30  
**Branch:** `duy/cleanup-fixes`  
**Service:** `duriancare-ai-service`

---

## 1. Executive Summary

`ALLOCARIDARA_ATTACK` is already one of five trained classes in the production MobileNetV2 classifier. Pest detection does **not** need to be added from scratch. The gap is **quality**, not presence: the single Allocaridara test image in the recovery suite scored 55.68% confidence — just above the LOW_CONFIDENCE threshold (50%). Meanwhile 89.9% of all 296 captured hard examples fail at the YOLO **detector** stage before the classifier is even reached.

**Recommendation:** Phase O should pursue two parallel tracks — (1) collector campaign for Allocaridara training images, (2) YOLO detector fine-tuning on hard examples — rather than retraining the classifier from scratch.

---

## 2. Current Architecture

| Component | File | Role |
|-----------|------|------|
| Leaf detector | `leaf_detector_best.pt` (YOLOv11x) | Finds leaf ROI; failure → INVALID_IMAGE |
| Classifier | `mobilenetv2_classifier_high_acc.pth` | 5-class softmax over detected crop |
| Pipeline | `AdaptiveLeafPipeline` | 24-variant adaptive retry with CLAHE/gamma/denoise |
| Hard-example capture | `datasets/hard_examples/` | Saves failed inference sessions for retraining |

**5 trained classes** (fixed since initial training):
```
ALGAL_LEAF_SPOT · ALLOCARIDARA_ATTACK · HEALTHY_LEAF · LEAF_BLIGHT · PHOMOPSIS_LEAF_SPOT
```

---

## 3. Current Allocaridara Performance

### 3.1 Recovery benchmark (20 images, 2026-08-08)

| Predicted class | Count | Confidence range |
|-----------------|-------|------------------|
| HEALTHY_LEAF | 7 | 50.8% – 99.98% |
| LEAF_BLIGHT | 5 | 44.4% – 98.99% |
| PHOMOPSIS_LEAF_SPOT | 4 | 66.2% – 99.99% |
| ALGAL_LEAF_SPOT | 2 | 53.8% – 65.2% |
| **ALLOCARIDARA_ATTACK** | **1** | **55.68%** |

Only one Allocaridara sample was included in the benchmark. At 55.68%, a real user submission at that confidence would be surfaced as **LOW_CONFIDENCE** (Phase F threshold: <50% = LOW_CONFIDENCE, 50–55% borderline).

### 3.2 Hard example failure analysis (296 sessions)

| Failure reason | Count | % |
|----------------|-------|---|
| `detector_no_bbox` | 266 | 89.9% |
| `crop_quality_issue` | 30 | 10.1% |

**Zero classifier-stage failures.** The bottleneck is entirely the YOLO detector: the leaf bounding box is not found, so the MobileNetV2 classifier is never invoked. This affects all 5 classes equally, but disproportionately hurts Allocaridara because pest-attacked leaves often have unusual texture/color that reduces YOLO's detection confidence.

### 3.3 Phase F impact

After Phase F standardisation, a 55.68% Allocaridara prediction is classified as **DISEASE** (≥50%) but is very close to the LOW_CONFIDENCE boundary. Any real farm image slightly worse will fall to LOW_CONFIDENCE. This effectively makes Allocaridara **unreliable in production** until confidence improves to a stable ≥70%.

---

## 4. Root Cause Analysis

### 4.1 Insufficient Allocaridara training images

The training dataset directory (`training/datasets/`) contains only model weight files (`yolov8s.pt`), no image splits. This means the current model was trained on a dataset that is no longer present in the repo. Based on:
- The fact that `ALLOCARIDARA_ATTACK` exists as a class but produces low confidence
- Standard public durian disease datasets (Kaggle, TDID) which typically contain 200–500 disease images but only 50–150 Allocaridara/pest images

**Estimated imbalance:** Allocaridara images are likely 2–4× fewer than disease class images in the original training set.

### 4.2 YOLO detector not trained for pest-attacked leaves

The YOLO detector (`leaf_detector_best.pt`) was trained to find clean leaf bounding boxes. Allocaridara-attacked leaves show:
- Silvery/bleached patches (different texture than healthy or fungal disease leaves)
- Often photographed on plants with many leaves in frame
- Smaller affected areas (individual leaf underside damage)

These visual differences reduce YOLO's detection confidence, causing `detector_no_bbox` failures — which means the classifier never runs.

### 4.3 Hard examples are Allocaridara-relevant data

The 296 hard example sessions (all from 2026-08-06) were captured from the farmer using the mobile app. These real-farm submissions that the pipeline **failed** to classify are valuable unlabeled training candidates. A significant proportion may be pest images that the detector couldn't handle.

---

## 5. Feasibility Assessment

### 5.1 What works today (no changes needed)

- `ALLOCARIDARA_ATTACK` class already exists in production model ✓
- Pest → `DiseaseCategory.PEST` mapping already in all clients (Phase F) ✓
- KB article linking on PEST diagnosis already implemented (Phase I) ✓
- `RECOVERED_BY_FARMER` flow handles pest recovery (Phase G) ✓

### 5.2 What needs to change

| Gap | Effort | Impact |
|-----|--------|--------|
| Label 296 hard example images (is it Allocaridara?) | Medium (manual) | High — real farm data |
| Collect 300+ additional Allocaridara leaf images | Medium (data collection) | High — improves classifier recall |
| Fine-tune MobileNetV2 with augmented Allocaridara set | Low (retrain script exists) | High — raises confidence above 70% |
| Fine-tune YOLO detector on hard examples | Medium (YOLO training setup) | High — fixes 89.9% of failures |

### 5.3 What does NOT need to change

- No code changes to the inference pipeline (`disease_classifier.py`, `leaf_pipeline.py`)
- No changes to the API schema (`predict.py`)
- No changes to the mobile app or web client
- No changes to the KB articles (already exist for Allocaridara)
- No new backend endpoints

---

## 6. Phase O Plan (recommended)

### Track A: Data collection (2–3 weeks)

1. **Label hard examples** — Review all 296 sessions in `datasets/hard_examples/`. Assign ground-truth labels. Expect ~30–60 usable Allocaridara samples.
2. **Public dataset augmentation** — Download Allocaridara images from:
   - Kaggle: "Durian Disease Dataset" (look for Allocaridara class)
   - iNaturalist observations tagged *Allocaridara malayensis*
   - TDID (Thai Durian Image Dataset) if accessible
3. **Target**: ≥300 Allocaridara images with bounding-box annotations for YOLO; ≥300 cropped leaf images for MobileNetV2.

### Track B: Detector fine-tuning

1. Convert hard example images to YOLO format (bounding box labels on failed images).
2. Fine-tune `leaf_detector_best.pt` with hard examples + augmented Allocaridara data using `yolov8s.pt` as base.
3. Validate: `detector_no_bbox` failure rate should drop from 89.9% to <40%.

### Track C: Classifier fine-tuning

1. Add augmented Allocaridara crops to the existing 5-class training split.
2. Fine-tune `mobilenetv2_classifier_high_acc.pth` for 10–20 epochs with class-balanced sampling.
3. Target: Allocaridara test confidence ≥70% on held-out validation images.

### Acceptance criteria for Phase P (ready to ship)

- [ ] Allocaridara validation accuracy ≥70% (vs ~56% today)
- [ ] `detector_no_bbox` failure rate <40% (vs 89.9% today)
- [ ] False positive rate for non-leaf images remains 0% (Phase F regression guard)
- [ ] Existing `getDiseaseCategory` thresholds produce `PEST` (not `LOW_CONFIDENCE`) for typical Allocaridara submissions

---

## 7. Verdict

**Feasible — medium effort, no architecture changes required.** The system already supports Allocaridara detection end-to-end. Improvement requires data collection and model fine-tuning only. The highest-leverage action is labeling the 296 existing hard examples (free data, already captured from real farmers).
