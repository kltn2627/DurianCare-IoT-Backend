const fs = require("node:fs");
const path = require("node:path");

async function createCameraFixtureSchema(client, schema) {
  if (!/^(camera|udp)_fixture_[a-f0-9]{32}$/.test(schema)) throw new Error("Invalid fixture namespace");
  const target = await client.query("SELECT current_database() AS name");
  if (target.rows[0]?.name !== "camera_registry_fixture") throw new Error("Disposable camera fixture database required");
  const migration = fs.readFileSync(path.join(__dirname, "../db/migration/V9__add_camera_devices.sql"), "utf8");
  const marker = "SET search_path TO duriancare_iot;";
  const parts = migration.split(marker);
  if (parts.length !== 2) throw new Error("Camera fixture migration contract changed");
  const sql = parts.join(`SET LOCAL search_path TO "${schema}";`);
  if (sql.includes("duriancare_iot")) throw new Error("Business schema reference forbidden in fixture DDL");
  await client.query(`CREATE SCHEMA "${schema}"`);
  await client.query(sql);
}
module.exports = { createCameraFixtureSchema };
