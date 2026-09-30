-- Extend telemetry table with soil moisture sensor data
ALTER TABLE duriancare_iot.telemetry
    ADD COLUMN IF NOT EXISTS soil_moisture NUMERIC(8, 3);

-- Widen the "at least one measurement" constraint to include soil_moisture
ALTER TABLE duriancare_iot.telemetry
    DROP CONSTRAINT IF EXISTS ck_telemetry_has_measurement;

ALTER TABLE duriancare_iot.telemetry
    ADD CONSTRAINT ck_telemetry_has_measurement CHECK (
        temperature IS NOT NULL
        OR humidity IS NOT NULL
        OR light IS NOT NULL
        OR soil_moisture IS NOT NULL
    );

-- Soil moisture is a percentage (0-100)
ALTER TABLE duriancare_iot.telemetry
    ADD CONSTRAINT ck_telemetry_soil_moisture_range CHECK (
        soil_moisture IS NULL OR (soil_moisture >= 0 AND soil_moisture <= 100)
    );
