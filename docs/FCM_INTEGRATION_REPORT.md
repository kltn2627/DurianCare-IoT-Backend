# Báo cáo tích hợp FCM – 2026-11-04

## Kết luận

Code Backend và Mobile đã xong, Backend đang chạy với push **tắt**. **Chưa có push thật nào được gửi hay nhận**: máy này chưa có Firebase credentials và chưa có điện thoại Android nào kết nối (`adb devices` trống). Vì vậy chưa thể coi tính năng là hoàn thành; xem [Phần chưa kiểm chứng](#phần-chưa-kiểm-chứng).

## Kiến trúc thực tế

```text
iot/farm/auth service ──Kafka (iot.alert.email)──▶ NotificationEventListener
                                                        │
                                       NotificationServiceImpl.createNotification
                                       ├─ lưu `notifications` (unique sourceEventId)  ◀── inbox như cũ
                                       └─ PushDeliveryService.enqueue (không bao giờ ném lỗi)
                                                        │  tạo `push_deliveries` (_id = notificationId)
                                                        ▼
                                         pushExecutor (ngoài luồng Kafka)
                                                        │  claim nguyên tử → SENDING
                                                        ▼
                                  FcmPushGateway ─ sendEachForMulticast ─▶ FCM ─▶ Android
                                                        ▲
                     PushRetryScheduler (30s) ──────────┘  RETRY đến hạn, PENDING sót, claim mồ côi

Mobile: quyền → token → PUT /device-tokens (qua Gateway, identity từ JWT)
        logout: DELETE /device-tokens/{deviceId} → auth logout → xoá token native
```

Quyết định chính:

- **Mobile dùng `expo-notifications`**, không dùng `@react-native-firebase`. Thư viện này có sẵn cho Expo SDK 57 / RN 0.86, trả về token FCM gốc qua `getDevicePushTokenAsync`, xử lý được cả nền và terminated. Không dùng Expo Push Service.
- **`push_deliveries` là collection riêng.** Các thao tác inbox `save` nguyên document, nên nếu trạng thái push nằm trong `notifications` thì `markAsRead` có thể ghi đè và gây gửi lại.
- **Message có `notification` và `data`**, để Android tự hiện khi app bị tắt.
- **Chống trùng ba lớp:** unique `sourceEventId` (inbox), claim nguyên tử theo `notificationId` (gửi), Android `tag = notificationId` (khay thông báo, phòng khi sender chết giữa chừng và gửi lại).
- **Giữ nguyên `firebase-admin` đầy đủ.** Thử loại Firestore/Storage để giảm dung lượng thì SDK thiếu lớp `JacksonFactory` lúc chạy (test bắt được), nên đã bỏ exclusion. Đổi lại ảnh Docker lớn hơn.

## Đáp ứng yêu cầu

| Yêu cầu | Cách làm |
|---|---|
| Identity từ JWT/Gateway | `X-Auth-User-Id`; body không có `userId` |
| Nhiều thiết bị / một máy một user | Mỗi `deviceId` một dòng; đăng ký mới vô hiệu hoá token khác cùng `deviceId` |
| Token hỏng | `UNREGISTERED`, `SENDER_ID_MISMATCH`, `INVALID_ARGUMENT` do token → `active:false` |
| Quota, timeout, Firebase lỗi | Retry tối đa 5 lần, chỉ token lỗi, backoff 30s → 15m, tôn trọng `Retry-After` |
| Lỗi FCM không mất lịch sử | Notification lưu trước; gửi tách luồng; mọi lỗi được bắt |
| Không vỡ khi thiếu Firebase | Tắt mặc định; credentials hỏng chỉ tắt push, service vẫn chạy |
| Replay Kafka | Thông báo cũ hơn `FCM_MAX_AGE` không push |
| Không lộ dữ liệu | Allow-list cho `data`; log chỉ có mã lỗi và ID, không có token |
| Logout khi mất mạng/hết phiên | Xoá token native; lần gửi sau FCM trả `UNREGISTERED`, Backend tự vô hiệu hoá |

## API và schema

Chi tiết ở [FCM_API_MOBILE.md](FCM_API_MOBILE.md).

- `PUT /api/v1/notification/device-tokens`: đăng ký/cập nhật.
- `DELETE /api/v1/notification/device-tokens/{deviceId}`: hủy, `204`.

MongoDB `duriancare_notification`, không cần migration; index tự tạo khi service khởi động, đã xác nhận trên DB thật:

| Collection | Trường chính | Index |
|---|---|---|
| `device_tokens` (mới) | `token, userId, deviceId, platform, appVersion, active, deactivationReason, deactivatedAt, createdAt, updatedAt` | unique `token`; `userId+active`; `deviceId+active`; TTL 30 ngày trên `deactivatedAt` |
| `push_deliveries` (mới) | `_id=notificationId, receiverId, status, attempts, nextAttemptAt, claimedAt, acceptedCount, invalidTokenCount, retryDeviceTokenIds, lastError, sentAt, createdAt` | `status+nextAttemptAt`; TTL 30 ngày trên `createdAt` |
| `notifications` (mở rộng) | Thêm `eventType`, `occurredAt`, tuỳ chọn | Không đổi |

`NotificationResponse` không đổi, API inbox giữ nguyên.

**Kafka:** không thêm topic hay consumer; vẫn listener cũ, topic theo `KAFKA_ALERT_TOPIC`. Sự kiện `USER_REGISTERED` trong `duriancare.auth.events` chỉ gửi OTP email, không push.

## Thay đổi theo repo

Không có thay đổi nào được commit.

**DurianCare-IoT-Backend**

- Mới: `config/FcmConfig`, `config/FcmProperties`, `controller/DeviceTokenController`, `domain/DeviceToken`, `domain/PushDelivery`, `domain/PushDeliveryStatus`, `dto/RegisterDeviceTokenRequest`, `dto/DeviceTokenResponse`, `repository/DeviceTokenRepository`, `repository/PushDeliveryRepository`, `service/DeviceTokenService`, `service/push/*` (8 file), 5 lớp test, 4 tài liệu `docs/FCM_*`.
- Sửa: `duriancare-notification-service/pom.xml`, `application.yml`, `Notification`, `CreateNotificationRequest`, `NotificationMapper`, `NotificationEventListener`, `NotificationServiceImpl`, 2 test cũ, `infrastructure/docker-compose.yml`, `.gitignore`, `docs/notifications.md`.

**DurianCare-IoT-Mobile-App**

- Mới: `src/features/notification/push/*` (4 file), `app.config.js`, `eas.json`, `tests/push-notifications.test.cjs`.
- Sửa: `app.json` (thêm `android.package`, plugin), `package.json`, `package-lock.json`, `app/_layout.tsx`, `src/session/SessionContext.tsx`, `notificationApi.ts`, `DurianNotificationInboxScreen.tsx` (chỉ tự làm mới khi có push, không đổi UI).

**DurianCare-IoT-Web-Client:** không đổi, push chỉ dành cho Android.

GitNexus impact trước khi sửa `createNotification`, `toDocument`, `handle`, `logout`, `clearLocalSession`, `SessionProvider`, `DurianCareRootLayout`: tất cả LOW.

## Kết quả kiểm tra

| Lệnh | Kết quả |
|---|---|
| `mvn ... -Dtest='com.duriancare.notification.**' test` (Mongo fixture thật, Firebase SDK thật + HTTP giả) | **84/84 pass**, fixture DB đã dọn |
| `npx tsc --noEmit` | Pass |
| `node --test tests/*.cjs` | **42/42 pass** |
| `npx expo export --platform android`, `--platform web` | Pass |
| `npx expo prebuild --platform android` | Pass; manifest có `default_notification_channel_id`, icon |
| `gradlew assembleDebug` trong thư mục OneDrive | **Fail** ở `react-native-worklets` (`build.ninja still dirty`), lỗi môi trường, không thuộc thay đổi này |
| `gradlew assembleDebug` từ bản sao ngoài OneDrive | **Pass**; APK arm64, 95 MB, gói `com.duriancare.smartfarm`; `aapt` xác nhận `POST_NOTIFICATIONS`, `ExpoFirebaseMessagingService`. Bản này build **không** có `google-services.json` nên không nhận được push. Đã xoá bản sao |
| `docker compose build` + `up -d --no-deps` | Service healthy, log `FCM push is disabled` |

**Trên hệ thống đang chạy** (không ghi dữ liệu thật):

| Kiểm tra | Kết quả |
|---|---|
| Gateway, không JWT / JWT sai / giả header `X-Auth-User-Id` | `401` cả ba |
| Service, thiếu identity | `401` |
| Service, gửi `ExponentPushToken[...]` | `400`, không lặp lại token |
| Service, `DELETE` user không có token | `204`, không đổi dữ liệu |
| Index hai collection mới | Đã tạo |
| `device_tokens`, `push_deliveries` | 0 dòng; `notifications` 33 dòng |
| Consumer Kafka | Đã gán partition, lag 0 |

Ảnh rollback: `duriancare/notification-service:pre-fcm-2026-11-04`.

## Phần chưa kiểm chứng

- **Android thật: chưa kiểm tra gì.** Gồm xin quyền, lấy token FCM thật, hiển thị ở foreground/background/terminated, nhấn điều hướng, không hiển thị trùng, logout rồi đăng nhập tài khoản khác.
- **Firebase thật: chưa gửi lần nào.** Phản hồi FCM trong test là giả lập theo định dạng FCM v1.
- **Integration end-to-end** (cảnh báo thật → FCM → điện thoại) chưa chạy.
- **`PUT` thành công qua Gateway với JWT thật** chưa chạy, vì sẽ ghi vào DB thật và cần tài khoản thật. Luồng lưu đã được test ở Mongo fixture.
- **Cách đọc payload khi nhấn thông báo** dựa trên đọc mã nguồn `expo-notifications`, chưa xác nhận trên máy.
- **Build release/AAB** chưa chạy; mới có debug APK.

## Giới hạn đã biết

1. **Chưa có nơi nào phát `DISEASE_DETECTED`.** Listener và Mobile đã hỗ trợ, nhưng AI service không gửi event, nên kết quả chẩn đoán bệnh lá hiện không tạo thông báo.
2. **Backend không biết máy đã nhận hay chưa.** `SENT` chỉ nghĩa là FCM đã nhận tin.
3. **Cổng 8085 đang mở ra host.** Ai vào được cổng này có thể giả `X-Auth-User-Id`, tức gắn token của họ vào tài khoản khác và nhận thông báo của tài khoản đó. Rủi ro này đã có với inbox, nay nghiêm trọng hơn. Production nên bỏ publish cổng.
4. Nếu sender chết đúng lúc đang gửi, lần gửi lại đi tới mọi token. `tag` ngăn thông báo chồng, nhưng máy có thể kêu lại.
5. Nếu `POST_NOTIFICATIONS` bị từ chối, app không đăng ký token và chưa có giao diện nhắc người dùng.
6. Đăng xuất xoá token native, nên mỗi lần đăng nhập có token mới; token cũ chỉ bị vô hiệu hoá ở lần gửi kế tiếp nếu `DELETE` thất bại.
7. Build Gradle local phải đặt repo ngoài OneDrive; EAS không bị ảnh hưởng.

## Việc người dùng cần làm

1. Tạo Firebase project + Android app `com.duriancare.smartfarm`, đặt `google-services.json` vào gốc Mobile.
2. Tạo service account, đặt vào `infrastructure/secrets/`, bật `FCM_ENABLED=true`, restart service ([FCM_SETUP_GUIDE.md](FCM_SETUP_GUIDE.md)).
3. Build APK có `google-services.json` (EAS, hoặc local ngoài OneDrive) rồi cài lên điện thoại.
4. Chạy 13 bước ở [FCM_TESTING_GUIDE.md](FCM_TESTING_GUIDE.md) mục 2.
5. Quyết định có commit không, và có xử lý Giới hạn 1, 3 không.
