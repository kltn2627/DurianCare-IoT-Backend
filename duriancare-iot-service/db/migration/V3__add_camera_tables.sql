-- Camera captures: stores metadata for each photo taken by ESP32-CAM
CREATE TABLE IF NOT EXISTS duriancare_iot.camera_captures (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id    VARCHAR(150) NOT NULL,
    image_url    VARCHAR(1000) NOT NULL,
    capture_type VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    captured_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ai_status    VARCHAR(50),
    notes        TEXT,
    CONSTRAINT ck_camera_capture_type
        CHECK (capture_type IN ('MANUAL', 'SCHEDULED'))
);

CREATE INDEX IF NOT EXISTS idx_camera_captures_device_captured
    ON duriancare_iot.camera_captures (device_id, captured_at DESC);

-- Camera schedules: configurable cron-based auto-capture jobs per device
CREATE TABLE IF NOT EXISTS duriancare_iot.camera_schedules (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       VARCHAR(150) NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    label           VARCHAR(200),
    enabled         BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_camera_schedules_device
    ON duriancare_iot.camera_schedules (device_id);
