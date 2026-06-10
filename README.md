# DurianCare IoT Backend

Base repository for the DurianCare microservice ecosystem.

## Services

| Service | Runtime | Default port | Responsibility |
| --- | --- | ---: | --- |
| `duriancare-gateway` | Spring Cloud Gateway | 8080 | Routing, CORS, JWT validation and token revocation checks |
| `duriancare-auth-service` | Spring Boot | 8081 | Accounts and `OWNER`/`ENGINEER` authorization |
| `duriancare-farm-service` | Spring Boot | 8082 | Farm zones, durian trees and treatment schedules |
| `duriancare-cultivation-service` | Spring Boot | 8085 | Cultivation calendar, fertilizer/pesticide schedules and task status tracking |
| `duriancare-iot-service` | Node.js | 3001 | MQTT ingestion and climate telemetry |
| `duriancare-ai-service` | FastAPI | 8000 | YOLOv8 disease inference endpoint |
| `duriancare-chat-service` | NestJS | 3002 | Socket.io chat and treatment reminders |
| `duriancare-traceability-service` | Spring Boot | 8083 | Crop history packaging and dynamic QR generation |

## Local infrastructure

Run the complete backend stack:

```bash
docker compose --project-directory . -f infrastructure/docker-compose.yml up -d --build
docker compose --project-directory . -f infrastructure/docker-compose.yml ps
```

This starts all seven application services plus PostgreSQL, MongoDB, Redis,
Kafka and EMQX. Compose injects Docker network hostnames such as `postgres`,
`redis` and `kafka`; no source-code configuration changes are required.

To run services from IntelliJ instead, start only the infrastructure:

```bash
docker compose --project-directory . -f infrastructure/docker-compose.yml up -d postgres mongodb redis kafka emqx
```

Applications running on the host use the `localhost` defaults from their
`application.yml` or service `.env` files.

Infrastructure endpoints:

- PostgreSQL: `localhost:5432`
- MongoDB: `localhost:27017`
- Redis: `localhost:6379`
- Kafka: `localhost:9092`
- EMQX MQTT: `localhost:1883`
- EMQX dashboard: `http://localhost:18083`
- API Gateway: `http://localhost:8080`

The AI service starts without model weights, but inference returns `503` until
`duriancare-ai-service/models/durian-disease.pt` is provided.

## Build

```bash
mvn clean verify
npm --prefix duriancare-iot-service install
npm --prefix duriancare-chat-service install
pip install -r duriancare-ai-service/requirements.txt
```
