"use strict";

// Host-side server discovery beacon. Run it on the machine whose LAN the ESP32 shares
// (the Windows host running Docker Desktop), NOT inside a container: a bridge-network
// container can neither see the host's LAN address nor broadcast onto the LAN.
//   npm run discovery:beacon            (foreground)
//   infrastructure/discovery-beacon/install-beacon-task.ps1   (starts it at every Windows logon)
//   node src/discovery/hostBeacon.js --health   (exit 0 only while it is announcing)
// Re-reads the network interfaces every tick, so a new DHCP/hotspot address is announced
// within one interval. Announces only while the HTTP port answers on that address.
const dgram = require("node:dgram");
const os = require("node:os");
const netModule = require("node:net");
const fs = require("node:fs");
const path = require("node:path");
const { selectLanInterface } = require("./udpDiscoveryService");
const {
  DISCOVERY_PORT, SENSOR_PATH, buildServerAnnouncement, buildLegacyAnnouncement, directedBroadcast, validIPv4,
} = require("./serverAnnouncement");

function probeTcp(ip, port, timeoutMs = 1500) {
  return new Promise(resolve => {
    const socket = netModule.connect({ host: ip, port });
    const done = ok => { socket.destroy(); resolve(ok); };
    socket.setTimeout(timeoutMs, () => done(false));
    socket.once("connect", () => done(true));
    socket.once("error", () => done(false));
  });
}

function createHostBeacon(options = {}) {
  const env = options.env ?? process.env;
  const logger = options.logger ?? console;
  const networkInterfaces = options.networkInterfaces ?? os.networkInterfaces;
  const createSocket = options.createSocket ?? (() => dgram.createSocket("udp4"));
  const probeHttp = options.probeHttp ?? probeTcp;
  const httpPort = Number(options.httpPort ?? env.DISCOVERY_HTTP_PORT ?? 3001);
  const override = env.HOST_OVERRIDE_IP?.trim() || null;
  // Last tick outcome, read by `--health` and the operator check script (no secrets in it).
  const statusFile = options.statusFile ?? env.DISCOVERY_STATUS_FILE ?? null;
  const writeStatus = options.writeStatus ?? ((status) => {
    if (!statusFile) return;
    try {
      fs.mkdirSync(path.dirname(statusFile), { recursive: true });
      fs.writeFileSync(`${statusFile}.tmp`, JSON.stringify(status));
      fs.renameSync(`${statusFile}.tmp`, statusFile);
    } catch { /* status is best-effort; announcing must not stop */ }
  });
  if (!Number.isInteger(httpPort) || httpPort < 1 || httpPort > 65535) throw new Error("INVALID_DISCOVERY_HTTP_PORT");
  if (override && !validIPv4(override)) throw new Error("INVALID_HOST_OVERRIDE_IP");

  let current = null;      // { ip, name, netmask, socket }
  let lastState = null;
  const report = (state, message, level = "info") => {
    if (state === lastState) return;
    lastState = state;
    logger[level](`[discovery-beacon] ${message}`);
  };

  function closeCurrent() {
    const socket = current?.socket;
    current = null;
    if (socket) try { socket.close(); } catch { /* already closed */ }
  }

  function openSocket(ip) {
    return new Promise((resolve, reject) => {
      const socket = createSocket();
      socket.once("error", reject);
      // Bind the chosen LAN address so Windows sends the broadcast out of that interface.
      socket.bind(0, ip, () => {
        socket.removeListener("error", reject);
        socket.on("error", () => { if (current?.socket === socket) closeCurrent(); });
        socket.setBroadcast(true);
        resolve(socket);
      });
    });
  }

  async function tick() {
    const iface = override
      ? { ip: override, name: "HOST_OVERRIDE_IP", netmask: env.HOST_OVERRIDE_NETMASK ?? null, reason: "explicit override" }
      : selectLanInterface(networkInterfaces());
    if (!iface) {
      closeCurrent();
      report("no-lan", "No usable LAN IPv4 interface; not announcing (SERVER_NOT_ANNOUNCED).", "warn");
      return { announced: false, reason: "NO_LAN" };
    }
    if (current?.ip !== iface.ip) {
      closeCurrent();
      try {
        current = { ...iface, socket: await openSocket(iface.ip) };
      } catch {
        report(`bind:${iface.ip}`, `Cannot bind UDP on ${iface.ip}; not announcing.`, "warn");
        return { announced: false, reason: "BIND_FAILED" };
      }
      logger.info(`[discovery-beacon] Selected ${iface.ip} on "${iface.name}" (${iface.reason}).`);
    }
    if (!(await probeHttp(iface.ip, httpPort))) {
      report(`down:${iface.ip}`, `HTTP ${iface.ip}:${httpPort} is not reachable; not announcing until it is.`, "warn");
      return { announced: false, reason: "HTTP_UNREACHABLE" };
    }
    const packets = [buildServerAnnouncement({ ip: iface.ip, port: httpPort, path: SENSOR_PATH }),
      buildLegacyAnnouncement({ ip: iface.ip, port: httpPort })];
    const targets = [...new Set([directedBroadcast(iface.ip, iface.netmask), "255.255.255.255"].filter(Boolean))];
    const socket = current.socket;
    for (const target of targets) {
      for (const text of packets) {
        try { socket.send(Buffer.from(text), DISCOVERY_PORT, target, () => {}); } catch { /* socket closed */ }
      }
    }
    report(`up:${iface.ip}`, `Announcing http://${iface.ip}:${httpPort}${SENSOR_PATH} to UDP ${targets.join(", ")}:${DISCOVERY_PORT}.`);
    return { announced: true, ip: iface.ip, targets };
  }

  let timer = null;
  let running = false;
  function start(intervalMs = 3000) {
    if (timer) return;
    const loop = async () => {
      if (running) return;
      running = true;
      let result;
      try { result = await tick(); } catch { result = { announced: false, reason: "TICK_FAILED" }; report("error", "Beacon tick failed; retrying.", "warn"); }
      finally { running = false; }
      writeStatus({ ...result, targets: undefined, httpPort, pid: process.pid, at: new Date().toISOString(), intervalMs });
    };
    void loop();
    timer = setInterval(loop, intervalMs);
  }
  function stop() {
    if (timer) clearInterval(timer);
    timer = null;
    closeCurrent();
  }
  return { tick, start, stop };
}

/** Healthy = the status file was written within 3 ticks and the last tick announced. */
function beaconHealth(statusFile, now = Date.now()) {
  let status;
  try { status = JSON.parse(fs.readFileSync(statusFile, "utf8")); } catch { return { healthy: false, reason: "NO_STATUS_FILE" }; }
  const age = now - Date.parse(status.at);
  if (!Number.isFinite(age) || age > 3 * (status.intervalMs || 3000) + 2000) return { healthy: false, reason: "STALE_STATUS", status };
  if (!status.announced) return { healthy: false, reason: status.reason || "NOT_ANNOUNCING", status };
  return { healthy: true, reason: "ANNOUNCING", status };
}

function defaultStatusFile(env = process.env) {
  const base = env.USERPROFILE || env.HOME || os.tmpdir();
  return path.join(base, ".duriancare", "discovery-beacon-status.json");
}

module.exports = { beaconHealth, createHostBeacon, defaultStatusFile, probeTcp };

if (require.main === module) {
  const statusFile = process.env.DISCOVERY_STATUS_FILE || defaultStatusFile();
  if (process.argv.includes("--health")) {
    const health = beaconHealth(statusFile);
    const s = health.status || {};
    console.log(`[discovery-beacon] ${health.healthy ? "HEALTHY" : "UNHEALTHY"} (${health.reason})` +
      (s.ip ? ` ip=${s.ip}:${s.httpPort}` : "") + (s.at ? ` last=${s.at}` : "") + (s.pid ? ` pid=${s.pid}` : ""));
    process.exit(health.healthy ? 0 : 1);
  }
  const beacon = createHostBeacon({ statusFile });
  beacon.start();
  for (const signal of ["SIGINT", "SIGTERM"]) process.on(signal, () => { beacon.stop(); process.exit(0); });
}
