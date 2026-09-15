# DurianCare AI — Exact Reproducible Baseline

**Date frozen:** 2026-09-16  
**Branch:** duy/cleanup-fixes  
**Commit:** d77effc16c3fd024ca55e428670c5f6f5dc01749  
**AI Version:** 0.3.0  

This document is the authoritative baseline record.  
**Do not modify model files without updating this document and re-running the full regression suite.**

---

## 1. Detector Model

| Field | Value |
|-------|-------|
| File | `duriancare-ai-service/models/leaf_detector_best.pt` |
| Container path | `/workspace/duriancare-ai-service/models/leaf_detector_best.pt` |
| Architecture | YOLOv11x (Ultralytics YOLO11) |
| Source | HuggingFace `pedromiguelsanchez/yolo-plant-leaf-detection` |
| License | MIT |
| Classes | 1 — `{0: 'leaf'}` (generic plant leaf, NOT durian-specific) |
| **SHA256** | `ac2bd7f82f9fd1e054f496a90f21ce77eb9697b27a71a4ecdf6d7c47ac71408b` |
| Size | ~114 MB |
| Note | Original durian-specific detector (SHA256 `afe27d65...`) is irrecoverably lost |

**Integrity verified:** Local host file SHA256 == Container runtime SHA256 == API `/api/v1/runtime-info` SHA256.

---

## 2. Disease Classifier Model

| Field | Value |
|-------|-------|
| File | `duriancare-ai-service/models/mobilenetv2_classifier_high_acc.pth` |
| Container path | `/workspace/duriancare-ai-service/models/mobilenetv2_classifier_high_acc.pth` |
| Architecture | MobileNetV2 (torchvision `models.mobilenet_v2`) |
| Head | `nn.Linear(in_features=1280, out_features=5)` |
| Training | 150 epochs, 2-phase: head-only (0–100), fine-tuning (100–150) |
| Test accuracy | 97.43% on n=1088 held-out test samples |
| **SHA256** | `fc3dc5c92e43a9dbb196029fa56f9722ed91613d11ae7ccdadda1093fd1e5afe` |

---

## 3. Disease Classes (Fixed Order — Must Not Change)

```python
CLASS_LABELS = (
    "ALGAL_LEAF_SPOT",       # index 0
    "ALLOCARIDARA_ATTACK",   # index 1
    "HEALTHY_LEAF",          # index 2
    "LEAF_BLIGHT",           # index 3
    "PHOMOPSIS_LEAF_SPOT",   # index 4
)
```

Changing this order silently produces wrong disease labels.

---

## 4. Preprocessing Configuration

| Parameter | Value | Source |
|-----------|-------|--------|
| Input resize | 224×224 | `resize_for_classifier()` |
| Normalize mean | `[0.485, 0.456, 0.406]` | ImageNet |
| Normalize std | `[0.229, 0.224, 0.225]` | ImageNet |
| TTA views | up to 4 | `build_classifier_views(mode="balanced", max_views=4)` |
| Temperature scaling | 1.0 | `AI_TEMPERATURE` env var (default 1.0) |
| Pipeline mode | balanced | `AI_PIPELINE_MODE` env var |

---

## 5. Safety & Validator Thresholds

| Threshold | Value | Env Var | Description |
|-----------|-------|---------|-------------|
| Green excess gate | 0.012 | `AI_MIN_IMAGE_GREEN_RATIO` | Pre-YOLO color check. `max(0, gm-(rm+bm)/2)/max(1,rm+gm+bm)*3 >= 0.012` |
| YOLO initial confidence | 0.25 | `YOLO_CONFIDENCE` | First-pass detection threshold |
| Minimum detection confidence | 0.20 | `AI_MIN_CLASSIFIER_CONFIDENCE` | Crop confidence to pass to classifier |
| Minimum crop area | 64×64 px | hardcoded | Reject tiny bboxes |
| Maximum crop area | 98% of image | hardcoded | Reject whole-image bboxes |
| Minimum texture energy | 1.5 | `AI_MIN_CROP_TEXTURE_ENERGY` | Laplacian energy on 32×32 thumbnail |
| Minimum prediction confidence | 0.0 | `AI_MIN_PREDICTION_CONFIDENCE` | Low-confidence flag threshold |

---

## 6. Rescue Pipeline Configuration

Multi-attempt detection (AdaptiveLeafPipeline) tries:
- 6 image variants: raw, clahe, gamma_0.85, denoise, contrast_stretch, brightness_norm
- 3 confidence levels: 0.25, 0.15, 0.10
- 2 IoU thresholds: 0.55, 0.35
- Total: up to ~36 YOLO calls per image before final failure

Fallback detector = primary detector (same `leaf_detector_best.pt`), preventing COCO download.

---

## 7. Runtime Environment

| Field | Value |
|-------|-------|
| Container image | `duriancare/ai-service:local` |
| Python | 3.12.14 |
| torch | 2.6.0+cpu |
| torchvision | 0.21.0+cpu |
| ultralytics | 8.3.102 |
| pillow | 11.1.0 |
| fastapi | 0.115.12 |
| uvicorn | 0.34.0 |
| Device | CPU (no GPU in runtime) |
| GPU available | No (verified via `torch.cuda.is_available() == False`) |
| Port | 8000 (host) |

---

## 8. Volume Mounts

```
DurianCare-IoT-Backend/ → /workspace:rw (live-bind; model files included)
duriancare_ai_vector_store → /workspace/duriancare-ai-service/vector_store:rw
duriancare_ai_artifacts   → /workspace/duriancare-ai-service/artifacts:rw
```

Because the repo is bind-mounted, the running container uses the exact files from the working tree. Editing files on the host immediately takes effect on the next request.

---

## 9. API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/v1/predict` | POST | Disease diagnosis (multipart, field `image`) |
| `/actuator/health` | GET | Service health and readiness |
| `/api/v1/runtime-info` | GET | Model SHA256, git commit, build time |

---

## 10. Build Timestamp (Current Container)

```
build_time: 2026-09-15T18:42:27.056609+00:00
git_commit: d77effc16c3fd024ca55e428670c5f6f5dc01749
```

---

## 11. Model Integrity Verification Command

```bash
# From repo root:
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
    print(f'{name}: {status}  ({h})')
    if h != expected[name]: ok = False
sys.exit(0 if ok else 1)
"
```
