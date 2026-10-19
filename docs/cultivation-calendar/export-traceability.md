# Export Traceability

For the verified local data inventory, source limitations and repeatable read-only harvest report,
see [Operational Data Audit](operational-data-audit.md). A release dossier is not evidence of measured
physical shipment weight; missing grading, packing and dispatch data must remain unknown.

`ExportRelease` stores immutable snapshots at creation time:

- harvest batch data;
- care history with activities, executions and input usages;
- compliance assessment details;
- generation timestamp.

After an export release is approved or released, later source-data changes do not rewrite the stored traceability snapshot.
Corrections should create a new release version or a recall workflow rather than mutating historical release evidence.

The traceability snapshot is returned by:

```text
GET /api/v1/export-releases/{id}/traceability
```

Released lots can be recalled through:

```text
POST /api/v1/export-releases/{id}/recall
```

Recall writes an audit log entry and preserves the original traceability snapshot.

The module stores external IDs for farm, plot, trees, season and users. Display names should be resolved through owning
services at query time or captured in snapshots when an export release is created.

## Demo / Seed Data

The current repository already includes demo export-compliance seed assets in the IoT service layer:

- `duriancare-iot-service/db/migration/V6__demo_farming_batches.sql` creates the PostgreSQL demo batch tables used by the export compliance prototype.
- `duriancare-iot-service/scripts/seed-demo.js` inserts deterministic demo pass/fail farming batches and related telemetry/camera/compliance data.

These records are **demo/seed data** for thesis presentation only. They must not be described as real completed shipments. The seed script is re-runnable with deterministic IDs and `ON CONFLICT DO NOTHING`; avoid `--reset` unless the operator explicitly approves deleting demo records in a disposable local database.

The newer cultivation-calendar MongoDB domain already owns `HarvestBatch` and `ExportRelease`. Do not create duplicate harvest/export tables for that service. If a separate completed-season seed is needed for the MongoDB cultivation module, add it as a dedicated idempotent seed against existing `HarvestBatch`/`ExportRelease` documents after confirming the target database is local/dev.
