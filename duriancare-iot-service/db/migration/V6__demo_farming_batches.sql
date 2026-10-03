-- V6: Farming batches and chemical application records
-- Enables batch-level chemical tracking for export compliance assessment

SET search_path TO duriancare_iot;

CREATE TABLE IF NOT EXISTS farming_batches (
  id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  batch_code    VARCHAR(100) NOT NULL UNIQUE,
  device_id     VARCHAR(150) NOT NULL,
  cam_device_id VARCHAR(150),
  variety       VARCHAR(100),
  farm_name     VARCHAR(200),
  start_date    DATE         NOT NULL,
  harvest_date  DATE,
  target_market VARCHAR(20)  NOT NULL DEFAULT 'CHINA',
  notes         TEXT,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_farming_batches_market
    CHECK (target_market IN ('CHINA', 'EU', 'US', 'JAPAN', 'DOMESTIC'))
);

CREATE TABLE IF NOT EXISTS batch_chemical_applications (
  id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  batch_id       UUID         NOT NULL REFERENCES farming_batches(id) ON DELETE CASCADE,
  chemical_id    VARCHAR(50)  NOT NULL,
  applied_at     DATE         NOT NULL,
  dose_kg_per_ha DECIMAL(8,3) NOT NULL DEFAULT 0.5,
  stage          VARCHAR(100),
  notes          TEXT
);

CREATE INDEX IF NOT EXISTS idx_farming_batches_device
  ON farming_batches (device_id);

CREATE INDEX IF NOT EXISTS idx_batch_chemicals_batch
  ON batch_chemical_applications (batch_id);
