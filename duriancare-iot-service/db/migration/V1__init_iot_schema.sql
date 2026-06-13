CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE SCHEMA IF NOT EXISTS duriancare_iot;

CREATE TABLE IF NOT EXISTS duriancare_iot.telemetry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id VARCHAR(150) NOT NULL,
    temperature NUMERIC(8, 3),
    humidity NUMERIC(8, 3),
    light NUMERIC(12, 3),
    "timestamp" TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_telemetry_has_measurement CHECK (
        temperature IS NOT NULL
        OR humidity IS NOT NULL
        OR light IS NOT NULL
    ),
    CONSTRAINT ck_telemetry_humidity_range CHECK (
        humidity IS NULL OR (humidity >= 0 AND humidity <= 100)
    )
);

CREATE INDEX IF NOT EXISTS idx_telemetry_device_measured
    ON duriancare_iot.telemetry (device_id, "timestamp" DESC);

CREATE INDEX IF NOT EXISTS idx_telemetry_measured
    ON duriancare_iot.telemetry ("timestamp" DESC);
