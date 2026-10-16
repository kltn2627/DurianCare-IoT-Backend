"use strict";

const cron = require("node-cron");
const { captureAndStore } = require("./cameraService");
const { getCameraConfig } = require("../config");
const { resolveCameraUrl } = require("./deviceService");

// deviceId → ScheduledTask[]
const activeTasks = new Map();

function clearDevice(deviceId) {
  const tasks = activeTasks.get(deviceId) ?? [];
  tasks.forEach((t) => t.stop());
  activeTasks.set(deviceId, []);
}

function scheduleOne({ deviceId, cronExpression, pool, schema, serverBaseUrl, uploadsDir, aiServiceUrl }) {
  if (!cron.validate(cronExpression)) {
    console.warn(`[camera-scheduler] Invalid cron "${cronExpression}" for device ${deviceId} — skipped`);
    return;
  }

  const task = cron.schedule(cronExpression, async () => {
    const { esp32CameraUrl: fallbackUrl, esp32CapturePath } = getCameraConfig();
    const { url: esp32CameraUrl, online, found } = await resolveCameraUrl(pool, schema, deviceId, fallbackUrl);
    if (found && !online) {
      console.log(`[camera-scheduler] Camera ${deviceId} is offline — skipping scheduled capture`);
      return;
    }
    try {
      await captureAndStore({
        pool, schema, deviceId,
        captureType: "SCHEDULED",
        esp32CameraUrl, esp32CapturePath, serverBaseUrl, uploadsDir, aiServiceUrl,
      });
      console.log(`[camera-scheduler] Scheduled capture + AI done for ${deviceId}`);
    } catch (err) {
      console.error(`[camera-scheduler] Scheduled capture failed for ${deviceId}:`, err.message);
    }
  });

  const list = activeTasks.get(deviceId) ?? [];
  list.push(task);
  activeTasks.set(deviceId, list);
}

async function loadAll({ pool, schema, serverBaseUrl, uploadsDir, aiServiceUrl }) {
  try {
    const { rows } = await pool.query(
      `SELECT device_id, cron_expression FROM "${schema}".camera_schedules WHERE enabled = true`,
    );
    for (const row of rows) {
      scheduleOne({
        deviceId: row.device_id,
        cronExpression: row.cron_expression,
        pool, schema, serverBaseUrl, uploadsDir, aiServiceUrl,
      });
    }
    console.log(`[camera-scheduler] ${rows.length} active schedule(s) loaded`);
  } catch (err) {
    console.error("[camera-scheduler] Failed to load schedules:", err.message);
  }
}

function reschedule({ deviceId, schedules, pool, schema, serverBaseUrl, uploadsDir, aiServiceUrl }) {
  clearDevice(deviceId);
  for (const s of schedules) {
    if (s.enabled) {
      scheduleOne({ deviceId, cronExpression: s.cron_expression, pool, schema, serverBaseUrl, uploadsDir, aiServiceUrl });
    }
  }
}

module.exports = { loadAll, reschedule };
