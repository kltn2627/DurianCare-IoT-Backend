const assert = require("node:assert/strict");
const { describe, it } = require("node:test");
const { spawnSync } = require("node:child_process");
const path = require("node:path");

const configPath = path.join(__dirname, "..", "src", "config.js");

describe("IoT health config", () => {
  it("loads documented defaults and watchdog interval", () => {
    const result = loadConfig({});
    assert.equal(result.status, 0);
    const health = JSON.parse(result.stdout).iotHealth;
    assert.equal(health.expectedTelemetryIntervalSeconds, 60);
    assert.equal(health.staleAfterIntervals, 3);
    assert.equal(health.offlineAfterIntervals, 10);
    assert.equal(health.offlineWatchdogIntervalSeconds, 60);
  });

  it("rejects invalid or incoherent health thresholds", () => {
    assert.notEqual(loadConfig({ IOT_EXPECTED_TELEMETRY_INTERVAL_SECONDS: "0" }).status, 0);
    assert.notEqual(loadConfig({ IOT_STALE_AFTER_INTERVALS: "-1" }).status, 0);
    assert.notEqual(loadConfig({ IOT_STALE_AFTER_INTERVALS: "10", IOT_OFFLINE_AFTER_INTERVALS: "10" }).status, 0);
    assert.notEqual(loadConfig({ IOT_OFFLINE_WATCHDOG_INTERVAL_SECONDS: "not-a-number" }).status, 0);
  });
});

function loadConfig(extraEnv) {
  return spawnSync(process.execPath, ["-e", `console.log(JSON.stringify(require(${JSON.stringify(configPath)})))`], {
    env: {
      ...process.env,
      IOT_EXPECTED_TELEMETRY_INTERVAL_SECONDS: "",
      IOT_STALE_AFTER_INTERVALS: "",
      IOT_OFFLINE_AFTER_INTERVALS: "",
      IOT_OFFLINE_WATCHDOG_INTERVAL_SECONDS: "",
      ...extraEnv
    },
    encoding: "utf8"
  });
}
