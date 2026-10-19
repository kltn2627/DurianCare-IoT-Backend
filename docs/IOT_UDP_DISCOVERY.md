# IoT UDP Discovery

Status: source and isolated integration verified; NOT DEPLOYED. LAN/device interoperability is unverified.
The IoT directory is part of DurianCare-IoT-Backend, not a separate Git repository.

## Lifecycle and Networking

- Existing HTTP port remains PORT (3001 by default). main.js passes the actual bound port to discovery;
  the standalone module also recognizes SERVICE_PORT only when PORT/an explicit bound port is absent.
- Start runs only in the successful HTTP listening callback. Failed HTTP bind exits nonzero without
  discovery. SIGINT/SIGTERM stop UDP/timers and drain accepted writes before HTTP/DB shutdown.
- Beacon binds UDP 4210 and immediately sends DURIANCARE_SERVER:http://<selected-ip>:<http-port>
  to 255.255.255.255:4210, then every 3000 ms, with setBroadcast(true). One timer per lifecycle.
- Listener binds UDP 4211. Duplicate start/stop and stop during pending bind are safe. Socket failures
  degrade discovery without taking down HTTP. Logs contain no raw packets, keys, SQL errors or secrets.
- LAN selection filters internal/loopback/IPv6 and virtual Docker/WSL/VPN names, ranks recognized
  physical interface names then private addresses, with deterministic name/address tie-breaking.
  On native hosts the beacon binds its selected LAN IP. Multiple NICs still need operator verification.
- Container/WSL addresses are not treated as host LAN evidence. Without an explicit validated
  HOST_OVERRIDE_IP those environments do not advertise a URL. Invalid/missing addresses never
  produce undefined/0.0.0.0/loopback beacons; HTTP and the listener can continue degraded.

## Camera Trust Boundary

Plain legacy syntax is parsed:

```text
DURIANCARE_CAM:<device-id>:http://<ipv4>[:port]
```

But UDP device ID alone is NOT authentication or proof of farm ownership. **Unsigned announcements
never write the database**, even for a known ID. There was no previous trusted UDP protocol to reuse;
the insecure default shared registration key is deliberately not reused as discovery authority.

An opt-in authenticated envelope preserves that exact announcement as a field:

```text
{"announcement":"DURIANCARE_CAM:<device-id>:<origin>","timestamp":<epoch-ms>,"signature":"<hex-hmac>"}
```

HMAC-SHA256 covers the exact UTF-8 string `timestamp + "\n" + announcement`. Signature comparison is
constant-time. Allowed skew is 30 seconds. The operator must provision an independent, strong key
(at least 32 UTF-8 bytes) for each device via the existing secure environment mechanism, never Git/chat:

UDP_DISCOVERY_CAMERA_BINDINGS is a JSON object keyed by exact device ID, with farmId and key fields.
Empty/unconfigured bindings deny all camera writes. Invalid bindings degrade discovery, fail closed,
and are never echoed. No keys or firmware were generated or installed during this task.

For a write, all conditions must pass:

1. Packet <= 1024 bytes; valid signature, timestamp and explicitly provisioned device binding.
2. HTTP/HTTPS origin with a literal private IPv4 host equal to the UDP sender IPv4.
3. No embedded credentials, query, fragment or base path. The existing schema stores IP/port/protocol;
   capture path is separate, so silently dropping a supplied base path would be incorrect.
4. Existing camera record with farm_id equal to the binding's authorized farm. No upsert/create.
5. URL differs, cooldown passed and no in-flight update for this device.
6. Conditional SQL matches device ID, expected farm and old IP/port/protocol. A concurrent heartbeat,
   manual origin change or reassignment cannot be blindly overwritten. No online/last_seen/tree/farm
   field is changed by discovery.

One in-flight operation per provisioned ID; attempts limited to one per 3 seconds; global packet work
limited to 100/sec. Out-of-order successful-update timestamps are rejected within the process. Busy
announcements are dropped, not queued indefinitely; a newer periodic packet retries after cooldown.
Replay state is process-local, not a durable exactly-once guarantee across restarts; the signed timestamp
window still applies. Do not treat UDP/source-IP checks as a substitute for per-device key protection.

Only a returned successful conditional DB update emits the requested synchronized-URL log. Logged
URLs are sanitized origins, never credentials/query/path. DB errors/unknown IDs/foreign farm/no-op
updates do not log success. Production firmware that only emits the bare packet remains BLOCKED
for automatic writes until an approved authenticated sender is available. No insecure enable flag exists.

## Database and Snapshot Cache

No schema migration or camera_url column was added. Existing updateCameraUrl accepts optional
protocol, expectedFarmId and expectedOrigin while retaining the manual endpoint's existing call shape.
resolveCameraUrl reads PostgreSQL each time. Snapshot now resolves that URL BEFORE consulting
frame cache and keys cache state by source origin, so old/offline cached frames cannot mask an IP
change. Already-running downloads retain their original cache-state object and cannot poison the new
origin's cache. This is logical invalidation on lookup, not a startup-cached URL.

There is no /live-stream route in the current service. Web live preview polls /api/v1/camera/snapshot.
No nonexistent stream test or new public stream endpoint was invented. Existing routes and HTTP
auth/authorization policy were not broadened; comprehensive camera HTTP policy hardening is separate.

## Docker Desktop / Windows

Base Compose remains unchanged. Optional infrastructure/docker-compose.udp-discovery.yml publishes
4210/udp and 4211/udp while preserving HTTP/network mode. Its merged config was validated only.
Neither the overlay nor new image was deployed; the running container still lacks the new module.

Publishing ports maps inbound UDP. It does NOT bridge outgoing 255.255.255.255 broadcasts to a LAN.
HOST_OVERRIDE_IP fixes an advertised address, not broadcast delivery. Do not blindly enable host
networking on Docker Desktop/Windows. An operator must select a supported native-service or reviewed
host relay/network design, firewall rules and permitted device LAN; do not run two ingestion/watchdog
instances against the same live database merely to test networking.

After network/trust approval, the activation proposal is limited to this service:

```powershell
docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.udp-discovery.yml --env-file infrastructure/.env config --quiet
docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.udp-discovery.yml --env-file infrastructure/.env build duriancare-iot-service
docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.udp-discovery.yml --env-file infrastructure/.env up -d --no-deps --no-build duriancare-iot-service
```

These last two commands were NOT executed here. Preserve the current image as a rollback tag before
any approved deployment; rollback only that service's image/config, never databases/volumes.

## Verification

Run npm run check and npm test from duriancare-iot-service. Default full tests skip only the two
explicit real-PG fixture wrappers when DURIANCARE_CAMERA_FIXTURE_PG_URI is absent. Providing
that URI through the operator's secure process environment runs all tests, including the legacy nine
registration/heartbeat cases. Both DB wrappers create UUID schemas using the actual V9 camera DDL,
with a checked fixture-only search_path, within one connection/transaction; finally ROLLBACK and
assert schema absence. Never defaults to a business schema or drops a database/collection.

Verified separately: fake-socket beacons/3000ms lifecycle; real loopback UDP ephemeral bind/receive;
real 127.0.0.1:4211 bind/close smoke; real UDP -> isolated PostgreSQL -> new snapshot origin; shutdown
signal dispatch in child-process mocks. JPEG bytes are transport fixtures, not physical camera images.

Still required: approved host/network mode, actual LAN packet capture on sender and receiver,
ESP32 receipt of the exact beacon and successful normal telemetry ingestion, authenticated CAM sender
and verified registered farm binding, then approved physical image/stream checks. No hardware or LAN
success is established by these automated tests. Do not send secrets in a beacon.
