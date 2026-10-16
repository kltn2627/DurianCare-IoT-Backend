# Export Traceability

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
