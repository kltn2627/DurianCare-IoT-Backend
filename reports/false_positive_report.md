# False Positive Elimination Report

**Date:** 2026-08-08  
**Branch:** `duy/cleanup-fixes`  
**Service:** `duriancare-ai-service`  
**Runtime test:** `2026-08-08T09:18:06Z`

## 1. Result Summary

| Metric | Result | Target |
|--------|--------|--------|
| Non-leaf images tested | 13 | — |
| False positives | 0 | 0 |
| False positive rate | **0.0%** | **0.0%** |
| Leaf images tested | 51 | — |
| False negatives (new) | 0 | 0 |
| False negatives (pre-existing) | 3 | 0 |
| False negative rate | 5.9% | 0.0% |

**FPR=0.0% achieved.** The 3 false negatives existed before any code changes in this session (confirmed in baseline iteration 1). They are genuine YOLO detector limitations.

## 2. Original Bug: Why T1 LoL Wallpaper Was Classified as Disease

### 2.1 Bug Chain

1. **Entry:** `POST /api/v1/predict` with `T1.png` (1920×1080 dark red/black eSports logo)
2. **Pipeline bug (AND logic in `_select_crops`):**
   ```python
   # BEFORE (BUG): required BOTH to be true to skip
   if quality < self.min_crop_quality and detection.confidence < 0.30:
       continue
   ```
   With YOLO confidence=70.16% (> 0.30), the `and` short-circuits — neither gate fired.
3. **YOLO hallucination:** The YOLO leaf detector returned confidence=70.16% on a bright region of the T1 logo (crop_quality=0.354, area=80.6% of image).
4. **MobileNet classification:** The cropped region was sent to MobileNet, which classified it as PHOMOPSIS_LEAF_SPOT at 31.87% confidence.
5. **Output:** `{"predictedDisease": "PHOMOPSIS_LEAF_SPOT", "confidence": "31.87%"}` — a complete false positive.

### 2.2 Root Cause

The `and` logic in `_select_crops` allowed high-confidence YOLO detections to bypass the quality gate. Since YOLO was trained on leaf images, it can hallucinate "leaf" shapes in any image with the right color/texture — and it returned 70.16% confidence for the T1 logo's bright region.

## 3. Defense Layers Added

Three layers of defense were added to the pipeline. Each layer catches a different category of false positive:

### Layer 1: Image-Level Green Ratio Pre-Check

**Location:** `disease_classifier.py → predict() → _image_has_leaf_color()`  
**Triggers:** Before YOLO runs  
**Threshold:** `AI_MIN_IMAGE_GREEN_RATIO=0.012`

```
green_excess = max(0, green_mean - (red_mean + blue_mean) / 2)
denominator  = red_mean + green_mean + blue_mean
green_ratio  = (green_excess / denominator) * 3.0
```

**Catches:** Any image with no significant green content. Response in ~8-60ms (no YOLO).

| Image | green_ratio | Result |
|-------|------------|--------|
| T1 LoL wallpaper (dark red/black) | 0.000 | REJECTED (57.8ms) |
| blank white | 0.000 | REJECTED (7.5ms) |
| pure black | 0.000 | REJECTED (8.6ms) |
| solid red | 0.000 | REJECTED (8.2ms) |
| solid blue | 0.000 | REJECTED (8.7ms) |
| red-blue gradient | 0.000 | REJECTED (9.1ms) |
| human skin tone | 0.000 | REJECTED (9.0ms) |
| grey cat fur | 0.000 | REJECTED (7.8ms) |
| brown dog fur | 0.000 | REJECTED (22.4ms) |
| silver car | 0.000 | REJECTED (19.5ms) |
| dark laptop | 0.000 | REJECTED (8.4ms) |
| dark keyboard | 0.000 | REJECTED (21.9ms) |
| minimum real leaf (ESP32 bluish) | 0.0246 | PASSED |

### Layer 2: AND Bug Fix + Confidence Gate

**Location:** `leaf_pipeline.py → _select_crops()`  
**Change:** Replaced `if quality < Q and conf < 0.30` with two independent gates

```python
# FIXED: separate gates, both must pass
if detection.confidence < self.min_crop_confidence:  # default 0.20
    continue
if quality < self.min_crop_quality:  # default 0.26
    continue
```

**Effect:** A high-confidence detection (like T1's 70.16%) no longer bypasses the quality gate.

### Layer 3: Crop Assertion + Laplacian Texture Check

**Location:** `disease_classifier.py → _assert_crops_valid()` + `_crop_has_texture()`  
**Triggers:** After YOLO, before MobileNet  
**Thresholds:** `AI_MIN_CLASSIFIER_CONFIDENCE=0.20`, `AI_MIN_CROP_TEXTURE_ENERGY=1.5`

The texture check computes the average discrete Laplacian of the 32×32 downsampled crop:
```
lap(x,y) = |4·c(x,y) − c(x−1,y) − c(x+1,y) − c(x,y−1) − c(x,y+1)|
avg_lap  = Σ lap(x,y) / count
```

A linear gradient has Laplacian=0 (second derivative of a ramp = 0). Real leaves have texture from veins and spots.

**Catches:** The synthetic green landscape gradient that passes the image-level green check.

| Crop | avg_lap | Result |
|------|---------|--------|
| Green gradient (landscape) | ≈ 0 | REJECTED — texture_energy < 1.5 |
| Healthy durian leaf | ≫ 5 | PASSED |
| Diseased/necrotic leaf | ≫ 5 | PASSED (veins/lesions add texture) |

## 4. Non-Leaf Images: Test Results

All 13 non-leaf categories tested on 2026-08-08:

| # | Category | Gate triggered | Time | HTTP |
|---|----------|---------------|------|------|
| 1 | T1 LoL wallpaper | Layer 1 (green_ratio=0.000) | 58ms | 422 |
| 2 | Blank white image | Layer 1 (green_ratio=0.000) | 8ms | 422 |
| 3 | Pure black image | Layer 1 (green_ratio=0.000) | 9ms | 422 |
| 4 | Solid red | Layer 1 (green_ratio=0.000) | 8ms | 422 |
| 5 | Solid blue | Layer 1 (green_ratio=0.000) | 9ms | 422 |
| 6 | Red-blue gradient | Layer 1 (green_ratio=0.000) | 9ms | 422 |
| 7 | Human face (skin tone) | Layer 1 (green_ratio=0.000) | 9ms | 422 |
| 8 | Cat (grey fur) | Layer 1 (green_ratio=0.000) | 8ms | 422 |
| 9 | Dog (brown fur) | Layer 1 (green_ratio=0.000) | 22ms | 422 |
| 10 | Car (silver) | Layer 1 (green_ratio=0.000) | 20ms | 422 |
| 11 | Laptop (dark) | Layer 1 (green_ratio=0.000) | 8ms | 422 |
| 12 | Keyboard (dark) | Layer 1 (green_ratio=0.000) | 22ms | 422 |
| 13 | Green landscape | Layer 3 (texture<1.5) | 1371ms | 422 |

## 5. Pre-Existing False Negatives (3 images)

These failures were present in iteration 1 (before any code changes) and remain unchanged.

| Image | Size | YOLO outcome | Root cause |
|-------|------|-------------|-----------|
| `1561756.jpg` | 224×224 | 0 detections in 12 attempts | YOLO cannot find leaf at any threshold/variant |
| `benh-dom-la-sau-rieng-va-bien-phap-phong-tri-kimnonggolstar-vn-1.jpg` | 224×224 | 0 detections in 12 attempts | YOLO cannot find leaf at any threshold/variant |
| `OIP.webp` | 474×266 | 0 detections in 12 attempts | YOLO cannot find leaf at any threshold/variant |

**All 12 attempts** used: clahe, contrast_stretch, gamma, denoise, brightness_norm preprocessing variants at confidence thresholds 0.25→0.113. Every attempt returned 0 bounding boxes.

**My code changes introduced zero new false negatives.** All 48 leaf images that passed before my changes still pass with identical predictions.

## 6. Constraint Compliance

| Constraint | Status |
|-----------|--------|
| DO NOT retrain any model | COMPLIED — no model retraining |
| DO NOT modify MobileNet weights | COMPLIED |
| DO NOT modify detector weights | COMPLIED |
| DO NOT change API contracts | COMPLIED — endpoint signatures unchanged |
| DO NOT change frontend | COMPLIED |
| DO NOT fake runtime evidence | COMPLIED — all data from live container |
