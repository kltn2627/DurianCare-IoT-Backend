-- V8: Batch lifecycle status + traceability code for QR-based provenance tracking
-- Vòng đời: PLANNED -> GROWING -> HARVESTING -> EVALUATING -> EXPORTED

SET search_path TO duriancare_iot;

ALTER TABLE farming_batches
  ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PLANNED'
    CONSTRAINT chk_batch_status
      CHECK (status IN ('PLANNED', 'GROWING', 'HARVESTING', 'EVALUATING', 'EXPORTED')),
  ADD COLUMN IF NOT EXISTS traceability_code VARCHAR(100),
  ADD COLUMN IF NOT EXISTS export_score     INTEGER,
  ADD COLUMN IF NOT EXISTS finalized_at     TIMESTAMPTZ;

ALTER TABLE farming_batches
  ADD CONSTRAINT uq_batch_traceability_code UNIQUE (traceability_code);

CREATE INDEX IF NOT EXISTS idx_farming_batches_status
  ON farming_batches (status);

CREATE INDEX IF NOT EXISTS idx_farming_batches_traceability_code
  ON farming_batches (traceability_code)
  WHERE traceability_code IS NOT NULL;
