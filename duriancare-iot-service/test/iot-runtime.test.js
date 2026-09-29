const assert = require("node:assert/strict");
const { describe, it, mock } = require("node:test");

const {
  requireInternalToken,
  runOfflineWatchdog,
  startOfflineWatchdogScheduler
} = require("../src/iot-runtime");

describe("IoT runtime watchdog", () => {
  it("requires the internal token for watchdog endpoint access", async () => {
    const middleware = requireInternalToken("secret");
    const response = {
      body: null,
      code: null,
      json(payload) {
        this.body = payload;
        return this;
      },
      status(code) {
        this.code = code;
        return this;
      }
    };
    let nextCalled = false;
    middleware({ get: () => "" }, response, () => {
      nextCalled = true;
    });
    assert.equal(response.code, 401);
    assert.equal(nextCalled, false);
    middleware({ get: () => "secret" }, response, () => {
      nextCalled = true;
    });
    assert.equal(nextCalled, true);
  });

  it("uses advisory lock to avoid simultaneous watchdog work", async () => {
    const pool = lockPool({ locked: false });
    let evaluated = false;
    const result = await runOfflineWatchdog({
      alertsTable: "iot_alerts",
      devicesTable: "iot_devices",
      evaluateOfflineDevices: async () => {
        evaluated = true;
        return { created: [], recovered: [] };
      },
      farmAccessClient: {},
      healthConfig: {},
      logger: quietLogger(),
      notificationPublisher: {},
      pool
    });
    assert.equal(result.skipped, true);
    assert.equal(evaluated, false);
  });

  it("runs watchdog under lock and releases it", async () => {
    const pool = lockPool({ locked: true });
    const result = await runOfflineWatchdog({
      alertsTable: "iot_alerts",
      devicesTable: "iot_devices",
      evaluateOfflineDevices: async () => ({ created: ["a"], recovered: [] }),
      farmAccessClient: {},
      healthConfig: {},
      logger: quietLogger(),
      notificationPublisher: {},
      pool
    });
    assert.equal(result.created.length, 1);
    assert.equal(pool.unlocked, true);
  });

  it("externalizes scheduler interval from health config", () => {
    const setIntervalMock = mock.method(global, "setInterval", () => ({ unref() {} }));
    const clearIntervalMock = mock.method(global, "clearInterval", () => undefined);
    const scheduler = startOfflineWatchdogScheduler({
      alertsTable: "iot_alerts",
      devicesTable: "iot_devices",
      evaluateOfflineDevices: async () => ({ created: [], recovered: [] }),
      farmAccessClient: {},
      healthConfig: { offlineWatchdogIntervalSeconds: 45 },
      logger: quietLogger(),
      notificationPublisher: {},
      pool: lockPool({ locked: false })
    });
    assert.equal(scheduler.intervalMs, 45000);
    assert.equal(setIntervalMock.mock.calls[0].arguments[1], 45000);
    scheduler.stop();
    assert.equal(clearIntervalMock.mock.calls.length, 1);
    setIntervalMock.mock.restore();
    clearIntervalMock.mock.restore();
  });
});

function lockPool({ locked }) {
  return {
    unlocked: false,
    async query(sql) {
      if (sql.includes("pg_try_advisory_lock")) {
        return { rows: [{ locked }] };
      }
      if (sql.includes("pg_advisory_unlock")) {
        this.unlocked = true;
        return { rows: [{ pg_advisory_unlock: true }] };
      }
      return { rows: [] };
    }
  };
}

function quietLogger() {
  return { error: () => undefined, info: () => undefined };
}
