# IoT Sensor Connectivity and Server Discovery

How the ESP32 sensor board (`esp32-01`, DHT22 + soil moisture) finds the laptop that runs the backend,
and how to verify it. Camera discovery/registry is a separate flow (see `IOT_UDP_DISCOVERY.md`,
`CAMERA_REGISTRY_FIRMWARE_INTEGRATION.md`).

## Ingestion endpoint (unchanged contract)

| Item | Value |
| --- | --- |
| Endpoint | `POST http://<laptop-LAN-IPv4>:3001/api/v1/sensors/data` |
| Served by | `duriancare-iot-service` (Node/Express, `src/main.js`), container port 3001 |
| Host port | `3001:3001` in `infrastructure/docker-compose.yml`, published on `0.0.0.0` |
| Auth | none (LAN device push) |
| Body | `{"device_id":"esp32-01","temperature":30.5,"humidity":70.1,"soil_moisture":42}` — temperature/humidity may be omitted while the DHT22 warms up |
| Success | `201 Created` with the stored row |
| Errors | `400` for missing/empty/non-string `device_id`, non-numeric values, or no measurement at all |

Readings land in `duriancare_iot.telemetry`, mark the device seen in `iot_devices`, and appear on the Web dashboard.

## ESP32 IP vs. server IP

DHCP gives the ESP32 **its own** address (and the router as gateway). It never tells the ESP32 where
the backend runs. The laptop's LAN address changes whenever it joins another Wi-Fi/hotspot (it was
`192.168.1.8`, then `192.168.1.10`), so it must be *discovered*, not hardcoded.

## Discovery protocol (UDP 4210, LAN only)

The server broadcasts every 3 s to UDP port **4210**, one datagram per message:

```text
v1:     {"service":"duriancare-iot","v":1,"ip":"192.168.1.10","port":3001,"path":"/api/v1/sensors/data"}
legacy: DURIANCARE_SERVER:http://192.168.1.10:3001
```

Firmware parser contract (`parseAnnouncement` in the sketch, `parseServerAnnouncement` in
`src/discovery/serverAnnouncement.js`): JSON ≤ 256 bytes; `service == "duriancare-iot"`; `v == 1`
(other versions ignored); `ip` a dotted IPv4 that is not `0.x`, `127.x` or multicast/broadcast; `port`
an integer 1–65535; `path` starts with `/`, ≤ 64 chars of `[A-Za-z0-9._~/-]`, no `..`. The firmware also
requires the datagram's source address to equal `ip`. Invalid packets are ignored. The legacy line is
still sent only so already-flashed boards keep working; the new firmware ignores it.

Targets: the subnet's directed broadcast (e.g. `192.168.1.255`) and `255.255.255.255`.

### Where the beacon runs

`duriancare-iot-service` runs in a Docker Desktop **bridge** container. Inside it the service cannot see
the laptop's LAN address and its broadcasts never reach the LAN — the runtime log shows
`[udp-discovery] Discovery degraded; HTTP remains available`. Therefore the sensor beacon runs **on the
Windows host**, next to Docker:

```bash
cd DurianCare-IoT-Backend/duriancare-iot-service
npm run discovery:beacon
```

Each tick it re-reads the network interfaces (skipping loopback, Docker/WSL/Hyper-V/VPN adapters,
preferring physical Wi-Fi/Ethernet with a private address), binds UDP to that address, and announces
**only while `<ip>:3001` accepts TCP** — so it never advertises a dead or container-only address.
It logs the chosen interface and every change. `HOST_OVERRIDE_IP` (and optional
`HOST_OVERRIDE_NETMASK`) force an address; `DISCOVERY_HTTP_PORT` overrides 3001. Stop with Ctrl+C.
Keep it running while the ESP32 is in use (e.g. a second terminal next to `docker compose`).

## Firmware behaviour (`Documents/Arduino/sketch_jul25a/sketch_jul25a.ino`)

- No hardcoded server. Until a valid v1 packet arrives it prints `SERVER_NOT_DISCOVERED` every 10 s
  and sends **no** POST.
- After every Wi-Fi (re)connect: logs ESP32 IP/gateway/subnet, restarts UDP 4210, forgets the old server.
- `SERVER_DISCOVERED` / `SERVER_CHANGED` when an announcement names a new endpoint (laptop IP change).
- POST every 10 s; connect timeout 3 s, response timeout 5 s; `HTTPClient` always ended.
- Failures back off 10 → 20 → 40 → 60 s (bounded). After 3 consecutive failures with no beacon for 30 s
  the endpoint is dropped (`ENDPOINT_STALE`) and discovery starts over.
- HTTPClient code `-1` ("connection refused") means *any* TCP connect failure (wrong IP, host down,
  blocked port), not necessarily an RST.

## Required connectivity / firewall

| Direction | Protocol/port | Needed for |
| --- | --- | --- |
| ESP32 → laptop | TCP 3001 inbound on the laptop | sensor POST |
| laptop → LAN broadcast | UDP 4210 outbound | discovery (no inbound rule needed) |

On this laptop TCP 3001 is admitted by the existing *Docker Desktop Backend* inbound-allow rule (the
Ethernet profile is **Public**). If that rule is missing, an administrator can add:

```powershell
New-NetFirewallRule -DisplayName "DurianCare IoT HTTP 3001" -Direction Inbound -Protocol TCP -LocalPort 3001 -Action Allow -Profile Private,Public
```

## Verify from the serial monitor (115200 baud)

```text
📡 IP của ESP32: 192.168.1.23 | Gateway: 192.168.1.1 | Subnet: 255.255.255.0
👂 [discovery] Đang lắng nghe máy chủ trên UDP 4210...
🎯 [discovery] SERVER_DISCOVERED: http://192.168.1.10:3001/api/v1/sensors/data (từ 192.168.1.10)
📡 [IoT Push] OK 201 -> http://192.168.1.10:3001/api/v1/sensors/data
```

The ESP32 and the advertised IP must be in the same subnet (compare with the gateway line). Still
`SERVER_NOT_DISCOVERED`? Check the beacon terminal says `Announcing …`. Discovered but POST fails?
From another LAN device (phone browser) open `http://<laptop-ip>:3001/actuator/health`; no answer means firewall/isolation.

## Phone hotspot

1. Connect the laptop **and** the ESP32 to the same hotspot (the ESP32 SSID is set in the sketch).
2. Start Docker and `npm run discovery:beacon`; it should select the hotspot adapter (e.g. `172.20.10.x`
   on iPhone, `192.168.43.x`/`10.x` on Android) and log `Announcing …`.
3. Watch the serial monitor for `SERVER_DISCOVERED` then `OK 201`.

Limitations: some hotspots enable client (AP) isolation — then the ESP32 cannot reach the laptop over
TCP either, and no discovery scheme helps; turn isolation off or use a router. Some hotspots drop
broadcasts but pass unicast; in that case the minimal fallback is to configure the endpoint explicitly
(not implemented: e.g. a persisted endpoint in ESP32 NVS set over the serial monitor and re-validated by
a successful POST). UDP broadcast never crosses subnets/VLANs.
