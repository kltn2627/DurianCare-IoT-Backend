# DurianCare Backend - Final Runtime Status

**Smoke test date:** 2026-07-11  
**Execution style:** developer-style startup checks from local repo  
**Java services:** `mvn -q -DskipTests spring-boot:run`  
**AI service:** `python -m uvicorn main:app`

## Infrastructure Started

The supporting containers were started successfully before service boot:

- PostgreSQL
- MongoDB
- Redis
- Kafka
- EMQX
- Elasticsearch

## Service-by-Service Startup Result

### 1) Config Server

✅ **Started successfully**

**Evidence**
- `Started ConfigServerApplication in 3.766 seconds`

**Notes**
- The service came up, but there were Eureka registration warnings while Eureka was not yet reachable during the smoke window.

---

### 2) Eureka / Discovery Server

✅ **Started successfully**

**Evidence**
- `Started DiscoveryServerApplication in 4.566 seconds`

---

### 3) API Gateway

✅ **Started successfully**

**Evidence**
- `Started DurianCareGatewayApplication in 5.415 seconds`

**Notes**
- Startup completed with a warning about renamed Redis property keys being mapped temporarily:
  - `spring.redis.host` -> `spring.data.redis.host`
  - `spring.redis.port` -> `spring.data.redis.port`
  - `spring.redis.password` -> `spring.data.redis.password`

---

### 4) Auth Service

❌ **Failed**

**Exact exception**
- `org.springframework.beans.factory.BeanCreationException`
- Root chain:
  - `BeanCreationException`
  - `FlywaySqlException`
  - `PSQLException`

**Root cause**
- PostgreSQL authentication failed because the datasource password resolved to empty during startup:
  - `The server requested SCRAM-based authentication, but no password was provided.`

**File / line / configuration involved**
- `duriancare-auth-service/src/main/resources/application.yml:10-12`
  - `spring.datasource.url`
  - `spring.datasource.username`
  - `spring.datasource.password`
- The failure occurred during application startup while Flyway tried to obtain the DB connection.

**Additional runtime note**
- The service also logged a config-server connection refusal earlier in startup, but that was not the crash cause.

---

### 5) Search Service

✅ **Started successfully**

**Evidence**
- `Started SearchServiceApplication in 4.987 seconds`

**Notes**
- Kafka consumer subscription initialized successfully for `search.document.upsert`.

---

### 6) Notification Service

❌ **Failed**

**Exact exception**
- `org.springframework.beans.factory.UnsatisfiedDependencyException`
- Root chain:
  - `UnsatisfiedDependencyException`
  - `BeanCreationException`
  - `MongoCommandException`

**Root cause**
- MongoDB authentication was required when Spring Data tried to create indexes through `MongoTemplate`:
  - `Command createIndexes requires authentication`

**File / line / configuration involved**
- `duriancare-notification-service/src/main/resources/application.yml:9-12`
  - `spring.data.mongodb.uri`
  - `spring.data.mongodb.auto-index-creation`
- The default URI in that file resolves to:
  - `mongodb://localhost:27017/duriancare_notification`
- That local URI did not include the authenticated MongoDB credentials used by the running container.

---

### 7) Cultivation Service

❌ **Failed**

**Exact exception**
- `org.springframework.beans.factory.BeanCreationException`
- Root chain:
  - `BeanCreationException`
  - `MongoTemplate` creation failure
  - `MongoCommandException`

**Root cause**
- MongoDB index creation required authentication, but the service connected with an unauthenticated local URI:
  - `Command createIndexes requires authentication`

**File / line / configuration involved**
- `duriancare-cultivation-service/src/main/resources/application.yml:9-12`
  - `spring.data.mongodb.uri`
  - `spring.data.mongodb.auto-index-creation`

---

### 8) Farm Service

❌ **Failed**

**Exact exception**
- `org.springframework.beans.factory.BeanCreationException`
- Root chain:
  - `BeanCreationException`
  - `MongoTemplate` creation failure
  - `MongoCommandException`

**Root cause**
- MongoDB authentication was required during index creation:
  - `Command createIndexes requires authentication`

**File / line / configuration involved**
- `duriancare-farm-service/src/main/resources/application.yml:9-12`
  - `spring.data.mongodb.uri`
  - `spring.data.mongodb.auto-index-creation`

---

### 9) Traceability Service

❌ **Failed**

**Exact exception**
- `org.springframework.beans.factory.BeanCreationException`
- Root chain:
  - `BeanCreationException`
  - `MongoTemplate` creation failure
  - `MongoCommandException`

**Root cause**
- MongoDB authentication was required during index creation:
  - `Command createIndexes requires authentication`

**File / line / configuration involved**
- `duriancare-traceability-service/src/main/resources/application.yml:9-12`
  - `spring.data.mongodb.uri`
  - `spring.data.mongodb.auto-index-creation`

---

### 10) AI Service

✅ **Started successfully**

**Evidence**
- `INFO: Application startup complete.`
- `INFO: Uvicorn running on http://127.0.0.1:8000`

**Runtime notes**
- The app started in a degraded mode:
  - disease model loading reported missing runtime dependencies
  - RAG initialization reported missing vector/RAG dependencies
  - S3 storage reported boto3 missing
- These warnings did **not** stop the FastAPI server from starting.

---

## Final Runtime Summary

- **Started successfully:** 5 services
  - Config Server
  - Eureka
  - Gateway
  - Search
  - AI

- **Failed:** 5 services
  - Auth
  - Notification
  - Cultivation
  - Farm
  - Traceability

## Main Failure Pattern

Two environment-related issues were responsible for the failed startups:

1. **Auth service**
   - PostgreSQL password was empty at runtime.

2. **Mongo-backed services**
   - The local MongoDB URI used during startup did not include credentials required by the authenticated MongoDB container.

## Conclusion

The runtime smoke test shows that the platform is **partially operational**:

- Core infrastructure boots correctly.
- Config, discovery, gateway, search, and AI can start.
- Auth and the Mongo-backed business services still need runtime credential alignment for a clean IntelliJ startup.

