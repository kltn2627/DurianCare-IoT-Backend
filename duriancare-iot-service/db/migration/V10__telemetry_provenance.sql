-- Telemetry provenance (DEVICE vs SYNTHETIC_BACKFILL) and backfill-run audit table.
-- Same statements as src/telemetryProvenance.js (applied idempotently at service startup).

CREATE TABLE IF NOT EXISTS "duriancare_iot".telemetry_backfill_runs (
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
);

ALTER TABLE "duriancare_iot".telemetry ADD COLUMN IF NOT EXISTS source VARCHAR(32) NOT NULL DEFAULT 'DEVICE';

ALTER TABLE "duriancare_iot".telemetry ADD COLUMN IF NOT EXISTS backfill_run_id UUID REFERENCES "duriancare_iot".telemetry_backfill_runs(id);

DO $$ BEGIN
   ALTER TABLE "duriancare_iot".telemetry ADD CONSTRAINT ck_telemetry_source CHECK (
     (source = 'DEVICE' AND backfill_run_id IS NULL) OR
     (source = 'SYNTHETIC_BACKFILL' AND backfill_run_id IS NOT NULL));
 EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_telemetry_synthetic_device_ts
   ON "duriancare_iot".telemetry (device_id, "timestamp") WHERE source = 'SYNTHETIC_BACKFILL';
