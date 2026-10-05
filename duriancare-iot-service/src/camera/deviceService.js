"use strict";

const DEVICE_COLS = `
  id, device_id, mac_address, ip_address, port, protocol, online, last_seen,
  firmware_version, ssid, tree_id, zone_id, farm_id, capabilities,
  created_at, updated_at`;

/**
 * Register or update a camera device.
 * Uses device_id as the stable identity key.
 * mac_address is used as an additional deduplication key if present.
 * IP address is always overwritten — it is runtime-dynamic.
 */
async function upsertCamera(pool, schema, {
  deviceId, macAddress, ipAddress, port = 80, protocol = "http",
  firmwareVersion, ssid, treeId, zoneId, farmId, capabilities,
}) {
  const now = new Date().toISOString();
  const { rows } = await pool.query(
    `INSERT INTO "${schema}".camera_devices
       (device_id, mac_address, ip_address, port, protocol, online, last_seen,
        firmware_version, ssid, tree_id, zone_id, farm_id, capabilities, updated_at)
     VALUES ($1,$2,$3,$4,$5,true,$6,$7,$8,$9,$10,$11,$12,$6)
     ON CONFLICT (device_id) DO UPDATE SET
       mac_address      = COALESCE(EXCLUDED.mac_address, camera_devices.mac_address),
       ip_address       = EXCLUDED.ip_address,
       port             = EXCLUDED.port,
       protocol         = EXCLUDED.protocol,
       online           = true,
       last_seen        = EXCLUDED.last_seen,
       firmware_version = COALESCE(EXCLUDED.firmware_version, camera_devices.firmware_version),
       ssid             = COALESCE(EXCLUDED.ssid, camera_devices.ssid),
       tree_id          = COALESCE(EXCLUDED.tree_id, camera_devices.tree_id),
       zone_id          = COALESCE(EXCLUDED.zone_id, camera_devices.zone_id),
       farm_id          = COALESCE(EXCLUDED.farm_id, camera_devices.farm_id),
       capabilities     = COALESCE(EXCLUDED.capabilities, camera_devices.capabilities),
       updated_at       = EXCLUDED.updated_at
     RETURNING ${DEVICE_COLS}`,
    [
      deviceId,
      macAddress ?? null,
      ipAddress,
      port,
      protocol,
      now,
      firmwareVersion ?? null,
      ssid ?? null,
      treeId ?? null,
      zoneId ?? null,
      farmId ?? null,
      capabilities ? JSON.stringify(capabilities) : null,
    ],
  );
  return rows[0];
}

/**
 * Heartbeat: update last_seen, ip_address, port, and mark online.
 * Does NOT change tree/zone/farm assignments.
 */
async function heartbeat(pool, schema, { deviceId, ipAddress, port = 80 }) {
  const now = new Date().toISOString();
  const { rows } = await pool.query(
    `UPDATE "${schema}".camera_devices
     SET ip_address = $2,
         port       = $3,
         online     = true,
         last_seen  = $4,
         updated_at = $4
     WHERE device_id = $1
     RETURNING ${DEVICE_COLS}`,
    [deviceId, ipAddress, port, now],
  );
  if (rows.length === 0) return null;
  return rows[0];
}

/**
 * Get a single camera device by device_id.
 */
async function getCamera(pool, schema, deviceId) {
  const { rows } = await pool.query(
    `SELECT ${DEVICE_COLS}
     FROM "${schema}".camera_devices
     WHERE device_id = $1`,
    [deviceId],
  );
  return rows[0] ?? null;
}

/**
 * List all camera devices, online ones first.
 */
async function listCameras(pool, schema) {
  const { rows } = await pool.query(
    `SELECT ${DEVICE_COLS}
     FROM "${schema}".camera_devices
     ORDER BY online DESC, last_seen DESC NULLS LAST`,
  );
  return rows;
}

/**
 * Assign a camera to a tree (and optionally zone/farm).
 * Passing null clears the assignment.
 */
async function assignTree(pool, schema, deviceId, { treeId, zoneId, farmId }) {
  const now = new Date().toISOString();
  const { rows } = await pool.query(
    `UPDATE "${schema}".camera_devices
     SET tree_id    = $2,
         zone_id    = $3,
         farm_id    = $4,
         updated_at = $5
     WHERE device_id = $1
     RETURNING ${DEVICE_COLS}`,
    [deviceId, treeId ?? null, zoneId ?? null, farmId ?? null, now],
  );
  return rows[0] ?? null;
}

/**
 * Mark cameras offline if they have not sent a heartbeat within the timeout.
 * Returns the number of rows updated.
 */
async function markOfflineStaleCameras(pool, schema, timeoutMinutes = 5) {
  const cutoff = new Date(Date.now() - timeoutMinutes * 60 * 1000).toISOString();
  const { rowCount } = await pool.query(
    `UPDATE "${schema}".camera_devices
     SET online     = false,
         updated_at = NOW()
     WHERE online = true
       AND (last_seen IS NULL OR last_seen < $1)`,
    [cutoff],
  );
  return rowCount;
}

/**
 * Resolve the current HTTP base URL for a device.
 * Returns an object: { url, online, found }
 *   url    — e.g. "http://192.168.1.137:81" (no trailing slash)
 *   online — whether the camera reported online recently
 *   found  — whether a camera_devices record exists for this device_id
 */
async function resolveCameraUrl(pool, schema, deviceId, fallbackUrl) {
  const cam = await getCamera(pool, schema, deviceId);
  if (!cam) {
    return { url: fallbackUrl ?? null, online: false, found: false };
  }
  const port = Number(cam.port);
  const portSuffix = (cam.protocol === "http" && port === 80) ||
                     (cam.protocol === "https" && port === 443)
    ? ""
    : `:${port}`;
  const url = `${cam.protocol}://${cam.ip_address}${portSuffix}`;
  return { url, online: Boolean(cam.online), found: true };
}

/**
 * Manually update ip_address and port for a camera device.
 * Used by the /config endpoint so operators can change the camera IP
 * at runtime (e.g. when switching Wi-Fi networks) without restarting containers.
 */
async function updateCameraUrl(pool, schema, deviceId, { ipAddress, port = 80 }) {
  const now = new Date().toISOString();
  const { rows } = await pool.query(
    `UPDATE "${schema}".camera_devices
     SET ip_address = $2,
         port       = $3,
         updated_at = $4
     WHERE device_id = $1
     RETURNING ${DEVICE_COLS}`,
    [deviceId, ipAddress, port, now],
  );
  return rows[0] ?? null;
}

module.exports = {
  upsertCamera,
  heartbeat,
  getCamera,
  listCameras,
  assignTree,
  markOfflineStaleCameras,
  resolveCameraUrl,
  updateCameraUrl,
};
