# DurianCare AI — Graduation Readiness Report

**Date:** 2026-09-16  
**Branch:** duy/cleanup-fixes  
**Commit:** d77effc16c3fd024ca55e428670c5f6f5dc01749  
**Prepared by:** Automated validation — Claude Sonnet 4.6, session c0bcc760

---

## Final Quality Gate

> **READY WITH KNOWN LIMITATIONS**

The AI pipeline is stable, deterministic, and suitable for the graduation demonstration. All demo-critical paths are verified. Known limitations are documented below and are appropriate for a thesis project. No safety failures were found.

---

## 1. Architecture

The pipeline implements defense-in-depth across four sequential gates:

```
Input image
    │
    ▼
Gate 1: Green excess gate (_image_has_leaf_color)
    │ fail → HTTP 422, "No durian leaf detected."  [5–30 ms]
    │ pass
    ▼
Gate 2: YOLO11x detection (multi-attempt rescue if first pass fails)
    │ no bbox → HTTP 422, reason=detector_no_bbox  [900–18,000 ms]
    │ bbox found
    ▼
Gate 3: Crop validation (area, detector confidence, texture)
    │ fail → HTTP 422, reason=leaf_validation_failed
    │ pass
    ▼
Gate 4: MobileNetV2 classifier (TTA 1–4 views, entropy-weighted ensemble)
    │
    ▼
HTTP 200 { predictedDisease, confidence, topPredictions }
```

The original full image is **never** passed to the classifier. All classification paths require a validated YOLO crop.

---

## 2. Detector

| Property | Value |
|----------|-------|
| Model | YOLO11x |
| Source | HuggingFace `pedromiguelsanchez/yolo-plant-leaf-detection` (MIT license) |
| SHA256 | `ac2bd7f82f9fd1e054f496a90f21ce77eb9697b27a71a4ecdf6d7c47ac71408b` |
| Domain | Generic plant leaf — **NOT** durian-specific |
| Historical FNR | 5.88% (3/51, prior benchmark on model `a6da7836`) |
| Session FNR | 0% (2/2, current session — insufficient for confidence interval) |
| FPR | **0.00%** (0/21+ non-leaf tests) |

The original durian-specific detector (`afe27d65...`) is irrecoverably lost. The current YOLO11x is the operational model.

---

## 3. Classifier

| Property | Value |
|----------|-------|
| Architecture | MobileNetV2 |
| Weights | `mobilenetv2_classifier_high_acc.pth` |
| SHA256 | `fc3dc5c92e43a9dbb196029fa56f9722ed91613d11ae7ccdadda1093fd1e5afe` |
| Training | 150 epochs (head 0–100, fine-tune 100–150) |
| Test accuracy | **97.43%** on n=1088 (classifier isolation — not end-to-end) |
| Classes | 5: ALGAL_LEAF_SPOT, ALLOCARIDARA_ATTACK, HEALTHY_LEAF, LEAF_BLIGHT, PHOMOPSIS_LEAF_SPOT |

**Per-class metrics (classifier isolation, n=1088):**

| Class | Precision | Recall | F1 |
|-------|-----------|--------|----|
| ALGAL_LEAF_SPOT | 0.96 | 0.97 | 0.96 |
| ALLOCARIDARA_ATTACK | 0.98 | 0.97 | 0.98 |
| HEALTHY_LEAF | 0.98 | 1.00 | 0.99 |
| LEAF_BLIGHT | 0.98 | 0.97 | 0.97 |
| PHOMOPSIS_LEAF_SPOT | 0.97 | 0.96 | 0.96 |
| **Macro avg** | **0.974** | **0.974** | **0.972** |

⚠️ These are **classifier-isolation** metrics, not end-to-end accuracy. End-to-end accuracy = classifier accuracy × leaf detection rate (~94%).

---

## 4. Pipeline

Key configuration at baseline:

| Parameter | Value |
|-----------|-------|
| Green gate threshold | 0.012 |
| YOLO initial confidence | 0.25 |
| Multi-attempt search | 6 variants × 3 conf × 2 IoU = up to 36 YOLO calls |
| Min detection confidence (crop) | 0.20 |
| Min crop area | 64×64 px |
| Min texture energy | 1.5 (Laplacian) |
| TTA views | up to 4 |
| Temperature | 1.0 |
| Ensemble | entropy-weighted |

---

## 5. Safety

| Safety Property | Status | Evidence |
|----------------|--------|----------|
| FPR = 0% | ✅ CONFIRMED | 12/12 (this session) + 13/13 (historical) = 25/25 non-leaf → 422 |
| No classification without YOLO bbox | ✅ CONFIRMED | Code audit: all paths through `predict()` require `selected_crops` |
| No original image to classifier | ✅ CONFIRMED | Code audit: `_classify_crops()` only accepts `CropCandidate` objects |
| HTTP 422 never silently becomes 200 | ✅ CONFIRMED | Code audit: 422 path throws `PredictionError`, no fallback to 200 |
| Graceful failure for invalid inputs | ✅ CONFIRMED | Phase 27.5: 11/11 PASS — 400/413/415/422 for all bad inputs, no fake diagnosis |
| Health endpoint accurately reflects state | ✅ CONFIRMED | Phase 27.2: SHA256 verified, health fields correct |

---

## 6. Regression Results (Phase 27.10, 2026-09-16)

| Phase | Run | PASS | FAIL | SKIP | Notes |
|-------|-----|------|------|------|-------|
| Safety regression | 12 | 12 | 0 | 0 | FPR = 0% |
| Diagnosis regression | 2 | 2 | 0 | 3 | 3 skipped — no images |
| ESP32 simulation | 8 | 8 | 0 | 0 | Simulated only |
| Failure behavior | 11 | 11 | 0 | 0 | No fake diagnoses |
| **Total** | **33** | **33** | **0** | **3** | |

Smoke test (22/22 PASS) verified in prior session. No regression detected.

---

## 7. Performance

All measurements: CPU, Docker container, single-threaded, Windows 11 host.

| Metric | 2026-09-15 | 2026-09-16 |
|--------|-----------|-----------|
| Avg latency | 2,181 ms | 2,291 ms |
| Median latency | 1,187 ms | 1,260 ms |
| P95 latency | 6,924 ms | 7,573 ms |
| Max latency | 18,086 ms | 17,464 ms |

**Latency breakdown (Phase 27.6 analysis):**

| Path | Latency | Cause |
|------|---------|-------|
| Pre-YOLO rejection | 5–30 ms | Green gate blocks; YOLO never runs. Fast and expected. |
| YOLO + no detection (leaf-green inputs) | 900–7,500 ms | YOLO runs, finds no leaf. Multi-attempt rescue exhausts variants. |
| YOLO warmup (cold start) | +14–16 s on first call | PyTorch model initialization + first YOLO inference. Subsequent calls ~4–5 s. |
| Leaf detected + classified | 1,100–5,000 ms | YOLO detection (~1–3 s on CPU) + MobileNetV2 TTA (~50–200 ms) |
| ESP32 simulated (320×240) | 1,100–2,200 ms | Smaller image = faster YOLO |

**The 17–18 second worst case** is: YOLO cold start (first call) + solid yellow image (passes green gate, YOLO runs full rescue pipeline, finds no leaf). This is expected behavior — not a bug, not unnecessary repeated inference. Warm calls with the same image type take ~7–7.5 s.

**No optimization was applied.** There is one safe optimization documented but not yet applied:
- Pre-warm YOLO on service start with a dummy inference (eliminating the ~15-second first-call spike). This is safe and does not affect accuracy or safety.

---

## 8. Demo Readiness

All 6 demo scenarios verified (see `AI_DEMO_TEST_PLAN.md`):

| Scenario | Result |
|----------|--------|
| Diseased leaf — ALGAL_LEAF_SPOT | ✅ PASS |
| Diseased leaf — LEAF_BLIGHT | ✅ PASS |
| Non-leaf (solid red) → 422 | ✅ PASS |
| Green noise → 422 (YOLO rejects) | ✅ PASS |
| Empty upload → 400 | ✅ PASS |
| ESP32 simulated (320×240) → correct | ✅ PASS (simulated) |

Demo is deterministic: given the same inputs, results are identical across runs.

---

## 9. Failure Handling

All failure paths verified graceful (no fake diagnoses, no unhandled exceptions):

| Failure Scenario | HTTP | Disease returned | Status |
|-----------------|------|-----------------|--------|
| Empty upload (0 bytes) | 400 | None | ✅ Safe |
| 1-byte upload | 400 | None | ✅ Safe |
| Plain text file | 415 | None | ✅ Safe |
| PDF header | 415 | None | ✅ Safe |
| Corrupt JPEG | 400 | None | ✅ Safe |
| Corrupt PNG | 400 | None | ✅ Safe |
| Non-leaf (solid green, passes gate) | 422 | None | ✅ Safe |
| Non-leaf (solid red, fails gate) | 422 | None | ✅ Safe |
| Oversized 11 MB | 413 | None | ✅ Safe |
| Wrong field name (`file` instead of `image`) | 422 | None | ✅ Safe |
| Valid leaf (positive case) | 200 | ALGAL_LEAF_SPOT ✓ | ✅ Safe |

---

## 10. Reproducibility

Single command from repo root:

```bash
python scripts/ai_validate.py
```

See `AI_REPRODUCIBILITY.md` for full instructions, prerequisites, and expected output.

---

## 11. License

| Component | License | Thesis use | Commercial use |
|-----------|---------|-----------|---------------|
| YOLO11x weights | MIT | ✅ | ✅ |
| Ultralytics framework | AGPL-3.0 | ✅ (non-distributed) | ⚠️ Requires review |
| PyTorch / torchvision | BSD-3-Clause | ✅ | ✅ |
| MobileNetV2 weights | Project-owned | ✅ | ✅ |

See `AI_LICENSE_AUDIT.md` for full analysis. For graduation/non-commercial use, no license blockers exist.

---

## 12. Known Limitations

| # | Limitation | Severity for Demo | Status |
|---|-----------|------------------|--------|
| L1 | Generic leaf detector (not durian-specific) | Low — works for demo | Accepted; original detector irrecoverable |
| L2 | 3/5 disease classes lack end-to-end test coverage | Medium | Documented; classifier isolation shows ≥96% for all classes |
| L3 | CPU-only (~1–4 s/request) | Low — acceptable for demo | Documented; GPU needed for production scale |
| L4 | Low confidence on algal-leaf-spot (48.59%) | Low — result is correct | Known; temperature scaling could improve calibration |
| L5 | YOLO cold-start spike (~17 s first call) | Low — occurs once at demo start | Pre-warm option available but not implemented |
| L6 | ESP32 results simulated only | Medium | Clearly documented; real-device test not possible |
| L7 | Ultralytics AGPL-3.0 unresolved for commercial | Not a demo blocker | Noted for future commercial deployment review |
| L8 | Hard-example images not preserved | Info | JSON metadata only; 280 examples cannot be used for retraining |

---

## 13. Thesis Traceability

Full requirement–implementation–test–evidence mapping: `AI_THESIS_TRACEABILITY.md`

| Requirement | Coverage |
|-------------|---------|
| REQ-AI-01: Leaf detection | ✅ Implemented, tested, 94% historical detection rate |
| REQ-AI-02: No-leaf rejection | ✅ FPR = 0%, verified 25+ tests |
| REQ-AI-03: Disease classification | ✅ 97.43% classifier, 2/5 classes end-to-end |
| REQ-AI-04: Variable image handling | ✅ 8/8 degraded variants (simulated) |
| REQ-AI-05: ESP32 compatibility | ⚠️ Simulated only, real device not tested |
| REQ-AI-06: False positive safety | ✅ FPR = 0% |
| REQ-AI-07: Health monitoring | ✅ SHA256 verified, health fields accurate |
| REQ-AI-08: Gateway integration | ✅ Smoke test 22/22 via gateway |

---

## 14. Exact Model SHA256

| Model | SHA256 |
|-------|--------|
| `leaf_detector_best.pt` | `ac2bd7f82f9fd1e054f496a90f21ce77eb9697b27a71a4ecdf6d7c47ac71408b` |
| `mobilenetv2_classifier_high_acc.pth` | `fc3dc5c92e43a9dbb196029fa56f9722ed91613d11ae7ccdadda1093fd1e5afe` |

Local host SHA256 == Docker container SHA256 == `/api/v1/runtime-info` reported SHA256. **VERIFIED MATCH.**

---

## 15. Exact Test Results

| Test suite | Date | PASS | FAIL | SKIP |
|-----------|------|------|------|------|
| Safety regression | 2026-09-16 | 12 | 0 | 0 |
| Diagnosis regression | 2026-09-16 | 2 | 0 | 3 |
| ESP32 simulation | 2026-09-16 | 8 | 0 | 0 |
| Failure behavior | 2026-09-16 | 11 | 0 | 0 |
| Smoke test (22-item) | 2026-09-15 | 22 | 0 | 0 |
| Model integrity | 2026-09-16 | 2 | 0 | 0 |
| **Grand total** | | **57** | **0** | **3** |

57/57 executed tests PASS. 3 skips are expected (missing disease images — documented, not hidden).

---

## 16. Final Quality Gate

**READY WITH KNOWN LIMITATIONS**

Rationale:
- All demo-critical paths are stable and produce deterministic results
- Safety properties are fully verified (FPR = 0%, no fake diagnoses)
- End-to-end coverage of 2/5 disease classes is a known and documented gap
- CPU latency is within acceptable range for thesis demonstration
- No safety failures, no regressions, no model integrity issues

The system is **not** READY FOR DEMO without qualification only because:
1. 3/5 disease classes lack end-to-end pipeline evidence (no test images)
2. ESP32-CAM robustness is simulated, not real-device proven

These do not prevent a successful graduation demonstration with the 2 available disease images.
