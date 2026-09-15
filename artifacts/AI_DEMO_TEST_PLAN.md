# DurianCare AI — Graduation Demo Test Plan

**Date:** 2026-09-16  
**Branch:** duy/cleanup-fixes  
**Status:** Deterministic — results verified against running production environment

---

## Pre-Demo Checklist

- [ ] Docker Desktop running
- [ ] `docker ps` shows `duriancare-ai-service` Up
- [ ] `GET http://localhost:8000/actuator/health` → `detectorReady=true, diagnosisReady=true`
- [ ] JWT token acquired via login (smoke test item 06)
- [ ] Test images accessible at `DurianCare-IoT-Mobile-App/assets/images/community/`

---

## Demo Scenario 1 — Diseased Leaf (ALGAL_LEAF_SPOT)

**Purpose:** Show that the system correctly identifies a real disease on a durian leaf.

| Field | Value |
|-------|-------|
| **Input** | `algal-leaf-spot.jpg` (1080×810) |
| **Method** | `POST http://localhost:8080/api/v1/predict` (via Gateway) |
| **Field name** | `image` (multipart/form-data) |
| **Expected HTTP** | 200 |
| **Expected disease** | `ALGAL_LEAF_SPOT` |
| **Typical confidence** | 48.59% |
| **Typical latency** | 3,500–5,000 ms (warm) |
| **Actual result (2026-09-16)** | HTTP 200, `ALGAL_LEAF_SPOT`, 48.59%, 4,739 ms |
| **Status** | ✅ VERIFIED |

**Demo talking point:** Note the moderate confidence (48.59%) — this reflects genuine uncertainty in the classifier. The system still produces the correct class. Confidence is not inflated.

---

## Demo Scenario 2 — Diseased Leaf (LEAF_BLIGHT)

**Purpose:** Show a second disease class at higher confidence.

| Field | Value |
|-------|-------|
| **Input** | `leaf-blight.jpg` (1080×810) |
| **Method** | `POST http://localhost:8080/api/v1/predict` |
| **Field name** | `image` |
| **Expected HTTP** | 200 |
| **Expected disease** | `LEAF_BLIGHT` |
| **Typical confidence** | 78.07% |
| **Typical latency** | 3,500–5,000 ms (warm) |
| **Actual result (2026-09-16)** | HTTP 200, `LEAF_BLIGHT`, 78.07%, 4,560 ms |
| **Status** | ✅ VERIFIED |

---

## Demo Scenario 3 — Non-Leaf Image (Solid Red)

**Purpose:** Show the safety gate — a non-plant image is rejected immediately.

| Field | Value |
|-------|-------|
| **Input** | Solid red 320×240 PNG (generated) |
| **Method** | `POST http://localhost:8080/api/v1/predict` |
| **Expected HTTP** | 422 |
| **Expected body** | `{"detail": "No durian leaf detected."}` |
| **Expected latency** | 5–30 ms (pre-YOLO rejection) |
| **Actual result (2026-09-16)** | HTTP 422, no disease, 5 ms |
| **Status** | ✅ VERIFIED |

**Demo talking point:** The green-excess gate rejects non-green images in milliseconds — YOLO never runs. This is a computational efficiency and safety feature.

---

## Demo Scenario 4 — Invalid/No-Leaf Image (Green Noise)

**Purpose:** Show that a green-but-structureless image passes the color gate but YOLO correctly finds no leaf.

| Field | Value |
|-------|-------|
| **Input** | Greenish random noise 320×240 PNG (generated) |
| **Method** | `POST http://localhost:8080/api/v1/predict` |
| **Expected HTTP** | 422 |
| **Reason** | `detector_no_bbox` — passes color gate, YOLO finds no leaf |
| **Expected latency** | 1,000–3,000 ms (YOLO runs but returns no bbox) |
| **Actual result (2026-09-16)** | HTTP 422, no disease, 2,364 ms |
| **Status** | ✅ VERIFIED |

**Demo talking point:** Green noise passes the color filter (it is green), but YOLO detects no leaf-shaped structure. The system does not classify it. Defense-in-depth works.

---

## Demo Scenario 5 — Empty/Invalid Upload

**Purpose:** Show graceful handling of a completely invalid upload.

| Field | Value |
|-------|-------|
| **Input** | Empty bytes (0-length body) |
| **Method** | `POST http://localhost:8080/api/v1/predict` |
| **Expected HTTP** | 400 or 422 |
| **Expected body** | Error detail, no disease field |
| **Actual result (2026-09-16)** | HTTP 400, no disease, 4 ms |
| **Status** | ✅ VERIFIED |

---

## Demo Scenario 6 — ESP32 Simulated Image (Low Resolution)

**Purpose:** Show that the system works with ESP32-CAM-quality images (simulated).

| Field | Value |
|-------|-------|
| **Input** | `algal-leaf-spot.jpg` resized to 320×240, JPEG q70 (simulated ESP32) |
| **Method** | `POST http://localhost:8080/api/v1/predict` |
| **Expected HTTP** | 200 |
| **Expected disease** | `ALGAL_LEAF_SPOT` |
| **Typical confidence** | 63.47% (higher than full-res — smaller image easier for detector) |
| **Expected latency** | 1,000–1,500 ms |
| **Actual result (2026-09-16)** | HTTP 200, `ALGAL_LEAF_SPOT`, 63.47%, 1,194 ms |
| **Status** | ✅ VERIFIED (SIMULATED — not real ESP32 hardware) |

**Demo talking point:** Simulated 320×240 ESP32-quality images still produce correct results. Note this is a simulation — a real ESP32-CAM device would require real-hardware validation.

---

## Scenario Results Summary

| Scenario | Input | Expected HTTP | Actual HTTP | Disease | Latency | Status |
|----------|-------|--------------|-------------|---------|---------|--------|
| 1. Algal leaf spot | algal-leaf-spot.jpg | 200 | 200 | ALGAL_LEAF_SPOT (48.59%) | 4,739 ms | ✅ PASS |
| 2. Leaf blight | leaf-blight.jpg | 200 | 200 | LEAF_BLIGHT (78.07%) | 4,560 ms | ✅ PASS |
| 3. Non-leaf (solid red) | solid red PNG | 422 | 422 | None | 5 ms | ✅ PASS |
| 4. Green noise | green noise PNG | 422 | 422 | None | 2,364 ms | ✅ PASS |
| 5. Empty upload | 0-byte body | 400/422 | 400 | None | 4 ms | ✅ PASS |
| 6. ESP32 simulated | 320×240 JPEG q70 | 200 | 200 | ALGAL_LEAF_SPOT (63.47%) | 1,194 ms | ✅ PASS (SIM) |

**Demo result: 6/6 scenarios PASS**

---

## Unavailable Demo Scenarios

The following scenarios are NOT available due to missing test images:

| Scenario | Reason |
|----------|--------|
| ALLOCARIDARA_ATTACK leaf | No test image available |
| HEALTHY_LEAF | No test image available |
| PHOMOPSIS_LEAF_SPOT | No test image available |
| Multi-leaf image | No multi-leaf image available |
| Real ESP32-CAM photo | No real device; only simulated |

These are known gaps. The classifier model achieves ≥96% precision/recall on all 5 classes in isolation (n=1088 test set), but end-to-end pipeline coverage is limited to 2 classes.

---

## Recovery If Demo Fails

| Symptom | Recovery |
|---------|---------|
| AI service not ready | `docker compose -f infrastructure/docker-compose.yml restart duriancare-ai-service`, wait 30 s |
| First request very slow (18+ s) | Expected — YOLO cold start. Second request will be ~4 s. |
| HTTP 503 from gateway | Check `docker ps`; restart gateway |
| Confidence looks unexpectedly low | Normal — 48.59% on algal is expected behavior |
