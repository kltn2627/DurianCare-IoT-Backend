# Hướng dẫn kiểm thử FCM

## 1. Test tự động

**Backend** (cần Mongo cho nhóm integration; mỗi test tạo database `notification_fixture_<uuid>` riêng và xoá sau khi chạy, không đụng DB thật). Dùng JDK 21. `DURIANCARE_FIXTURE_MONGO_URI` là URI tới Mongo Docker (cổng 27018); không đặt biến này thì test Mongo tự bỏ qua:

```bash
mvn -pl duriancare-notification-service -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest='com.duriancare.notification.**' test
```

| Yêu cầu | Test |
|---|---|
| Đăng ký hợp lệ, token không hợp lệ, chưa đăng nhập | `DeviceTokenControllerTest` |
| Token trùng, cập nhật, logout, nhiều thiết bị, đổi tài khoản | `PushFlowMongoIntegrationTest` |
| Firebase thành công/lỗi, token hết hiệu lực (qua SDK thật, HTTP giả) | `FcmPushGatewayTest` |
| Kafka event, retry + dedup, lỗi không ảnh hưởng consumer | `PushFlowMongoIntegrationTest` |
| Bật/tắt FCM, thiếu hoặc hỏng credentials | `FcmConfigTest` |
| Payload, allow-list, không lộ dữ liệu | `PushMessageFactoryTest` |

**Mobile:**

```bash
node --test tests/*.cjs
```

```bash
npx tsc --noEmit
```

`tests/push-notifications.test.cjs` chạy module TypeScript thật với native giả: quyền, lấy/gửi token, refresh, logout, đổi tài khoản, đọc payload, điều hướng. Test này không chứng minh được hành vi trên điện thoại; phần đó ở mục 2.

## 2. Kiểm thử trên Android thật

Chuẩn bị: làm xong [FCM_SETUP_GUIDE.md](FCM_SETUP_GUIDE.md), cài APK, điện thoại cùng mạng với Gateway. Dùng tài khoản của chính bạn và thiết bị thử (`is_test`) nếu chưa muốn cảnh báo tới người dùng thật.

Theo dõi Backend bằng:

```bash
docker logs -f duriancare-notification-service 2>&1 | grep -E "Push for|FCM|invalid FCM"
```

Xem trạng thái trong Mongo (`duriancare_notification`):

```js
db.device_tokens.find({}, { token: 0 }).sort({ updatedAt: -1 }).limit(5)
db.push_deliveries.find().sort({ createdAt: -1 }).limit(5)
```

| # | Thao tác | Kỳ vọng |
|---|---|---|
| 1 | Đăng nhập lần đầu trên Android 13+ | Hiện hộp thoại quyền; sau khi cho phép, `device_tokens` có 1 dòng `active: true`. |
| 2 | Từ chối quyền, rồi bật trong Cài đặt, quay lại app | Dòng token xuất hiện, không hỏi quyền lại. |
| 3 | App mở (foreground), tạo cảnh báo | Hiện đúng 1 banner; inbox đang mở tự cập nhật. |
| 4 | App ở nền, tạo cảnh báo | Hiện đúng 1 thông báo hệ thống. |
| 5 | Vuốt tắt app (terminated), tạo cảnh báo | Hiện đúng 1 thông báo. Một số máy (Xiaomi, Oppo, Vivo) chặn app bị vuốt tắt; bật tự khởi chạy nếu cần. |
| 6 | Nhấn thông báo ở mỗi trạng thái 3–5 | Mở đúng màn hình, thông báo chuyển đã đọc. |
| 7 | Kiểm tra inbox | Bản ghi có mặt, không trùng. `push_deliveries.status = SENT`. |
| 8 | Đăng xuất | Dòng token `active: false`, lý do `LOGOUT`; khay thông báo được dọn; cảnh báo mới không tới máy này. |
| 9 | Đăng nhập tài khoản khác | Token mới thuộc tài khoản mới; cảnh báo của tài khoản cũ không tới. |
| 10 | Tắt mạng, đăng xuất, bật mạng, tạo cảnh báo cho tài khoản cũ | Không có thông báo; lần gửi sau trả `UNREGISTERED`, token bị đánh dấu `INVALID`. |
| 11 | Gỡ app, tạo cảnh báo | `UNREGISTERED`, token `INVALID`. Google có thể báo chậm vài giờ. |
| 12 | Tắt mạng điện thoại, tạo cảnh báo, bật lại trong 6 giờ | Thông báo tới 1 lần. |
| 13 | Dựng lỗi `FIREBASE_CREDENTIALS_FILE` sai, tạo cảnh báo | Service healthy, inbox có bản ghi, không push. |

**Tạo cảnh báo thật:** cho thiết bị vượt ngưỡng (ví dụ rút cảm biến đất) hoặc để thiết bị offline. Không publish tay vào Kafka vào tài khoản thật khi chưa được đồng ý.

## 3. Ba mức trạng thái

| Mức | Bằng chứng | Ý nghĩa |
|---|---|---|
| Đã lưu | Có bản ghi trong `notifications` | Inbox hiển thị được, độc lập với FCM. |
| Đã gửi FCM | `push_deliveries.status = SENT`, `acceptedCount > 0` | Google đã nhận tin. **Chưa** có nghĩa điện thoại đã nhận. |
| Thiết bị đã nhận | Quan sát trên máy; báo cáo tổng hợp ở Firebase Console → Messaging | Backend không theo dõi: Admin SDK không có xác nhận theo từng thiết bị. |

| `status` | Nghĩa |
|---|---|
| `PENDING`, `SENDING`, `RETRY` | Đang xử lý. |
| `SENT` | FCM nhận cho ít nhất 1 thiết bị, không còn gì để thử lại. |
| `NO_DEVICE` | Người nhận không có thiết bị active. |
| `FAILED` | Hết lượt thử hoặc lỗi không thử lại được; xem `lastError`. |
| `EXPIRED`, `CANCELLED` | Quá cũ, hoặc thông báo đã bị xoá trước khi gửi. |

## 4. Xử lý sự cố

| Triệu chứng | Kiểm tra |
|---|---|
| Không có dòng `device_tokens` | Có `google-services.json` lúc build không; app có chạy từ APK (không phải Expo Go) không; đã cho phép thông báo chưa. |
| `FAILED`, `SENDER_ID_MISMATCH` | `google-services.json` và service account thuộc hai Firebase project khác nhau. |
| `RETRY` lặp lại, `THIRD_PARTY_AUTH_ERROR`/`PERMISSION_DENIED` | Service account sai hoặc chưa bật Firebase Cloud Messaging API (V1). |
| `NO_DEVICE` | Token đã logout hoặc `receiverId` là tài khoản khác. |
| `SENT` nhưng máy không hiện | Quyền thông báo, channel bị tắt, chế độ tiết kiệm pin của hãng. |
