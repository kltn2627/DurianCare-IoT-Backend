# CI/CD — DurianCare IoT Backend

Workflow: `.github/workflows/ci.yml` ("Continuous Integration").

## When it runs

- Pull requests into `dev` or `main` (the merge gate).
- Pushes to `dev` or `main` (re-checks the merged result).
- Manually via "Run workflow" (`workflow_dispatch`).
- Feature-branch pushes alone do not trigger runs; their PR does. Newer runs for the same PR/branch cancel older ones.
- Token permissions: `contents: read`. No secrets are used.

## What it checks

| Job | What | Not covered |
|---|---|---|
| `java-build` | JDK 21, `mvn clean test` for all modules in the parent POM | Integration with real Postgres/Mongo/Kafka/Elasticsearch |
| `iot-service` | Node 20, `npm ci`, `npm run check`, `npm test` incl. isolated-PostgreSQL tests (ephemeral service container) | Real MQTT broker, Kafka, ESP32 hardware |
| `chat-service` | Node 20, `npm ci`, `npm run check`, `npm run test:security` | — |
| `ai-service` | Python 3.12, unit tests with a minimal pinned subset of `requirements.txt` | Model inference (PyTorch/YOLO) and RAG with real models |
| `compose-validation` | `docker compose config` for `infrastructure/docker-compose.yml` | Starting the stack |
| `docker-build` | `docker build` (no push) for iot-service and chat-service | Java and AI images (built by Compose; heavy) |

Local results when this workflow was introduced (2026-10-19): Maven 345 tests (0 failures, 7 skipped) on JDK 21;
IoT 111/111 with PostgreSQL; chat 10/10; AI 26/26; Compose valid; both Docker builds OK.

## Suggested required checks (branch protection / ruleset)

Not configured by this change. For `dev` and `main`, require:
`Spring Boot modules (compile + unit tests)`, `IoT service (Node 20)`, `Chat service (Node 20)`,
`AI service unit tests (Python 3.12)`, `Validate Docker Compose`.

## CD (deployment) — not implemented

There is no deployment workflow. Nothing in CI pushes images, connects to servers, runs migrations or restarts containers.
To add a deployment workflow later, decide and provide first:

1. Target: server host/OS, how it runs the stack (Docker Compose), and who may deploy.
2. Image registry (e.g. GHCR) and naming/tagging (commit SHA + release tag).
3. GitHub Environment `production` with required reviewers (manual approval) and these secrets, never in YAML:
   SSH host/user/key or a deploy agent token, and the runtime env (`JWT_SECRET`, `DEFAULT_ADMIN_*`,
   database passwords, `CAMERA_DEVICE_BINDINGS`, mail and AWS settings).
4. Migration policy (Flyway runs at service start) and a backup/rollback procedure (previous image tag).
5. Trigger: `workflow_dispatch` (or release tag) only, gated by the environment approval.

Before the first real deployment, rotate the JWT secret and admin password: older commits contained defaults.
