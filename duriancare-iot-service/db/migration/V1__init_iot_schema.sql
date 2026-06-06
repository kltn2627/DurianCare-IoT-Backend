CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE iot_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_code VARCHAR(80) NOT NULL,
    farm_id UUID NOT NULL,
    farm_zone_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    hardware_model VARCHAR(100) NOT NULL,
    firmware_version VARCHAR(50),
    mqtt_client_id VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    installed_at TIMESTAMPTZ,
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_iot_devices_device_code UNIQUE (device_code),
    CONSTRAINT uk_iot_devices_mqtt_client_id UNIQUE (mqtt_client_id),
    CONSTRAINT ck_iot_devices_status
        CHECK (status IN ('ONLINE', 'OFFLINE', 'MAINTENANCE', 'DISABLED'))
);

CREATE TABLE sensor_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    unit VARCHAR(30) NOT NULL,
    minimum_valid_value NUMERIC(12, 4) NOT NULL,
    maximum_valid_value NUMERIC(12, 4) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sensor_types_code UNIQUE (code),
    CONSTRAINT ck_sensor_types_code
        CHECK (code IN (
            'AIR_TEMPERATURE_DHT22',
            'AIR_HUMIDITY_DHT22',
            'SOIL_MOISTURE'
        )),
    CONSTRAINT ck_sensor_types_range
        CHECK (maximum_valid_value > minimum_valid_value)
);

CREATE TABLE device_sensors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL,
    sensor_type_id UUID NOT NULL,
    channel VARCHAR(50),
    calibration_offset NUMERIC(12, 4) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_device_sensors_device
        FOREIGN KEY (device_id) REFERENCES iot_devices (id) ON DELETE CASCADE,
    CONSTRAINT fk_device_sensors_sensor_type
        FOREIGN KEY (sensor_type_id) REFERENCES sensor_types (id) ON DELETE RESTRICT,
    CONSTRAINT uk_device_sensors_device_type
        UNIQUE (device_id, sensor_type_id)
);

CREATE TABLE sensor_readings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL,
    sensor_type_id UUID NOT NULL,
    farm_zone_id UUID NOT NULL,
    value NUMERIC(12, 4) NOT NULL,
    quality VARCHAR(20) NOT NULL DEFAULT 'GOOD',
    measured_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sensor_readings_device
        FOREIGN KEY (device_id) REFERENCES iot_devices (id) ON DELETE CASCADE,
    CONSTRAINT fk_sensor_readings_sensor_type
        FOREIGN KEY (sensor_type_id) REFERENCES sensor_types (id) ON DELETE RESTRICT,
    CONSTRAINT ck_sensor_readings_quality
        CHECK (quality IN ('GOOD', 'UNCERTAIN', 'BAD'))
);

CREATE TABLE sensor_reading_series (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL,
    sensor_type_id UUID NOT NULL,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    interval_seconds INT NOT NULL,
    minimum_value NUMERIC(12, 4) NOT NULL,
    maximum_value NUMERIC(12, 4) NOT NULL,
    average_value NUMERIC(12, 4) NOT NULL,
    sample_count BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sensor_series_device
        FOREIGN KEY (device_id) REFERENCES iot_devices (id) ON DELETE CASCADE,
    CONSTRAINT fk_sensor_series_sensor_type
        FOREIGN KEY (sensor_type_id) REFERENCES sensor_types (id) ON DELETE RESTRICT,
    CONSTRAINT uk_sensor_series_bucket
        UNIQUE (device_id, sensor_type_id, period_start, interval_seconds),
    CONSTRAINT ck_sensor_series_period CHECK (period_end > period_start),
    CONSTRAINT ck_sensor_series_interval CHECK (interval_seconds > 0),
    CONSTRAINT ck_sensor_series_values CHECK (maximum_value >= minimum_value),
    CONSTRAINT ck_sensor_series_sample_count CHECK (sample_count > 0)
);

INSERT INTO sensor_types (
    code,
    name,
    unit,
    minimum_valid_value,
    maximum_valid_value,
    description
) VALUES
    ('AIR_TEMPERATURE_DHT22', 'Nhiệt độ không khí DHT22', 'CELSIUS', -40, 80,
     'Nhiệt độ không khí đo bằng cảm biến DHT22.'),
    ('AIR_HUMIDITY_DHT22', 'Độ ẩm không khí DHT22', 'PERCENT', 0, 100,
     'Độ ẩm tương đối của không khí đo bằng cảm biến DHT22.'),
    ('SOIL_MOISTURE', 'Độ ẩm đất', 'PERCENT', 0, 100,
     'Phần trăm độ ẩm đất tại vùng rễ cây sầu riêng.');

CREATE INDEX idx_iot_devices_zone_status ON iot_devices (farm_zone_id, status);
CREATE INDEX idx_sensor_readings_device_measured
    ON sensor_readings (device_id, measured_at DESC);
CREATE INDEX idx_sensor_readings_zone_type_measured
    ON sensor_readings (farm_zone_id, sensor_type_id, measured_at DESC);
