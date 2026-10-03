-- Ensure all AI diagnosis columns exist on camera_captures.
-- Idempotent: ADD COLUMN IF NOT EXISTS is safe whether V4 ran or not.
-- Adds the previously-missing `severity` column.
SET search_path TO duriancare_iot;

ALTER TABLE camera_captures
  ADD COLUMN IF NOT EXISTS disease_detected  VARCHAR(100),
  ADD COLUMN IF NOT EXISTS confidence_score  NUMERIC(5, 2),
  ADD COLUMN IF NOT EXISTS severity          VARCHAR(50),
  ADD COLUMN IF NOT EXISTS diagnosis_result  JSONB;

-- Index on severity for future dashboard filtering
CREATE INDEX IF NOT EXISTS idx_camera_captures_severity
  ON camera_captures (severity)
  WHERE severity IS NOT NULL;
