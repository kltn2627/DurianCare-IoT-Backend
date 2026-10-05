# DurianCare AI — Graduation Baseline Freeze

**This AI configuration is frozen for graduation-demo purposes.**

**Freeze date:** 2026-09-16  
**Freeze commit:** d8a19cc (Phase 27 final hardening)  
**Branch:** duy/cleanup-fixes  
**AI Version:** 0.3.0

Do not modify model files, thresholds, CLASS_LABELS order, or the Docker environment without re-running the full regression suite and updating this document.

---

## Models

| Role | File | SHA256 |
|------|------|--------|
| Leaf detector | `duriancare-ai-service/models/leaf_detector_best.pt` | `ac2bd7f82f9fd1e054f496a90f21ce77eb9697b27a71a4ecdf6d7c47ac71408b` |
| Disease classifier | `duriancare-ai-service/models/mobilenetv2_classifier_high_acc.pth` | `fc3dc5c92e43a9dbb196029fa56f9722ed91613d11ae7ccdadda1093fd1e5afe` |

Container paths (bind-mounted from repo root):

```
/workspace/duriancare-ai-service/models/leaf_detector_best.pt
/workspace/duriancare-ai-service/models/mobilenetv2_classifier_high_acc.pth
```

---

## Runtime Environment

| Component | Version |
|-----------|---------|
| Python | 3.12.14 |
| torch | 2.6.0+cpu |
| torchvision | 0.21.0+cpu |
| ultralytics | 8.3.102 |
| pillow | 11.1.0 |
| fastapi | 0.115.12 |
| uvicorn | 0.34.0 |
| Device | CPU (no GPU) |
| Container image | `duriancare/ai-service:local` |
| Service port | 8000 |

---

## Key Thresholds (Do Not Change)

| Threshold | Value | Env var |
|-----------|-------|---------|
| Green excess gate | 0.012 | `AI_MIN_IMAGE_GREEN_RATIO` |
| YOLO initial confidence | 0.25 | `YOLO_CONFIDENCE` |
| Minimum crop confidence | 0.20 | `AI_MIN_CLASSIFIER_CONFIDENCE` |
| Minimum crop area | 64×64 px | hardcoded |
| Minimum texture energy | 1.5 | `AI_MIN_CROP_TEXTURE_ENERGY` |
| Temperature scaling | 1.0 | `AI_TEMPERATURE` |

---

## Disease Classes (Fixed Order — Do Not Change)

```python
CLASS_LABELS = (
    "ALGAL_LEAF_SPOT",       # 0
    "ALLOCARIDARA_ATTACK",   # 1
    "HEALTHY_LEAF",          # 2
    "LEAF_BLIGHT",           # 3
    "PHOMOPSIS_LEAF_SPOT",   # 4
)
```

---

## Required Environment Variables (No Secrets Listed)

```env
# Model paths (relative to service root — do not change for frozen config)
YOLO_MODEL_PATH=models/leaf_detector_best.pt
YOLO_FALLBACK_MODEL_PATH=models/leaf_detector_best.pt

# AI pipeline settings
ENABLE_YOLO_CROP=true
YOLO_CONFIDENCE=0.25
AI_TEMPERATURE=1.0
AI_MIN_IMAGE_GREEN_RATIO=0.012
AI_MIN_CLASSIFIER_CONFIDENCE=0.20
AI_MIN_CROP_TEXTURE_ENERGY=1.5

# Image size limit
MAX_IMAGE_SIZE_BYTES=10485760

# Capture detection failures for post-hoc analysis
AI_CAPTURE_HARD_EXAMPLES=true
```

Secrets (database passwords, API keys) are in `.env` files which are gitignored and NOT listed here.

---

## Volume Mounts

```
DurianCare-IoT-Backend/ → /workspace:rw
duriancare_ai_vector_store → /workspace/duriancare-ai-service/vector_store:rw
duriancare_ai_artifacts   → /workspace/duriancare-ai-service/artifacts:rw
```

---

## Frozen Validation Results (2026-09-16)

| Suite | Result |
|-------|--------|
| Safety regression | 12/12 PASS, FPR = 0% |
| Diagnosis regression | 2/2 PASS + 3 SKIP (no images) |
| ESP32 simulation | 8/8 PASS (simulated) |
| Failure behavior | 11/11 PASS |
| Model integrity | 2/2 PASS (local == container == runtime) |
| Final regression | 57/57 PASS, 3 SKIP |
| Smoke test | 22/22 PASS |
| Demo scenarios | 6/6 PASS |

**Quality gate: READY WITH KNOWN LIMITATIONS**

---

## Integrity Verification

```bash
python -c "
import hashlib
files = {
    'detector':   'duriancare-ai-service/models/leaf_detector_best.pt',
    'classifier': 'duriancare-ai-service/models/mobilenetv2_classifier_high_acc.pth',
}
expected = {
    'detector':   'ac2bd7f82f9fd1e054f496a90f21ce77eb9697b27a71a4ecdf6d7c47ac71408b',
    'classifier': 'fc3dc5c92e43a9dbb196029fa56f9722ed91613d11ae7ccdadda1093fd1e5afe',
}
for name, path in files.items():
    h = hashlib.sha256(open(path,'rb').read()).hexdigest()
    print(name, 'OK' if h == expected[name] else 'MISMATCH', h[:16]+'...')
"
```
