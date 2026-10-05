# DurianCare AI — Troubleshooting Guide

**Date:** 2026-09-16  
**Scope:** Graduation demo and development environment only.

All recovery steps below are safe — they do not alter models, thresholds, or database data.

---

## Failure 1: Service Does Not Start

**SYMPTOM:** `docker ps` shows container not running, or health check times out.

**CAUSE:** Docker Desktop not running, or a port conflict on 8000.

**SAFE CHECK:**
```bash
docker ps -a | grep duriancare-ai
```
If the container shows `Exited (1)` or similar, read the exit reason:
```bash
docker logs duriancare-ai-service --tail 30
```

**SAFE FIX:**
1. Open Docker Desktop and wait for it to reach "Engine running" state.
2. Restart just the AI service:
```bash
docker compose -f infrastructure/docker-compose.yml restart duriancare-ai-service
```
3. Wait 30 seconds, then re-run the health check.
4. If port 8000 is occupied: find and stop the other process using port 8000, then restart.

---

## Failure 2: `detectorReady: false` or `diagnosisReady: false`

**SYMPTOM:** Health endpoint returns HTTP 200 but one or more ready flags are `false`.

**CAUSE:** Model file not found (wrong path), model file corrupted, or container mount failed.

**SAFE CHECK:**
```bash
# Verify model files exist on host
ls -la duriancare-ai-service/models/
# Should show both:
# leaf_detector_best.pt
# mobilenetv2_classifier_high_acc.pth

# Verify container can see them
docker exec duriancare-ai-service ls /workspace/duriancare-ai-service/models/
```

**SAFE FIX:**
1. If files are missing from host: restore from git (the `.pt` and `.pth` files are NOT in git — they are local only). Check backup locations.
2. If files exist but container cannot see them: the volume mount failed. Restart the compose stack:
```bash
docker compose -f infrastructure/docker-compose.yml down
docker compose -f infrastructure/docker-compose.yml up -d
```
3. Re-verify health after 45 seconds.

---

## Failure 3: Predict Returns HTTP 422 for a Real Leaf Image

**SYMPTOM:** You upload `algal-leaf-spot.jpg` but get HTTP 422 "No durian leaf detected."

**CAUSE A:** Image is being sent with wrong field name.

**SAFE CHECK:**
```bash
# Field name MUST be "image" — not "file" or "photo"
curl -X POST http://localhost:8080/api/v1/predict -F "image=@algal-leaf-spot.jpg"
```

**CAUSE B:** File path is wrong or file is corrupted.

**SAFE CHECK:**
```bash
# Verify the file exists and is non-zero
ls -la DurianCare-IoT-Mobile-App/assets/images/community/algal-leaf-spot.jpg
```

**CAUSE C:** YOLO detector is not warmed up and times out.

**SAFE FIX:** Send a warm-up request first (can take 15–18 s), then retry. The second request will succeed.

**DO NOT:** Remove the safety gates to force a 200 response. If the leaf image legitimately gets rejected, the image may not have sufficient green channel signal — try the original file directly.

---

## Failure 4: Very First Request Takes 17+ Seconds

**SYMPTOM:** The first predict request after service restart takes 15–18 seconds.

**CAUSE:** YOLO model cold start. PyTorch initializes on the first inference call, not at startup. This is expected behavior, not a bug.

**SAFE FIX:** This is not a failure. Send one warm-up request before the demo:
```bash
curl -X POST http://localhost:8000/api/v1/predict \
  -F "image=@DurianCare-IoT-Mobile-App/assets/images/community/algal-leaf-spot.jpg" \
  > /dev/null
```
Wait for it to complete. Subsequent calls will take ~4–5 seconds.

**DO NOT:** Increase timeout beyond 60 s expecting it to speed up; the cold-start time is fixed.

---

## Failure 5: Confidence Is Lower Than Expected

**SYMPTOM:** The model returns the correct disease but at lower confidence than the verified 48.59% / 78.07%.

**CAUSE A:** Image has been re-saved or re-compressed since the baseline run (JPEG compression changes pixel values).

**CAUSE B:** Different image orientation or slightly different crop.

**SAFE CHECK:** This is usually not a real failure. The disease label is correct even at lower confidence. Confidence will fluctuate slightly (±5%) with image variations.

**DO NOT:** Adjust the temperature parameter or thresholds to inflate confidence. The baseline is frozen.

---

## Failure 6: HTTP 500 Internal Server Error

**SYMPTOM:** Predict endpoint returns HTTP 500.

**CAUSE:** Unhandled exception in the service. Usually caused by a corrupt model file or an incompatible library version.

**SAFE CHECK:**
```bash
docker logs duriancare-ai-service --tail 50
```
Look for `ERROR`, `Exception`, or `Traceback` in the logs.

**SAFE FIX:**
1. If logs show a model loading error: verify SHA256 of model files:
```bash
python -c "
import hashlib
print(hashlib.sha256(open('duriancare-ai-service/models/leaf_detector_best.pt','rb').read()).hexdigest()[:16])
print(hashlib.sha256(open('duriancare-ai-service/models/mobilenetv2_classifier_high_acc.pth','rb').read()).hexdigest()[:16])
"
```
Expected prefixes: `ac2bd7f82f9fd1e0` and `fc3dc5c92e43a9db`.

2. If SHA256 does not match: model files are corrupted. Restore from backup.
3. If SHA256 matches: restart the container. If 500 persists, check if the Docker image needs to be rebuilt.

---

## Failure 7: Gateway Returns HTTP 503

**SYMPTOM:** Requests to port 8080 (gateway) fail with 503 Service Unavailable.

**CAUSE:** API gateway container is not running or cannot reach the AI service.

**SAFE CHECK:**
```bash
docker ps | grep gateway
docker ps | grep duriancare-ai
```

**SAFE FIX:**
```bash
docker compose -f infrastructure/docker-compose.yml restart
```
Wait 45 seconds. The gateway and AI service should both reach healthy state.

---

## Failure 8: `python scripts/ai_validate.py` Fails

**SYMPTOM:** Validation script exits with FAIL (not SKIP).

**SAFE CHECK:** Read the failure message carefully. The script names the specific test and expected vs. actual values.

**Common causes:**
| FAIL message | Likely cause |
|-------------|-------------|
| `health check failed` | AI service not running |
| `SHA256 mismatch` | Model file replaced or corrupted |
| `expected 422, got 200` | Safety gate removed (critical — investigate immediately) |
| `expected 200, got 422` | Test image missing or field name wrong |
| `connection refused` | Docker not running |

**DO NOT:** Modify the test to skip a failing case. The FAIL is telling you something real.

---

## What NOT to Do

- **DO NOT** change any threshold (green gate, YOLO confidence, crop area) to make a test pass.
- **DO NOT** remove the safety gates to get a 200 response for non-leaf images.
- **DO NOT** replace the model files with different weights.
- **DO NOT** disable `ENABLE_YOLO_CROP`.
- **DO NOT** run `docker compose down -v` — this destroys persistent volumes.
- **DO NOT** modify `CLASS_LABELS` order in `disease_classifier.py`.
