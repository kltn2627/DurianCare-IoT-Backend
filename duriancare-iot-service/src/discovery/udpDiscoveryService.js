"use strict";

const dgram = require("node:dgram");
const os = require("node:os");
const net = require("node:net");
const fs = require("node:fs");
const crypto = require("node:crypto");
const devices = require("../camera/deviceService");

function usableIPv4(ip) {
  if (!net.isIPv4(ip)) return false;
  const [a, b] = ip.split(".").map(Number);
  return a !== 0 && a !== 127 && a < 224 && !(a === 169 && b === 254);
}

function privateIPv4(ip) {
  if (!usableIPv4(ip)) return false;
  const [a, b] = ip.split(".").map(Number);
  return a === 10 || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168);
}

function selectLanIPv4(interfaces, override, isContainer = false) {
  if (override) {
    if (!usableIPv4(override)) throw new Error("INVALID_HOST_OVERRIDE_IP");
    return override;
  }
  // A container's eth0 is not evidence of a host LAN address.
  if (isContainer) return null;
  const candidates = [];
  for (const [name, addresses] of Object.entries(interfaces)) {
    if (/^(veth|br-|docker|virbr|vbox|vmnet|vEthernet|loopback|lo$|tun|tap|tailscale|utun|wg\d)|wsl|hyper-v|virtual|vmware|vpn/i.test(name)) continue;
    for (const address of addresses ?? []) {
      if (!['IPv4', 4].includes(address.family) || address.internal || !usableIPv4(address.address)) continue;
      const physical = /^(wi-fi|wifi|ethernet|wlan\d*|eth\d*|en\d*|enp\w*|ens\w*)/i.test(name);
      candidates.push({ ip: address.address, rank: (physical ? 100 : 0) + (privateIPv4(address.address) ? 10 : 0), name });
    }
  }
  candidates.sort((a, b) => b.rank - a.rank || a.name.localeCompare(b.name) || a.ip.localeCompare(b.ip));
  return candidates[0]?.ip ?? null;
}

function parseCameraAnnouncement(packet, validateAddress = privateIPv4) {
  if (!Buffer.isBuffer(packet) || packet.length > 1024) return null;
  const text = packet.toString("utf8");
  const match = /^DURIANCARE_CAM:([A-Za-z0-9._-]{1,150}):(https?:\/\/[^\s]+)$/.exec(text);
  if (!match) return null;
  try {
    const url = new URL(match[2]);
    // Existing schema stores protocol/IP/port, not a base path, query or credentials.
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password ||
        url.pathname !== "/" || url.search || url.hash || !validateAddress(url.hostname)) return null;
    const port = Number(url.port || (url.protocol === "https:" ? 443 : 80));
    if (port < 1 || port > 65535) return null;
    return { deviceId: match[1], url: url.origin, ipAddress: url.hostname, port, protocol: url.protocol.slice(0, -1) };
  } catch { return null; }
}

function parseBindings(value) {
  if (!value) return {};
  const bindings = JSON.parse(value);
  if (!bindings || typeof bindings !== "object" || Array.isArray(bindings) || Object.keys(bindings).length > 100) throw new Error("INVALID_DISCOVERY_BINDINGS");
  for (const [id, binding] of Object.entries(bindings)) {
    if (!/^[A-Za-z0-9._-]{1,150}$/.test(id) || !binding || typeof binding.farmId !== "string" || !binding.farmId.trim() ||
        typeof binding.key !== "string" || Buffer.byteLength(binding.key) < 32) throw new Error("INVALID_DISCOVERY_BINDINGS");
  }
  return bindings;
}

function verifyEnvelope(packet, bindings, now, validateAddress) {
  if (!Buffer.isBuffer(packet) || packet.length > 1024) return null;
  try {
    const envelope = JSON.parse(packet.toString("utf8"));
    if (typeof envelope.announcement !== "string" || !Number.isSafeInteger(envelope.timestamp) ||
        Math.abs(now - envelope.timestamp) > 30000 || !/^[a-f0-9]{64}$/.test(envelope.signature)) return null;
    const announcement = parseCameraAnnouncement(Buffer.from(envelope.announcement), validateAddress);
    const binding = announcement && Object.hasOwn(bindings, announcement.deviceId) && bindings[announcement.deviceId];
    if (!binding) return null;
    const expected = crypto.createHmac("sha256", binding.key).update(`${envelope.timestamp}\n${envelope.announcement}`).digest();
    if (!crypto.timingSafeEqual(expected, Buffer.from(envelope.signature, "hex"))) return null;
    return { ...announcement, farmId: binding.farmId, timestamp: envelope.timestamp };
  } catch { return null; }
}

function createUdpDiscoveryService(options = {}) {
  const env = options.env ?? process.env;
  const virtualHost = options.isContainer ?? (fs.existsSync("/.dockerenv") || Boolean(env.WSL_INTEROP || env.WSL_DISTRO_NAME) || /microsoft/i.test(os.release()));
  const logger = options.logger ?? console;
  const createSocket = options.createSocket ?? (type => dgram.createSocket(type));
  const networkInterfaces = options.networkInterfaces ?? os.networkInterfaces;
  const now = options.now ?? Date.now;
  const validateAddress = options.validateCameraAddress ?? privateIPv4;
  const setTimer = options.setInterval ?? setInterval;
  const clearTimer = options.clearInterval ?? clearInterval;
  const getCamera = options.getCamera ?? (id => devices.getCamera(options.pool, options.schema, id));
  const updateCamera = options.updateCameraUrl ?? ((id, update) => devices.updateCameraUrl(options.pool, options.schema, id, update));
  let running = false, beacon, listener, timer, bindings = {}, ip;
  let generation = 0;
  let stopPromise = Promise.resolve();
  let bucketAt = 0, bucketCount = 0;
  const busy = new Map(), attempts = new Map(), versions = new Map();
  const state = { running: false, beaconReady: false, listenerReady: false, degraded: false };
  const warn = () => { if (!state.degraded) logger.warn("[udp-discovery] Discovery degraded; HTTP remains available"); state.degraded = true; };

  function closeSocket(socket) {
    if (!socket) return Promise.resolve();
    return new Promise(resolve => { try { socket.close(resolve); } catch { resolve(); } });
  }

  async function handleCameraPacket(packet, peer) {
    if (!running) return false;
    const time = now();
    if (time - bucketAt >= 1000) { bucketAt = time; bucketCount = 0; }
    if (++bucketCount > 100) return false;
    const camera = verifyEnvelope(packet, bindings, time, validateAddress);
    // Bare announcements remain untrusted hints, never DB write authority.
    if (!camera || camera.ipAddress !== peer.address || busy.has(camera.deviceId) ||
        time - (attempts.get(camera.deviceId) ?? -Infinity) < 3000 ||
        camera.timestamp <= (versions.get(camera.deviceId) ?? -Infinity)) return false;
    attempts.set(camera.deviceId, time);
    const operation = (async () => {
      try {
        const current = await getCamera(camera.deviceId);
        if (!current || current.farm_id !== camera.farmId) return false;
        if (current.last_seen && new Date(current.last_seen).getTime() >= camera.timestamp) return false;
        if (current.ip_address === camera.ipAddress && Number(current.port) === camera.port && current.protocol === camera.protocol) return false;
        const updated = await updateCamera(camera.deviceId, { ...camera, announcedAt: camera.timestamp, expectedFarmId: camera.farmId,
          expectedOrigin: { ipAddress: current.ip_address, port: current.port, protocol: current.protocol } });
        if (!updated) return false;
        versions.set(camera.deviceId, camera.timestamp);
        // Snapshot cache is keyed by the DB-resolved origin; there is no URL cache to invalidate.
        logger.info(`\u{1F3AF} [Auto-Discovery] Đã đồng bộ Camera IP mới: ${camera.url}`);
        return true;
      } catch { warn(); return false; }
    })();
    busy.set(camera.deviceId, operation);
    try { return await operation; } finally { busy.delete(camera.deviceId); }
  }

  async function startUdpDiscovery() {
    await stopPromise;
    if (running) return { ...state };
    running = state.running = true;
    const epoch = ++generation;
    state.degraded = false;
    try { bindings = parseBindings(env.UDP_DISCOVERY_CAMERA_BINDINGS); } catch { bindings = {}; warn(); }
    try {
      ip = selectLanIPv4(networkInterfaces(), env.HOST_OVERRIDE_IP, virtualHost);
    } catch { ip = null; }
    const servicePort = Number(options.servicePort ?? env.PORT ?? env.SERVICE_PORT ?? 3001);
    if (!ip || !Number.isInteger(servicePort) || servicePort < 1 || servicePort > 65535) { ip = null; warn(); }
    try {
      const socket = listener = createSocket("udp4");
      socket.on("error", () => { if (epoch === generation) { state.listenerReady = false; warn(); } void closeSocket(socket); });
      socket.on("message", (packet, peer) => { if (epoch === generation) void handleCameraPacket(packet, peer); });
      socket.bind(options.cameraPort ?? 4211, options.bindAddress ?? "0.0.0.0", () => { if (epoch === generation) state.listenerReady = running; });
    } catch { warn(); }
    if (ip) {
      try {
        const socket = beacon = createSocket("udp4");
        socket.on("error", () => {
          if (epoch !== generation) return;
          state.beaconReady = false; warn();
          if (timer) clearTimer(timer);
          timer = null;
          void closeSocket(socket);
        });
        socket.bind(options.beaconPort ?? 4210, options.bindAddress ?? (virtualHost ? "0.0.0.0" : ip), () => {
          if (!running || epoch !== generation) return;
          try {
            socket.setBroadcast(true);
            state.beaconReady = true;
            const send = () => {
              if (!running || epoch !== generation) return;
              try { socket.send(Buffer.from(`DURIANCARE_SERVER:http://${ip}:${servicePort}`), 4210, "255.255.255.255", error => { if (error) warn(); }); }
              catch { warn(); }
            };
            send();
            timer = setTimer(send, 3000);
            timer.unref?.();
          } catch { warn(); }
        });
      } catch { warn(); }
    }
    return { ...state };
  }

  function stopUdpDiscovery() {
    if (!running && !beacon && !listener) return stopPromise;
    generation++;
    running = state.running = false;
    state.beaconReady = state.listenerReady = false;
    if (timer) clearTimer(timer);
    timer = null;
    const sockets = [beacon, listener];
    beacon = listener = null;
    stopPromise = Promise.allSettled([...sockets.map(closeSocket), ...busy.values()]).then(() => {
      attempts.clear(); versions.clear();
    });
    return stopPromise;
  }
  return { startUdpDiscovery, stopUdpDiscovery, handleCameraPacket, getState: () => ({ ...state }) };
}

let singleton;
function startUdpDiscovery(options) { singleton ??= createUdpDiscoveryService(options); return singleton.startUdpDiscovery(); }
function stopUdpDiscovery() { return singleton?.stopUdpDiscovery() ?? Promise.resolve(); }

module.exports = { createUdpDiscoveryService, startUdpDiscovery, stopUdpDiscovery, selectLanIPv4, parseCameraAnnouncement, privateIPv4 };
