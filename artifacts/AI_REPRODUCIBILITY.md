# DurianCare AI — Reproducibility Guide

**Date:** 2026-09-16  
**Branch:** duy/cleanup-fixes  

---

## One-Command Validation

From the repo root, with the AI service running:

```bash
python scripts/ai_validate.py
```

This single command runs the full validation suite:
- Safety regression (non-leaf rejection)
- Diagnosis regression (disease classification)
- ESP32 simulation (degraded-image robustness)
- Performance measurement (latency)
- Final summary with PASS/FAIL/SKIP counts

---

## Prerequisites

| Requirement | Check |
|-------------|-------|
| Docker running | `docker ps` |
| AI service healthy | `curl http://localhost:8080/api/v1/predict` via Gateway, or `curl http://localhost:8000/actuator/health` direct |
| Python 3.9+ on host | `python --version` |
| PIL/numpy (ESP32 sim only) | `pip install pillow numpy` |
| Test images available | `DurianCare-IoT-Mobile-App/assets/images/community/algal-leaf-spot.jpg` and `leaf-blight.jpg` |

ESP32 simulation tests require PIL and numpy. If not available, those 8 tests are skipped automatically and the remaining 14 tests still run.

---

## Starting the Services

```bash
# From repo root
docker compose -f infrastructure/docker-compose.yml up -d
```

Wait ~30 seconds for the AI service to load models (YOLO11x warmup on first request takes ~15–18 s).

---

## Running Individual Phases

```bash
# Safety regression only (fast, ~5–30 ms per test)
python scripts/ai_validate.py --phase safety

# Diagnosis regression only
python scripts/ai_validate.py --phase diagnosis

# Full suite with verbose output
python scripts/ai_validate.py --verbose
```

---

## Verifying Model Integrity

```bash
python scripts/ai_validate.py --integrity-check
```

Or directly:

```bash
python -c "
import hashlib, sys
files = {
    'detector':   'duriancare-ai-service/models/leaf_detector_best.pt',
    'classifier': 'duriancare-ai-service/models/mobilenetv2_classifier_high_acc.pth',
}
expected = {
    'detector':   'ac2bd7f82f9fd1e054f496a90f21ce77eb9697b27a71a4ecdf6d7c47ac71408b',
    'classifier': 'fc3dc5c92e43a9dbb196029fa56f9722ed91613d11ae7ccdadda1093fd1e5afe',
}
ok = True
for name, path in files.items():
    h = hashlib.sha256(open(path,'rb').read()).hexdigest()
    status = 'OK' if h == expected[name] else 'MISMATCH'
    print(f'{name}: {status}')
    if h != expected[name]: ok = False
sys.exit(0 if ok else 1)
"
```

---

## Expected Baseline Results

Run from commit `d77effc`, AI service healthy, models matching SHA256 above:

```
Safety regression:    12/12 PASS   FPR = 0%
Diagnosis regression:  2/2  PASS   (3/5 classes skipped — no images)
ESP32 simulation:      8/8  PASS   (SIMULATED — not real device)
Model integrity:       2/2  PASS
Latency avg:       ~2,181 ms
Latency median:    ~1,187 ms
Latency P95:       ~6,924 ms
```

Any result deviating from the above (new FAILs, model hash mismatches) must be investigated before considering the system regression-safe.

---

## Test Image Locations

| Image | Location | Resolution | Used for |
|-------|----------|-----------|---------|
| algal-leaf-spot.jpg | `DurianCare-IoT-Mobile-App/assets/images/community/` | 1080×810 | ALGAL_LEAF_SPOT end-to-end |
| leaf-blight.jpg | `DurianCare-IoT-Mobile-App/assets/images/community/` | 1080×810 | LEAF_BLIGHT end-to-end |
| Synthetic images | Generated at runtime in `%TEMP%/claude_benchmark_images/` | 320×240 | Non-leaf safety tests |

---

## Re-running Without PIL/numpy

The core safety + diagnosis regression (14 tests) runs with standard library only. ESP32 simulation (8 tests) requires PIL + numpy. If those are unavailable, a warning is printed and those tests are skipped.

---

## What the Script Does NOT Test

- ALLOCARIDARA_ATTACK, HEALTHY_LEAF, PHOMOPSIS_LEAF_SPOT end-to-end (no images available)
- Multi-leaf scenarios (no multi-leaf images available)
- Real ESP32-CAM hardware (simulated only)
- Gateway JWT auth path (smoke test handles this separately)
- Database, RAG, chat services (outside AI validation scope)
