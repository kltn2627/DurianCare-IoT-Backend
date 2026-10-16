CREATE TABLE IF NOT EXISTS duriancare_iot.iot_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_registry_id UUID REFERENCES duriancare_iot.iot_devices(id),
    device_uid VARCHAR(150) NOT NULL,
    farm_id VARCHAR(150) NOT NULL,
    cultivation_area_id VARCHAR(150),
    type VARCHAR(60) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'ALERTING',
    measured_value DOUBLE PRECISION,
    threshold_value DOUBLE PRECISION,
    message TEXT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_observed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recovered_at TIMESTAMPTZ,
    acknowledged_at TIMESTAMPTZ,
    acknowledged_by VARCHAR(150),
    notification_event_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_iot_alerts_status CHECK (
        status IN ('ALERTING', 'RECOVERED', 'ACKNOWLEDGED')
    )
);

CREATE INDEX IF NOT EXISTS idx_iot_alerts_farm_status
    ON duriancare_iot.iot_alerts (farm_id, status, last_observed_at DESC);

CREATE INDEX IF NOT EXISTS idx_iot_alerts_device_status
    ON duriancare_iot.iot_alerts (device_uid, status, last_observed_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_iot_alerts_active_device_type
    ON duriancare_iot.iot_alerts (device_uid, type)
    WHERE status = 'ALERTING';
