# Detector Execution Trace

**Date:** 2026-08-08  
**Branch:** `duy/cleanup-fixes`  
**Service:** `duriancare-ai-service`  
**Runtime:** Docker container `duriancare-ai-service` (live code via volume mount)

## 1. Pipeline Architecture

```
POST /api/v1/predict
        │
        ▼
[Gate 0: image.open + decode]   ~1-5ms
        │
        ▼
[Gate 1: _image_has_leaf_color] ~1-5ms
        │ green_ratio < 0.012
        ├─── REJECT → HTTP 422 "No durian leaf detected." (reason=no_leaf_color)
        │
        │ green_ratio ≥ 0.012
        ▼
[Gate 2: YOLOv8 leaf detector]  ~100-2000ms (AdaptiveLeafPipeline)
        │ selected_crops empty
        ├─── REJECT → HTTP 422 "No durian leaf detected." (reason=detector_no_bbox)
        │
        │ selected_crops non-empty
        ▼
[Gate 3: _assert_crops_valid]   ~1-5ms
        │   - bbox != None, w>0, h>0
        │   - area ≥ 64×64 px
        │   - area < 98% of original image
        │   - detection.confidence ≥ 0.20
        │   - crop Laplacian energy ≥ 1.5
        │ all crops fail
        ├─── REJECT → HTTP 422 "No durian leaf detected." (reason=leaf_validation_failed)
        │
        │ ≥1 crop passes
        ▼
[Gate 4: MobileNetV2 classifier] ~100-800ms
        │
        ▼
HTTP 200 DiseasePrediction
```

## 2. AdaptiveLeafPipeline: Detection Variants

When `AI_DEBUG=true` (default), `run_detection_search` tries up to 12 variants:

| Step | Variant | Confidence threshold |
|------|---------|---------------------|
| 1 | clahe | 0.25 (base) |
| 2 | clahe | 0.195 (−22%) |
| 3 | contrast_stretch | 0.25 |
| 4 | contrast_stretch | 0.195 |
| 5 | gamma_0_85 / gamma_1_15 | 0.25 |
| 6 | gamma | 0.195 |
| 7 | original / brightness_norm | 0.25 |
| 8 | original / brightness_norm | 0.195 |
| 9 | denoise | 0.138 (rescue) |
| 10 | denoise | 0.113 (deep rescue) |
| 11 | brightness_norm / contrast_stretch | 0.138 |
| 12 | brightness_norm / contrast_stretch | 0.113 |

Pipeline stops at the first variant that produces `selected_crops`.

## 3. Execution Traces — Representative Cases

### 3.1 T1 LoL Wallpaper (False Positive — ELIMINATED)

```
[Gate 0] decode T1.png (1920×1080) — 5ms
[Gate 1] _image_has_leaf_color:
         R_mean=82.1, G_mean=68.9, B_mean=74.3
         green_excess = max(0, 68.9 - (82.1+74.3)/2) = max(0, 68.9-78.2) = 0.0
         green_ratio  = 0.000
         0.000 < 0.012 → FAIL
→ HTTP 422 "No durian leaf detected." [elapsed: 57ms]
[Gate 2-4 never reached]
```

### 3.2 Green Landscape Gradient (False Positive — ELIMINATED)

```
[Gate 0] decode landscape_green.jpg (640×480) — 3ms
[Gate 1] _image_has_leaf_color:
         RGB_mean ≈ (80, 140, 130) blended sky+ground
         green_excess > 0 → green_ratio ≈ 0.18
         0.18 ≥ 0.012 → PASS
[Gate 2] YOLOv8 (clahe, conf=0.25): 1 detection
         conf=0.290, quality=0.372, area_ratio=0.876, class=No Disease
         _select_crops: conf(0.290)≥0.20 ✓, quality(0.372)≥0.26 ✓
         selected_crops=[CropCandidate(bbox=(0,14,640,466))]  [1380ms]
[Gate 3] _assert_crops_valid:
         crop size: 640×452 = 289280 px ✓ (≥4096)
         area ratio: 289280/(640×480) = 0.941 < 0.98 ✓
         detection.confidence: 0.290 ≥ 0.20 ✓
         _crop_has_texture(32×32 Laplacian):
           gradient → Laplacian ≈ 0.4 < 1.5 → FAIL
→ HTTP 422 "No durian leaf detected." [elapsed: 1371ms]
[Gate 4 never reached]
```

### 3.3 Valid Leaf (sau-rieng-bi-dom-la.jpg) — Correct Detection

```
[Gate 0] decode sau-rieng-bi-dom-la.jpg (700×466) — 3ms
[Gate 1] _image_has_leaf_color:
         Green leaf dominates → green_ratio ≈ 0.35
         0.35 ≥ 0.012 → PASS
[Gate 2] YOLOv8 (clahe, conf=0.25):
         detection conf=0.271, quality=0.755, bbox=(173,68,307,428) [700ms]
         _select_crops: conf(0.271)≥0.20 ✓, quality(0.755)≥0.26 ✓
[Gate 3] _assert_crops_valid:
         size: 134×360 = 48240 px ✓ (≥4096)
         area: 48240/326200 = 0.148 < 0.98 ✓
         conf: 0.271 ≥ 0.20 ✓
         Laplacian: leaf texture → ≫ 5 ≥ 1.5 ✓
[Gate 4] MobileNetV2: PHOMOPSIS_LEAF_SPOT conf=74.43%
→ HTTP 200 [elapsed: 1708ms]
```

### 3.4 Pre-existing False Negative (1561756.jpg)

```
[Gate 0] decode 1561756.jpg (224×224) — 2ms
[Gate 1] _image_has_leaf_color: green_ratio ≈ 0.25 → PASS
[Gate 2] YOLOv8 — 12 attempts:
         clahe(0.25): 0 detections
         clahe(0.195): 0 detections
         contrast_stretch(0.25): 0 detections
         contrast_stretch(0.195): 0 detections
         gamma_0_85(0.25): 0 detections
         gamma_0_85(0.195): 0 detections
         original(0.25): 0 detections
         original(0.195): 0 detections
         denoise(0.138): 0 detections
         denoise(0.113): 0 detections
         brightness_norm(0.138): 0 detections
         brightness_norm(0.113): 0 detections
         selected_crops=[]  [10142ms]
→ HTTP 422 "No durian leaf detected." (reason=detector_no_bbox)
[Root cause: YOLO model cannot detect leaf in this image at any configuration]
[Not caused by false-positive prevention code — identical result in iteration 1 baseline]
```

## 4. Timing Summary (Full Test Run)

| Phase | Images | Avg time | Notes |
|-------|--------|----------|-------|
| Green ratio gate (reject) | 12 non-leaf | 10ms | No YOLO |
| Green ratio gate (pass) | 39 leaf | 5ms | YOLO follows |
| YOLO fast path (1 variant) | ~30 images | 650ms | Leaf detected |
| YOLO full search (12 variants) | ~24 images | 9900ms | Multi-attempt |
| Texture gate (reject) | 1 landscape | 1371ms | YOLO ran, texture fails |
| MobileNet classification | 48 images | 350ms | Per-image |

## 5. Debug Artifacts

Saved to container path `/workspace/duriancare-ai-service/artifacts/debug_runs/` when `AI_DEBUG=true`.

Each run contains: `yolo_result.json`, `classifier_result.json`, `final_decision.json`, `pipeline_log.txt`, `original_image.jpg`, `detector_heatmap.jpg`, `crops/`.

Key fields in `yolo_result.json`:
```json
{
  "model": "/workspace/…/leaf_detector_best.pt",
  "attempts": [
    {
      "variant": "clahe",
      "confidenceThreshold": 0.25,
      "imageWidth": 700, "imageHeight": 466,
      "processingTimeMs": 272.145,
      "detections": [
        {
          "confidence": 0.271, "crop_quality": 0.755,
          "area_ratio": 0.148, "class_name": "Leaf Spot",
          "left": 173, "top": 68, "right": 307, "bottom": 428
        }
      ]
    }
  ]
}
```
