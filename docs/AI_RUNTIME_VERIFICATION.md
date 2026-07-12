# AI Service Runtime Verification

Date: 2026-07-11  
Scope: `duriancare-ai-service` runtime verification only

## Verified Runtime Evidence

### 1. Docker startup

Command:

```powershell
docker compose --env-file .env -f infrastructure/docker-compose.yml up -d --build --force-recreate duriancare-ai-service
```

Result:

- Container built successfully.
- Container started successfully.
- Uvicorn reached `Application startup complete.`

### 2. Health endpoint

Command:

```powershell
docker exec duriancare-ai-service python -
```

Request performed inside the container:

```python
GET http://127.0.0.1:8000/actuator/health
```

Result:

- HTTP `200`
- `status = UP`
- `modelsLoaded = true`
- `databaseReady = true`
- `embeddingReady = true`
- `vectorStoreReady = true`
- `ragReady = true`
- `cacheReady = true`
- `cacheStatus = redis`
- `documentsIndexed = 23`

### 3. Prediction endpoint

Request performed inside the container:

```python
POST /api/v1/predict
```

Payload:

- Synthetic PNG image
- `source = MOBILE`

Result:

- HTTP `200`
- Response returned:
  - `predicted_disease = HEALTHY_LEAF`
  - `confidence = 36.38%`
  - uploaded image metadata
  - recommendation payload
  - decision support payload

### 4. Chat endpoint

Request performed inside the container:

```python
POST /api/v1/chat/ask
```

Result:

- HTTP `200`
- English query `Anthracnose` returned grounded knowledge from `anthracnose.md`
- Vietnamese query `Bệnh thán thư` returned grounded Vietnamese response
- Mixed export query returned export guidance in Vietnamese

### 5. Conversation memory follow-up

Request sequence performed inside the container:

1. `Anthracnose`
2. `Can I spray tomorrow?`

Result:

- The second question stayed on the Anthracnose disease context instead of falling back to the generic refusal path.
- Redis memory now stores disease context fields such as:
  - `mentioned_diseases`
  - `latest_diagnosis`
  - `latest_recommendation`
- A separate user key was also verified to remain isolated from the first conversation.

Status:

- `VERIFIED` as working
- Memory isolation between users was also verified

### 6. RAG admin endpoints

Requests performed inside the container:

- `GET /admin/rag/status`
- `GET /admin/rag/documents`
- `GET /admin/rag/statistics`
- `POST /admin/rag/reload`

Result:

- All returned HTTP `200`
- `POST /admin/rag/reload` returned:
  - `documentsScanned = 23`
  - `documentsChanged = 0`
  - `documentsRemoved = 0`

### 7. Redis cache verification

Evidence:

- Redis cache health changed from fallback memory to `cacheStatus = redis`
- Redis contains runtime cache keys matching `duriancare:rag:*`
- Repeated chat calls returned the same answer from cache path

Observed Redis key example:

- `duriancare:rag:memory:<hash>`

### 8. Gateway upload workflow

Request performed through the API Gateway:

```http
POST http://localhost:8080/api/v1/predict
```

Request details:

- `Authorization: Bearer <valid HS256 JWT signed with repository JWT_SECRET>`
- Multipart field `image` with a valid PNG file
- Multipart field `source=MOBILE`

Result:

- HTTP `200`
- Gateway forwarded the request successfully to the AI service
- Response returned:
  - `predicted_disease = HEALTHY_LEAF`
  - `confidence = 50.35%`
  - recommendation payload present

Status:

- `VERIFIED` as working
- This proves the backend Gateway upload path.
- The browser-side frontend upload flow was **not** exercised in this verification pass, so that broader end-to-end path remains `NOT VERIFIED`.

### 9. Benchmark snapshot

Measured inside container:

- Health endpoint: `7.06 ms`
- First chat request: `2815.17 ms`
- Cached chat request: `2.50 ms`
- Prediction request: `383.74 ms`

## Files Changed During Verification

- `duriancare-ai-service/.dockerignore`
  - removed `db` from ignore list so AI migration scripts are included in Docker image
- `infrastructure/docker-compose.yml`
  - added Redis env mapping for AI service so cache can use Docker Redis instead of `localhost`

## Not Verified / Remaining Risk

### Stress, failover, and observability validation

Status:

- `NOT VERIFIED` in this pass

Reason:

- This runtime pass proved the AI service, conversation memory, Redis-backed cache, and Gateway upload path.
- Dedicated stress tests, failover drills, and metrics/trace validation were not executed in this session.
- Browser-driven frontend upload integration was also not exercised in this pass.

## Conclusion

The AI service and Gateway backend upload path are now runtime-verified for:

- Docker startup
- Health endpoint
- Prediction endpoint
- Chat endpoint
- Conversation memory follow-up
- Memory isolation
- RAG admin endpoints
- Redis-backed cache
- API Gateway multipart upload forwarding

The remaining non-blocking items for a stricter stabilization pass are:

- Browser-driven frontend upload integration
- Stress testing
- Failover validation
- Observability review

All of the above are explicitly **NOT VERIFIED** in this session.
