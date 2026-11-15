const assert = require("node:assert/strict");
const { describe, it } = require("node:test");
const express = require("express");

const {
  chooseBucketSeconds,
  parseBucketRange,
  parseHistoryRange,
  registerReadApi,
  requireActor,
  toTelemetryResponse
} = require("../src/read-api");

describe("IoT read API helpers", () => {
  it("requires gateway identity headers", () => {
    assert.throws(
      () => requireActor({ get: () => "" }),
      /Authenticated user is required/
    );
    const actor = requireActor({
      get: (name) => ({
        "X-Auth-Email": "farmer@example.test",
        "X-Auth-Role": "FARMER",
        "X-Auth-User-Id": "user-1"
      }[name])
    });
    assert.deepEqual(actor, {
      email: "farmer@example.test",
      role: "FARMER",
      userId: "user-1"
    });
  });

  it("validates history ranges and limits", () => {
    assert.throws(
      () => parseHistoryRange({ from: "2026-01-02T00:00:00.000Z", to: "2026-01-01T00:00:00.000Z" }),
      /from must be before/
    );
    assert.throws(() => parseHistoryRange({ limit: "1001" }), /limit must be/);
    const range = parseHistoryRange({
      from: "2026-01-01T00:00:00.000Z",
      limit: "10",
      to: "2026-01-01T01:00:00.000Z"
    });
    assert.equal(range.limit, 10);
    assert.equal(range.resolution, "raw");
  });
});

describe("IoT read API authorization", () => {
  it("protects legacy raw telemetry routes from missing identity and cross-farm access", async () => {
    for (const route of ["latest", "history"]) {
      const app = createTestApp({ allowedFarmIds: new Set(["farm-1"]), rowsByQuery: {find: [deviceRow({farm_id: "farm-2"})]} });
      app.get(`/api/v1/sensors/${route}`, (_req, res) => res.json({ reached: true }));
      assert.equal((await request(app, `/api/v1/sensors/${route}?device_id=esp32-1`, {auth: false})).status, 401);
      assert.equal((await request(app, `/api/v1/sensors/${route}?device_id=esp32-1`)).status, 403);
    }
  });

  it("does not treat an unmapped UID as evidence of ownership", async () => {
    const app = createTestApp({ allowedFarmIds: new Set(["farm-1"]), rowsByQuery: {find: []} });
    app.get("/api/v1/sensors/latest", (_req, res) => res.json({ reached: true }));
    assert.equal((await request(app, "/api/v1/sensors/latest?device_id=esp32-1")).status, 404);
  });

  it("requires a device scope but permits authorized legacy reads", async () => {
    const app = createTestApp({ allowedFarmIds: new Set(["farm-1"]), rowsByQuery: {find: [deviceRow()]} });
    app.get("/api/v1/sensors/latest", (_req, res) => res.json({ reached: true }));
    assert.equal((await request(app, "/api/v1/sensors/latest")).status, 400);
    assert.equal((await request(app, "/api/v1/sensors/latest?device_id=ESP32-1")).body.reached, true);
  });

  it("filters inaccessible devices from list", async () => {
    const app = createTestApp({
      allowedFarmIds: new Set(["farm-1"]),
      rowsByQuery: {
        list: [
          deviceRow({ farm_id: "farm-1", id: "dev-row-1" }),
          deviceRow({ farm_id: "farm-2", id: "dev-row-2" })
        ]
      }
    });
    const response = await request(app, "/api/iot/devices");
    assert.equal(response.status, 200);
    assert.equal(response.body.devices.length, 1);
    assert.equal(response.body.devices[0].farmId, "farm-1");
  });

  it("denies direct device access when farm authorization rejects it", async () => {
    const app = createTestApp({
      allowedFarmIds: new Set(["farm-1"]),
      rowsByQuery: {
        find: [deviceRow({ farm_id: "farm-2", id: "dev-row-2" })]
      }
    });
    const response = await request(app, "/api/iot/devices/dev-row-2/telemetry/latest");
    assert.equal(response.status, 403);
  });

  it("returns the latest telemetry server-side", async () => {
    const app = createTestApp({
      allowedFarmIds: new Set(["farm-1"]),
      rowsByQuery: {
        find: [deviceRow({ farm_id: "farm-1", id: "dev-row-1" })],
        latest: [
          {
            device_id: "esp32-1",
            humidity: "78.2",
            id: "reading-2",
            light: "330",
            received_at: "2026-09-13T02:01:00.000Z",
            temperature: "29.8",
            timestamp: "2026-09-13T02:00:00.000Z"
          }
        ]
      }
    });
    const response = await request(app, "/api/iot/devices/dev-row-1/telemetry/latest");
    assert.equal(response.status, 200);
    assert.equal(response.body.telemetry.id, "reading-2");
    assert.equal(response.body.telemetry.temperature, 29.8);
  });

  it("matches registry device UIDs and telemetry IDs case-insensitively", async () => {
    const app = createTestApp({
      allowedFarmIds: new Set(["farm-1"]),
      rowsByQuery: {
        find: [deviceRow({ device_uid: "ESP32-01", farm_id: "farm-1", id: "dev-row-1" })],
        latest: [
          {
            device_id: "esp32-01",
            humidity: "83.5",
            id: "reading-esp32",
            light: null,
            received_at: "2026-10-14T11:16:43.000Z",
            soil_moisture: "92",
            temperature: "33.5",
            timestamp: "2026-10-14T11:16:42.481Z"
          }
        ]
      }
    });
    const response = await request(app, "/api/iot/devices/esp32-01/telemetry/latest");
    assert.equal(response.status, 200);
    assert.equal(response.body.telemetry.deviceUid, "esp32-01");
    assert.equal(response.body.telemetry.humidity, 83.5);
    assert.equal(response.body.telemetry.soilMoisture, 92);
    assert.equal(response.body.telemetry.status, "WET_WARNING");
    assert.equal(
      app.locals.sqlLog.some((sql) => /LOWER\(d\.device_uid\)\s*=\s*LOWER\(\$1\)/i.test(sql)),
      true
    );
    assert.equal(
      app.locals.sqlLog.some((sql) => /LOWER\(device_id\)\s*=\s*LOWER\(\$1\)/i.test(sql)),
      true
    );
  });

  it("normalizes legacy air_humidity telemetry payloads", () => {
    const payload = toTelemetryResponse({
      air_humidity: "83.5",
      device_id: "esp32-01",
      id: "reading-legacy",
      light: null,
      received_at: null,
      soil_moisture: "57",
      temperature: "33.5",
      timestamp: "2026-10-14T11:16:42.481Z"
    });
    assert.equal(payload.humidity, 83.5);
    assert.equal(payload.soilMoisture, 57);
    assert.equal(payload.status, "DRY_WARNING");
    assert.equal(payload.deviceUid, "esp32-01");
  });
});

describe("IoT device management authorization", () => {
  it("binds a device directly only for administrators; a farmer must use a claim code even on own farm", async () => {
    const app = createTestApp({
      allowedFarmIds: new Set(["farm-1"]),
      manageableFarmIds: new Set(["farm-1"]),
      rowsByQuery: {}
    });
    const body = {
      cultivationAreaId: "area-1",
      deviceUid: "esp32:claim-1",
      farmId: "farm-1",
      name: "North Block ESP32"
    };
    const farmer = await request(app, "/api/iot/devices", { body, method: "POST" });
    assert.equal(farmer.status, 403);
    assert.match(farmer.body.message, /claim code/);
    assert.equal(app.locals.sqlLog.some((sql) => sql.includes("INSERT INTO")), false);
    const admin = await request(app, "/api/iot/devices", { body, method: "POST", role: "ADMIN" });
    assert.equal(admin.status, 201);
    assert.equal(admin.body.device.deviceUid, "esp32:claim-1");
    assert.equal(admin.body.device.farmId, "farm-1");
  });

  it("rejects registration into a farm the actor cannot configure", async () => {
    const app = createTestApp({
      allowedFarmIds: new Set(["farm-1"]),
      manageableFarmIds: new Set(["farm-1"]),
      rowsByQuery: {}
    });
    const response = await request(app, "/api/iot/devices", {
      body: { deviceUid: "esp32-2", farmId: "farm-2" },
      method: "POST"
    });
    assert.equal(response.status, 403);
  });

  it("maps duplicate deviceUid registration to conflict", async () => {
    const app = createTestApp({
      allowedFarmIds: new Set(["farm-1"]),
      duplicateInsert: true,
      manageableFarmIds: new Set(["farm-1"]),
      rowsByQuery: {}
    });
    const response = await request(app, "/api/iot/devices", {
      body: { deviceUid: "esp32-1", farmId: "farm-1" },
      method: "POST",
      role: "ADMIN"
    });
    assert.equal(response.status, 409);
  });

  it("blocks read-only farm users from mutating device assignment", async () => {
    const app = createTestApp({
      allowedFarmIds: new Set(["farm-1"]),
      manageableFarmIds: new Set(),
      rowsByQuery: {
        find: [deviceRow({ farm_id: "farm-1", id: "dev-row-1" })]
      }
    });
    const response = await request(app, "/api/iot/devices/dev-row-1", {
      body: { name: "Renamed" },
      method: "PATCH"
    });
    assert.equal(response.status, 403);
  });

  it("renames a device when the actor can configure its farm", async () => {
    const app = createTestApp({
      allowedFarmIds: new Set(["farm-1"]),
      manageableFarmIds: new Set(["farm-1"]),
      rowsByQuery: {
        find: [deviceRow({ farm_id: "farm-1", id: "dev-row-1", name: "Old name" })]
      }
    });
    const response = await request(app, "/api/iot/devices/dev-row-1", {
      body: { name: "Renamed" },
      method: "PATCH"
    });
    assert.equal(response.status, 200);
    assert.equal(response.body.device.name, "Renamed");
    assert.equal(response.body.device.farmId, "farm-1");
  });

  it("allows reassignment only after access checks on current and target scope", async () => {
    const app = createTestApp({
      allowedFarmIds: new Set(["farm-1"]),
      manageableFarmIds: new Set(["farm-1"]),
      manageableScopes: new Set(["farm-1:area-1", "farm-1:area-2"]),
      rowsByQuery: {
        find: [deviceRow({ cultivation_area_id: "area-1", farm_id: "farm-1", id: "dev-row-1" })]
      }
    });
    const response = await request(app, "/api/iot/devices/dev-row-1", {
      body: { cultivationAreaId: "area-2" },
      method: "PATCH"
    });
    assert.equal(response.status, 200);
    assert.equal(response.body.device.cultivationAreaId, "area-2");
  });

  it("rejects reassignment to a farm or area outside management scope", async () => {
    const app = createTestApp({
      allowedFarmIds: new Set(["farm-1"]),
      manageableFarmIds: new Set(["farm-1"]),
      manageableScopes: new Set(["farm-1:area-1"]),
      rowsByQuery: {
        find: [deviceRow({ cultivation_area_id: "area-1", farm_id: "farm-1", id: "dev-row-1" })]
      }
    });
    const response = await request(app, "/api/iot/devices/dev-row-1", {
      body: { cultivationAreaId: "area-2" },
      method: "PATCH"
    });
    assert.equal(response.status, 403);
  });

  it("does not reveal or mutate a device when an unrelated actor guesses its id", async () => {
    const app = createTestApp({
      allowedFarmIds: new Set(),
      manageableFarmIds: new Set(),
      rowsByQuery: {
        find: [deviceRow({ farm_id: "farm-2", id: "dev-row-2" })]
      }
    });
    const response = await request(app, "/api/iot/devices/dev-row-2", {
      body: { status: "MAINTENANCE" },
      method: "PATCH"
    });
    assert.equal(response.status, 403);
  });

  it("soft-deletes only the registry row and leaves telemetry untouched", async () => {
    const app = createTestApp({
      allowedFarmIds: new Set(["farm-1"]),
      manageableFarmIds: new Set(["farm-1"]),
      rowsByQuery: {
        find: [deviceRow({ farm_id: "farm-1", id: "dev-row-1" })]
      }
    });
    const response = await request(app, "/api/iot/devices/dev-row-1", { method: "DELETE" });
    assert.equal(response.status, 200);
    assert.equal(response.body.device.status, "DELETED");
    assert.equal(app.locals.sqlLog.some((sql) => /DELETE\s+FROM\s+telemetry/i.test(sql)), false);
  });

  it("requires authentication for device mutation routes", async () => {
    const app = createTestApp({
      allowedFarmIds: new Set(["farm-1"]),
      manageableFarmIds: new Set(["farm-1"]),
      rowsByQuery: {}
    });
    const response = await request(app, "/api/iot/devices", {
      auth: false,
      body: { deviceUid: "esp32-1", farmId: "farm-1" },
      method: "POST"
    });
    assert.equal(response.status, 401);
  });
});

describe("IoT alert API authorization", () => {
  it("lists only alerts from readable farms", async () => {
    const app = createTestApp({
      allowedFarmIds: new Set(["farm-1"]),
      rowsByQuery: {
        alertList: [
          alertRow({ farm_id: "farm-1", id: "alert-1" }),
          alertRow({ farm_id: "farm-2", id: "alert-2" })
        ]
      }
    });
    const response = await request(app, "/api/iot/alerts");
    assert.equal(response.status, 200);
    assert.equal(response.body.alerts.length, 1);
    assert.equal(response.body.alerts[0].farmId, "farm-1");
  });

  it("denies guessed alert IDs outside farm authorization", async () => {
    const app = createTestApp({
      allowedFarmIds: new Set(["farm-1"]),
      rowsByQuery: {
        alertFind: [alertRow({ farm_id: "farm-2", id: "alert-2" })]
      }
    });
    const response = await request(app, "/api/iot/alerts/alert-2");
    assert.equal(response.status, 403);
  });

  it("acknowledges own farm alert only after read authorization", async () => {
    const app = createTestApp({
      allowedFarmIds: new Set(["farm-1"]),
      rowsByQuery: {
        alertFind: [alertRow({ farm_id: "farm-1", id: "alert-1" })]
      }
    });
    const response = await request(app, "/api/iot/alerts/alert-1/acknowledge", { method: "POST" });
    assert.equal(response.status, 200);
    assert.equal(response.body.alert.status, "ACKNOWLEDGED");
    assert.equal(response.body.alert.acknowledgedBy, "user-1");
  });

  it("requires authentication for alert reads", async () => {
    const app = createTestApp({
      allowedFarmIds: new Set(["farm-1"]),
      rowsByQuery: { alertList: [alertRow()] }
    });
    const response = await request(app, "/api/iot/alerts", { auth: false });
    assert.equal(response.status, 401);
  });
});

describe("IoT downsampled history", () => {
  it("chooses the smallest bucket that keeps the range within maxBuckets", () => {
    const day = 86400 * 1000;
    assert.equal(chooseBucketSeconds(day, 240), 600);
    assert.equal(chooseBucketSeconds(31 * day, 240), 21600);
    // Season 2026-06-01 .. 2026-10-22 renders as daily buckets.
    assert.equal(chooseBucketSeconds(143 * day, 240), 86400);
    assert.equal(chooseBucketSeconds(3 * 365 * day, 240), 604800);
    assert.ok(chooseBucketSeconds(30 * 365 * day, 240) % 86400 === 0);
  });

  it("validates bucket ranges", () => {
    assert.throws(() => parseBucketRange({ from: "2026-02-01", to: "2026-01-01" }), /from must be before/);
    assert.throws(() => parseBucketRange({ maxBuckets: "0" }), /maxBuckets must be/);
    assert.throws(() => parseBucketRange({ from: "nope" }), /valid ISO-8601/);
    const range = parseBucketRange({ from: "2026-06-01T00:00:00Z", to: "2026-10-22T00:00:00Z" });
    assert.equal(range.resolution, "bucketed");
    assert.equal(range.bucketSeconds, 86400);
  });

  it("returns per-source buckets and never merges measured with synthetic values", async () => {
    const app = createTestApp({
      allowedFarmIds: new Set(["farm-1"]),
      rowsByQuery: {
        buckets: [
          bucketRow({ source: "DEVICE", temperature_avg: "30.123", sample_count: 8640 }),
          bucketRow({ source: "SYNTHETIC_BACKFILL", temperature_avg: "27", sample_count: 144, soil_moisture_avg: null, dry_count: 0, optimal_count: 0, wet_count: 0 })
        ],
        find: [deviceRow({ device_uid: "esp32-01" })]
      }
    });
    const response = await request(app, "/api/iot/devices/dev-row-1/telemetry/buckets?from=2026-06-01T00:00:00Z&to=2026-10-22T00:00:00Z");
    assert.equal(response.status, 200);
    assert.equal(response.body.range.bucketSeconds, 86400);
    assert.deepEqual(response.body.buckets.map((b) => [b.source, b.count, b.temperature]), [
      ["DEVICE", 8640, 30.12],
      ["SYNTHETIC_BACKFILL", 144, 27]
    ]);
    assert.equal(response.body.buckets[0].bucketEnd, "2026-08-13T17:00:00.000Z");
    assert.deepEqual(response.body.buckets[0].soilStatusCounts, { DRY_WARNING: 1, OPTIMAL: 2, WET_WARNING: 3 });
    assert.equal(response.body.buckets[1].soilMoisture, null);
    const sql = app.locals.sqlLog.find((text) => text.includes("date_bin"));
    assert.match(sql, /GROUP BY 1, 2/);
    assert.match(sql, /LOWER\(device_id\) = LOWER\(\$1\)/);
    assert.deepEqual(app.locals.bucketParams.slice(0, 4), ["esp32-01", "2026-06-01T00:00:00.000Z", "2026-10-22T00:00:00.000Z", 86400]);
  });

  it("applies identity and farm access to both bucket routes", async () => {
    for (const path of ["/api/iot/devices/dev-row-2/telemetry/buckets", "/api/v1/sensors/history/buckets?device_id=esp32-1"]) {
      const app = createTestApp({ allowedFarmIds: new Set(["farm-1"]), rowsByQuery: { find: [deviceRow({ farm_id: "farm-2" })] } });
      assert.equal((await request(app, path, { auth: false })).status, 401);
      assert.equal((await request(app, path)).status, 403);
      assert.equal(app.locals.sqlLog.some((text) => text.includes("date_bin")), false);
    }
    const app = createTestApp({ allowedFarmIds: new Set(["farm-1"]), rowsByQuery: { buckets: [bucketRow()], find: [deviceRow()] } });
    const ok = await request(app, "/api/v1/sensors/history/buckets?device_id=ESP32-1&from=2026-10-01T00:00:00Z&to=2026-10-02T00:00:00Z");
    assert.equal(ok.status, 200);
    assert.equal(ok.body.buckets.length, 1);
    assert.equal((await request(app, "/api/v1/sensors/history/buckets")).status, 400);
  });
});

function bucketRow(overrides = {}) {
  return {
    bucket_start: new Date("2026-08-12T17:00:00.000Z"),
    dry_count: 1,
    humidity_avg: "80",
    light_avg: null,
    optimal_count: 2,
    sample_count: 6,
    soil_moisture_avg: "70",
    source: "DEVICE",
    temperature_avg: "30",
    temperature_max: "33",
    temperature_min: "27",
    wet_count: 3,
    ...overrides
  };
}

function createTestApp({ allowedFarmIds, duplicateInsert = false, manageableFarmIds, manageableScopes, rowsByQuery }) {
  const app = express();
  app.use(express.json({ limit: "32kb" }));
  app.locals.sqlLog = [];
  const pool = {
    async query(sql, params = []) {
      app.locals.sqlLog.push(sql);
      if (sql.includes("date_bin")) {
        app.locals.bucketParams = params;
        return { rows: rowsByQuery.buckets || [] };
      }
      if (sql.includes("ORDER BY COALESCE")) {
        return { rows: rowsByQuery.list || [] };
      }
      if (sql.includes("FROM iot_alerts a") && sql.includes("WHERE a.id::text")) {
        return { rowCount: (rowsByQuery.alertFind || []).length, rows: rowsByQuery.alertFind || [] };
      }
      if (sql.includes("FROM iot_alerts a")) {
        return { rowCount: (rowsByQuery.alertList || []).length, rows: rowsByQuery.alertList || [] };
      }
      if (sql.includes("UPDATE iot_alerts")) {
        const current = rowsByQuery.alertFind?.[0] || alertRow({ id: params[0] });
        return {
          rowCount: 1,
          rows: [{ ...current, acknowledged_by: params[1], status: "ACKNOWLEDGED" }]
        };
      }
      if (sql.includes("(d.id::text")) {
        return { rowCount: (rowsByQuery.find || []).length, rows: rowsByQuery.find || [] };
      }
      if (sql.includes("INSERT INTO")) {
        if (duplicateInsert) {
          const error = new Error("duplicate key value violates unique constraint");
          error.code = "23505";
          throw error;
        }
        return {
          rowCount: 1,
          rows: [
            deviceRow({
              cultivation_area_id: params[3],
              device_uid: params[0],
              farm_id: params[2],
              id: "created-device",
              latest_id: null,
              name: params[1],
              status: params[4]
            })
          ]
        };
      }
      if (sql.includes("SET status = 'DELETED'")) {
        const current = rowsByQuery.find?.[0] || deviceRow({ id: params[0] });
        return {
          rowCount: 1,
          rows: [deviceRow({ ...current, latest_id: null, status: "DELETED" })]
        };
      }
      if (sql.includes("UPDATE")) {
        const current = rowsByQuery.find?.[0] || deviceRow({ id: params[0] });
        return {
          rowCount: 1,
          rows: [
            deviceRow({
              ...current,
              cultivation_area_id: params[3],
              farm_id: params[2],
              latest_id: null,
              name: params[1] ?? current.name,
              status: params[4] ?? current.status
            })
          ]
        };
      }
      if (sql.includes("ORDER BY \"timestamp\" DESC")) {
        return { rows: rowsByQuery.latest || [] };
      }
      if (sql.includes("ORDER BY \"timestamp\" ASC")) {
        return { rows: rowsByQuery.history || [] };
      }
      return { rows: [] };
    }
  };
  registerReadApi(app, {
    alertsTable: "iot_alerts",
    devicesTable: "iot_devices",
    farmAccessClient: {
      async canReadIot(_actor, farmId) {
        return allowedFarmIds.has(farmId);
      },
      async canManageIot(_actor, farmId, cultivationAreaId) {
        if (manageableScopes) {
          return manageableScopes.has(`${farmId}:${cultivationAreaId || ""}`);
        }
        return (manageableFarmIds || allowedFarmIds).has(farmId);
      }
    },
    pool,
    telemetryTable: "telemetry"
  });
  return app;
}

function alertRow(overrides = {}) {
  return {
    acknowledged_at: null,
    acknowledged_by: null,
    cultivation_area_id: "area-1",
    device_name: "ESP32 North",
    device_registry_id: "dev-row-1",
    device_uid: "esp32-1",
    farm_id: "farm-1",
    id: "alert-1",
    last_observed_at: "2026-09-13T01:00:00.000Z",
    measured_value: 40,
    message: "ESP32 North: TEMPERATURE_HIGH",
    recovered_at: null,
    started_at: "2026-09-13T01:00:00.000Z",
    status: "ALERTING",
    threshold_value: 38,
    type: "TEMPERATURE_HIGH",
    ...overrides
  };
}

function deviceRow(overrides = {}) {
  return {
    cultivation_area_id: "area-1",
    device_uid: "esp32-1",
    farm_id: "farm-1",
    humidity: "75",
    id: "dev-row-1",
    latest_id: "reading-1",
    latest_received_at: "2026-09-13T01:01:00.000Z",
    latest_timestamp: "2026-09-13T01:00:00.000Z",
    light: "220",
    name: "ESP32 North",
    status: "ACTIVE",
    temperature: "28.5",
    ...overrides
  };
}

async function request(app, path, options = {}) {
  const server = await new Promise((resolve) => {
    const instance = app.listen(0, () => resolve(instance));
  });
  try {
    const port = server.address().port;
    const headers = {
      "Content-Type": "application/json"
    };
    if (options.auth !== false) {
      headers["X-Auth-Role"] = options.role || "FARMER";
      headers["X-Auth-User-Id"] = "user-1";
    }
    const response = await fetch(`http://127.0.0.1:${port}${path}`, {
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      headers,
      method: options.method || "GET"
    });
    return {
      body: await response.json(),
      status: response.status
    };
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
}
