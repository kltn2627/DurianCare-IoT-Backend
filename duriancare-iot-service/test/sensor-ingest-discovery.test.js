const { test } = require("node:test");
const assert = require("node:assert/strict");
const { fork } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");
const { EventEmitter } = require("node:events");
const {
  buildServerAnnouncement, buildLegacyAnnouncement, parseServerAnnouncement, directedBroadcast,
} = require("../src/discovery/serverAnnouncement");
const { createHostBeacon } = require("../src/discovery/hostBeacon");
const { selectLanInterface } = require("../src/discovery/udpDiscoveryService");

async function withService(run) {
  const child = fork(path.join(__dirname, "../src/main.js"), [], { silent: true,
    execArgv: ["-r", path.join(__dirname, "../test-support/sensor-ingest.cjs")] });
  try {
    const port = await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error("sensor fixture timed out")), 8000);
      child.on("message", m => { if (m?.port) { clearTimeout(timer); resolve(m.port); } });
      child.on("exit", code => reject(new Error(`fixture exited ${code}`)));
    });
    const inserts = () => new Promise(resolve => {
      const onMessage = m => { if (m?.inserts) { child.off("message", onMessage); resolve(m.inserts); } };
      child.on("message", onMessage);
      child.send("inserts");
    });
    const queries = () => new Promise(resolve => {
      const onMessage = m => { if (m?.queries) { child.off("message", onMessage); resolve(m.queries); } };
      child.on("message", onMessage);
      child.send("queries");
    });
    const get = pathAndQuery => fetch(`http://127.0.0.1:${port}${pathAndQuery}`);
    const post = body => fetch(`http://127.0.0.1:${port}/api/v1/sensors/data`, {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
    await run({ post, inserts, queries, get });
  } finally { child.kill(); }
}

test("sensor ingestion accepts the exact firmware payload with 201 and rejects bad device_id / values", async () => {
  await withService(async ({ post, inserts }) => {
    const ok = await post({ device_id: "esp32-01", temperature: 30.5, humidity: 70.1, soil_moisture: 42 });
    assert.equal(ok.status, 201);
    assert.equal((await ok.json()).device_id, "esp32-01");
    // Firmware omits temperature/humidity while the DHT22 is not ready.
    assert.equal((await post({ device_id: "esp32-01", soil_moisture: 40 })).status, 201);
    for (const bad of [{ temperature: 30 }, { device_id: "", soil_moisture: 1 }, { device_id: 7, soil_moisture: 1 },
      { device_id: "esp32-01", temperature: "hot" }, { device_id: "esp32-01" }]) {
      assert.equal((await post(bad)).status, 400, JSON.stringify(bad));
    }
    const rows = await inserts();
    assert.equal(rows.length, 2, "rejected payloads must not be stored");
    assert.deepEqual(rows[0].slice(0, 4), ["esp32-01", 30.5, 70.1, 42]);
  });
});

test("history returns provenance; latest values and startup schema only trust device readings", async () => {
  await withService(async ({ get, queries, post }) => {
    assert.equal((await post({ device_id: "esp32-01", temperature: 30, humidity: 70, soil_moisture: 50 })).status, 201);
    const history = await (await get("/api/v1/sensors/history?device_id=esp32-01&limit=10")).json();
    assert.deepEqual(history.data.map(r => r.source), ["SYNTHETIC_BACKFILL", "DEVICE"]);
    await get("/api/v1/sensors/latest?device_id=esp32-01");
    await get("/api/v1/sensors/latest");
    const sql = await queries();
    // Latest-value reads of telemetry (the device-registry lookup also uses LIMIT 1 but reads no telemetry).
    const latest = sql.filter(q => q.includes(".telemetry") && (q.includes("LIMIT 1") || q.includes("DISTINCT ON")));
    assert.ok(latest.length >= 2 && latest.every(q => q.includes("source = 'DEVICE'")));
    assert.ok(sql.some(q => q.includes("ADD COLUMN IF NOT EXISTS source")), "provenance schema ensured at startup");
    const insert = sql.find(q => q.includes("INSERT INTO") && q.includes("telemetry"));
    assert.ok(insert && !insert.includes("SYNTHETIC"), "live ingestion keeps the DEVICE default");
  });
});

test("ingestion accepts only registered, claimed devices and records firmware + config acknowledgement", async () => {
  await withService(async ({ post, inserts, queries }) => {
    const unknown = await post({ device_id: "unknown-77", soil_moisture: 40 });
    assert.equal(unknown.status, 404);
    assert.equal((await unknown.json()).code, "DEVICE_NOT_REGISTERED");
    const unclaimed = await post({ device_id: "unclaimed-78", soil_moisture: 40 });
    assert.equal(unclaimed.status, 409);
    assert.equal((await unclaimed.json()).code, "DEVICE_NOT_CLAIMED");
    assert.equal((await inserts()).length, 0, "nothing stored for unknown or unclaimed devices");

    assert.equal((await post({ device_id: "esp32-01", soil_moisture: 40, firmware_version: "bad version!" })).status, 400);
    assert.equal((await post({ device_id: "esp32-01", soil_moisture: 40, config_version: -1 })).status, 400);

    const first = await post({ device_id: "esp32-01", temperature: 30, soil_moisture: 40, firmware_version: "1.3.0" });
    assert.equal(first.status, 201);
    assert.deepEqual((await first.json()).config, { version: 3, publish_interval_seconds: 30 });
    const applied = await post({ device_id: "esp32-01", soil_moisture: 41, config_version: 3 });
    assert.equal(applied.status, 201);
    assert.equal((await applied.json()).config, undefined, "an applied version is not sent again");

    const rows = await inserts();
    assert.equal(rows.length, 2);
    assert.deepEqual(rows[0].slice(6, 8), ["farm-1", "area-1"], "zone attribution captured at ingestion time");
    const sql = await queries();
    assert.ok(sql.some((q) => q.includes("SET firmware_version")), "firmware version recorded");
    assert.ok(sql.some((q) => q.includes("delivery_status = 'DELIVERED'")), "config marked delivered");
    assert.ok(sql.some((q) => q.includes("delivery_status = 'APPLIED'")), "config marked applied after the device echoed it");
  });
});

test("compose publishes the iot-service HTTP port 3001 on the host", () => {
  const compose = fs.readFileSync(path.join(__dirname, "../../infrastructure/docker-compose.yml"), "utf8");
  const block = compose.slice(compose.indexOf("\n  duriancare-iot-service:")).slice(0, 1500);
  assert.match(block, /- "3001:3001"/);
  assert.match(block, /PORT: 3001/);
});

test("v1 announcement round-trips and invalid packets are ignored", () => {
  const text = buildServerAnnouncement({ ip: "192.168.1.10", port: 3001 });
  assert.equal(text, '{"service":"duriancare-iot","v":1,"ip":"192.168.1.10","port":3001,"path":"/api/v1/sensors/data"}');
  assert.deepEqual(parseServerAnnouncement(Buffer.from(text)),
    { ip: "192.168.1.10", port: 3001, path: "/api/v1/sensors/data", url: "http://192.168.1.10:3001/api/v1/sensors/data" });
  const v1 = fields => JSON.stringify({ service: "duriancare-iot", v: 1, ip: "192.168.1.10", port: 3001, path: "/x", ...fields });
  const bad = [
    "", "garbage", buildLegacyAnnouncement({ ip: "192.168.1.10", port: 3001 }),
    v1({ service: "other" }), v1({ v: 2 }), v1({ ip: "127.0.0.1" }), v1({ ip: "999.1.1.1" }), v1({ ip: "0.0.0.0" }),
    v1({ port: 70000 }), v1({ port: 0 }), v1({ port: "3001" }), v1({ path: "x" }), v1({ path: "/../etc" }),
    v1({ path: "/a?b=1" }), "x".repeat(300),
  ];
  for (const packet of bad) assert.equal(parseServerAnnouncement(packet), null, packet.slice(0, 80));
  assert.throws(() => buildServerAnnouncement({ ip: "0.0.0.0", port: 3001 }));
  assert.equal(directedBroadcast("192.168.1.10", "255.255.255.0"), "192.168.1.255");
  assert.equal(directedBroadcast("172.20.10.3", "255.255.255.240"), "172.20.10.15");
  assert.equal(directedBroadcast("10.0.0.5", undefined), null);
});

test("LAN interface selection skips Docker/WSL/loopback and explains the choice", () => {
  const pick = selectLanInterface({
    "vEthernet (WSL (Hyper-V firewall))": [{ family: "IPv4", address: "172.17.48.1", netmask: "255.255.240.0", internal: false }],
    "Loopback Pseudo-Interface 1": [{ family: "IPv4", address: "127.0.0.1", netmask: "255.0.0.0", internal: true }],
    "Ethernet 2": [{ family: "IPv4", address: "192.168.1.10", netmask: "255.255.255.0", internal: false }],
  });
  assert.equal(pick.ip, "192.168.1.10");
  assert.equal(pick.name, "Ethernet 2");
  assert.equal(pick.netmask, "255.255.255.0");
  assert.match(pick.reason, /physical/);
});

function fakeBeacon({ nics, httpUp = () => true }) {
  const sockets = [];
  const logs = [];
  const beacon = createHostBeacon({
    env: {}, networkInterfaces: () => nics.current,
    probeHttp: async ip => httpUp(ip),
    logger: { info: m => logs.push(m), warn: m => logs.push(m) },
    createSocket: () => {
      const socket = new EventEmitter();
      Object.assign(socket, { sent: [], closed: false, boundTo: null, broadcast: false,
        bind(_port, ip, cb) { this.boundTo = ip; setImmediate(cb); },
        setBroadcast(v) { this.broadcast = v; },
        send(buf, port, host, cb) { this.sent.push({ text: buf.toString(), port, host }); cb?.(); },
        close() { this.closed = true; } });
      sockets.push(socket);
      return socket;
    },
  });
  return { beacon, sockets, logs };
}

const wifi = ip => ({ "Wi-Fi": [{ family: "IPv4", address: ip, netmask: "255.255.255.0", internal: false }] });

test("host beacon announces v1 + legacy to subnet and limited broadcast from the LAN address", async () => {
  const { beacon, sockets } = fakeBeacon({ nics: { current: wifi("192.168.1.10") } });
  assert.equal((await beacon.tick()).announced, true);
  assert.equal(sockets[0].boundTo, "192.168.1.10");
  assert.equal(sockets[0].broadcast, true);
  assert.deepEqual([...new Set(sockets[0].sent.map(s => s.host))], ["192.168.1.255", "255.255.255.255"]);
  assert.ok(sockets[0].sent.every(s => s.port === 4210));
  assert.equal(parseServerAnnouncement(sockets[0].sent[0].text).url, "http://192.168.1.10:3001/api/v1/sensors/data");
  assert.equal(sockets[0].sent[1].text, "DURIANCARE_SERVER:http://192.168.1.10:3001");
});

test("host beacon follows a server IP change and never announces an unreachable or missing address", async () => {
  const nics = { current: wifi("192.168.1.8") };
  let up = true;
  const { beacon, sockets, logs } = fakeBeacon({ nics, httpUp: () => up });
  await beacon.tick();
  nics.current = wifi("172.20.10.3"); // laptop moved to a phone hotspot
  assert.equal((await beacon.tick()).ip, "172.20.10.3");
  assert.equal(sockets[0].closed, true, "old socket released");
  assert.equal(parseServerAnnouncement(sockets[1].sent[0].text).ip, "172.20.10.3");
  assert.ok(logs.some(l => l.includes('Selected 172.20.10.3 on "Wi-Fi"')));
  up = false;
  const before = sockets[1].sent.length;
  assert.deepEqual(await beacon.tick(), { announced: false, reason: "HTTP_UNREACHABLE" });
  assert.equal(sockets[1].sent.length, before);
  nics.current = {};
  assert.deepEqual(await beacon.tick(), { announced: false, reason: "NO_LAN" });
  assert.ok(sockets.every(s => s.sent.every(p => !p.text.includes("127.0.0.1"))));
});

test("soil probe quality (firmware 1.4.0): invalid/uncalibrated soil is stored as raw + status, never as a percentage", async () => {
  await withService(async ({ post, inserts }) => {
    // Exact firmware 1.4.0 payload: probe reads 0 V (unconnected AO) while the DHT22 works.
    const noSignal = await post({ device_id: "esp32-01", temperature: 29.4, humidity: 80.4, dht_status: "OK",
      soil_raw: 0, soil_status: "NO_SIGNAL", firmware_version: "1.4.0" });
    assert.equal(noSignal.status, 201);
    const body = await noSignal.json();
    assert.equal(body.soil_moisture, null);
    assert.equal(body.soil_status, "NO_SIGNAL");
    assert.equal(body.soil_raw, 0);
    // A percentage sent next to a non-OK verdict is dropped, not trusted.
    assert.equal((await post({ device_id: "esp32-01", soil_moisture: 100, soil_raw: 0, soil_status: "NO_SIGNAL" })).status, 201);
    // In range but uncalibrated: raw kept, no percentage; diagnosis-only reading is still accepted.
    assert.equal((await post({ device_id: "esp32-01", soil_raw: 2100, soil_status: "UNCALIBRATED", dht_status: "SENSOR_ERROR",
      temperature: 0, humidity: 0 })).status, 201);
    // Calibrated and valid: percentage kept.
    assert.equal((await post({ device_id: "esp32-01", soil_moisture: 55, soil_raw: 2100, soil_status: "OK" })).status, 201);
    for (const bad of [{ soil_raw: 5000 }, { soil_raw: 1.5 }, { soil_status: "WET" }, { dht_status: "MAYBE" }]) {
      assert.equal((await post({ device_id: "esp32-01", temperature: 30, ...bad })).status, 400, JSON.stringify(bad));
    }
    const rows = await inserts();
    assert.equal(rows.length, 4);
    // params: device, temperature, humidity, soil_moisture, ts, received, farm, zone, soil_raw, soil_status, dht_status
    assert.deepEqual([rows[0][3], rows[0][8], rows[0][9], rows[0][10]], [null, 0, "NO_SIGNAL", "OK"]);
    assert.equal(rows[1][3], null, "100 % from an unconnected probe is not stored");
    assert.deepEqual([rows[2][1], rows[2][2], rows[2][3], rows[2][9], rows[2][10]], [null, null, null, "UNCALIBRATED", "SENSOR_ERROR"]);
    assert.deepEqual([rows[3][3], rows[3][9]], [55, "OK"]);
  });
});

test("beacon health: healthy only while the status file is fresh and the last tick announced", () => {
  const { beaconHealth } = require("../src/discovery/hostBeacon");
  const os = require("node:os");
  const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), "beacon-")), "status.json");
  assert.equal(beaconHealth(file).reason, "NO_STATUS_FILE");
  const at = Date.parse("2026-10-31T08:00:00Z");
  fs.writeFileSync(file, JSON.stringify({ announced: true, ip: "192.168.1.10", httpPort: 3001, at: new Date(at).toISOString(), intervalMs: 3000 }));
  assert.equal(beaconHealth(file, at + 5000).healthy, true);
  assert.equal(beaconHealth(file, at + 60_000).reason, "STALE_STATUS", "a dead beacon process is detected");
  fs.writeFileSync(file, JSON.stringify({ announced: false, reason: "HTTP_UNREACHABLE", at: new Date(at).toISOString(), intervalMs: 3000 }));
  assert.deepEqual([beaconHealth(file, at + 1000).healthy, beaconHealth(file, at + 1000).reason], [false, "HTTP_UNREACHABLE"]);
  fs.rmSync(path.dirname(file), { recursive: true, force: true });
});

test("beacon start() writes a status file after every tick", async () => {
  const written = [];
  const beacon = createHostBeacon({ env: {}, logger: { info() {}, warn() {} }, networkInterfaces: () => ({}),
    writeStatus: (s) => written.push(s) });
  beacon.start(50);
  await new Promise((r) => setTimeout(r, 140));
  beacon.stop();
  assert.ok(written.length >= 2);
  assert.deepEqual([written[0].announced, written[0].reason, written[0].intervalMs], [false, "NO_LAN", 50]);
});
