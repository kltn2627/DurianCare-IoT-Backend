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

```bash
docker compose up -d
```

Infrastructure endpoints:

- PostgreSQL: `localhost:5432`
- MongoDB: `localhost:27017`
- Redis: `localhost:6379`
- Kafka: `localhost:9092`
- EMQX MQTT: `localhost:1883`
- EMQX dashboard: `http://localhost:18083`

## Build

```bash
mvn clean verify
npm --prefix duriancare-iot-service install
npm --prefix duriancare-chat-service install
pip install -r duriancare-ai-service/requirements.txt
```
