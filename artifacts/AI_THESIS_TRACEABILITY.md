# DurianCare AI — Thesis Requirement Traceability

**Date:** 2026-09-16  
**Branch:** duy/cleanup-fixes  
**Format:** Requirement → Implementation → Test → Evidence

---

## REQ-AI-01: Leaf Detection

**Requirement:** The system must detect whether an input image contains a durian leaf before attempting disease classification.

| Field | Detail |
|-------|--------|
| **Implementation** | `duriancare-ai-service/app/services/disease_classifier.py` |
| **Key function** | `DoubleModelDiseaseClassifier.predict()` → `_run_detection_pipeline()` |
| **Mechanism** | Green excess gate (`_image_has_leaf_color()`) as fast pre-check, then YOLO11x leaf detector, then multi-attempt rescue pipeline (`AdaptiveLeafPipeline`) |
| **Endpoint** | `POST /api/v1/predict` via `duriancare-ai-service/app/api/predict.py` |
| **Test** | Phase 19 Safety Regression, Phase 17 Diagnosis Regression |
| **Result** | Detector found leaves in 2/2 available test images (100% detection rate, n=2) |
| **Historical result** | 48/51 detection success = 94.12% (model `a6da7836`, prior benchmark) |
| **Evidence** | `artifacts/AI_DIAGNOSIS_REGRESSION.csv`, `artifacts/AI_SAFETY_REGRESSION.csv` |

---

## REQ-AI-02: No-Leaf Rejection (False Positive Prevention)

**Requirement:** The system must reject non-leaf inputs with an appropriate error response and never produce a disease diagnosis for a non-leaf image.

| Field | Detail |
|-------|--------|
| **Implementation** | `duriancare-ai-service/app/services/disease_classifier.py` |
| **Key function** | `_image_has_leaf_color()` (green excess gate), `_assert_crops_valid()` (post-YOLO crop validation) |
| **Mechanism** | Multi-gate pipeline: green gate → YOLO bbox required → crop area/confidence/green/texture validation. Any gate failure → HTTP 422, no classification |
| **HTTP response** | `422 Unprocessable Entity`, body `{"detail": "No durian leaf detected."}` |
| **Endpoint** | `POST /api/v1/predict` |
| **Test** | Phase 19 Safety Regression (12 non-leaf images), Phase 27.5 Failure Behavior (10 invalid inputs) |
| **Result** | **FPR = 0%** — 12/12 non-leaf → 422, 0 false diagnoses across all sessions (combined 21/21) |
| **Evidence** | `artifacts/AI_SAFETY_REGRESSION.csv` |

---

## REQ-AI-03: Disease Classification

**Requirement:** For a valid leaf image, the system must classify the disease into one of 5 classes with a confidence score.

| Field | Detail |
|-------|--------|
| **Implementation** | `duriancare-ai-service/app/services/disease_classifier.py` |
| **Key class** | `DoubleModelDiseaseClassifier._classify_crops()`, `_ensemble_predictions()` |
| **Mechanism** | MobileNetV2 with entropy-weighted ensemble, TTA (up to 4 views), temperature scaling |
| **Classes** | ALGAL_LEAF_SPOT, ALLOCARIDARA_ATTACK, HEALTHY_LEAF, LEAF_BLIGHT, PHOMOPSIS_LEAF_SPOT |
| **Endpoint** | `POST /api/v1/predict` → response field `data.predictedDisease` (string), `data.confidence` (string percent) |
| **Test** | Phase 17 End-to-End Diagnosis Regression, Classifier isolation benchmark (n=1088) |
| **End-to-end result** | 2/2 tested disease classes correct (ALGAL_LEAF_SPOT: 48.59%, LEAF_BLIGHT: 78.07%) |
| **Classifier isolation accuracy** | 97.43% on n=1088 held-out test set |
| **3 classes NOT tested end-to-end** | ALLOCARIDARA_ATTACK, HEALTHY_LEAF, PHOMOPSIS_LEAF_SPOT — no test images available |
| **Evidence** | `artifacts/AI_DIAGNOSIS_REGRESSION.csv`, `artifacts/AI_CONFUSION_MATRIX.csv` |

---

## REQ-AI-04: Multiple/Variable Image Handling

**Requirement:** The system must handle variable image sizes, formats, and quality levels.

| Field | Detail |
|-------|--------|
| **Implementation** | `duriancare-ai-service/app/services/image_enhancement.py`, `app/services/esp32_preprocessing.py` |
| **Mechanism** | Image resized to 224×224 for classifier; YOLO runs on original resolution; ESP32 profile detection (`estimate_esp32_profile()`) applies preprocessing for low-quality inputs; multi-attempt rescue with 6 enhancement variants |
| **Size limit** | 10 MB (`MAX_IMAGE_SIZE_BYTES=10485760`) — HTTP 413 above |
| **Format** | JPEG, PNG tested; PIL handles many formats |
| **Test** | Phase 21 ESP32 Simulation — 8 degraded variants (low-res, JPEG q20, dark, blurred) |
| **Result** | 8/8 PASS on simulated degraded inputs; disease classification maintained under all degradation types |
| **Caveat** | ESP32 simulation results are PROGRAMMATICALLY GENERATED, not real device images |
| **Evidence** | `artifacts/AI_SAFETY_REGRESSION.csv` (esp32_simulation rows) |

---

## REQ-AI-05: ESP32-CAM Compatibility

**Requirement:** The system must process images from ESP32-CAM devices (low resolution, JPEG compressed, potentially dark or blurred).

| Field | Detail |
|-------|--------|
| **Implementation** | `duriancare-ai-service/app/services/esp32_preprocessing.py` |
| **Key function** | `estimate_esp32_profile()`, `preprocess_for_esp32()` |
| **Mechanism** | Detects ESP32-like characteristics (small resolution, low blur score, dark, compressed) and applies targeted preprocessing before the detector |
| **Test** | Phase 21 — 4 ESP32 simulation variants per disease image (8 total) |
| **Result** | 8/8 PASS (simulated) — ALGAL_LEAF_SPOT and LEAF_BLIGHT correctly identified under: 320×240 JPEG q70, JPEG q20 640×480, 0.35× brightness, Gaussian blur r=3 |
| **Important caveat** | **Results are SIMULATED only.** No real ESP32-CAM hardware images were used. Real-device testing is required before claiming production ESP32 compatibility. |
| **Evidence** | `artifacts/AI_SAFETY_REGRESSION.csv` (subtype=lowres_320x240, jpeg_q20_640x480, dark_underexposed, motion_blur) |

---

## REQ-AI-06: Safety Against False Positives

**Requirement:** The system must maintain a false positive rate of 0% (never produce a disease diagnosis for a non-leaf input).

| Field | Detail |
|-------|--------|
| **Implementation** | Defense-in-depth: 4 independent gates all must pass before classification runs |
| **Gate 1** | Green excess gate in `_image_has_leaf_color()` — fast color check, rejects non-green images in 5–30 ms |
| **Gate 2** | YOLO bbox required — no classification without a detected leaf bounding box |
| **Gate 3** | `_assert_crops_valid()` — crop area, YOLO confidence, green ratio, texture energy must all pass |
| **Gate 4** | Classifier runs only on validated crops — never on the original full image |
| **Test** | Safety regression across 3 sessions: 12/12 (this session), 8/8 (prior session), 13/13 (historical) |
| **FPR** | **0.00%** (0 false positives / 33+ non-leaf tests) |
| **Failure behavior** | HTTP 422, body includes reason code (`no_leaf_color`, `detector_no_bbox`, `leaf_validation_failed`) |
| **Evidence** | `artifacts/AI_SAFETY_REGRESSION.csv`, `artifacts/AI_FINAL_VALIDATION_REPORT.md` §5 |

---

## REQ-AI-07: Runtime Health Monitoring

**Requirement:** The system must expose its health and readiness state so operators can monitor AI pipeline availability.

| Field | Detail |
|-------|--------|
| **Implementation** | `duriancare-ai-service/main.py` — `/actuator/health` and `/api/v1/runtime-info` endpoints |
| **Health fields** | `status`, `detectorReady`, `diagnosisReady`, `classifierLoaded`, `modelsLoaded`, `device`, `databaseReady`, `ragReady` |
| **Degraded state** | When YOLO detector fails to load: `detectorReady=false`, `diagnosisReady=false`, service still UP for non-AI routes |
| **Integrity field** | `/api/v1/runtime-info` returns `detector_sha256` and `code_sha256` for model verification |
| **Endpoint** | `GET /actuator/health` → `{"status": "UP", "detectorReady": true, ...}` |
| **Test** | Smoke test items 02–05 (22-item smoke test), Phase 27.2 model integrity check |
| **Result** | Health correctly reports `detectorReady=true`, `diagnosisReady=true`, SHA256 matches baseline |
| **Evidence** | `artifacts/AI_BASELINE.md` §11, smoke test results |

---

## REQ-AI-08: Gateway Integration

**Requirement:** The AI diagnosis endpoint must be accessible through the API Gateway with authentication.

| Field | Detail |
|-------|--------|
| **Implementation** | `infrastructure/docker-compose.yml` — gateway service routes `/api/v1/predict` and `/actuator/health` to AI service |
| **Gateway** | Spring Cloud Gateway on port 8080; routes to AI service on port 8000 |
| **Auth** | JWT bearer token required for most routes; health endpoint may be unauthenticated |
| **Test** | Smoke test item 17 (real leaf via Gateway), smoke test items 01–22 general gateway health |
| **Result** | `POST http://localhost:8080/api/v1/predict` → 200 ALGAL_LEAF_SPOT (with valid JWT) |
| **Evidence** | Smoke test 22/22 PASS record in `artifacts/AI_FINAL_VALIDATION_REPORT.md` §10 |

---

## Traceability Matrix Summary

| Requirement | Implementation File | Test | Result |
|-------------|--------------------|----|--------|
| REQ-AI-01: Leaf detection | `disease_classifier.py`, `leaf_pipeline.py` | Phase 17, 19 | 2/2 detected, 94.12% historical |
| REQ-AI-02: No-leaf rejection | `disease_classifier.py` (`_image_has_leaf_color`, `_assert_crops_valid`) | Phase 19, 27.5 | 12/12 PASS, FPR=0% |
| REQ-AI-03: Disease classification | `disease_classifier.py` (classifier + ensemble) | Phase 17, 18 | 2/2 end-to-end, 97.43% classifier |
| REQ-AI-04: Variable image handling | `image_enhancement.py`, `esp32_preprocessing.py` | Phase 21 | 8/8 simulated degraded PASS |
| REQ-AI-05: ESP32-CAM | `esp32_preprocessing.py` | Phase 21 (simulated) | 8/8 PASS (simulated only) |
| REQ-AI-06: Zero FPR | Multi-gate pipeline in `disease_classifier.py` | Phase 19, 27.5 | FPR=0%, 33+ tests |
| REQ-AI-07: Health monitoring | `main.py` health endpoints | Smoke test, Phase 27.2 | Health accurate, SHA256 verified |
| REQ-AI-08: Gateway integration | `docker-compose.yml`, gateway routing | Smoke test | 22/22 PASS incl. gateway path |
