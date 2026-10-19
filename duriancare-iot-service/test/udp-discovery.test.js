"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { EventEmitter, once } = require("node:events");
const crypto = require("node:crypto");
const dgram = require("node:dgram");
const { createUdpDiscoveryService, selectLanIPv4, parseCameraAnnouncement } = require("../src/discovery/udpDiscoveryService");

const address = (ip, extra = {}) => ({ address: ip, internal: false, family: "IPv4", ...extra });
test("LAN selection excludes virtual, internal and IPv6 interfaces and prefers physical names", () => {
  const interfaces = { docker0: [address("172.17.0.1")], "vEthernet (WSL)": [address("172.20.0.1")],
    lo: [address("127.0.0.1", { internal: true })], "Wi-Fi": [address("fe80::1", { family: "IPv6" }), address("192.168.9.2")],
    unknown: [address("10.0.0.1")], "br-fixture": [address("172.18.0.1")] };
  assert.equal(selectLanIPv4(interfaces), "192.168.9.2");
  assert.equal(selectLanIPv4({ eth0: [address("10.1.1.2")] }), "10.1.1.2");
  assert.equal(selectLanIPv4({ eth0: [address("172.17.0.2")] }, null, true), null);
  assert.equal(selectLanIPv4({}, "192.168.8.2", true), "192.168.8.2");
  assert.equal(selectLanIPv4({}), null);
  for (const ip of ["invalid", "0.0.0.0", "127.0.0.1", "255.255.255.255", "::1"]) assert.throws(() => selectLanIPv4({}, ip));
});

test("plain CAM parser accepts representable origins and rejects unsafe/nonrepresentable packets", () => {
  assert.deepEqual(parseCameraAnnouncement(Buffer.from("DURIANCARE_CAM:cam-1:https://192.168.1.3:81/")), {
    deviceId: "cam-1", url: "https://192.168.1.3:81", ipAddress: "192.168.1.3", protocol: "https", port: 81,
  });
  for (const text of ["WRONG:cam:http://192.168.1.2", "DURIANCARE_CAM::http://192.168.1.2",
    "DURIANCARE_CAM:cam:file:///etc/passwd", "DURIANCARE_CAM:cam:http://user:pass@192.168.1.2",
    "DURIANCARE_CAM:cam:http://127.0.0.1", "DURIANCARE_CAM:cam:http://169.254.169.254",
    "DURIANCARE_CAM:cam:http://192.168.1.2/capture", "DURIANCARE_CAM:cam:http://192.168.1.2?key=value",
    "DURIANCARE_CAM:cam:http://192.168.1.2#x", "DURIANCARE_CAM:cam:javascript:alert(1)"]) {
    assert.equal(parseCameraAnnouncement(Buffer.from(text)), null);
  }
  assert.equal(parseCameraAnnouncement(Buffer.alloc(1025)), null);
});

class Socket extends EventEmitter {
  sent = []; closes = 0;
  bind(port, host, callback) { this.port = port; this.host = host; callback(); }
  setBroadcast(value) { this.broadcast = value; }
  send(bytes, port, host, callback) { this.sent.push({ text: bytes.toString(), port, host }); callback(this.sendError); }
  close(callback) { this.closes++; callback(); }
}

function fixture(extra = {}) {
  let time = 100000;
  const key = crypto.randomBytes(32).toString("hex");
  const sockets = [], timers = [], logs = [], writes = [];
  let row = { device_id: "cam-1", farm_id: "farm-fixture", ip_address: "192.168.1.2", port: 80, protocol: "http", updated_at: new Date(0) };
  const service = createUdpDiscoveryService({
    env: { UDP_DISCOVERY_CAMERA_BINDINGS: JSON.stringify({ "cam-1": { farmId: "farm-fixture", key } }) },
    isContainer: false, networkInterfaces: () => ({ "Wi-Fi": [address("192.168.1.10")] }), servicePort: 3001,
    createSocket: () => { const socket = new Socket(); sockets.push(socket); return socket; },
    now: () => time, setInterval: (callback, interval) => { const timer = { callback, interval }; timers.push(timer); return timer; },
    clearInterval: timer => { timer.cleared = true; }, logger: { warn: () => logs.push("degraded"), info: text => logs.push(text) },
    getCamera: async id => id === row?.device_id ? row : null,
    updateCameraUrl: async (id, update) => { writes.push({ id, update }); row = { ...row, ip_address: update.ipAddress, port: update.port, protocol: update.protocol }; return row; },
    ...extra,
  });
  return { service, sockets, timers, logs, writes, advance: () => { time += 4000; }, setRow: value => { row = value; },
    packet: (url = "http://192.168.1.3", timestamp = time) => {
      const announcement = `DURIANCARE_CAM:cam-1:${url}`;
      return Buffer.from(JSON.stringify({ announcement, timestamp,
        signature: crypto.createHmac("sha256", key).update(`${timestamp}\n${announcement}`).digest("hex") }));
    } };
}

test("beacon sends immediately with broadcast enabled, one 3000ms timer, idempotent start/stop/restart", async () => {
  const f = fixture();
  await Promise.all([f.service.startUdpDiscovery(), f.service.startUdpDiscovery()]);
  assert.equal(f.sockets.length, 2);
  const socket = f.sockets[1];
  assert.equal(socket.broadcast, true);
  assert.equal(socket.port, 4210);
  assert.deepEqual(socket.sent[0], { text: "DURIANCARE_SERVER:http://192.168.1.10:3001", port: 4210, host: "255.255.255.255" });
  assert.equal(f.timers.length, 1);
  assert.equal(f.timers[0].interval, 3000);
  f.timers[0].callback();
  assert.equal(socket.sent.length, 2);
  await Promise.all([f.service.stopUdpDiscovery(), f.service.stopUdpDiscovery()]);
  assert.ok(f.timers[0].cleared);
  assert.ok(f.sockets.every(s => s.closes === 1));
  f.timers[0].callback();
  assert.equal(socket.sent.length, 2);
  await f.service.startUdpDiscovery();
  assert.equal(f.sockets.length, 4);
  await f.service.stopUdpDiscovery();
});

test("missing LAN/invalid override does not advertise an unusable address; listener can still start", async () => {
  for (const extra of [{ networkInterfaces: () => ({}) }, { env: { HOST_OVERRIDE_IP: "0.0.0.0" } }, { isContainer: true }]) {
    const f = fixture(extra);
    await f.service.startUdpDiscovery();
    assert.equal(f.sockets.length, 1);
    assert.equal(f.service.getState().degraded, true);
    assert.equal(f.service.getState().listenerReady, true);
    await f.service.stopUdpDiscovery();
  }
});

test("registered signed announcement updates only expected farm, same URL/replay creates no write/log", async () => {
  const f = fixture(); await f.service.startUdpDiscovery();
  assert.equal(await f.service.handleCameraPacket(f.packet(), { address: "192.168.1.3" }), true);
  assert.equal(f.writes[0].update.expectedFarmId, "farm-fixture");
  assert.equal(f.writes.length, 1);
  assert.equal(f.logs.filter(l => l.includes("Auto-Discovery")).length, 1);
  f.advance();
  assert.equal(await f.service.handleCameraPacket(f.packet(), { address: "192.168.1.3" }), false);
  assert.equal(f.writes.length, 1);
  await f.service.stopUdpDiscovery();
});

test("persisted REST timestamp rejects an older signed UDP packet even after discovery restart", async () => {
  const f = fixture();
  f.setRow({device_id:"cam-1",farm_id:"farm-fixture",ip_address:"192.168.1.9",port:80,protocol:"http",
    last_seen:new Date(100001).toISOString()});
  await f.service.startUdpDiscovery();
  assert.equal(await f.service.handleCameraPacket(f.packet(),{address:"192.168.1.3"}),false);
  await f.service.stopUdpDiscovery();
  await f.service.startUdpDiscovery();
  assert.equal(await f.service.handleCameraPacket(f.packet(),{address:"192.168.1.3"}),false);
  assert.equal(f.writes.length,0);
  f.advance();
  assert.equal(await f.service.handleCameraPacket(f.packet(),{address:"192.168.1.3"}),true);
  assert.equal(f.writes[0].update.announcedAt,104000);
  await f.service.stopUdpDiscovery();
});

test("bare packet, forgery, stale packet, wrong peer, unknown camera and foreign farm cannot write", async () => {
  const f = fixture(); await f.service.startUdpDiscovery();
  const forged = JSON.parse(f.packet()); forged.signature = "0".repeat(64);
  for (const [packet, peer] of [[Buffer.from("DURIANCARE_CAM:cam-1:http://192.168.1.3"), "192.168.1.3"],
    [Buffer.from(JSON.stringify(forged)), "192.168.1.3"], [f.packet(undefined, 1), "192.168.1.3"],
    [f.packet(), "192.168.1.9"], [Buffer.alloc(2048), "192.168.1.3"]]) {
    assert.equal(await f.service.handleCameraPacket(packet, { address: peer }), false);
  }
  f.setRow(null);
  assert.equal(await f.service.handleCameraPacket(f.packet(), { address: "192.168.1.3" }), false);
  f.advance();
  f.setRow({ device_id: "cam-1", farm_id: "other-farm" });
  assert.equal(await f.service.handleCameraPacket(f.packet(), { address: "192.168.1.3" }), false);
  assert.equal(f.writes.length, 0);
  await f.service.stopUdpDiscovery();
});

test("DB failure/CAS conflict and socket errors degrade without reporting camera success", async () => {
  for (const updateCameraUrl of [async () => { throw new Error("sensitive backend detail"); }, async () => null]) {
    const f = fixture({ updateCameraUrl }); await f.service.startUdpDiscovery();
    assert.equal(await f.service.handleCameraPacket(f.packet(), { address: "192.168.1.3" }), false);
    assert.equal(f.logs.some(l => l.includes("sensitive") || l.includes("Auto-Discovery")), false);
    f.sockets[1].emit("error", new Error("sensitive socket detail"));
    assert.equal(f.service.getState().running, true);
    assert.equal(f.service.getState().degraded, true);
    await f.service.stopUdpDiscovery();
  }
});

test("concurrent updates are bounded and shutdown drains an already accepted write", async () => {
  let release;
  const f = fixture({ updateCameraUrl: () => new Promise(resolve => { release = resolve; }) });
  await f.service.startUdpDiscovery();
  const first = f.service.handleCameraPacket(f.packet(), { address: "192.168.1.3" });
  await new Promise(resolve => setImmediate(resolve));
  f.advance();
  assert.equal(await f.service.handleCameraPacket(f.packet("http://192.168.1.4"), { address: "192.168.1.4" }), false);
  const stopping = f.service.stopUdpDiscovery();
  assert.equal(await f.service.handleCameraPacket(f.packet(), { address: "192.168.1.3" }), false);
  release({ device_id: "cam-1" });
  await first; await stopping;
  assert.equal(f.service.getState().running, false);
});

test("real localhost UDP bind/receive/close: signed fixture only, no business DB or LAN broadcast", async () => {
  const key = crypto.randomBytes(32).toString("hex");
  const sockets = [];
  let resolveWrite;
  const written = new Promise(resolve => { resolveWrite = resolve; });
  const service = createUdpDiscoveryService({ env: { UDP_DISCOVERY_CAMERA_BINDINGS: JSON.stringify({ fixture: { farmId: "f", key } }) },
    isContainer: true, cameraPort: 0, bindAddress: "127.0.0.1", validateCameraAddress: ip => ip === "127.0.0.1",
    createSocket: type => { const socket = dgram.createSocket(type); sockets.push(socket); return socket; },
    logger: { warn() {}, info() {} }, getCamera: async () => ({ farm_id: "f", ip_address: "old", port: 80, protocol: "http" }),
    updateCameraUrl: async () => { resolveWrite(); return { device_id: "fixture" }; } });
  const sender = dgram.createSocket("udp4");
  try {
    await service.startUdpDiscovery();
    if (!service.getState().listenerReady) await once(sockets[0], "listening");
    const timestamp = Date.now(), announcement = "DURIANCARE_CAM:fixture:http://127.0.0.1:81";
    const packet = Buffer.from(JSON.stringify({ timestamp, announcement,
      signature: crypto.createHmac("sha256", key).update(`${timestamp}\n${announcement}`).digest("hex") }));
    await new Promise((resolve, reject) => sender.send(packet, sockets[0].address().port, "127.0.0.1", error => error ? reject(error) : resolve()));
    let deadline;
    try { await Promise.race([written, new Promise((_, reject) => { deadline = setTimeout(() => reject(new Error("UDP fixture timed out")), 2000); })]); }
    finally { clearTimeout(deadline); }
    assert.equal(service.getState().listenerReady, true);
  } finally { sender.close(); await service.stopUdpDiscovery(); }
});

test("send callbacks/bind failures are sanitized and never escape discovery startup", async () => {
  const f = fixture(); await f.service.startUdpDiscovery();
  f.sockets[1].sendError = new Error("private socket detail");
  assert.doesNotThrow(() => f.timers[0].callback());
  assert.equal(f.service.getState().degraded, true);
  assert.equal(f.logs.some(line => line.includes("private socket")), false);
  await f.service.stopUdpDiscovery();
  const broken = fixture({ createSocket() { throw new Error("private bind detail"); } });
  await broken.service.startUdpDiscovery();
  assert.equal(broken.service.getState().degraded, true);
  await broken.service.stopUdpDiscovery();
});

test("stop during real pending bind closes sockets and invalidates late callbacks", async () => {
  const sockets = [];
  const service = createUdpDiscoveryService({ env: {}, isContainer: true, cameraPort: 0, bindAddress: "127.0.0.1",
    logger: { warn() {}, info() {} }, createSocket: type => { const socket = dgram.createSocket(type); sockets.push(socket); return socket; } });
  await service.startUdpDiscovery();
  await Promise.all([service.stopUdpDiscovery(), service.stopUdpDiscovery()]);
  assert.equal(service.getState().listenerReady, false);
  assert.throws(() => sockets[0].address());
});
