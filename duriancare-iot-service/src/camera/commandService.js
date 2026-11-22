"use strict";

// Camera command queue for the PUSH delivery mode.
//
// The backend never connects to the camera: "capture now" (Web/Mobile) and scheduled captures insert a
// CAPTURE command; the camera polls GET /api/v1/public/cameras/{id}/commands every ~5 s (and also learns
// "commandsPending" from its 60 s heartbeat), takes the photo and uploads it with
// POST /api/v1/public/cameras/{id}/frames?commandId=..., which completes the command.
//
//   PENDING -> DELIVERED (returned to the camera) -> COMPLETED (frame stored) | FAILED (camera reported)
//   PENDING/DELIVERED past expires_at -> EXPIRED (evaluated lazily on every read; no scheduler needed)

const REASONS = new Set(["ON_DEMAND", "SCHEDULED"]);
const COMMAND_COLS = `id, device_id, type, reason, preview, status, requested_by, created_at, delivered_at,
  completed_at, expires_at, capture_id, error`;

function cameraPushStatements(schema) {
  const s = `"${schema}"`;
  return [
    `CREATE TABLE IF NOT EXISTS ${s}.camera_commands (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      device_id VARCHAR(150) NOT NULL,
      type VARCHAR(16) NOT NULL DEFAULT 'CAPTURE',
      reason VARCHAR(16) NOT NULL,
      preview BOOLEAN NOT NULL DEFAULT FALSE,
      status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
      requested_by VARCHAR(150),
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      delivered_at TIMESTAMPTZ,
      completed_at TIMESTAMPTZ,
      expires_at TIMESTAMPTZ NOT NULL,
      capture_id UUID,
      error VARCHAR(64),
      CONSTRAINT ck_camera_commands_type CHECK (type IN ('CAPTURE')),
      CONSTRAINT ck_camera_commands_reason CHECK (reason IN ('ON_DEMAND', 'SCHEDULED')),
      CONSTRAINT ck_camera_commands_status CHECK (status IN ('PENDING', 'DELIVERED', 'COMPLETED', 'FAILED', 'EXPIRED'))
    )`,
    `CREATE INDEX IF NOT EXISTS idx_camera_commands_open ON ${s}.camera_commands (device_id, created_at)
       WHERE status IN ('PENDING', 'DELIVERED')`,
    // At most one open scheduled capture per camera: an offline camera does not accumulate a backlog.
    `CREATE UNIQUE INDEX IF NOT EXISTS uq_camera_commands_one_scheduled ON ${s}.camera_commands (device_id)
       WHERE status IN ('PENDING', 'DELIVERED') AND reason = 'SCHEDULED'`,
    // Push-mode metadata on the existing tables (additive; old code ignores these columns).
    `ALTER TABLE ${s}.camera_captures ADD COLUMN IF NOT EXISTS command_id UUID`,
    `ALTER TABLE ${s}.camera_captures ADD COLUMN IF NOT EXISTS delivery VARCHAR(8)`,
    `ALTER TABLE ${s}.camera_captures ADD COLUMN IF NOT EXISTS received_at TIMESTAMPTZ`,
    `ALTER TABLE ${s}.camera_devices ADD COLUMN IF NOT EXISTS diagnostics JSONB`,
    `ALTER TABLE ${s}.camera_devices ADD COLUMN IF NOT EXISTS wifi_rssi INTEGER`,
    `ALTER TABLE ${s}.camera_devices ADD COLUMN IF NOT EXISTS capabilities_reported TEXT[]`,
    `ALTER TABLE ${s}.camera_devices ADD COLUMN IF NOT EXISTS last_frame_at TIMESTAMPTZ`,
  ];
}

async function ensureCameraPushSchema(pool, schema) {
  for (const statement of cameraPushStatements(schema)) await pool.query(statement);
}

async function expireCommands(pool, schema, deviceId) {
  await pool.query(
    `UPDATE "${schema}".camera_commands SET status = 'EXPIRED', completed_at = NOW()
     WHERE device_id = $1 AND status IN ('PENDING', 'DELIVERED') AND expires_at <= NOW()`, [deviceId]);
}

/**
 * Queue a capture. ON_DEMAND requests from the UI reuse an open ON_DEMAND command (double clicks do not make
 * the camera take two photos); SCHEDULED is limited to one open command by a unique index.
 * Returns { command, created }.
 */
async function enqueueCapture(pool, schema, { deviceId, reason, preview = false, requestedBy = null, ttlSeconds }) {
  if (!REASONS.has(reason)) throw new Error("Invalid capture reason");
  await expireCommands(pool, schema, deviceId);
  const open = await pool.query(
    `SELECT ${COMMAND_COLS} FROM "${schema}".camera_commands
     WHERE device_id = $1 AND reason = $2 AND preview = $3 AND status IN ('PENDING', 'DELIVERED')
     ORDER BY created_at DESC LIMIT 1`, [deviceId, reason, Boolean(preview)]);
  if (open.rows[0]) return { command: open.rows[0], created: false };
  const { rows } = await pool.query(
    `INSERT INTO "${schema}".camera_commands (device_id, reason, preview, requested_by, expires_at)
     VALUES ($1, $2, $3, $4, NOW() + make_interval(secs => $5))
     ON CONFLICT DO NOTHING
     RETURNING ${COMMAND_COLS}`,
    [deviceId, reason, Boolean(preview), requestedBy, ttlSeconds]);
  if (rows[0]) return { command: rows[0], created: true };
  const again = await pool.query(
    `SELECT ${COMMAND_COLS} FROM "${schema}".camera_commands
     WHERE device_id = $1 AND reason = $2 AND status IN ('PENDING', 'DELIVERED')
     ORDER BY created_at DESC LIMIT 1`, [deviceId, reason]);
  return { command: again.rows[0] ?? null, created: false };
}

/** Open commands for the camera (oldest first); PENDING ones become DELIVERED. */
async function takeCommands(pool, schema, deviceId, limit = 3) {
  await expireCommands(pool, schema, deviceId);
  const { rows } = await pool.query(
    `UPDATE "${schema}".camera_commands c
     SET status = 'DELIVERED', delivered_at = COALESCE(c.delivered_at, NOW())
     WHERE c.id IN (
       SELECT id FROM "${schema}".camera_commands
       WHERE device_id = $1 AND status IN ('PENDING', 'DELIVERED')
       ORDER BY created_at ASC LIMIT $2)
     RETURNING ${COMMAND_COLS}`, [deviceId, limit]);
  return rows.sort((a, b) => new Date(a.created_at) - new Date(b.created_at));
}

async function countOpenCommands(pool, schema, deviceId) {
  const { rows } = await pool.query(
    `SELECT COUNT(*)::int AS n FROM "${schema}".camera_commands
     WHERE device_id = $1 AND status IN ('PENDING', 'DELIVERED') AND expires_at > NOW()`, [deviceId]);
  return rows[0]?.n ?? 0;
}

/** Open command of this camera, or null (unknown id, other camera, already finished). */
async function findOpenCommand(pool, schema, deviceId, commandId) {
  if (!isUuid(commandId)) return null;
  const { rows } = await pool.query(
    `SELECT ${COMMAND_COLS} FROM "${schema}".camera_commands
     WHERE id = $1 AND device_id = $2 AND status IN ('PENDING', 'DELIVERED')`, [commandId, deviceId]);
  return rows[0] ?? null;
}

async function completeCommand(pool, schema, commandId, captureId) {
  await pool.query(
    `UPDATE "${schema}".camera_commands SET status = 'COMPLETED', completed_at = NOW(), capture_id = $2
     WHERE id = $1 AND status IN ('PENDING', 'DELIVERED')`, [commandId, captureId]);
}

async function failCommand(pool, schema, deviceId, commandId, error) {
  const { rowCount } = await pool.query(
    `UPDATE "${schema}".camera_commands SET status = 'FAILED', completed_at = NOW(), error = $3
     WHERE id = $1 AND device_id = $2 AND status IN ('PENDING', 'DELIVERED')`, [commandId, deviceId, error]);
  return rowCount > 0;
}

async function getCommand(pool, schema, commandId) {
  if (!isUuid(commandId)) return null;
  const { rows } = await pool.query(
    `SELECT ${COMMAND_COLS} FROM "${schema}".camera_commands WHERE id = $1`, [commandId]);
  const row = rows[0];
  if (row && ["PENDING", "DELIVERED"].includes(row.status) && new Date(row.expires_at).getTime() <= Date.now()) {
    await expireCommands(pool, schema, row.device_id);
    return { ...row, status: "EXPIRED" };
  }
  return row ?? null;
}

function isUuid(value) {
  return typeof value === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}

/** Wire shape for the camera (epoch ms keeps the firmware free of date parsing). */
function commandForDevice(row) {
  return { id: row.id, type: row.type, reason: row.reason, preview: Boolean(row.preview),
    issuedAt: new Date(row.created_at).getTime(), expiresAt: new Date(row.expires_at).getTime() };
}

/** Wire shape for Web/Mobile. */
function commandView(row, capture = null) {
  return { id: row.id, cameraId: row.device_id, type: row.type, reason: row.reason, preview: Boolean(row.preview),
    status: row.status, createdAt: row.created_at, deliveredAt: row.delivered_at, completedAt: row.completed_at,
    expiresAt: row.expires_at, captureId: row.capture_id ?? null, error: row.error ?? null, capture };
}

module.exports = {
  REASONS,
  cameraPushStatements,
  commandForDevice,
  commandView,
  completeCommand,
  countOpenCommands,
  enqueueCapture,
  ensureCameraPushSchema,
  failCommand,
  findOpenCommand,
  getCommand,
  isUuid,
  takeCommands,
};
