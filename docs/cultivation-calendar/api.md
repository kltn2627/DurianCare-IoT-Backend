# Cultivation Calendar API

Base endpoints are exposed by `duriancare-cultivation-service`.

## Plans And Activities

- `POST /api/v1/cultivation-plans`
- `GET /api/v1/cultivation-plans/{id}`
- `GET /api/v1/cultivation-plans/{id}/calendar`
- `POST /api/v1/cultivation-activities`
- `GET /api/v1/cultivation-activities`
- `GET /api/v1/cultivation-activities/{id}`
- `PUT /api/v1/cultivation-activities/{id}`
- `PATCH /api/v1/cultivation-activities/{id}`
- `POST /api/v1/cultivation-activities/{id}/approve`
- `POST /api/v1/cultivation-activities/{id}/reject`
- `POST /api/v1/cultivation-activities/{id}/start`
- `POST /api/v1/cultivation-activities/{id}/complete`
- `POST /api/v1/cultivation-activities/{id}/skip`
- `POST /api/v1/cultivation-activities/{id}/cancel`

## Season History

- `GET /api/v1/cultivation-seasons/{id}/care-history`
- `GET /api/v1/cultivation-seasons/{id}/chemical-history`
- `GET /api/v1/cultivation-seasons/{id}/safe-harvest-date`
- `POST /api/v1/cultivation-seasons/{id}/compliance-assessments`

## Inputs, MRL And Lab

- `POST /api/v1/agricultural-inputs`
- `POST /api/v1/residue-standards`
- `GET /api/v1/residue-standards`
- `POST /api/v1/residue-standards/import` with an array of residue standard requests
- `POST /api/v1/lab-samples`
- `POST /api/v1/lab-results`

## Harvest And Export

- `POST /api/v1/harvest-batches`
- `GET /api/v1/harvest-batches/{id}`
- `POST /api/v1/export-releases/assess`
- `POST /api/v1/export-releases`
- `POST /api/v1/export-releases/{id}/submit`
- `POST /api/v1/export-releases/{id}/approve`
- `POST /api/v1/export-releases/{id}/release`
- `POST /api/v1/export-releases/{id}/recall`
- `GET /api/v1/export-releases/{id}/traceability`
- `GET /api/v1/audit-logs`

The legacy `/api/cultivation-schedules` API remains available and unchanged.
