-- Add AI diagnosis result columns to camera_captures
ALTER TABLE duriancare_iot.camera_captures
  ADD COLUMN IF NOT EXISTS disease_detected  VARCHAR(100),
  ADD COLUMN IF NOT EXISTS confidence_score  DECIMAL(5,4),
  ADD COLUMN IF NOT EXISTS diagnosis_result  JSONB;

-- Index for filtering by disease type
CREATE INDEX IF NOT EXISTS idx_camera_captures_disease
  ON duriancare_iot.camera_captures (disease_detected)
  WHERE disease_detected IS NOT NULL;
