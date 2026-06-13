# DurianCare Persistence Bootstrap

## Database Ownership

| Service | Storage |
| --- | --- |
| Auth | MongoDB `duriancare_auth` |
| Farm | MongoDB `duriancare_farm` |
| Cultivation | MongoDB `duriancare_cultivation` |
| Traceability | MongoDB `duriancare_traceability` |
| Notification | Redis OTP + MongoDB `duriancare_notification` |
| Chat | MongoDB `duriancare_chat` |
| IoT | PostgreSQL schema `duriancare_iot` |
| AI RAG | Chroma local vector store |

MongoDB collections and indexes are managed by Spring Data MongoDB or
Mongoose. These services do not run Flyway.

## IoT PostgreSQL Bootstrap

The IoT schema is defined at:

```text
duriancare-iot-service/db/migration/V1__init_iot_schema.sql
```

Docker Compose mounts this file into PostgreSQL's
`/docker-entrypoint-initdb.d/` directory. PostgreSQL executes it only when a
new `postgres_data` volume is initialized.

For an existing environment, apply the SQL explicitly before deploying the
new IoT service. Do not remove a production volume merely to rerun bootstrap
scripts.
