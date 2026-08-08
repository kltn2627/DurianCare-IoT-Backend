# RUNTIME DEPLOYMENT REPORT

Date: 2026-08-06
Repository: `DurianCare-IoT-Backend`
Service: `duriancare-ai-service`

## 1. Executive Result

Status: PASS

The running Docker AI container now matches the current repository state for:

- detector model hash
- repository code hash
- git commit exposure via runtime-info
- end-to-end prediction on the test image

The main runtime blocker was a deployment/config mismatch:

- the container had been running with `S3_ENABLED=true`
- `predict()` attempted a real S3 upload and failed with `Could not upload image to S3`
- the compose/runtime config was aligned back to `S3_ENABLED=false`
- after recreating the AI container, prediction succeeded

## 2. Runtime Verification

### 2.1 Runtime info endpoint

`GET /api/v1/runtime-info`

Returned:

- `git_commit`: `505610ffb4a7cee75ce4815fbbb4b99f070df693`
- `detector_model`: `/workspace/duriancare-ai-service/models/leaf_detector_best.pt`
- `detector_sha256`: `a6da7836c0ceba1fe6a9a4afb570274959c8ecc3afafe341f362ce00abddf3f0`
- `code_sha256`: `dfb0b7d59a1657b69e258115b6bcc028e9ccd1fed18b647a52df9330eefb4090`
- `build_time`: `2026-08-06T15:46:35.119381+00:00`
- `ai_version`: `0.3.0`

### 2.2 Startup logging

The AI service now prints runtime metadata during startup, including:

- git commit
- detector model path
- detector SHA256
- code SHA256
- device
- `ENABLE_YOLO_CROP`
- `AI_DEBUG`
- `YOLO_MODEL_PATH`

Verified in container logs after restart:

- `AI runtime info: {...}`
- `AI runtime assets: ...`

### 2.3 Host vs runtime hash comparison

Repository hashes:

- `duriancare-ai-service/app/api/predict.py`
  - `E2C6F144712D176FFA62CE273681AA2EFC72D69D6DD97237F897BB99E2C6817D`
- `duriancare-ai-service/app/services/disease_classifier.py`
  - `6843EC405990F9214CEB6A12DD15324676E323467A1088E9E2AC844BF80A2089`
- `duriancare-ai-service/app/services/leaf_pipeline.py`
  - `70DB547857514B2DDDCE76E3B74EBB9CD342B0AAE939E8CAE4E71C460FF744D4`
- `duriancare-ai-service/models/leaf_detector_best.pt`
  - `A6DA7836C0CEBA1FE6A9A4AFB570274959C8ECC3AFAFE341F362CE00ABDDF3F0`

Runtime hashes inside the rebuilt Docker container:

- `predict.py`
  - `E2C6F144712D176FFA62CE273681AA2EFC72D69D6DD97237F897BB99E2C6817D`
- `disease_classifier.py`
  - `6843EC405990F9214CEB6A12DD15324676E323467A1088E9E2AC844BF80A2089`
- `leaf_pipeline.py`
  - `70DB547857514B2DDDCE76E3B74EBB9CD342B0AAE939E8CAE4E71C460FF744D4`
- `leaf_detector_best.pt`
  - `A6DA7836C0CEBA1FE6A9A4AFB570274959C8ECC3AFAFE341F362CE00ABDDF3F0`

Result:

- Repository detector hash == runtime detector hash: PASS
- Repository code hash == runtime code hash: PASS

### 2.4 Docker runtime identifiers

- Container ID: `f63546af965609728a3f9123f930b5a46f688ed1011ecca31d2672e464c94a68`
- Image ID: `sha256:23d414b29a0bcc4acf1d82215b50a613309c148f9257a37571519121b5ab936f`
- Image tag: `duriancare/ai-service:local`

## 3. Root Cause

### 3.1 Old runtime mismatch

The previously running container was stale and did not match the repository:

- runtime detector hash differed from repository
- runtime source files differed from repository
- runtime container had stale deployment state

### 3.2 S3 upload failure

The container was also running with `S3_ENABLED=true` from `infrastructure/.env`, which caused:

- a real S3 upload attempt
- `Could not upload image to S3`
- prediction failure for `/api/v1/predict`

After changing the deployment config back to `S3_ENABLED=false` and recreating the container, prediction succeeded.

## 4. Files Changed

- `duriancare-ai-service/main.py`
  - added startup/runtime-info logging
  - added `/api/v1/runtime-info`
  - added runtime hash reporting
- `infrastructure/docker-compose.yml`
  - updated AI service deployment/runtime wiring
  - mounted current repository into the AI container
  - ensured runtime uses the current source tree
- `infrastructure/.env`
  - aligned S3 runtime configuration back to `S3_ENABLED=false`

## 5. Runtime Evidence

### 5.1 Prediction replay

Test image:

- `C:\Users\minhd\OneDrive\Desktop\Test Fail\sau-rieng-bi-dom-la.jpg`

Result:

```json
{
  "status": "success",
  "data": {
    "predictedDisease": "PHOMOPSIS_LEAF_SPOT",
    "confidence": "74.43%",
    "usedDetectionCrop": true,
    "boundingBox": {
      "left": 173,
      "top": 68,
      "right": 307,
      "bottom": 428
    }
  }
}
```

### 5.2 Runtime info

The container returned the expected runtime identity and hashes from `/api/v1/runtime-info`.

## 6. Verification PASS/FAIL

- Runtime uses latest repository code: PASS
- Runtime uses latest detector: PASS
- Runtime-info endpoint available: PASS
- Startup runtime metadata printed: PASS
- Prediction replay on test image: PASS
- S3-related runtime blocker resolved: PASS

## 7. Final Conclusion

Docker runtime now matches the current repository and the AI service successfully processes the test image after the deployment/runtime fix.

