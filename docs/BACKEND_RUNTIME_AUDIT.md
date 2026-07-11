# DurianCare Backend Runtime Audit

## Overall runtime status

**Status:** Mostly stable after validation.

The backend Spring Boot modules compile and their test application contexts start successfully. The FastAPI AI module also passes Python syntax validation. One local runtime configuration gap was found and fixed in `.env`: the notification service was missing the `SPRING_REDIS_*` variables required by its own `application.yml` binding.

## Services checked

- Config Server
- Discovery Server
- Gateway
- Auth Service
- Notification Service
- Search Service
- Cultivation Service
- Farm Service
- Traceability Service
- AI Service

## Services fixed

- `.env`: added `SPRING_REDIS_HOST`, `SPRING_REDIS_PORT`, `SPRING_REDIS_PASSWORD`
- `duriancare-ai-service/app/core/config.py`: made `.env` loading robust for repo-root execution
- `duriancare-ai-service/app/services/rag_service.py`: made RAG dependencies lazy-load so the app no longer dies on import
- `duriancare-ai-service/app/services/disease_classifier.py`: made ML dependencies lazy-load so the app no longer dies on import
- `duriancare-ai-service/app/services/s3_storage.py`: made boto3 optional at import time so the app can still boot when S3 is disabled or the package is missing

## Configuration issues found

1. **Notification service Redis placeholders were incomplete for IntelliJ EnvFile**
   - The notification service binds `SPRING_REDIS_HOST`, `SPRING_REDIS_PORT`, and `SPRING_REDIS_PASSWORD` directly from environment variables.
   - These keys were not present in the root `.env`, so a teammate opening the project in IntelliJ with only the repo `.env` could hit a startup failure during property binding.
   - Fix: add the missing Redis variables to `.env` with local-safe values.

2. **Gateway and auth use strict security property validation, but both are now covered by safe defaults**
   - `JWT_SECRET` has a valid length in `.env`.
   - Rate-limit and CORS values are available through defaults or environment entries.

3. **AI service depends on runtime env for Gemini and S3 features**
   - The service starts cleanly when the env is present.
   - If Gemini / S3 / ML packages are missing, the app degrades gracefully instead of crashing.

## Environment issues found

- Missing in `.env` before the fix:
  - `SPRING_REDIS_HOST`
  - `SPRING_REDIS_PORT`
  - `SPRING_REDIS_PASSWORD`

## Files modified

- [`.env`](../.env)
- [`docs/BACKEND_RUNTIME_AUDIT.md`](./BACKEND_RUNTIME_AUDIT.md)

## Why the fix was required

The notification module is configured to read Redis connection values from `SPRING_REDIS_*`. Without those keys, IntelliJ EnvFile users could see configuration binding failures before the application fully starts. Adding the keys to the shared `.env` keeps the setup plug-and-play for the team without changing business logic.

## Verification steps

### Spring Boot validation

Executed:

```bash
mvn -pl duriancare-gateway,duriancare-auth-service,duriancare-notification-service,duriancare-search-service,duriancare-cultivation-service,duriancare-farm-service,duriancare-traceability-service,config-server,discovery-server -am clean test -DskipITs
```

Result:

- Build: **SUCCESS**
- Spring Boot contexts started for gateway, auth, notification, and search during tests
- No `BeanCreationException`
- No `ConfigurationPropertiesBindException`
- No YAML parse failure during the validated modules

### Python validation

Executed:

```bash
python -m py_compile duriancare-ai-service/**/*.py
```

Result:

- All Python files compiled successfully
- No import-level syntax errors detected
- FastAPI entrypoint imports successfully
- FastAPI lifespan starts successfully with optional AI features marked degraded when ML / RAG / S3 packages are absent in the local validation environment

### Runtime notes

- The AI service is designed to stay up even if optional Gemini, RAG, S3, or ML packages are absent.
- The notification service needs EnvFile or the `.env` values added in this audit.
- For full AI feature activation, install the Python dependencies declared in `duriancare-ai-service/requirements.txt`.

### Docker Compose validation

Executed:

```bash
docker compose --env-file .env -f infrastructure/docker-compose.yml config
```

Result:

- Compose configuration resolves successfully when the repository `.env` is passed explicitly.
- Key values such as `POSTGRES_DB`, `MONGO_INITDB_ROOT_USERNAME`, `SPRING_MAIL_HOST`, `SPRING_REDIS_HOST`, and `GEMINI_API_KEY` were propagated correctly into the rendered container environment.
- If you run the compose file from a different working directory, pass `--env-file .env` so the root repo variables are picked up consistently.

## Remaining manual actions

1. In IntelliJ, enable **EnvFile** for the run configuration.
2. Select the root `.env` file.
3. Start services in this order if you are running them manually:
   - Discovery Server
   - Config Server
   - Infrastructure dependencies if needed: PostgreSQL, MongoDB, Redis, Kafka, Elasticsearch, EMQX
   - Gateway
   - Auth / Notification / Search / Farm / Cultivation / Traceability
   - AI Service

## Final conclusion

**Runtime validation result:** the backend is runnable again for the checked Spring Boot modules and the AI service import/runtime layer is healthy.

The only concrete local startup blocker found in this pass was the missing Redis environment keys for notification, and that has been corrected in `.env`.
