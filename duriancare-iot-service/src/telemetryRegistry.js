"use strict";

// Shared by every telemetry ingestion path (MQTT and HTTP POST /api/v1/sensors/data).
// Connectivity (alert-engine connectivityForDevice) is derived from iot_devices.last_seen_at,
// so a path that stores telemetry without stamping the registry leaves the device UNKNOWN
// and invisible to the offline watchdog.
async function recordDeviceSeen({ pool, devicesTable, deviceId, receivedAt }) {
  const result = await pool.query(
    `UPDATE ${devicesTable}
     SET last_seen_at = $2, updated_at = CURRENT_TIMESTAMP
     WHERE LOWER(device_uid) = LOWER($1)
       AND status <> 'DELETED'
     RETURNING id, device_uid, name, farm_id, cultivation_area_id, status, last_seen_at`,
    [deviceId, receivedAt]
  );
  return result.rowCount > 0 ? result.rows[0] : null;
}

// Telemetry event in the same shape the MQTT path passes to evaluateTelemetryAlerts.
// HTTP readings carry no light value and are timestamped at server receipt.
function httpTelemetryEvent({ id, deviceId, temperature, humidity, soilMoisture, receivedAt }) {
  return { id, deviceId, temperature, humidity, light: null, soilMoisture, timestamp: receivedAt, receivedAt };
}

module.exports = { recordDeviceSeen, httpTelemetryEvent };
