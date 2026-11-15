# DurianCare IoT — Deployment Guide (môi trường vườn / laptop Windows + Docker Desktop)

## 1. Một lần duy nhất (cài đặt)

1. Docker Desktop: Settings → General → bật "Start Docker Desktop when you sign in".
2. Khởi động stack (giữ dữ liệu, KHÔNG dùng `down -v`):
   ```bash
   cd DurianCare-IoT-Backend/infrastructure && docker compose -p duriancare up -d
   ```
   Các service ứng dụng có `restart: unless-stopped`, nên sau khi Docker Desktop khởi động chúng tự chạy lại.
3. Cài beacon tự khởi động (không cần quyền admin):
   ```bash
   powershell -ExecutionPolicy Bypass -File DurianCare-IoT-Backend/infrastructure/discovery-beacon/install-beacon-task.ps1
   ```
   Task "DurianCare Discovery Beacon" chạy mỗi lần đăng nhập Windows (trễ 30 s), tự restart mỗi phút nếu thoát.
   Beacon chỉ phát khi `http://<IP Wi-Fi laptop>:3001` trả lời, và tự đổi IP khi laptop đổi mạng — không cấu hình IP ở đâu cả.

## 2. Vì sao beacon không chạy trong Docker

Trên Docker Desktop for Windows, container nằm trong VM Linux sau NAT: không thấy IP Wi-Fi của laptop và không broadcast được ra LAN
(log iot-service: `[udp-discovery] Discovery degraded`). Beacon vì vậy chạy trên Windows; mọi thứ khác chạy trong Docker.
Trên máy chủ Linux thật có thể chạy beacon bằng systemd (`node src/discovery/hostBeacon.js`) theo cùng nguyên tắc.

## 3. Kiểm tra sau khi bật

```bash
powershell -ExecutionPolicy Bypass -File DurianCare-IoT-Backend/infrastructure/discovery-beacon/check-beacon.ps1
```
```bash
docker ps --format "{{.Names}} {{.Status}}"
```
- `check-beacon.ps1` phải in `RESULT: OK` (task Running, `HEALTHY (ANNOUNCING)`, iot-service UP).
- `docker ps`: iot-service, notification-service, gateway, farm, config-server, discovery-server phải "(healthy)" — không chỉ "Up".

## 4. Healthcheck và thứ tự khởi động

| Service | Healthcheck | Chờ |
|---|---|---|
| discovery-server / config-server | `/actuator/health` | config-server chờ discovery-server healthy |
| auth, farm, cultivation, traceability, notification, search | `/actuator/health` (farm, notification) | config-server healthy + DB/Kafka healthy |
| iot-service | `/actuator/health` (chạy `SELECT 1` trên PostgreSQL) | postgres, kafka, emqx healthy |
| gateway | `/actuator/health` | config healthy, iot/notification/farm healthy |

## 5. Cập nhật / rollback

- Rebuild một service: `docker tag <image hiện tại> <tên>:pre-<ngày>` → `docker compose -p duriancare build <svc>` → `docker compose -p duriancare up -d --no-deps <svc>`.
- Rollback iot-service lần này: image `duriancare/iot-service:pre-hardening-2026-10-31`. Các cột DB mới là cột cộng thêm, code cũ bỏ qua — không cần rollback DB.
- Gỡ beacon: `uninstall-beacon-task.ps1`.

## 6. Firmware

Xem `IOT_HARDWARE_STARTUP_GUIDE.md`. Nạp lại chỉ khi đổi Wi-Fi hoặc cập nhật firmware; IP máy chủ không bao giờ phải sửa.
