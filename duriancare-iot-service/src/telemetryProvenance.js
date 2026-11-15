"use strict";

// Telemetry provenance: every row says whether a device measured it or it is labelled synthetic
// history. Mirrors db/migration/V10__telemetry_provenance.sql; idempotent so it can run at startup.
const SOURCE_DEVICE = "DEVICE";
const SOURCE_SYNTHETIC = "SYNTHETIC_BACKFILL";

function provenanceStatements(schema) {
  const telemetry = `"${schema}".telemetry`;
  const runs = `"${schema}".telemetry_backfill_runs`;
  return [
    `CREATE TABLE IF NOT EXISTS ${runs} (
      id UUID PRIMARY KEY,
      device_id VARCHAR(150) NOT NULL,
      range_from TIMESTAMPTZ NOT NULL,
      range_to TIMESTAMPTZ NOT NULL,
      cadence_seconds INTEGER NOT NULL CHECK (cadence_seconds > 0),
      method VARCHAR(80) NOT NULL,
      seed BIGINT NOT NULL,
      planned_count INTEGER NOT NULL CHECK (planned_count >= 0),
      inserted_count INTEGER NOT NULL DEFAULT 0 CHECK (inserted_count >= 0),
      status VARCHAR(20) NOT NULL DEFAULT 'STARTED',
      created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
      completed_at TIMESTAMPTZ,
      rolled_back_at TIMESTAMPTZ,
      CONSTRAINT ck_telemetry_backfill_runs_status CHECK (status IN ('STARTED', 'COMPLETED', 'ROLLED_BACK')),
      CONSTRAINT ck_telemetry_backfill_runs_range CHECK (range_from < range_to)
    )`,
    `ALTER TABLE ${telemetry} ADD COLUMN IF NOT EXISTS source VARCHAR(32) NOT NULL DEFAULT '${SOURCE_DEVICE}'`,
    `ALTER TABLE ${telemetry} ADD COLUMN IF NOT EXISTS backfill_run_id UUID REFERENCES ${runs}(id)`,
    `DO $$ BEGIN
       ALTER TABLE ${telemetry} ADD CONSTRAINT ck_telemetry_source CHECK (
         (source = '${SOURCE_DEVICE}' AND backfill_run_id IS NULL) OR
         (source = '${SOURCE_SYNTHETIC}' AND backfill_run_id IS NOT NULL));
     EXCEPTION WHEN duplicate_object THEN NULL; END $$`,
    // One synthetic row per device/timestamp makes re-running a backfill a no-op.
    `CREATE UNIQUE INDEX IF NOT EXISTS uq_telemetry_synthetic_device_ts
       ON ${telemetry} (device_id, "timestamp") WHERE source = '${SOURCE_SYNTHETIC}'`,
  ];
}

async function ensureTelemetryProvenanceSchema(pool, schema) {
  for (const statement of provenanceStatements(schema)) await pool.query(statement);
}

module.exports = { SOURCE_DEVICE, SOURCE_SYNTHETIC, provenanceStatements, ensureTelemetryProvenanceSchema };
