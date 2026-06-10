# DurianCare Database Migrations

## Migration Files

| Service | Runtime | Migration path |
| --- | --- | --- |
| Auth | Spring Boot | `duriancare-auth-service/src/main/resources/db/migration/V1__init_auth_schema.sql` |
| Farm | Spring Boot | `duriancare-farm-service/src/main/resources/db/migration/V1__init_farm_schema.sql` |
| Treatment and Traceability | Spring Boot | `duriancare-traceability-service/src/main/resources/db/migration/V1__init_treatment_traceability_schema.sql` |
| AI Disease | FastAPI | `duriancare-ai-service/db/migration/V1__init_ai_disease_schema.sql` |
| IoT | Node.js | `duriancare-iot-service/db/migration/V1__init_iot_schema.sql` |
| Chat and Notification | NestJS | `duriancare-chat-service/db/migration/V1__init_chat_notification_schema.sql` |
| Community | Not scaffolded yet | `duriancare-community-service/db/migration/V1__init_community_schema.sql` |

## Spring Boot

Spring Boot automatically discovers SQL files from:

```text
src/main/resources/db/migration/
```

Auth, Farm and Traceability already include `flyway-core`,
`flyway-database-postgresql`, and service-specific Flyway configuration.

## Node.js And FastAPI

These services are not Maven modules, so Spring Boot cannot discover their
migrations. Run their `db/migration` directories with Flyway CLI or a Flyway
container as a deployment step before starting the application.

Example:

```bash
flyway \
  -url=jdbc:postgresql://localhost:5432/duriancare_iot \
  -user=duriancare_iot \
  -password=change-me \
  -locations=filesystem:duriancare-iot-service/db/migration \
  migrate
```

Use a separate database and credential for every service in production.
Cross-service UUID columns intentionally have no foreign-key constraints.

## Versioning Rule

Never modify a migration after it has been applied to a shared environment.
Create `V2__...sql`, `V3__...sql`, and later versions for subsequent changes.
