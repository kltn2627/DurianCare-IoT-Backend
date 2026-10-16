# Runtime Verification Report

**Date:** 2026-08-08T09:18:06Z  
**Branch:** `duy/cleanup-fixes`  
**Service:** `duriancare-ai-service`

## 1. Identity Snapshot

| Field | Value |
|-------|-------|
| git_commit | `505610ffb4a7cee75ce4815fbbb4b99f070df693` |
| git_branch | `duy/cleanup-fixes` |
| ai_version | `0.3.0` |
| build_time (container start) | `2026-08-08T09:13:29.577759+00:00` |
| detector_model | `/workspace/duriancare-ai-service/models/leaf_detector_best.pt` |
| detector_sha256 | `a6da7836c0ceba1fe6a9a4afb570274959c8ecc3afafe341f362ce00abddf3f0` |
| code_sha256 (combined) | `4de85373cbd1079c056b9c27e47922b7b8029a00f2bd752894776d32a9c39cba` |
| endpoint | `http://localhost:8000/api/v1/predict` |

The `code_sha256` is the SHA-256 of the concatenated hashes of:
1. `app/api/predict.py`
2. `app/services/disease_classifier.py`
3. `app/services/leaf_pipeline.py`

## 2. File Hashes (Host vs Runtime)

Verified via `docker exec duriancare-ai-service sha256sum`:

| File | Host SHA256 (first 16) | Runtime SHA256 (first 16) | Match |
|------|----------------------|--------------------------|-------|
| `disease_classifier.py` | `8a594e0d5b934d02` | `8a594e0d5b934d02` | ✓ |
| `leaf_pipeline.py` | `4c7114a7389bd5b7` | `4c7114a7389bd5b7` | ✓ |
| `leaf_detector_best.pt` | `a6da7836c0ceba1f` | `a6da7836c0ceba1f` | ✓ |

All runtime files match host repository. No stale code running.

## 3. Test Execution Evidence

Test run at `2026-08-08T09:18:06Z` via:
```
python duriancare-ai-service\scripts\test_false_positive_elimination.py
```

### 3.1 Non-Leaf Test Results (13/13 PASS)

```
PASS  [422] T1_lol_wallpaper       (57.8ms)   detail='No durian leaf detected.'
PASS  [422] blank_white            (7.5ms)    detail='No durian leaf detected.'
PASS  [422] pure_black             (8.6ms)    detail='No durian leaf detected.'
PASS  [422] solid_red              (8.2ms)    detail='No durian leaf detected.'
PASS  [422] solid_blue             (8.7ms)    detail='No durian leaf detected.'
PASS  [422] gradient_rb            (9.1ms)    detail='No durian leaf detected.'
PASS  [422] human_face_skin        (9.0ms)    detail='No durian leaf detected.'
PASS  [422] cat_grey_fur           (7.8ms)    detail='No durian leaf detected.'
PASS  [422] dog_brown_fur          (22.4ms)   detail='No durian leaf detected.'
PASS  [422] car_silver             (19.5ms)   detail='No durian leaf detected.'
PASS  [422] laptop_dark            (8.4ms)    detail='No durian leaf detected.'
PASS  [422] keyboard_dark          (21.9ms)   detail='No durian leaf detected.'
PASS  [422] landscape_green        (1370.8ms) detail='No durian leaf detected.'
```

**FALSE POSITIVE RATE: 0.0%** ✓

### 3.2 Leaf Test Summary (48/51 PASS)

The 48 passing leaf images span all disease categories:

| Disease | Count |
|---------|-------|
| HEALTHY_LEAF | 12 |
| LEAF_BLIGHT | 11 |
| ALLOCARIDARA_ATTACK | 10 |
| PHOMOPSIS_LEAF_SPOT | 9 |
| ALGAL_LEAF_SPOT | 6 |

The 3 failing leaf images (`1561756.jpg`, `benh-dom-la...vn-1.jpg`, `OIP.webp`) return HTTP 422 because YOLO returns 0 bounding boxes across all 12 rescue attempts. These failures are:
- Present in all 4 test iterations (iterations 1-4)
- Produced by the YOLO detector, not by the false-positive prevention code
- Not caused by any code changes in this session

## 4. Evidence of Baseline (Iteration 1 — Before Any Code Changes)

Iteration 1 ran with the original code (505610ff, before false-positive fixes).  
Results confirmed by reviewing iteration 1 test output:

- `T1_lol_wallpaper`: **FAIL [200]** — classified as PHOMOPSIS_LEAF_SPOT 31.87%  
- `leaf_1561756`: **FAIL [422]** — 0 detections (pre-existing)
- `leaf_benh-dom-la...vn-1`: **FAIL [422]** — 0 detections (pre-existing)
- `leaf_OIP`: **FAIL [422]** — 0 detections (pre-existing)

After code changes (iteration 4):
- `T1_lol_wallpaper`: **PASS [422]** — caught by green ratio check ✓
- All 3 pre-existing FN still fail (unchanged by code changes)

## 5. Code Changes That Eliminate False Positives

### 5.1 `leaf_pipeline.py` — Fix `_select_crops` AND Bug

```python
# BEFORE (vulnerable to bypass by high-confidence YOLO detections):
if quality < self.min_crop_quality and detection.confidence < 0.30:
    continue

# AFTER (separate independent gates):
if detection.confidence < self.min_crop_confidence:  # 0.20
    continue
if quality < self.min_crop_quality:  # 0.26
    continue
```

### 5.2 `disease_classifier.py` — Pre-YOLO Green Ratio Gate

```python
def _image_has_leaf_color(self, image: Image.Image) -> bool:
    min_ratio = float(os.getenv("AI_MIN_IMAGE_GREEN_RATIO", "0.012"))
    green_excess = max(0.0, gm - (rm + bm) / 2.0)
    green_ratio = min(1.0, (green_excess / max(1.0, rm+gm+bm)) * 3.0)
    return green_ratio >= min_ratio
```

### 5.3 `disease_classifier.py` — Laplacian Texture Gate

```python
@staticmethod
def _crop_has_texture(crop_image: Image.Image, min_energy: float) -> bool:
    small = crop_image.convert("L").resize((32, 32), Image.BILINEAR)
    pix = small.load()
    total = sum(abs(4*pix[x,y] - pix[x-1,y] - pix[x+1,y] - pix[x,y-1] - pix[x,y+1])
                for y in range(1,31) for x in range(1,31))
    return (total / 900) >= min_energy  # 900 = 30×30 interior pixels
```

## 6. Assertion: No New False Negatives Introduced

Before any changes (iteration 1): 48 leaf images pass  
After all changes (iteration 4): 48 leaf images pass  

Identical 48 images pass in both iterations. No leaf image that was correctly classified before now fails.

## 7. Container Runtime

```
Container: duriancare-ai-service
Image: duriancare/ai-service:local
Code mounted from: ../  →  /workspace  (volume mount)
Port: 8000
Health: /actuator/health → UP
Runtime info: /api/v1/runtime-info → 200
```

All evidence comes from live HTTP responses to `http://localhost:8000` during active test run.
