# DurianCare Service Boundaries

## Platform

| Module | Runtime | Port | Persistence | Responsibility |
| --- | --- | ---: | --- | --- |
| `duriancare-gateway` | Spring Cloud Gateway | 8080 | Redis blacklist | External routing and JWT validation |
| `discovery-server` | Eureka Server | 8761 | None | Java service registration and discovery |
| `config-server` | Spring Cloud Config | 8888 | Native config repository | Centralized Spring configuration |
| `duriancare-auth-service` | Spring Boot | 8081 | MongoDB `duriancare_auth`, Redis | Accounts, roles, tokens and connections |
| `duriancare-farm-service` | Spring Boot | 8082 | MongoDB `duriancare_farm` | Farms, zones, trees and engineer authorization |
| `duriancare-traceability-service` | Spring Boot | 8083 | MongoDB `duriancare_traceability` | Treatment history and public QR snapshots |
| `duriancare-cultivation-service` | Spring Boot | 8084 | MongoDB `duriancare_cultivation` | Cultivation schedules and treatment execution |
| `duriancare-notification-service` | Spring Boot | 8085 | MongoDB `duriancare_notification`, Redis | Delivery history, OTP and alerts |
| `duriancare-search-service` | Spring Boot | 8086 | Elasticsearch | Kafka-fed search read models |
| `duriancare-ai-service` | FastAPI | 8000 | Chroma local volume | Image inference and RAG |
| `duriancare-iot-service` | Express | 3001 | PostgreSQL schema `duriancare_iot` | MQTT telemetry ingestion |
| `duriancare-chat-service` | NestJS | 3002 | MongoDB `duriancare_chat` | Socket.IO `/chat` and message persistence |

## Spring Package Contract

New Spring features use:

```text
controller/
dto/
service/
service/impl/
entity/
repository/
config/
event/publisher/
event/listener/
```

Existing MongoDB documents under `domain/` remain valid compatibility code.
Do not create direct database joins across services and do not import another
service's entity classes.

## Event Contract

- IoT publishes `iot.telemetry.received` after PostgreSQL commit.
- Business services publish immutable state-change events after their local
  MongoDB operation succeeds.
- Search consumes `search.document.upsert` and owns Elasticsearch indexes.
- Notification consumes alert/reminder events and owns delivery history.
- Traceability consumes IoT, disease and treatment events to build snapshots.
- Kafka payloads contain external IDs only. Consumers must be idempotent.
