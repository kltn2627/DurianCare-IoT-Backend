-- Camera devices: runtime state for each registered ESP32-CAM / IP camera.
-- IP address is DYNAMIC — updated on every registration/heartbeat.
-- device_id is the STABLE identity (e.g. "esp32-cam-01", or MAC-derived).
SET search_path TO duriancare_iot;

CREATE TABLE IF NOT EXISTS camera_devices (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id        VARCHAR(150) NOT NULL,
    mac_address      VARCHAR(50),
    ip_address       VARCHAR(45)  NOT NULL,
    port             INTEGER      NOT NULL DEFAULT 80,
    protocol         VARCHAR(8)   NOT NULL DEFAULT 'http',
    online           BOOLEAN      NOT NULL DEFAULT false,
    last_seen        TIMESTAMPTZ,
    firmware_version VARCHAR(100),
    ssid             VARCHAR(150),
    tree_id          VARCHAR(150),
    zone_id          VARCHAR(150),
    farm_id          VARCHAR(150),
    capabilities     JSONB,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_camera_devices_device_id  UNIQUE (device_id),
    CONSTRAINT uq_camera_devices_mac        UNIQUE (mac_address),
    CONSTRAINT ck_camera_devices_protocol   CHECK (protocol IN ('http', 'https')),
    CONSTRAINT ck_camera_devices_port       CHECK (port > 0 AND port <= 65535)
);

CREATE INDEX IF NOT EXISTS idx_camera_devices_online
    ON camera_devices (online, last_seen DESC);

CREATE INDEX IF NOT EXISTS idx_camera_devices_tree_id
    ON camera_devices (tree_id)
    WHERE tree_id IS NOT NULL;
