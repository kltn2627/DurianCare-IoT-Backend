# FCM – API và payload cho Mobile

Mọi route đi qua Gateway (`:8080`), cần `Authorization: Bearer <accessToken>`. Chủ sở hữu token luôn lấy từ JWT (`X-Auth-User-Id` do Gateway gắn), không gửi `userId` trong body; nếu gửi sẽ bị bỏ qua.

## Đăng ký / cập nhật token

`PUT /api/v1/notification/device-tokens`

```json
{ "token": "<FCM registration token>", "deviceId": "<id bản cài>", "platform": "ANDROID", "appVersion": "1.0.0" }
```

| Trường | Ràng buộc |
|---|---|
| `token` | Bắt buộc, 32–4096 ký tự, `A-Za-z0-9_:.-`. `ExponentPushToken[...]` bị từ chối. |
| `deviceId` | Bắt buộc, 8–128 ký tự, `A-Za-z0-9_-`. Giữ nguyên suốt vòng đời bản cài. |
| `platform` | Bắt buộc, hiện chỉ `ANDROID`. |
| `appVersion` | Tuỳ chọn, tối đa 32 ký tự. |

`200`:

```json
{ "deviceId": "...", "platform": "ANDROID", "active": true, "updatedAt": "2026-11-04T14:00:00Z" }
```

Idempotent, gọi lại mỗi lần mở app hoặc khi token đổi đều an toàn. Response không bao giờ trả lại token.

Hành vi:

- Token đã tồn tại: cập nhật dòng đó và chuyển chủ về người gọi.
- Mọi token active khác cùng `deviceId` bị vô hiệu hoá (`REPLACED`). Đây là cơ chế xử lý token refresh và đổi tài khoản trên cùng máy.
- Một user có thể có nhiều `deviceId`, mỗi máy nhận riêng.

## Hủy đăng ký

`DELETE /api/v1/notification/device-tokens/{deviceId}` → `204`

Idempotent và chỉ tác động lên token của người gọi. Phải gọi **trước** `POST /api/auth/logout`, vì sau đó access token đã bị thu hồi.

## Lỗi

| Mã | Khi nào |
|---|---|
| `400` | Sai định dạng token, `deviceId`, `platform`. Body lỗi có `message`, không lặp lại token. |
| `401` | Thiếu, sai, hết hạn hoặc đã thu hồi JWT (Gateway); thiếu `X-Auth-User-Id` (service). |

## Payload push

FCM message gồm `notification` (title, body do hệ thống hiển thị) và `data`. Mọi giá trị trong `data` là chuỗi.

| Key | Luôn có | Ý nghĩa |
|---|---|---|
| `schemaVersion` | Có | Hiện là `"1"`. |
| `notificationId` | Có | `id` của bản ghi inbox. Đọc chi tiết bằng API inbox. |
| `eventId` | Có | ID ổn định: `sourceEventId`, không có thì bằng `notificationId`. |
| `eventType` | Nếu event có | Ví dụ `SOIL_MOISTURE_LOW`, xem bảng dưới. |
| `notificationType` | Nếu có | `DEVICE`, `DISEASE`, `CARE`, `EXPERT`, `WEATHER`, `SYSTEM`, `ACCOUNT`, `SECURITY`, `GENERAL`. |
| `occurredAt` | Có | ISO-8601 UTC. |
| Khoá điều hướng | Nếu metadata có | Xem danh sách dưới. |

Ví dụ cảnh báo độ ẩm đất:

```json
{
  "notification": { "title": "Cảnh báo", "body": "Độ ẩm đất thấp ..." },
  "data": {
    "schemaVersion": "1", "notificationId": "6aeb...", "eventId": "c1f0...",
    "eventType": "SOIL_MOISTURE_LOW", "notificationType": "DEVICE",
    "occurredAt": "2026-11-04T14:00:00Z",
    "alertId": "42", "deviceId": "17", "deviceUid": "esp32-1", "farmId": "...", "severity": "WARNING"
  },
  "android": { "priority": "high", "ttl": "21600s", "notification": { "channel_id": "duriancare-alerts", "tag": "<notificationId>" } }
}
```

| Sự kiện | `eventType` | Màn hình Mobile |
|---|---|---|
| Độ ẩm đất | `SOIL_MOISTURE_LOW`, `SOIL_MOISTURE_HIGH` | Cảm biến |
| Nhiệt độ | `TEMPERATURE_LOW`, `TEMPERATURE_HIGH` | Cảm biến |
| Độ ẩm không khí, ánh sáng | `HUMIDITY_*`, `LIGHT_*` | Cảm biến |
| Thiết bị/cảm biến mất kết nối | `DEVICE_OFFLINE` | Cảm biến |
| Hết cảnh báo | `IOT_ALERT_RECOVERED` | Cảm biến |
| Chẩn đoán bệnh lá | `DISEASE_DETECTED` | Kết quả chẩn đoán (có ID) hoặc lịch sử |
| Nhắc chăm sóc cây | `CARE_FOLLOWUP` | Chi tiết cây |
| Uỷ quyền vườn | `AGRONOMIST_INVITATION_*`, `FARM_AUTHORIZATION_*` | Uỷ quyền |
| Kết nối chuyên gia | `CONNECTION_*` | Hội thoại khi có ID, nếu không thì chi tiết thông báo |
| Kiến thức | `KNOWLEDGE_*` | Duyệt kiến thức |
| Khác | `SYSTEM_ALERT`, ... | Chi tiết thông báo |

Mobile không tự ánh xạ `eventType`: payload được đưa vào đúng `resolveNotificationTarget` mà inbox dùng, nên nhấn push và nhấn dòng trong inbox mở cùng một màn hình.

**Khoá điều hướng được phép** (allow-list trong `PushMessageFactory`): `activityId, agronomistInvitationId, alertId, alertType, articleId, articleSlug, authorizationId, carePlanId, chatConversationId, communityPostId, conversationId, cultivationActivityId, cultivationAreaId, cultivationPlanId, deviceId, deviceUid, diagnosisId, entryId, exportReleaseId, farmAuthorizationId, farmId, followUpDate, harvestBatchId, invitationId, knowledgeId, knowledgeSlug, peerUserId, planId, postId, predictionId, releaseId, reviewId, sensorId, severity, slug, threadId, traceabilityId, treeId, zoneId`, cùng `targetUrl` nếu là đường dẫn nội bộ bắt đầu bằng `/`.

Metadata khác (ví dụ số điện thoại) không rời server. Khi thêm khoá mới vào `notificationTargetResolver`, phải thêm vào allow-list.

Không dùng `body`, `message`, `title` làm khoá `data`: `expo-notifications` diễn giải riêng các khoá này.
