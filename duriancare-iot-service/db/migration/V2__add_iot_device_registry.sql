CREATE TABLE IF NOT EXISTS duriancare_iot.iot_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_uid VARCHAR(150) NOT NULL UNIQUE,
    name VARCHAR(160),
    farm_id VARCHAR(150) NOT NULL,
    cultivation_area_id VARCHAR(150),
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_iot_devices_status CHECK (
        status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE', 'DELETED')
    )
);

CREATE INDEX IF NOT EXISTS idx_iot_devices_farm_area
    ON duriancare_iot.iot_devices (farm_id, cultivation_area_id);

CREATE INDEX IF NOT EXISTS idx_iot_devices_last_seen
    ON duriancare_iot.iot_devices (last_seen_at DESC);
