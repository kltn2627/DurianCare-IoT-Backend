# DurianCare IoT Backend

Base repository for the DurianCare microservice ecosystem.

## Services

| Service | Runtime | Default port | Responsibility |
| --- | --- | ---: | --- |
| `duriancare-gateway` | Spring Cloud Gateway | 8080 | Routing, CORS, JWT validation and token revocation checks |
| `discovery-server` | Eureka Server | 8761 | Spring service registration and discovery |
| `config-server` | Spring Cloud Config | 8888 | Centralized production configuration |
| `duriancare-auth-service` | Spring Boot | 8081 | Accounts and `FARMER`/`ENGINEER`/`CUSTOMER` authorization |
| `duriancare-farm-service` | Spring Boot | 8082 | Farm zones, durian trees and treatment schedules |
| `duriancare-cultivation-service` | Spring Boot | 8084 | Cultivation calendar, fertilizer/pesticide schedules and task status tracking |
| `duriancare-search-service` | Spring Boot | 8086 | Kafka-fed Elasticsearch read models |
| `duriancare-iot-service` | Node.js | 3001 | MQTT ingestion and climate telemetry |
| `duriancare-ai-service` | FastAPI | 8000 | Disease inference and RAG agricultural advice |
| `duriancare-chat-service` | NestJS | 3002 | Socket.io chat and treatment reminders |
| `duriancare-traceability-service` | Spring Boot | 8083 | Crop history packaging and dynamic QR generation |
| `duriancare-notification-service` | Spring Boot | 8085 | Redis-backed OTP, SMTP delivery and MongoDB notification history |

## Local infrastructure

Run the complete backend stack:

```bash
docker compose --env-file .env -f infrastructure/docker-compose.yml up -d --build
docker compose --env-file .env -f infrastructure/docker-compose.yml ps
```

This starts all twelve platform/application services plus PostgreSQL, MongoDB,
Redis, Elasticsearch, Kafka and EMQX. Compose injects Docker network hostnames;
no source-code configuration changes are required.

Business services use separate MongoDB databases. IoT telemetry is stored in
the PostgreSQL `duriancare_iot` schema.

To run services from IntelliJ instead, start only the infrastructure:

```bash
docker compose --env-file .env -f infrastructure/docker-compose.yml up -d postgres mongodb redis elasticsearch kafka emqx discovery-server config-server
```

Applications running on the host use the `localhost` defaults from their
`application.yml` or service `.env` files.

Infrastructure endpoints:

- PostgreSQL: `localhost:5432`
- MongoDB: `localhost:27017`
- Redis: `localhost:6379`
- Kafka: `localhost:9092`
- Elasticsearch: `http://localhost:9200`
- EMQX MQTT: `localhost:1883`
- EMQX MQTT WebSocket: `localhost:8084`
- EMQX dashboard: `http://localhost:18083`
- Cultivation API direct access: `http://localhost:18084`
- Eureka dashboard: `http://localhost:8761`
- Config Server: `http://localhost:8888`
- Search API direct access: `http://localhost:8086`
- API Gateway: `http://localhost:8080`

The AI service remains available for health checks if model loading fails, but
prediction endpoints return `503` until the required models are available.

### AI disease prediction

The AI service uses MobileNetV2 to classify images into five durian leaf
classes. Generic COCO-pretrained YOLO cropping is disabled by default because
COCO has no leaf class and incorrect crops reduce classification accuracy.
Set `ENABLE_YOLO_CROP=true` only when `YOLO_MODEL_PATH` points to a detector
fine-tuned for durian leaves.

```text
POST /api/v1/predict
Content-Type: multipart/form-data
Field name: image
Optional fields: source=MOBILE|WEB|IOT_CAMERA, device_id
```

Required local checkpoint:

```text
duriancare-ai-service/models/mobilenetv2_classifier_high_acc.pth
```

`yolov8m.pt` is downloaded automatically by Ultralytics on first startup.

Simulate an ESP32-CAM or camera gateway upload:

```bash
cd duriancare-ai-service
python scripts/send_camera_image.py path/to/leaf.jpg \
  --url http://localhost:8000/api/v1/predict \
  --device-id ESP32-CAM-DEMO-001
```

Camera images should be uploaded over HTTP. MQTT should carry the resulting
event metadata rather than the image binary.

### RAG agricultural chatbot

Place PDF, TXT or Markdown documents in:

```text
duriancare-ai-service/knowledge_base/
```

Configure `GEMINI_API_KEY` in the local `.env`. The service scans the knowledge
base at startup, splits documents into 1000-character chunks with 200-character
overlap, and persists a Chroma index in the ignored `vector_store/` directory.
The index is rebuilt only when source documents or embedding configuration
change.

```text
POST /api/v1/chat/ask
Content-Type: application/json

{"question": "Dấu hiệu và cách trị bệnh thối rễ sầu riêng là gì?"}
```

Optional environment variables:

```text
GEMINI_CHAT_MODEL=gemini-2.5-flash
GEMINI_EMBEDDING_MODEL=gemini-embedding-001
```

Missing credentials, an empty knowledge base, or an indexing failure does not
stop disease prediction; only the chat endpoint returns `503`.

### Evaluate the disease classifier

Use a held-out test set that was not used for training or model selection:

```text
test/
  ALGAL_LEAF_SPOT/
  ALLOCARIDARA_ATTACK/
  HEALTHY_LEAF/
  LEAF_BLIGHT/
  PHOMOPSIS_LEAF_SPOT/
```

Run:

```bash
cd duriancare-ai-service
python scripts/evaluate_classifier.py --data-dir path/to/test
```

The report includes accuracy, macro precision, macro recall, macro F1, metrics
for each class, and the confusion matrix. API confidence is the softmax score
for one image and must not be reported as model accuracy.

Evaluate the complete HTTP pipeline, including upload validation, YOLO and
MobileNetV2:

```bash
python scripts/evaluate_api.py \
  --data-dir path/to/test \
  --url http://localhost:8000/api/v1/predict \
  --limit-per-class 20
```

## Build

```bash
mvn clean verify
npm --prefix duriancare-iot-service install
npm --prefix duriancare-chat-service install
pip install -r duriancare-ai-service/requirements.txt
```
