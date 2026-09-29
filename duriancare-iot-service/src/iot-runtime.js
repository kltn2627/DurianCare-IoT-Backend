const WATCHDOG_LOCK_KEY = 2026091301;

function requireInternalToken(expectedToken) {
  return (request, response, next) => {
    const provided = String(request.get("X-Internal-Token") || "").trim();
    if (!expectedToken || provided !== expectedToken) {
      response.status(401).json({ error: "Unauthorized", message: "Internal token is required" });
      return;
    }
    next();
  };
}

async function runOfflineWatchdog({
  alertsTable,
  devicesTable,
  evaluateOfflineDevices,
  farmAccessClient,
  healthConfig,
  logger = console,
  notificationPublisher,
  pool,
  now
}) {
  const locked = await tryAdvisoryLock(pool);
  if (!locked) {
    logger.info("iot.offlineWatchdog.skipped", { reason: "advisory_lock_busy" });
    return { created: [], recovered: [], skipped: true };
  }
  try {
    logger.info("iot.offlineWatchdog.started");
    const result = await evaluateOfflineDevices({
      alertsTable,
      devicesTable,
      farmAccessClient,
      healthConfig,
      notificationPublisher,
      pool,
      now
    });
    logger.info("iot.offlineWatchdog.finished", {
      created: result.created.length,
      recovered: result.recovered.length
    });
    return { ...result, skipped: false };
  } finally {
    await releaseAdvisoryLock(pool);
  }
}

function startOfflineWatchdogScheduler({
  alertsTable,
  devicesTable,
  evaluateOfflineDevices,
  farmAccessClient,
  healthConfig,
  logger = console,
  notificationPublisher,
  pool
}) {
  const intervalMs = Math.round(healthConfig.offlineWatchdogIntervalSeconds * 1000);
  if (!Number.isFinite(intervalMs) || intervalMs <= 0) {
    throw new Error("IOT_OFFLINE_WATCHDOG_INTERVAL_SECONDS must be a positive number");
  }
  const run = () =>
    runOfflineWatchdog({
      alertsTable,
      devicesTable,
      evaluateOfflineDevices,
      farmAccessClient,
      healthConfig,
      logger,
      notificationPublisher,
      pool
    }).catch((error) => {
      logger.error("iot.offlineWatchdog.failed", { message: error.message });
    });
  const timer = setInterval(run, intervalMs);
  if (typeof timer.unref === "function") {
    timer.unref();
  }
  logger.info("iot.offlineWatchdog.schedulerStarted", { intervalMs });
  run();
  return {
    intervalMs,
    stop() {
      clearInterval(timer);
    }
  };
}

async function tryAdvisoryLock(pool) {
  const result = await pool.query("SELECT pg_try_advisory_lock($1) AS locked", [WATCHDOG_LOCK_KEY]);
  return result.rows[0]?.locked === true;
}

async function releaseAdvisoryLock(pool) {
  await pool.query("SELECT pg_advisory_unlock($1)", [WATCHDOG_LOCK_KEY]);
}

module.exports = {
  WATCHDOG_LOCK_KEY,
  requireInternalToken,
  runOfflineWatchdog,
  startOfflineWatchdogScheduler
};
