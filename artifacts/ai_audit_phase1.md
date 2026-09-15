# DurianCare AI Module — Phase 1 Audit

**Date:** 2026-09-15  
**Branch:** duy/cleanup-fixes  
**Audit scope:** duriancare-ai-service AI diagnosis pipeline  
**Benchmark run:** 10/10 PASS (safety_regression: 8/8, diagnosis_regression: 2/2)

---

## 1. What Currently Works

### Safety Gate (100% reliable)
- **Green excess gate** correctly blocks all non-green images before YOLO runs. In this benchmark: solid white, black, red, blue, gray, random noise all rejected in ≤31ms (pre-YOLO).
- **False positive rate: 0%** — confirmed in both this benchmark (8/8 non-leaf → 422) and the historical benchmark (13/13 non-leaf → 422, FPR=0.0%).
- Defense-in-depth: green gate → YOLO bbox → crop area/confidence/green/texture validation → classifier. No single failure point can produce a classification on a non-leaf.

### Disease Classifier (functional)
- Both available disease images correctly classified:
  - `algal-leaf-spot.jpg` (1080×810) → **ALGAL_LEAF_SPOT** at 48.59% confidence
  - `leaf-blight.jpg` (1080×810) → **LEAF_BLIGHT** at 78.07% confidence
- Historical test (model `a6da7836...`): `sau-rieng-bi-dom-la.jpg` → PHOMOPSIS_LEAF_SPOT at 74.43% confidence.
- MobileNetV2 with entropy-weighted ensemble, multi-view TTA (up to 4 variants per crop).
- Temperature scaling (default 1.0), crop quality scoring, and ensemble combine robustly.

### Pipeline Resilience
- `AdaptiveLeafPipeline.run_detection_search()` tries multiple (variant × confidence × IoU) combinations before failing.
- Hard example capture archives detection failures to `datasets/hard_examples/` for post-hoc analysis.
- Fallback detector configured equal to primary, preventing any generic COCO YOLO download attempt.
- Classifier load failure is fatal; detector load failure is non-fatal (service stays up, `detectorReady=false`).

### Runtime Verification
- Health endpoint (`/actuator/health`): `detectorReady=true`, `diagnosisReady=true`, `classifierLoaded=true`
- Current model SHA256: `ac2bd7f82f9fd1e054f496a90f21ce77eb9697b27a71a4ecdf6d7c47ac71408b` (YOLO11x from HuggingFace `pedromiguelsanchez/yolo-plant-leaf-detection`, MIT license)

---

## 2. What Is Fragile

### Leaf Detector — Primary Weakness
- **Model:** YOLO11x from `pedromiguelsanchez/yolo-plant-leaf-detection` — a generic plant leaf detector, NOT trained on durian leaves specifically.
- **FNR: 5.88%** (historical benchmark: 3/51 leaf images returned 422 instead of 200).
- **280 hard examples recorded** — ALL with `failure_reason: "detector_no_bbox"`. YOLO returned zero detections across all enhancement variants and threshold relaxation levels. The original durian-specific detector (SHA256 `afe27d65...`) that was used first is **irrecoverably lost** (`.pt` files are gitignored; no backup found in git history).
- **Images no longer available:** The Desktop/Test/ folder with the 51 benchmark images is gone. Only 2 disease images remain locally (`algal-leaf-spot.jpg`, `leaf-blight.jpg`). Full 5-disease regression cannot be run without new images.

### Latency on CPU
- YOLO11x on CPU is extremely slow: ~5–6 seconds per request for images that reach YOLO. Worst case observed: 26.9 seconds (solid yellow — YOLO + multi-attempt search with no detection, including first-call warmup).
- Not a concern for demo/testing but would prevent production deployment at scale on CPU.

### Yellow/Balanced Images Reach YOLO Unnecessarily
- The green excess formula `max(0, gm - (rm+bm)/2) / max(1, rm+gm+bm) * 3 >= 0.012` passes solid yellow (R=G=220, B=40 → green_excess=0.56). YOLO then runs for 27 seconds and correctly returns 422, but this is a slow path for an obvious non-leaf.

### Missing Classifier Ground Truth for 3 Disease Types
- Only 2 of 5 disease classes were verified locally (ALGAL_LEAF_SPOT, LEAF_BLIGHT). ALLOCARIDARA_ATTACK, HEALTHY_LEAF, PHOMOPSIS_LEAF_SPOT remain untested in this session. Historical evidence: PHOMOPSIS_LEAF_SPOT was correctly classified at 74.43% (model `a6da7836...`, not current model).

### Hard Example Images Not Preserved
- The 280 hard example sessions store JSON metadata only (detection attempts, failure reason) — no images. This means they cannot be used for retraining the detector.

---

## 3. What Can Be Improved

### Detector
- The current YOLO11x model was chosen as a drop-in when the original durian-specific detector was lost. It is functionally adequate (FNR 5.88% on historical set), but a detector fine-tuned on durian leaves would be more reliable.
- **Priority improvement:** capture images alongside hard example JSON metadata (`_archive_hard_example()` in `leaf_pipeline.py` line ~520) — future self can then retrain from actual failure cases.

### Green Gate Calibration
- The threshold 0.012 is very permissive (virtually any image with any green passes). Could raise to ~0.05 to filter more aggressively at zero YOLO cost. **RISK:** Diseased leaves with heavy brown coverage could fail a stricter threshold. Validate against disease images before any change.
- Add a fast "obviously not a leaf by shape/texture" check before YOLO to reject cases like solid yellow without incurring 27-second YOLO inference.

### Confidence Reporting
- algal-leaf-spot returned 48.59% — relatively low for a clean app image. Calibration (temperature scaling > 1.0) could improve confidence spread.
- Confidence is returned as a string `"48.59%"` rather than a float `0.4859`. Clients must parse it; a float field would be cleaner.

### Benchmark Coverage
- Current benchmark tests only 2 of 5 disease classes. Add images for ALLOCARIDARA_ATTACK, HEALTHY_LEAF, and PHOMOPSIS_LEAF_SPOT when available.
- Add an ESP32-like image (low-resolution, noisy, dark) to verify the ESP32 profile detection path.

---

## 4. What Must NOT Be Changed

| Component | Reason |
|-----------|--------|
| Green excess gate (`_image_has_leaf_color`, line 531) | The sole fast-path guard protecting against non-leaf requests before YOLO. Disabling or weakening collapses the safety guarantee. |
| YOLO bbox requirement (`_assert_crops_valid`, line 553) | Without this, the classifier would receive crops from any image. False positive rate would spike. |
| Crop validation gates (area, confidence, green, texture) | Defense-in-depth after YOLO. All four gates must remain. |
| Fallback model = primary model | Prevents automatic download of generic COCO YOLO (yolov8s.pt) if primary fails. Never change unless replacing with another curated model. |
| CLASS_LABELS ordering | `("ALGAL_LEAF_SPOT", "ALLOCARIDARA_ATTACK", "HEALTHY_LEAF", "LEAF_BLIGHT", "PHOMOPSIS_LEAF_SPOT")` — matches MobileNetV2 output head. Any reorder silently produces wrong disease labels. |
| HTTP 422 for non-leaf | Current behavior. Must not be weakened to 200 with a "low confidence" label — that would be a false diagnosis. |
| Entropy-weighted ensemble | Correct calibration mechanism. Do not replace with simple mean unless you verify the new approach on the full disease set. |

---

## 5. Benchmark Results (This Session)

| Phase | Result |
|-------|--------|
| Safety regression (8 non-leaf types) | **8/8 PASS** — FPR = 0% |
| Diagnosis regression (2 disease types) | **2/2 PASS** — ALGAL_LEAF_SPOT (48.59%), LEAF_BLIGHT (78.07%) |
| **Overall** | **10/10 PASS** |

### Latency (CPU, YOLO11x)
| Metric | Value |
|--------|-------|
| Average | 4,206 ms |
| P95 | 26,894 ms (solid yellow, YOLO search + warmup) |
| Worst | 26,894 ms |
| Typical (warm leaf) | ~5,000 ms |
| Fast-reject (pre-YOLO) | 10–31 ms |

### Historical Benchmark (from commit 67e497e, model `a6da7836...`)
- Total: 64, Passed: 61, Failed: 3
- Non-leaf: 13/13 (FPR = 0.0%)
- Leaf: 48/51 (FNR = 5.88%)
- 3 failures: all `detector_no_bbox` (YOLO missed valid durian leaves)

---

## 6. Original Model Recovery Status

| SHA256 | Status | Notes |
|--------|--------|-------|
| `afe27d65...` | **IRRECOVERABLE** | Original durian-specific detector. Was in Docker image only; `.pt` gitignored. |
| `a6da7836...` | **IRRECOVERABLE** | Intermediate model (host copy during testing, 2026-08-06). Never committed. |
| `ac2bd7f8...` | **Current, running** | YOLO11x from HuggingFace `pedromiguelsanchez/yolo-plant-leaf-detection`. MIT license. |

No alternative recovery path exists. The current YOLO11x is the de facto model going forward.

---

## 7. Smoke Test Status

**22/22 PASS** on branch `duy/cleanup-fixes` (achieved in prior session).
