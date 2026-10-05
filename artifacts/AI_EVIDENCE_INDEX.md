# DurianCare AI — Evidence Index

**Purpose:** Maps every AI claim to the artifact(s) and test(s) that back it up.  
**Date:** 2026-09-16  
**Freeze commit:** d8a19cc

Use this table when a thesis examiner or reviewer asks "where is the evidence for X?"

---

## Evidence Index

| Claim | Supporting Artifact(s) | Test / Measurement | Result |
|-------|----------------------|-------------------|--------|
| Leaf detector uses YOLO11x | `AI_FREEZE.md` §Models, `AI_GRADUATION_READINESS_REPORT.md` §2 | Model SHA256 verified | detector SHA256 = `ac2bd7f8…` |
| Detector sourced from HuggingFace (MIT license) | `AI_LICENSE_AUDIT.md`, `AI_FREEZE.md` | Provenance audit | MIT license confirmed |
| Detector is generic plant-leaf (not durian-specific) | `AI_GRADUATION_READINESS_REPORT.md` §2, `AI_CLAIMS_GUIDE.md` | Code audit, model card review | Confirmed: single class `{0: 'leaf'}` |
| Historical leaf detection rate 94.12% | `AI_FINAL_VALIDATION_REPORT.md`, `AI_GRADUATION_READINESS_REPORT.md` §2 | Phase 16 benchmark, 51 valid leaf images | 48/51 detected = 94.12% |
| Original durian-specific detector irrecoverable | `AI_GRADUATION_READINESS_REPORT.md` §2, `AI_THESIS_TRACEABILITY.md` | SHA256 lookup, git history audit | Model `afe27d65…` not found |
| MobileNetV2 classifier: 97.43% accuracy | `AI_CONFUSION_MATRIX.csv`, `AI_GRADUATION_READINESS_REPORT.md` §3 | Classifier-isolation test, n=1,088 | 97.43% test accuracy |
| Per-class precision/recall 0.96–1.00 | `AI_CONFUSION_MATRIX.csv`, `AI_GRADUATION_READINESS_REPORT.md` §3 | Classifier-isolation benchmark | All 5 classes ≥0.96 |
| Classifier trained for 150 epochs | `AI_THESIS_TRACEABILITY.md`, `AI_GRADUATION_READINESS_REPORT.md` §3 | Training metadata | 2-phase: head 0–100, fine-tune 100–150 |
| Classifier SHA256 verified | `AI_FREEZE.md` §Models | SHA256 check: local == container == runtime | `fc3dc5c9…` all match |
| CLASS_LABELS order fixed (must not change) | `AI_FREEZE.md` §Disease Classes | Code audit: `disease_classifier.py:42-48` | Tuple order frozen |
| End-to-end: ALGAL_LEAF_SPOT verified | `AI_DEMO_TEST_PLAN.md` §Scenario 1, `AI_DIAGNOSIS_REGRESSION.csv` | Live end-to-end request | HTTP 200, correct, 48.59% |
| End-to-end: LEAF_BLIGHT verified | `AI_DEMO_TEST_PLAN.md` §Scenario 2, `AI_DIAGNOSIS_REGRESSION.csv` | Live end-to-end request | HTTP 200, correct, 78.07% |
| ALLOCARIDARA, HEALTHY, PHOMOPSIS not end-to-end tested | `AI_DEMO_TEST_PLAN.md` §Unavailable, `AI_CLAIMS_GUIDE.md` | No test images available | SKIP (documented) |
| Safety FPR = 0% | `AI_SAFETY_REGRESSION.csv`, `AI_GRADUATION_READINESS_REPORT.md` §5 | 12/12 safety tests + 13/13 historical | 25+ non-leaf inputs → all 422 |
| No non-leaf image ever produces a diagnosis | `AI_SAFETY_REGRESSION.csv`, failure behavior tests | Phase 27.5: 11/11 PASS | No fake diagnoses in any run |
| Original full image never sent to classifier | `AI_GRADUATION_READINESS_REPORT.md` §5 | Code audit: `disease_classifier.py` | All classification paths require YOLO crop |
| HTTP 422 never silently becomes 200 | `AI_GRADUATION_READINESS_REPORT.md` §5 | Code audit: `PredictionError` throws | 422 path never falls back to 200 |
| Average latency ~2,200 ms | `AI_PERFORMANCE_DATA.csv`, `AI_PERFORMANCE_REPORT.md` | Two measurement sessions | 2,181 ms (2026-09-15), 2,291 ms (2026-09-16) |
| YOLO cold-start ~17 s (first call) | `AI_GRADUATION_READINESS_REPORT.md` §7 | Timed measurement | First call 14–18 s; warm calls 4–5 s |
| Fast rejection 5–30 ms for non-green images | `AI_GRADUATION_READINESS_REPORT.md` §7, `AI_DEMO_TEST_PLAN.md` §Scenario 3 | Solid red image timed | 5 ms measured |
| ESP32 simulation: 8/8 PASS | `AI_DIAGNOSIS_REGRESSION.csv`, `AI_GRADUATION_READINESS_REPORT.md` §6 | scripts/ai_validate.py ESP32 suite | 8/8 PASS (simulated) |
| ESP32 results are simulation, not real hardware | `AI_DEMO_TEST_PLAN.md` §Scenario 6, `AI_CLAIMS_GUIDE.md` | Explicit documentation | "SIMULATED" flag on all ESP32 results |
| Single-command reproducibility | `AI_REPRODUCIBILITY.md` | `python scripts/ai_validate.py` | 25/25 PASS, 3 SKIP |
| Grand regression: 57/57 PASS | `AI_GRADUATION_READINESS_REPORT.md` §15 | Phase 27.10 final regression | 57 PASS, 0 FAIL, 3 SKIP |
| Smoke test: 22/22 PASS | `AI_GRADUATION_READINESS_REPORT.md` §15 | Full gateway smoke test | 22/22 PASS |
| Quality gate: READY WITH KNOWN LIMITATIONS | `AI_GRADUATION_READINESS_REPORT.md` §16 | All validation suites combined | See §12 known limitations |
| Ultralytics AGPL-3.0 (non-commercial OK) | `AI_LICENSE_AUDIT.md` | License review | Thesis/graduation use cleared |
| Thesis requirements REQ-AI-01 to REQ-AI-08 | `AI_THESIS_TRACEABILITY.md` | Full traceability matrix | See per-requirement coverage |

---

## Artifact File Locations

| Artifact | Path |
|----------|------|
| `AI_FREEZE.md` | `artifacts/AI_FREEZE.md` |
| `AI_CLAIMS_GUIDE.md` | `artifacts/AI_CLAIMS_GUIDE.md` |
| `AI_DEMO_CHEAT_SHEET.md` | `artifacts/AI_DEMO_CHEAT_SHEET.md` |
| `AI_TROUBLESHOOTING.md` | `artifacts/AI_TROUBLESHOOTING.md` |
| `AI_GRADUATION_READINESS_REPORT.md` | `artifacts/AI_GRADUATION_READINESS_REPORT.md` |
| `AI_DEMO_TEST_PLAN.md` | `artifacts/AI_DEMO_TEST_PLAN.md` |
| `AI_THESIS_TRACEABILITY.md` | `artifacts/AI_THESIS_TRACEABILITY.md` |
| `AI_LICENSE_AUDIT.md` | `artifacts/AI_LICENSE_AUDIT.md` |
| `AI_REPRODUCIBILITY.md` | `artifacts/AI_REPRODUCIBILITY.md` |
| `AI_BASELINE.md` | `artifacts/AI_BASELINE.md` |
| `AI_FINAL_VALIDATION_REPORT.md` | `artifacts/AI_FINAL_VALIDATION_REPORT.md` |
| `AI_CONFUSION_MATRIX.csv` | `artifacts/AI_CONFUSION_MATRIX.csv` |
| `AI_SAFETY_REGRESSION.csv` | `artifacts/AI_SAFETY_REGRESSION.csv` |
| `AI_DIAGNOSIS_REGRESSION.csv` | `artifacts/AI_DIAGNOSIS_REGRESSION.csv` |
| `AI_PERFORMANCE_REPORT.md` | `artifacts/AI_PERFORMANCE_REPORT.md` |
| `AI_PERFORMANCE_DATA.csv` | `artifacts/AI_PERFORMANCE_DATA.csv` |
| Validation script | `scripts/ai_validate.py` |
| Classifier source | `duriancare-ai-service/app/services/disease_classifier.py` |

---

## Notes on Evidence Quality

| Evidence type | Notes |
|--------------|-------|
| Live end-to-end tests | Run against `duriancare/ai-service:local` on host Docker, 2026-09-16 |
| Classifier metrics | Offline benchmark — model evaluated on held-out set in training environment |
| ESP32 simulation | Programmatic via PIL/numpy — not real ESP32 hardware |
| Historical detection rate | From prior benchmark session; FNR may differ on new image sets |
| Model SHA256 | Verified: local file == Docker container == runtime-info API response |
