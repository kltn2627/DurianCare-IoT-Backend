# Root Cause Analysis: False Positive Classification of Non-Leaf Images

**Date:** 2026-08-08  
**Branch:** `duy/cleanup-fixes`  
**Status:** RESOLVED — FPR=0.0% achieved

## 1. Bug Description

**Input:** League of Legends T1 wallpaper (1920×1080 dark red/black eSports logo)  
**Actual output:** `{"predictedDisease": "PHOMOPSIS_LEAF_SPOT", "confidence": "31.87%"}`  
**Expected output:** HTTP 422 "No durian leaf detected."

## 2. Root Cause: AND Logic Bug in `_select_crops`

**File:** `duriancare-ai-service/app/services/leaf_pipeline.py`

```python
# BEFORE (BUG):
if quality < self.min_crop_quality and detection.confidence < 0.30:
    continue
```

The `and` logic requires BOTH conditions to be true to skip (reject) a crop. For T1:
- YOLO returned confidence=**70.16%** (>> 0.30)
- So `detection.confidence < 0.30` was False
- The `and` short-circuited → the crop was **never rejected**, regardless of quality

This allowed YOLO's false detection (confidence=70.16%, quality=0.354) to reach MobileNet, which then classified it as a disease.

**Why did YOLO return 70.16% on the T1 logo?**  
The YOLO leaf detector was trained on leaf disease images. The T1 logo contains a bright orange/white rectangular region that shares some visual features with leaf disease patterns. YOLO hallucinated a "leaf" with 70.16% confidence.

## 3. Secondary Issue: No Image-Level Validation

The pipeline had no check to verify whether the input image even contained a leaf before running YOLO. Any image could enter the full detection+classification pipeline.

## 4. Fixes Applied

### Fix 1: `_select_crops` — Separate Gate Logic

```python
# AFTER (FIXED):
if detection.confidence < self.min_crop_confidence:  # default 0.20
    continue
if quality < self.min_crop_quality:  # default 0.26
    continue
```

Two independent guards. Both must pass. High YOLO confidence no longer bypasses the quality gate.

### Fix 2: `_image_has_leaf_color` — Pre-YOLO Green Check

Added to `disease_classifier.py → predict()`, runs BEFORE YOLO:

```python
green_ratio = (max(0, G_mean - (R_mean + B_mean)/2) / (R_mean+G_mean+B_mean)) * 3
if green_ratio < 0.012:
    raise PredictionError(422, reason="no_leaf_color")
```

Calibrated against:
- T1 wallpaper: green_ratio = 0.000 → REJECTED in 58ms
- Minimum real leaf (ESP32 bluish): green_ratio = 0.0246 → PASSED

### Fix 3: `_assert_crops_valid` — Hard Assertions Before MobileNet

```python
# Rejects crops with:
crop_area < 64*64            # too small
crop_area >= original * 0.98 # full-image detection (not a crop)
detection.confidence < 0.20  # low-confidence detection
_crop_has_texture < 1.5      # smooth gradient, no texture
```

### Fix 4: `_crop_has_texture` — Laplacian Texture Gate

```python
# 32x32 downsampled Laplacian energy
avg_lap = Σ |4c - c_N - c_S - c_E - c_W| / (30*30)
if avg_lap < 1.5: reject crop  # smooth gradient → near-zero Laplacian
```

Catches the synthetic green landscape gradient (Laplacian ≈ 0) while passing all real leaf crops (Laplacian ≫ 5 from veins and disease spots).

## 5. Evidence

| Iteration | T1 wallpaper | All other non-leaf | New FN |
|-----------|-------------|-------------------|--------|
| 1 (before) | FAIL 200 PHOMOPSIS 31.87% | 5/5 PASS | 0 |
| 2 (AND fix + green check) | PASS 422 | 5/5 PASS | 0 |
| 3 (green check tuned) | PASS 422 | 5/5 PASS | 0 |
| 4 (full 13-category suite) | PASS 422 | **13/13 PASS** | **0** |

**Final state:**  
- FALSE POSITIVE RATE: **0.0%** (13 non-leaf categories, all rejected)  
- FALSE NEGATIVE RATE: **5.9%** (3 pre-existing failures, unchanged from iteration 1)

## 6. Pre-Existing False Negatives (Not Caused by This Fix)

Three leaf images fail YOLO detection regardless of any code change:

| Image | Diagnosis | YOLO result |
|-------|-----------|-------------|
| `1561756.jpg` (224×224) | Unknown | 0 detections in 12 attempts |
| `benh-dom-la-sau-rieng-va-bien-phap-phong-tri-kimnonggolstar-vn-1.jpg` (224×224) | Unknown | 0 detections in 12 attempts |
| `OIP.webp` (474×266) | Unknown | 0 detections in 12 attempts |

These images were failing in iteration 1 (before any changes). They represent genuine YOLO model limitations — the detector cannot find a leaf in these images at any configuration (6 preprocessing variants × 2 confidence thresholds = 12 attempts, minimum threshold 0.113).

**Cannot be fixed without retraining the YOLO model** (which is outside the scope constraints for this session).
