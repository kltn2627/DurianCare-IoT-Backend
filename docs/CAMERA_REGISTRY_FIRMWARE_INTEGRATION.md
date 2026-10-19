# Camera Registry and Firmware Integration

Date: 2026-10-16. Source implementation only; no deployment or physical camera validation.

## Architecture and Source of Truth

Reuse PostgreSQL camera_devices (existing V9 schema). Stable device_id identifies the camera;
ip_address/protocol/port describe its current origin. No second registry, new schema or migration.
Device firmware registration and heartbeat update this same row. Existing signed UDP discovery
also updates this row using its existing compare-and-swap rule; the UDP wire protocol is unchanged.
The database does not persist which transport last wrote the address. DTO addressSource therefore
truthfully says REGISTRY, not an invented per-update audit provenance.

Flow: provisioned camera -> authenticated registration/heartbeat -> camera_devices ->
authenticated/farm-scoped IoT read API -> Gateway -> Web/Mobile -> authenticated snapshot proxy.
Endpoint version is an opaque hash of the current origin. It changes when address/port/scheme
changes, not at every heartbeat. Both clients invalidate the old preview on version/selection change.

The current preview protocol is HTTP JPEG snapshots, polled every 8 seconds. It is NOT MJPEG,
RTSP, a WebSocket video feed or continuous video. streamAvailable=false and streamUrl=null.
An ONLINE registry state proves a fresh authenticated heartbeat only, not successful capture.
Successful snapshot HTTP 200 requires image/jpeg, non-empty complete JPEG markers, a bounded
body, and unchanged current registry origin when the frame completes.

## Provisioning and Trust

Before firmware first registration, an authorized operator must provision a camera_devices row
with its verified farm_id/zone_id ownership and stable device_id. This change does not invent
ownership, create an account, supply credentials or expose a public provisioning API.
Unknown camera IDs return 404 until provisioned. No ownership is inferred from request source IP.

Supply CAMERA_DEVICE_BINDINGS securely in the IoT service environment. It is a JSON object
keyed by stable camera ID, each value containing a unique key of at least 32 characters, farmId
and optional zoneId. Keep secrets out of source, docs, shell history and logs. Do not share keys
between cameras. The operator must reconcile the binding with the persisted row.

The new REST device binding is authentication configuration, not a second address registry.
UDP_DISCOVERY_CAMERA_BINDINGS remains the existing independent signed-UDP configuration.
If both transports are used, operators must bind each stable ID to the same verified farm.

UI endpoints use Gateway-validated X-Auth-User-Id/X-Auth-Role and the existing Farm internal
READ_IOT / CONFIGURE_DEVICE checks. Do not expose the IoT container directly to untrusted
clients capable of forging those headers. This follows the repository's current service trust
boundary, not a new independent JWT verifier in Node.
Gateway already routes /api/v1/camera/** and /api/v1/public/** to IoT; no route/whitelist widening.
The public camera prefix means no Farmer JWT is required for device calls, NOT anonymous writes:
registration/heartbeat validate the per-camera key before any mutation.

## REST Contract

| Method and Gateway path | Authentication / effect |
| --- | --- |
| POST /api/v1/public/cameras/register | Per-device X-Camera-Key; update an already provisioned camera's current address and heartbeat. |
| POST /api/v1/public/cameras/heartbeat | Same key/scope/validation; same address/version concurrency guard. |
| GET /api/v1/camera/devices | Farmer session plus READ_IOT; only authorized cameras returned. |
| GET /api/v1/camera/devices/{cameraId} | Same per-camera farm/zone check; status computed at read time. |
| GET /api/v1/camera/snapshot?device_id={cameraId} | Authenticated JPEG proxy; stale camera returns 503, bad image/upstream returns 502, timeout 504. |
| POST /api/v1/camera/capture-now?device_id={cameraId} | CONFIGURE_DEVICE plus fresh heartbeat; existing capture/persistence/AI flow. |
| POST /api/v1/camera/config | Authorized operator manual origin correction only for existing camera; CAS and scope checks, no auto-registration. |
| GET/PUT /api/v1/camera/schedule | Existing schedule API, now scoped to camera ownership/permissions. |

Example body template for BOTH register and heartbeat (replace placeholders; do not send literally):

```json
{
  "cameraId": "<PROVISIONED_CAMERA_ID>",
  "ipAddress": "<CURRENT_CAMERA_LAN_IPV4_OR_LOCAL_HOSTNAME>",
  "port": 80,
  "protocol": "http",
  "announcedAt": 0
}
```

announcedAt MUST be current UTC Unix epoch milliseconds (integer), not the template zero.
Firmware clock must be synchronized. The server allows at most 30 seconds of skew and
persists announcedAt as last_seen. Heartbeats strictly newer than last_seen update the row;
exact same timestamp+origin retries return 200 without duplicate writes. Older/conflicting
announcements return 409 and cannot restore an old IP after a newer announcement.
Do not supply farmId, zoneId or treeId; these claims return 403. Missing/wrong key returns 401.
Missing/invalid IP/port/scheme/timestamp returns 400. Registry/access dependency failure returns 503.
No X-Forwarded-For, X-Real-IP or remote socket address is used as the camera address.

Supported device origins: private IPv4 or .local hostname representable by the current 45-character
address column. HTTP/HTTPS only, port 1-65535; no userinfo, base path, query or fragment.
Loopback, public IP and metadata/link-local destinations are rejected for device/config writes.
IPv6 and general public hostnames are not supported by this deployment profile.
ESP32_CAPTURE_PATH is a service-wide absolute path (default /capture), not arbitrary firmware URL.
Hostname resolution still requires the IoT host to resolve the camera hostname; no IP fallback
is substituted when mDNS fails. Prefer announced numeric LAN IPv4 on Docker networks.

Device response retains existing snake_case fields for compatibility and adds:

```json
{
  "cameraId": "<PROVISIONED_CAMERA_ID>",
  "status": "ONLINE",
  "lastSeenAt": "<UTC_TIMESTAMP>",
  "updatedAt": "<UTC_TIMESTAMP>",
  "addressSource": "REGISTRY",
  "snapshotUrl": "/api/v1/camera/snapshot?device_id=<ENCODED_ID>",
  "endpointVersion": "<OPAQUE_VERSION>",
  "previewAvailable": true,
  "streamAvailable": false,
  "streamUrl": null
}
```

ONLINE depends on row.online AND fresh last_seen; expired heartbeat is OFFLINE, never-seen is
UNKNOWN. The origin/row is retained when offline. CAMERA_OFFLINE_TIMEOUT_MIN defaults to 5
and must be positive. This is calculated on GET; no new scheduler is needed for correctness.
The legacy watchdog remains idempotent and only marks stale rows offline; no registry deletion.

## Configuration Migration

- Registry mode: no ESP32_CAMERA_URL or DEFAULT_ESP32_IP required at startup.
- CAMERA_LEGACY_ENABLED defaults false. ESP32_CAMERA_URL defaults empty, never a pretend IP.
- ESP32_CAPTURE_PATH defaults /capture.
- DEFAULT_ESP32_IP is no longer used; no silent .local -> old IP fallback.
- Old ESP32_CAMERA_URL / DEFAULT_ESP32_IP / CAMERA_REGISTRATION_KEY trigger a generic,
  value-free deprecation warning. The old shared registration key is NOT accepted by the new
  device endpoints; migrate firmware credentials, do not restore the insecure default.
- Explicit CAMERA_LEGACY_ENABLED=true permits ESP32_CAMERA_URL only for the existing
  scheduler's missing-row legacy resolution. Existing registry rows always win, even when
  offline; manual capture/preview still require a provisioned, authorized row and fresh heartbeat.
  Prefer registry-only mode; legacy configuration cannot safely identify farm ownership.
- Real .env files were not edited. A blank non-secret template is in duriancare-iot-service/.env.example.
- Internal Docker service URLs, Gateway base URL and SERVER_BASE_URL are NOT camera origins.

## Network and Deployment Limits

On local/dev, IoT host/container must have a route to the camera LAN and be allowed outbound
TCP to its HTTP port. In Docker, a LAN multicast/mDNS name might not resolve. Registry fixes
address freshness, not network reachability, certificate trust or firewall routing.

A cloud server cannot reach a private LAN camera solely because its IP is registered.
An operator-approved VPN/site-to-site route, LAN edge relay or reverse tunnel is required.
No relay, MQTT video protocol, public port forwarding or TLS bypass was added in this task.
HTTPS Web/Mobile calls the authenticated Gateway proxy, not the private camera directly.
Legacy /uploads and stored-image access behavior was not redesigned; assess historical image
privacy separately before public deployment. No new public stream URL was introduced.

Existing scheduled captures may run per application replica. This task adds no second scheduler
and makes read-time status deterministic; multi-replica capture leadership remains a deployment
constraint. Use a single scheduler instance or separately design leadership before scaling it.

## Canonical Transport Decision (2026-10-18)

- Camera registration and heartbeat use ONLY the HTTP Camera Registry above
  (`POST /api/v1/public/cameras/{register,heartbeat}`), because it is the only path that enforces
  the per-device key, timestamp freshness and server-side ownership.
- MQTT carries sensor telemetry only: the IoT service subscribes to `duriancare/devices/+/telemetry`
  (`src/main.js` mqtt.connect/subscribe). EMQX runs with its default anonymous listener, so an MQTT
  registration topic could not authenticate a camera. No backend component consumes
  `esp32/camera/register`; camera firmware 2.0.0 therefore does not connect to or publish on MQTT.
- `GET /api/v1/public/devices/mqtt-config` (`src/public/deviceConfigRoutes.js`) remains available
  for MQTT telemetry clients (host from `MQTT_PUBLIC_HOST`). Its `mqtt_topic` is not a camera
  registration channel and must not be used as one until an authenticated consumer exists.

## Firmware 2.0.0 (Arduino `Documents/Arduino/CameraWebServer`)

- `camera_registry_config.h`: `CAMERA_ID`, `BACKEND_HOST`/`BACKEND_PORT` (direct IoT port 3001),
  registry paths, NTP servers, minimum valid epoch, timeouts and backoff limits. No secrets.
- `camera_secrets.h` (local only, never share) / `camera_secrets.example.h`: Wi-Fi and
  `CAMERA_DEVICE_KEY`. A `static_assert` refuses to compile with a key shorter than 32 characters.
- Sends `cameraId`, `ipAddress`, `port`, `protocol`, `announcedAt` (UTC epoch ms from
  `gettimeofday`, only after `time()` passes `MIN_VALID_EPOCH_SEC`), `firmwareVersion`; never
  farm/zone/tree. Header `X-Camera-Key` is never logged.
- HTTPClient: 5 s connect / 7 s response timeout, redirects disabled, response bodies printed only
  when <= 1 KB and sanitized. Status-specific handling: 400/409 -> resync clock; 401/403/404 ->
  slow retry with operator message; 503/transport -> jittered exponential backoff 5 s..120 s.
- Wi-Fi auto-reconnect, IP change detection (re-register immediately), subnet mismatch warning
  for a stale `BACKEND_HOST`. No silent fallback address.
- `/capture`: frame must start with FFD8 and end with FFD9; one retry; rejected frames are
  returned with `esp_camera_fb_return`.
- Contract pinned by `duriancare-iot-service/test/firmware-contract.test.js`.

## Firmware Requirements (original checklist)

1. Preserve a stable provisioned camera ID across Wi-Fi reconnects.
2. Store its separately provisioned key securely; never print it.
3. Synchronize UTC clock and obtain the current LAN IPv4 after Wi-Fi connects.
4. POST register to the configured Gateway backend URL with X-Camera-Key, explicit address,
   HTTP port/scheme and fresh announcedAt. Do not send ownership claims.
5. On success, POST heartbeat about every 60 seconds with the same contract. On DHCP change,
   send the new address immediately with a newer timestamp.
6. Retry timeout/503 with jittered exponential backoff (for example 2, 4, 8 up to 60 seconds).
   Retrying the exact announcement is idempotent. On 409 refresh the timestamp/current address;
   do not replay an older address. Stop/reconcile provisioning on 401/403/404; do not brute-force.
7. Use verified HTTPS for the Gateway if outside an isolated trusted local network. A key sent
   over plaintext transport is not protected from network interception.
8. Keep serving /capture as a complete image/jpeg body (implemented in firmware 2.0.0, see above).

When hardware is available later, separately test boot/reconnect, DHCP A->B, heartbeat expiry,
real JPEG decoding, latency/timeouts, concurrent clients and deployed LAN reachability.
Current evidence: automated fixture tests plus a successful esp32cam compile (2026-10-18); firmware 2.0.0 HARDWARE_NOT_TESTED.
