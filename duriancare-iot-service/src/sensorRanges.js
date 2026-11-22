"use strict";

// Physical ranges accepted at ingestion (DHT22: -40..80 °C, 0..100 %RH; soil index 0..100 %).
// humidity / soil_moisture match the database CHECK constraints (V1, V2 migrations).
const MEASUREMENT_RANGES = {
  temperature: [-40, 80],
  humidity: [0, 100],
  soil_moisture: [0, 100],
};

function measurementRangeError(values) {
  for (const [field, [min, max]] of Object.entries(MEASUREMENT_RANGES)) {
    const value = values[field];
    if (value !== null && value !== undefined && (value < min || value > max)) {
      return { field, message: `${field} must be between ${min} and ${max}` };
    }
  }
  return null;
}

function parseOptionalRssi(value) {
  if (value === undefined || value === null || value === "") return null;
  if (!Number.isInteger(value) || value < -127 || value > 0) throw new Error("wifi_rssi must be an integer between -127 and 0 dBm");
  return value;
}

module.exports = { MEASUREMENT_RANGES, measurementRangeError, parseOptionalRssi };
