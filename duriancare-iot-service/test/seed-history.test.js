const { test } = require("node:test");
const assert = require("node:assert/strict");
const { spawnSync } = require("node:child_process");
const path = require("node:path");
process.env.IOT_SEED_ACCOUNT_CREATED_AT = "2026-07-22T06:01:31.848Z";
process.env.IOT_SEED_TO = "2026-10-15T00:00:00Z";
process.env.IOT_SEED_DEVICE_ID = "demo-synthetic-sensors";
const { buildReadings, stableId, seed } = require("../scripts/seed-iot-history");

test("history is deterministic, bounded by account date, varied and marked by synthetic device", () => {
  const rows = buildReadings();
  assert.deepEqual(rows, buildReadings());
  assert.ok(rows.length > 400 && rows.length < 600);
  assert.equal(new Set(rows.map(stableId)).size, rows.length);
  for (const r of rows) {
    assert.ok(Date.parse(r.timestamp) >= Date.parse(process.env.IOT_SEED_ACCOUNT_CREATED_AT));
    assert.ok(Date.parse(r.timestamp) <= Date.parse(process.env.IOT_SEED_TO));
    assert.equal(r.deviceId, "demo-synthetic-sensors");
    const hour = (new Date(r.timestamp).getUTCHours() + 7) % 24;
    const day = hour >= 6 && hour < 18;
    assert.ok(r.temperature >= (day ? 29 : 24) && r.temperature <= (day ? 35 : 27));
    assert.ok(r.humidity >= (day ? 60 : 80) && r.humidity <= (day ? 75 : 95));
    assert.ok(r.soilMoisture >= 55 && r.soilMoisture <= 100);
  }
  assert.ok(new Set(rows.map(r => r.temperature)).size > 20);
});

test("CLI defaults dry-run and rejects real device, missing date and future range without connections", () => {
  const run = extra => spawnSync(process.execPath, [path.join(__dirname, "../scripts/seed-iot-history.js")], {
    env: { ...process.env, IOT_SEED_APPLY: "false", ...extra }, encoding: "utf8"
  });
  const result = run({});
  assert.equal(result.status, 0);
  assert.equal(JSON.parse(result.stdout).source, "DEMO_SYNTHETIC");
  assert.equal(JSON.parse(result.stdout).writes, 0);
  for (const extra of [{ IOT_SEED_DEVICE_ID: "esp32-01" }, { IOT_SEED_ACCOUNT_CREATED_AT: "" }, { IOT_SEED_TO: "2099-01-01" }]) {
    const failed = run(extra);
    assert.equal(failed.status, 1);
    assert.match(failed.stderr, /SEED_BLOCKED/);
  }
});

test("fixture writer serializes retries, rejects wrong scope and rolls back failures (mock DB only)", async () => {
  Object.assign(process.env, { IOT_SEED_APPLY: "true", IOT_SEED_ENVIRONMENT: "test",
    IOT_SEED_SCHEMA: "iot_demo_fixture_sensors", IOT_SEED_FIXTURE_PG_URI: "fixture-not-a-real-uri",
    IOT_SEED_FARM_ID: "fixture-farm", IOT_SEED_ZONE_ID: "fixture-zone", IOT_SEED_OWNER_ID: "fixture-owner" });
  const queries = [];
  const ids = new Set();
  let scope = true, fail = false;
  class FixturePool {
    async connect() { return { release() {}, async query(sql, args) {
      queries.push(sql);
      if (sql.startsWith("SELECT created_at")) return { rows: [{ created_at: new Date(process.env.IOT_SEED_ACCOUNT_CREATED_AT) }] };
      if (sql.startsWith("SELECT name")) {
        assert.deepEqual(args, ["demo-synthetic-sensors", "fixture-farm", "fixture-zone"]);
        return { rows: scope ? [{ name: "DEMO_SYNTHETIC sensor" }] : [] };
      }
      if (sql.startsWith("INSERT")) {
        if (fail) throw new Error("fixture failure");
        const exists = ids.has(args[5]); ids.add(args[5]); return { rowCount: exists ? 0 : 1 };
      }
      return { rows: [] };
    } }; }
    async end() {}
  }
  const oldLog = console.log;
  const reports = [];
  console.log = value => reports.push(JSON.parse(value));
  try {
    await seed({ Pool: FixturePool });
    await seed({ Pool: FixturePool });
    assert.ok(reports[0].inserted > 0);
    assert.equal(reports[1].inserted, 0);
    assert.ok(queries.some(q => q.includes("pg_advisory_xact_lock")));
    scope = false;
    await assert.rejects(seed({ Pool: FixturePool }), /FIXTURE_SCOPE/);
    assert.equal(queries.at(-1), "ROLLBACK");
    scope = true; fail = true;
    await assert.rejects(seed({ Pool: FixturePool }), /fixture failure/);
    assert.equal(queries.at(-1), "ROLLBACK");
    process.env.IOT_SEED_SCHEMA = "duriancare_iot";
    await assert.rejects(seed({ Pool: FixturePool }), /ISOLATED_FIXTURE/);
  } finally {
    console.log = oldLog;
    delete process.env.IOT_SEED_APPLY;
  }
});
