CREATE TABLE IF NOT EXISTS ai_prediction_history (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    image_url TEXT,
    image_path TEXT,
    source TEXT NOT NULL,
    device_id TEXT,
    predicted_disease TEXT NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    confidence_text TEXT NOT NULL,
    severity TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    used_detection_crop BOOLEAN NOT NULL DEFAULT FALSE,
    diagnosed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    image_object_key TEXT,
    original_filename TEXT,
    response_payload JSONB NOT NULL,
    deleted_at TIMESTAMPTZ
);

ALTER TABLE ai_prediction_history
    ADD COLUMN IF NOT EXISTS image_url TEXT,
    ADD COLUMN IF NOT EXISTS image_path TEXT,
    ADD COLUMN IF NOT EXISTS source TEXT,
    ADD COLUMN IF NOT EXISTS device_id TEXT,
    ADD COLUMN IF NOT EXISTS confidence_text TEXT,
    ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS used_detection_crop BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS diagnosed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS image_object_key TEXT,
    ADD COLUMN IF NOT EXISTS original_filename TEXT,
    ADD COLUMN IF NOT EXISTS response_payload JSONB,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

UPDATE ai_prediction_history
SET diagnosed_at = created_at
WHERE diagnosed_at IS NULL;

UPDATE ai_prediction_history
SET confidence_text = CONCAT(ROUND(confidence::numeric, 2), '%')
WHERE confidence_text IS NULL AND confidence IS NOT NULL;

UPDATE ai_prediction_history
SET response_payload = '{}'::jsonb
WHERE response_payload IS NULL;

CREATE INDEX IF NOT EXISTS idx_ai_prediction_history_user_diagnosed_at
    ON ai_prediction_history (user_id, diagnosed_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_ai_prediction_history_status
    ON ai_prediction_history (status)
    WHERE deleted_at IS NULL;
