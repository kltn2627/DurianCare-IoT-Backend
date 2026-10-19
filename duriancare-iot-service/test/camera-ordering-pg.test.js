"use strict";
const {test} = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const {Pool} = require("pg");
const {createCameraFixtureSchema} = require("../test-support/camera-schema.cjs");
const {heartbeat, updateCameraUrl, upsertCamera, getCamera} = require("../src/camera/deviceService");

test("isolated PG serializes competing REST and UDP timestamps in either order", {
  skip: !process.env.DURIANCARE_CAMERA_FIXTURE_PG_URI && "isolated PostgreSQL required"
}, async () => {
  const pool = new Pool({connectionString: process.env.DURIANCARE_CAMERA_FIXTURE_PG_URI});
  const schema = "camera_fixture_" + crypto.randomUUID().replaceAll("-", "");
  const client = await pool.connect(); let created = false;
  try {
    const identity = await client.query("SELECT current_database() AS name");
    assert.equal(identity.rows[0].name, "camera_registry_fixture", "Must use disposable test database");
    await client.query("BEGIN"); await createCameraFixtureSchema(client, schema);
    await client.query("COMMIT"); created = true;
    for (const newerTransport of ["REST", "UDP"]) {
      const id = "fixture-" + newerTransport;
      await upsertCamera(pool, schema, {deviceId:id, farmId:"fixture-farm", ipAddress:"10.1.1.1"});
      const now = Date.now() + 10;
      const restTime = newerTransport === "REST" ? now + 2 : now + 1;
      const udpTime = newerTransport === "UDP" ? now + 2 : now + 1;
      await Promise.all([
        heartbeat(pool, schema, {deviceId:id,ipAddress:"10.1.1.2",expectedFarmId:"fixture-farm",announcedAt:restTime}),
        updateCameraUrl(pool, schema, id, {ipAddress:"10.1.1.3",protocol:"http",expectedFarmId:"fixture-farm",announcedAt:udpTime,
          expectedOrigin:{ipAddress:"10.1.1.1",port:80,protocol:"http"}})
      ]);
      const row = await getCamera(pool, schema, id);
      assert.equal(new Date(row.last_seen).getTime(), now + 2);
      assert.equal(row.ip_address, newerTransport === "REST" ? "10.1.1.2" : "10.1.1.3");
      assert.equal(row.zone_id, null);
      assert.equal(await updateCameraUrl(pool,schema,id,{ipAddress:"10.1.1.4",protocol:"http",expectedFarmId:"fixture-farm",announcedAt:now}),null);
      assert.equal(await heartbeat(pool,schema,{deviceId:id,ipAddress:"10.1.1.5",expectedFarmId:"fixture-farm",announcedAt:now}),null);
    }
  } finally {
    await client.query("ROLLBACK");
    if (created) {
      await client.query(`DROP SCHEMA "${schema}" CASCADE`);
      assert.equal((await client.query("SELECT 1 FROM information_schema.schemata WHERE schema_name=$1",[schema])).rowCount,0);
    }
    client.release(); await pool.end();
  }
});
