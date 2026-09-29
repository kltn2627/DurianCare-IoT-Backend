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

## Cultivation Authorization Enforcement

Status: `FIXED` for v1 farm/area-scoped cultivation resources after backend compile and targeted security tests passed.

Gateway authenticates JWT access tokens, strips spoofed `X-Auth-*` and `X-Internal-*` headers, then forwards `X-Auth-User-Id`, `X-Auth-Email`, and `X-Auth-Role` to backend services. Cultivation-service now uses those forwarded headers as the authenticated principal. Request body fields such as `createdBy`, `executedBy`, and approval `userId` remain workflow/audit fields and are not used as authentication identity.

Source of truth remains farm-service:

- Farmer access is checked against `Farm.ownerUserId`.
- Engineer/Expert access is checked against `FarmAuthorization` with `ACTIVE` status, non-expired `validUntil`, matching `farmId`, matching `engineerUserId`, required permission, and `allowedCultivationAreaIds` when `plotId` is present.
- Admin has no implicit cultivation bypass in this phase.
- FarmAuthorization is not duplicated into cultivation-service. Cultivation-service calls `POST /internal/v1/farm-access/check` directly on farm-service using `X-Internal-Token`.
- Authorization failures are fail-closed: 401 for missing principal, 403 for denied/unavailable farm access.

### Endpoint Audit

| Endpoint | Before | After |
|---|---|---|
| `POST /api/v1/cultivation-plans` | Trusted request `farmId`, `plotId`, `createdBy` | Requires create permission for target farm/plot |
| `GET /api/v1/cultivation-plans` | Query/list could return cross-farm data | Server filters each result by view permission |
| `GET /api/v1/cultivation-plans/{id}` | Direct IDOR possible by guessed id | Loads plan, then authorizes resolved farm/plot |
| `GET /api/v1/cultivation-plans/{id}/calendar` | Direct plan id leaked activity calendar | Loads plan, authorizes plan, filters activities |
| `POST /api/v1/cultivation-activities` | Trusted request scope and plan id | Loads plan, requires matching farm/plot/season, then create permission |
| `GET /api/v1/cultivation-activities` | List could leak cross-farm activities | Server filters by view permission |
| `GET/PUT/PATCH /api/v1/cultivation-activities/{id}` | Direct IDOR possible | Loads activity, then view/update permission |
| `POST /api/v1/cultivation-activities/{id}/approve|reject|start|complete|skip|cancel` | Direct ID mutation possible | Loads activity, then update permission |
| `GET /api/v1/cultivation-seasons/{id}/care-history` | Season id could leak activities/executions/usages | Filters authorized activities before loading execution/input usage |
| `GET /api/v1/cultivation-seasons/{id}/chemical-history` | Season id could leak chemical activities | Filters authorized activities |
| `GET /api/v1/cultivation-seasons/{id}/safe-harvest-date` | Season aggregate could leak PHI timeline | Resolves season scope from plan/activity/harvest data, then requires view |
| `POST /api/v1/cultivation-seasons/{id}/compliance-assessments` | Season/harvest aggregate unguarded | Authorizes harvest batch or resolved season scope |
| `POST/GET /api/v1/lab-samples*`, `POST /api/v1/lab-results` | Lab data linked by season/harvest unguarded | Authorizes via harvest batch or resolved season scope |
| `POST/GET /api/v1/harvest-batches*` | Farm/plot in request or direct id unguarded | Requires create/view permission for batch farm/plot |
| `POST/GET /api/v1/export-releases*` | Export and traceability linked by harvest unguarded | Authorizes through harvest batch farm/plot |
| `POST/GET /api/v1/agricultural-inputs*`, residue standards | Global catalog, not farm scoped | Unchanged; farm authorization not applied |
| `GET /api/v1/audit-logs` | Global audit read | Unchanged; still a remaining admin/security contract gap |

### Threat Model Verification

| Case | Result |
|---|---|
| Engineer authorized on Farm 1 calls Farm 2 by guessed id | Denied by farm-service FarmAuthorization check |
| Engineer authorized to Area X calls Area Y | Denied by `allowedCultivationAreaIds` check |
| Revoked engineer uses old activity id | Denied because only `ACTIVE` authorization is accepted |
| Expired engineer uses old activity id | Denied because `validUntil` is checked at runtime |
| Farmer A calls Farmer B resource | Denied by `Farm.ownerUserId` check |
| Irrelevant role calls cultivation API | Denied; only owner Farmer or Engineer/Expert with authorization is accepted |
| Direct UUID/id endpoints | Resource is loaded first, then resolved farm/plot is authorized |

### Action Matrix

| Action | Owner Farmer | Engineer | Expert | Admin |
|---|---|---|---|---|
| View plan/activity/history/harvest/lab/export | Own farm only | Requires `VIEW_CARE_SCHEDULE` and scope | Requires `VIEW_CARE_SCHEDULE` and scope | No implicit bypass |
| Create plan/activity/harvest/lab/export | Own farm only | Requires `CREATE_CARE_SCHEDULE` and scope | Requires `CREATE_CARE_SCHEDULE` and scope | No implicit bypass |
| Update/start/complete/skip/cancel/approve/reject/submit/release/recall | Own farm only | Requires `UPDATE_CARE_SCHEDULE` and scope | Requires `UPDATE_CARE_SCHEDULE` and scope | No implicit bypass |

### Remaining Contract Gaps

- `GET /api/v1/audit-logs` still needs an admin/operator authorization contract.
- Agricultural inputs and residue standards are global catalogs in the current model, not farm-scoped resources.
- Cultivation season has no canonical catalog/entity. Season authorization is resolved from plans, activities, and harvest batches; empty/unresolvable season scope is not trusted.
- Verification used the Maven installation bundled with IntelliJ IDEA because the repository has no Maven wrapper and no global `mvn` in PATH.
