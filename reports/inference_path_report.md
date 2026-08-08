# Inference Path Report

**Date:** 2026-08-08  
**Branch:** `duy/cleanup-fixes`  
**Purpose:** Full analysis of every code path taken during inference, from API endpoint to HTTP response.

## 1. Code Path: Leaf Image (Happy Path)

```
app/api/predict.py → POST /api/v1/predict
  ↓ validate Content-Type, read image bytes
  ↓ PIL.Image.open(BytesIO(image_bytes))
  ↓ classifier.predict(image)
    disease_classifier.py → DoubleModelDiseaseClassifier.predict()
      ↓ _image_has_leaf_color(image)          ← Layer 1
          if green_ratio ≥ 0.012 → PASS
      ↓ _run_detection_pipeline(image)
          if AI_DEBUG=true → pipeline.run_detection_search()
          else             → _run_single_detector_pass()
          [AdaptiveLeafPipeline tries up to 12 variants]
          if selected_crops non-empty → return DetectionRun
      ↓ _assert_crops_valid(selected_crops, image)   ← Layer 3
          for each crop:
            check w>0, h>0
            check crop_area ≥ 4096 px
            check crop_area < image_area * 0.98
            check detection.confidence ≥ 0.20
            check _crop_has_texture(crop) ≥ 1.5     ← Texture gate
          return valid_crops
      ↓ _classify_crops(valid_crops)
          for each crop:
            build_classifier_views(crop, mode, max_views=4)
            MobileNetV2(view_batch) → logits → softmax
            weighted ensemble
      ↓ _ensemble_predictions(crop_predictions)
      ↓ return DiseasePrediction
  ↓ HTTP 200 {status, data{predictedDisease, confidence, boundingBox}}
```

## 2. Code Path: Non-Leaf → Immediate Green Rejection

```
POST /api/v1/predict
  ↓ classifier.predict(image)
    ↓ _image_has_leaf_color(image)
        R_mean, G_mean, B_mean = ImageStat.Stat(channel).mean[0]
        green_excess = max(0, G_mean - (R_mean + B_mean) / 2)
        denominator  = R_mean + G_mean + B_mean
        green_ratio  = (green_excess / denominator) * 3.0
        if green_ratio < 0.012 → raise PredictionError(422, reason=no_leaf_color)
  ↓ HTTP 422 {"detail": "No durian leaf detected."}
[YOLO never runs — ~8ms total]
```

## 3. Code Path: Green Image, YOLO Detects, Texture Fails

```
POST /api/v1/predict
  ↓ _image_has_leaf_color → PASS (green_ratio ≥ 0.012)
  ↓ _run_detection_pipeline → selected_crops non-empty
  ↓ _assert_crops_valid:
      for crop in selected_crops:
        ... (size, area, confidence checks all pass)
        _crop_has_texture(crop):
          small = crop.convert("L").resize((32,32))
          avg_lap = Σ|4c − c_N−c_S−c_E−c_W| / (30*30)
          if avg_lap < 1.5 → REJECT this crop
      if valid_crops empty → raise PredictionError(422, reason=leaf_validation_failed)
  ↓ HTTP 422 {"detail": "No durian leaf detected."}
```

## 4. Code Path: YOLO Finds Nothing

```
POST /api/v1/predict
  ↓ _image_has_leaf_color → PASS
  ↓ _run_detection_pipeline:
      tries 12 variants (6 preprocessing × 2 thresholds)
      all return selected_crops=[]
      return DetectionRun(selected_crops=[])
  ↓ if not detection_run.selected_crops:
      raise PredictionError(422, reason=detector_no_bbox)
  ↓ HTTP 422 {"detail": "No durian leaf detected."}
```

## 5. Key Data Flows

### 5.1 Green Ratio Computation

```python
# disease_classifier.py → _image_has_leaf_color()
rgb    = image.convert("RGB")
r, g, b = rgb.split()
rm, gm, bm = [Stat(ch).mean[0] for ch in (r, g, b)]
green_excess = max(0.0, gm - (rm + bm) / 2.0)
green_ratio  = min(1.0, (green_excess / max(1.0, rm+gm+bm)) * 3.0)
```

### 5.2 YOLO Crop Quality Calculation

Crop quality is computed inside `leaf_pipeline.py → _compute_quality_score()`.  
It combines aspect ratio, area ratio, and class membership score.  
Non-leaf YOLO classes (e.g., "banana", "scissors") receive quality=0.000 by default.  
Only leaf-disease classes (Leaf Spot, Leaf Blight, Algal Leaf Spot, No Disease / Healthy) receive non-zero quality.

### 5.3 Laplacian Texture Energy

```python
# disease_classifier.py → _crop_has_texture()
small = crop.convert("L").resize((32, 32), BILINEAR)
pix   = small.load()
avg   = Σ |4*pix[x,y] − pix[x-1,y] − pix[x+1,y] − pix[x,y-1] − pix[x,y+1]|
         / (30 * 30)
# Smooth gradient: avg ≈ 0   (Laplacian of linear ramp = 0)
# Real leaf:       avg ≫ 5   (veins, spots, disease lesions create edges)
```

### 5.4 MobileNetV2 Ensemble

```
For each CropCandidate:
  1. build up to 4 view variants (contrast adjustments)
  2. transform each to 224×224 tensor
  3. batch inference: logits = mobilenet(batch)
  4. scaled_logits = logits / temperature (default=1.0)
  5. per_view_probs = softmax(scaled_logits)
  6. quality_weights[i] = variant.score (adjusted for blur/contrast)
  7. crop_probs = weighted_sum(per_view_probs)

Across all crops (ensemble):
  8. entropy_score[i] = 1 - H(probs[i]) / log(5)
  9. final_weights = crop_quality * entropy_score
  10. ensemble_probs = weighted_sum(crop_probs)
  11. final_label, confidence = argmax(ensemble_probs)
```

## 6. Environment Variables (Tunable Gates)

| Variable | Default | Effect |
|----------|---------|--------|
| `AI_MIN_IMAGE_GREEN_RATIO` | `0.012` | Layer 1 green check threshold |
| `AI_MIN_CROP_CONFIDENCE` | `0.20` | Min YOLO confidence to pass Gate 3 |
| `AI_MIN_CROP_GREEN_RATIO` | `0.0` | Crop-level green check (disabled) |
| `AI_MIN_CROP_TEXTURE_ENERGY` | `1.5` | Laplacian texture energy threshold |
| `AI_MIN_CLASSIFIER_CONFIDENCE` | `0.20` | Crop assertion confidence gate |
| `AI_MIN_CROP_QUALITY` | `0.26` | YOLO crop quality gate in pipeline |
| `AI_MIN_CROP_CONFIDENCE` | `0.20` | YOLO confidence gate in pipeline |
| `AI_DEBUG` | `true` | Enable full detection search + debug artifacts |
| `AI_TEMPERATURE` | `1.0` | MobileNet temperature scaling |

## 7. Files Modified in This Session

| File | Change |
|------|--------|
| `duriancare-ai-service/app/services/leaf_pipeline.py` | Fixed AND bug in `_select_crops`; added `min_crop_confidence` parameter |
| `duriancare-ai-service/app/services/disease_classifier.py` | Added `_image_has_leaf_color()` pre-YOLO check; added `_assert_crops_valid()` hard assertions; added `_crop_has_texture()` Laplacian texture gate |
