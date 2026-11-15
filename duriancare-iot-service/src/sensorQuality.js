"use strict";

// Per-reading sensor quality reported by the sensor firmware (>= 1.4.0). A value the firmware marks as
// not valid is never stored as a measurement, so it can neither be displayed as real nor raise an alert.
//   soil_raw     ADC reading 0..4095, always stored when sent (diagnosis / later calibration)
//   soil_status  OK            calibrated and in range: soil_moisture is a real percentage
//                UNCALIBRATED  in range but the probe has not been calibrated: no percentage
//                NO_SIGNAL     ~0 V: AO/VCC not connected or shorted
//                SATURATED     ~3.3 V: probe open / not in soil
//                UNSTABLE      samples spread widely: floating ADC input
//   dht_status   OK | SENSOR_ERROR
// Older firmware sends none of these; its readings are stored exactly as before (status NULL).
const SOIL_STATUSES = ["OK", "UNCALIBRATED", "NO_SIGNAL", "SATURATED", "UNSTABLE"];
const DHT_STATUSES = ["OK", "SENSOR_ERROR"];

function sensorQualityStatements(schema) {
  const telemetry = `"${schema}".telemetry`;
  const inList = (values) => values.map((v) => `'${v}'`).join(", ");
  return [
    `ALTER TABLE ${telemetry} ADD COLUMN IF NOT EXISTS soil_raw INTEGER`,
    `ALTER TABLE ${telemetry} ADD COLUMN IF NOT EXISTS soil_status VARCHAR(20)`,
    `ALTER TABLE ${telemetry} ADD COLUMN IF NOT EXISTS dht_status VARCHAR(20)`,
    `DO $$ BEGIN
       ALTER TABLE ${telemetry} ADD CONSTRAINT ck_telemetry_sensor_quality CHECK (
         (soil_raw IS NULL OR soil_raw BETWEEN 0 AND 4095) AND
         (soil_status IS NULL OR soil_status IN (${inList(SOIL_STATUSES)})) AND
         (dht_status IS NULL OR dht_status IN (${inList(DHT_STATUSES)})) AND
         (soil_status IS NULL OR soil_status = 'OK' OR soil_moisture IS NULL));
     EXCEPTION WHEN duplicate_object THEN NULL; END $$`,
  ];
}

async function ensureSensorQualitySchema(pool, schema) {
  for (const sql of sensorQualityStatements(schema)) await pool.query(sql);
}

function validationError(message) {
  return Object.assign(new Error(message), { status: 400 });
}

/** Reads the optional quality fields; throws a 400-style error when present but malformed. */
function parseSensorQuality(body = {}) {
  const { soil_raw: soilRaw = null, soil_status: soilStatus = null, dht_status: dhtStatus = null } = body;
  if (soilRaw !== null && !(Number.isInteger(soilRaw) && soilRaw >= 0 && soilRaw <= 4095)) {
    throw validationError("soil_raw must be an integer between 0 and 4095");
  }
  if (soilStatus !== null && !SOIL_STATUSES.includes(soilStatus)) {
    throw validationError(`soil_status must be one of ${SOIL_STATUSES.join(", ")}`);
  }
  if (dhtStatus !== null && !DHT_STATUSES.includes(dhtStatus)) {
    throw validationError(`dht_status must be one of ${DHT_STATUSES.join(", ")}`);
  }
  return { soilRaw, soilStatus, dhtStatus };
}

/** Drops measurements the device itself declared invalid. */
function applySensorQuality({ temperature, humidity, soilMoisture }, quality) {
  const soilValid = quality.soilStatus === null || quality.soilStatus === "OK";
  const dhtValid = quality.dhtStatus !== "SENSOR_ERROR";
  return {
    temperature: dhtValid ? temperature : null,
    humidity: dhtValid ? humidity : null,
    soilMoisture: soilValid ? soilMoisture : null,
  };
}

/** A reading that carries no measurement but a diagnosis is still evidence the device is alive. */
function hasDiagnosis(quality) {
  return quality.soilStatus !== null || quality.dhtStatus !== null;
}

module.exports = {
  DHT_STATUSES,
  SOIL_STATUSES,
  applySensorQuality,
  ensureSensorQualitySchema,
  hasDiagnosis,
  parseSensorQuality,
  sensorQualityStatements,
};
