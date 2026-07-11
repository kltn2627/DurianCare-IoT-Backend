# IntelliJ Runtime Fix for DurianCare

## 1. Root Cause of the Docker vs IntelliJ Difference

- **Docker Compose worked** because it injects environment variables into each container at runtime.
- **IntelliJ failed** because Spring Boot does **not** automatically read the repository `.env` file.
- Several services were depending on placeholders that only existed in Docker container env, so IntelliJ fell back to empty or unauthenticated defaults.
- One additional issue existed only in local development:
  - `duriancare-cultivation-service` used port `8084`, which conflicts with the EMQX dashboard port exposed by Docker Compose.

## 2. Files Modified

### Root env

- [`.env`](C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DurianCare-IoT-Backend\.env)

### Spring Boot runtime config

- [`config-server/src/main/resources/application.yml`](C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DurianCare-IoT-Backend\config-server\src\main\resources\application.yml)
- [`config-server/src/main/resources/config/duriancare-cultivation-service.yml`](C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DurianCare-IoT-Backend\config-server\src\main\resources\config\duriancare-cultivation-service.yml)
- [`config-server/src/main/resources/config/duriancare-farm-service.yml`](C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DurianCare-IoT-Backend\config-server\src\main\resources\config\duriancare-farm-service.yml)
- [`config-server/src/main/resources/config/duriancare-notification-service.yml`](C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DurianCare-IoT-Backend\config-server\src\main\resources\config\duriancare-notification-service.yml)
- [`config-server/src/main/resources/config/duriancare-traceability-service.yml`](C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DurianCare-IoT-Backend\config-server\src\main\resources\config\duriancare-traceability-service.yml)
- [`duriancare-gateway/src/main/resources/application.yml`](C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DurianCare-IoT-Backend\duriancare-gateway\src\main\resources\application.yml)
- [`duriancare-auth-service/src/main/resources/application.yml`](C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DurianCare-IoT-Backend\duriancare-auth-service\src\main\resources\application.yml)
- [`duriancare-search-service/src/main/resources/application.yml`](C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DurianCare-IoT-Backend\duriancare-search-service\src\main\resources\application.yml)
- [`duriancare-notification-service/src/main/resources/application.yml`](C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DurianCare-IoT-Backend\duriancare-notification-service\src\main\resources\application.yml)
- [`duriancare-cultivation-service/src/main/resources/application.yml`](C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DurianCare-IoT-Backend\duriancare-cultivation-service\src\main\resources\application.yml)
- [`duriancare-farm-service/src/main/resources/application.yml`](C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DurianCare-IoT-Backend\duriancare-farm-service\src\main\resources\application.yml)
- [`duriancare-traceability-service/src/main/resources/application.yml`](C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DurianCare-IoT-Backend\duriancare-traceability-service\src\main\resources\application.yml)

## 3. Configuration Changes Made

### Shared `.env` loading

- Added `spring.config.import` to the Spring Boot startup configs so services can read:
  - `./.env` from the repository root
  - `../.env` when the service is launched from its module folder
- This keeps IntelliJ and Docker aligned on the same value source.

### Auth service

- Added missing local-development datasource keys to `.env`:
  - `SPRING_DATASOURCE_URL`
  - `SPRING_DATASOURCE_USERNAME`
  - `SPRING_DATASOURCE_PASSWORD`

### Mongo-backed services

- Added service-specific MongoDB URI variables to `.env`:
  - `FARM_MONGODB_URI`
  - `CULTIVATION_MONGODB_URI`
  - `TRACEABILITY_MONGODB_URI`
  - `NOTIFICATION_MONGODB_URI`
- Updated the matching service configs and config-server copies to prefer the service-specific URI first, then fall back to the Docker-provided `MONGODB_URI`.

### Cultivation port alignment

- Changed the **local default** cultivation port to `18084`.
- Docker Compose already maps cultivation through `18084:8084`, so this avoids the local conflict with EMQX on port `8084`.

## 4. Why Auth Could Not Read `POSTGRES_PASSWORD`

- The Auth service was not failing because PostgreSQL lacked a password.
- It failed because IntelliJ was starting Spring without the repository `.env` loaded.
- As a result, `SPRING_DATASOURCE_PASSWORD` resolved to an empty value.
- Flyway then tried to connect to PostgreSQL and PostgreSQL rejected the connection with SCRAM authentication because no password was supplied.

## 5. Why Mongo Services Ignored Credentials

- The Mongo services were using a URI-based configuration.
- Docker Compose injected a full authenticated `MONGODB_URI` per service.
- IntelliJ did not inject that runtime value, so Spring fell back to the default local URI, which had no credentials.
- The fix was:
  - load `.env` in Spring startup
  - provide service-specific MongoDB URIs in `.env`
  - let the service prefer the explicit service URI, with Docker's `MONGODB_URI` as fallback

## 6. Startup Instructions for IntelliJ

1. Clone the repository.
2. Open the backend repo in IntelliJ.
3. Use the normal Spring Boot run configuration for each service.
4. No manual copy-paste of secret values is required.
5. Run services in this order:
   - Config Server
   - Eureka
   - Auth
   - Farm
   - Cultivation
   - Traceability
   - Notification
   - Search
   - Gateway
   - AI

## 7. Confirmation That Docker and IntelliJ Now Use the Same Configuration Model

- Docker Compose still resolves the same `.env` values and the compose model remains valid.
- IntelliJ now also resolves the same `.env` file directly through Spring config import.
- The two runtimes are now aligned on the same source of truth:
  - repository `.env`
  - Spring placeholder resolution
  - Docker Compose interpolation

## 8. Property Source Verification

Verified by starting each Spring Boot service with `spring-boot:run` and exposing the Actuator `env` endpoint on a temporary local port.

### Exact property source for critical configuration

| Service | `SPRING_DATASOURCE_PASSWORD` | `JWT_SECRET` | `MAIL_ENABLED` | `MONGODB_URI` |
|---|---|---|---|---|
| `config-server` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | Not present |
| `discovery-server` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | Not present |
| `duriancare-gateway` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | Not present |
| `duriancare-auth-service` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | Not present |
| `duriancare-farm-service` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | Not present; `FARM_MONGODB_URI` comes from the same `.env` source |
| `duriancare-cultivation-service` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | Not present; `CULTIVATION_MONGODB_URI` comes from the same `.env` source |
| `duriancare-traceability-service` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | Not present; `TRACEABILITY_MONGODB_URI` comes from the same `.env` source |
| `duriancare-notification-service` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | Not present; `NOTIFICATION_MONGODB_URI` comes from the same `.env` source |
| `duriancare-search-service` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | `Config resource 'file [..\.env]' via location 'optional:file:../.env[.properties]'` | Not present |

### Interpretation

- `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, and `MAIL_ENABLED` are resolved from the repository `.env` for every Spring Boot service verified.
- `MONGODB_URI` is **not** defined as a generic repository variable. The Mongo services intentionally use service-specific keys:
  - `FARM_MONGODB_URI`
  - `CULTIVATION_MONGODB_URI`
  - `TRACEABILITY_MONGODB_URI`
  - `NOTIFICATION_MONGODB_URI`
- Those service-specific keys are also loaded from the repository `.env`.
- The resolved Mongo binding in each service comes from the corresponding config-server file, which points to the service-specific environment key.

## 9. Validation Performed

- Confirmed startup logs for:
  - Config Server
  - Eureka
  - Gateway
  - Auth
  - Search
  - Notification
  - Farm
  - Cultivation
  - Traceability
  - AI
- Confirmed Docker Compose config still validates successfully:
  - `docker compose --env-file .env -f infrastructure/docker-compose.yml config`

## 10. Notes

- The AI service still logs non-blocking warnings when optional model/RAG/S3 dependencies are not installed in the local Python environment, but the application reaches startup successfully.
- No business logic was changed.
- No security was weakened.
