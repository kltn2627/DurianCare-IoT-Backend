# DurianCare AI — Demo Cheat Sheet

**Print and keep at hand during the graduation demo.**  
**Date:** 2026-09-16

---

## 1. Start the AI Service

```bash
docker compose -f infrastructure/docker-compose.yml up -d duriancare-ai-service
```

Wait ~30 seconds, then verify health.

---

## 2. Verify Health

```bash
curl http://localhost:8000/actuator/health
```

Expected (all four fields must be true):
```json
{
  "status": "UP",
  "detectorReady": true,
  "diagnosisReady": true,
  "classifierLoaded": true
}
```

If any field is false — do NOT proceed with the demo. See Troubleshooting.

---

## 3. Verify Model Integrity (Optional but Recommended)

Run from repo root:

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
    print(name, 'OK' if h == expected[name] else 'MISMATCH')
"
```

Expected: `detector OK` and `classifier OK`.

---

## 4. Run Full Validation Suite

```bash
python scripts/ai_validate.py
```

Expected: **25 PASS, 3 SKIP, 0 FAIL** (3 SKIPs are expected — missing disease images).

---

## 5. Demo Images

Located at:
```
DurianCare-IoT-Mobile-App/assets/images/community/
```

| File | Expected result | Confidence |
|------|----------------|------------|
| `algal-leaf-spot.jpg` | ALGAL_LEAF_SPOT (HTTP 200) | ~48.59% |
| `leaf-blight.jpg` | LEAF_BLIGHT (HTTP 200) | ~78.07% |

**No images available** for: ALLOCARIDARA_ATTACK, HEALTHY_LEAF, PHOMOPSIS_LEAF_SPOT.

---

## 6. Manual Predict Request (via Gateway, port 8080)

```bash
curl -X POST http://localhost:8080/api/v1/predict \
  -F "image=@path/to/algal-leaf-spot.jpg"
```

Response location: `data.predictedDisease`, `data.confidence`

Expected:
```json
{
  "data": {
    "predictedDisease": "ALGAL_LEAF_SPOT",
    "confidence": 0.4859
  }
}
```

Field name is **`image`** — not `file`, not `photo`.

---

## 7. Expected Demo Results (All Verified 2026-09-16)

| Input | HTTP | Disease | Confidence | Latency |
|-------|------|---------|------------|---------|
| algal-leaf-spot.jpg | 200 | ALGAL_LEAF_SPOT | 48.59% | ~4,700 ms |
| leaf-blight.jpg | 200 | LEAF_BLIGHT | 78.07% | ~4,600 ms |
| Solid red image | 422 | None | — | ~5 ms |
| Green noise | 422 | None | — | ~2,400 ms |
| Empty upload | 400 | None | — | ~4 ms |
| ESP32 sim (320×240) | 200 | ALGAL_LEAF_SPOT | 63.47% | ~1,200 ms |

**First request after cold start takes 15–18 seconds.** This is normal. Make a warm-up call before the demo.

---

## 8. Warm Up Before Demo

Send one dummy request before the presentation starts to load YOLO into memory:

```bash
curl -X POST http://localhost:8080/api/v1/predict \
  -F "image=@path/to/algal-leaf-spot.jpg" > /dev/null
```

Subsequent calls will take ~4–5 seconds instead of 17.

---

## 9. Quick Recovery Commands

| Symptom | Command |
|---------|---------|
| AI service not responding | `docker compose -f infrastructure/docker-compose.yml restart duriancare-ai-service` |
| Gateway not responding | `docker compose -f infrastructure/docker-compose.yml restart` |
| Check all services | `docker ps` |
| View AI logs | `docker logs duriancare-ai-service --tail 50` |

---

## 10. Key Claims for Thesis Defense

- Classifier accuracy: **97.43%** (n=1,088, classifier isolation — say this explicitly)
- End-to-end verified: **2 of 5** disease classes
- Safety false-positive rate: **0%** (25+ non-leaf tests)
- ESP32 robustness: **simulated** (not real hardware)
- Quality gate: **READY WITH KNOWN LIMITATIONS**
- Detector: **generic plant-leaf** (YOLO11x, not durian-specific)
