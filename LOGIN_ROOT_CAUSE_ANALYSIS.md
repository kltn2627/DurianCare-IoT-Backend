# Login HTTP 500 Root Cause Analysis

## Root cause

The login failure was caused by the Gateway using a stale service-discovery route during local IntelliJ runtime.

Evidence from the gateway runtime log showed the route resolving Auth Service to an unreachable LAN address:

`ConnectTimeoutException: connection timed out after 5000 ms: /192.168.1.12:8081`

That address was advertised by Eureka, but the Auth Service was only reachable on `localhost:8081` in this development runtime.

## Affected files

- `config-server/src/main/resources/config/application.yml`
- `duriancare-gateway/src/main/resources/application.yml`
- `infrastructure/docker-compose.yml`

## Fix applied

1. Local development now defaults to localhost-based routing in the Gateway:
   - `auth-service -> http://localhost:8081`
   - `farm-service -> http://localhost:8082`
   - `cultivation-service -> http://localhost:8084`
   - `traceability-service -> http://localhost:8083`
   - `notification-service -> http://localhost:8085`
   - `search-service -> http://localhost:8086`

2. Docker Compose keeps container-to-container routing explicitly:
   - `AUTH_SERVICE_URL=http://duriancare-auth-service:8081`
   - `FARM_SERVICE_URL=http://duriancare-farm-service:8082`
   - `CULTIVATION_SERVICE_URL=http://duriancare-cultivation-service:8084`
   - `TRACEABILITY_SERVICE_URL=http://duriancare-traceability-service:8083`
   - `NOTIFICATION_SERVICE_URL=http://duriancare-notification-service:8085`
   - `SEARCH_SERVICE_URL=http://duriancare-search-service:8086`

3. Eureka registration is now configurable so local runtime can avoid advertising an unreachable IP:
   - `EUREKA_PREFER_IP_ADDRESS=false` by default
   - `EUREKA_INSTANCE_HOSTNAME=localhost` by default
   - Docker Compose overrides `EUREKA_PREFER_IP_ADDRESS=true`

## Runtime evidence

### Broken runtime evidence before the fix

- Gateway log:
  - `ConnectTimeoutException: connection timed out after 5000 ms: /192.168.1.12:8081`
- Gateway response before fix:
  - `POST /api/auth/login -> HTTP 500`

### Verified runtime after the fix

- Auth Service direct:
  - `POST http://localhost:8081/api/auth/login -> HTTP 200`
- Gateway local runtime:
  - `POST http://localhost:8080/api/auth/login -> HTTP 200`
  - `GET http://localhost:8080/api/users/me -> HTTP 200`
- Gateway also worked with a raw JSON file payload over curl:
  - `POST http://localhost:8080/api/auth/login -> HTTP 200`

## Stack trace / exception

The gateway error chain was:

`Spring Cloud Gateway -> Eureka load-balanced resolution -> connect timeout to /192.168.1.12:8081`

The exception seen in runtime logs was:

`io.netty.channel.ConnectTimeoutException: connection timed out after 5000 ms: /192.168.1.12:8081`

## Build result

- Gateway build passed:
  - `mvn -pl duriancare-gateway -DskipTests package`

## Test result

- Login:
  - `POST /api/auth/login` returned `200`
- Current user:
  - `GET /api/users/me` returned `200`
- No API contract changes were required.

## Regression risk

- Low for local development.
- Docker runtime remains supported through environment overrides in `docker-compose.yml`.
- The gateway no longer depends on an unreachable LAN IP in IntelliJ runtime.

## Final conclusion

The HTTP 500 was caused by stale service-discovery routing in local runtime, not by the Auth login logic itself. After the routing and Eureka registration defaults were corrected, login and authenticated requests succeed through the Gateway.
