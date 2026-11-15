# Hướng dẫn cấu hình FCM

Khi chưa cấu hình, hệ thống vẫn chạy bình thường: inbox, email, Kafka không đổi, chỉ không có push.

## 1. Tạo Firebase project và Android app

1. Vào [Firebase Console](https://console.firebase.google.com) và tạo project (hoặc dùng project có sẵn).
2. **Project settings → General → Your apps → Add app → Android**.
3. **Android package name** phải đúng: `com.duriancare.smartfarm` (khai báo ở `app.json`). Đổi sau này phải tạo lại Android app trong Firebase.
4. Tải `google-services.json`, đặt ở gốc `DurianCare-IoT-Mobile-App/`. File đã nằm trong `.gitignore`, không commit.

Dùng chung `google-services.json` cho build local; với EAS, tải lên dưới dạng file secret:

```bash
eas env:create --scope project --name GOOGLE_SERVICES_JSON --type file --visibility secret --value ./google-services.json
```

## 2. Tạo service account cho Backend

1. **Project settings → Service accounts → Generate new private key**.
2. Lưu thành `DurianCare-IoT-Backend/infrastructure/secrets/firebase-service-account.json`.

Thư mục `secrets/` và mẫu `firebase-service-account*.json`, `*firebase-adminsdk*.json` đã bị git bỏ qua. Trước khi commit, xác nhận bằng:

```bash
git check-ignore infrastructure/secrets/firebase-service-account.json
```

File này là khoá quản trị của project. Không gửi qua chat, không đưa vào ảnh Docker, không đặt trong repo Mobile.

## 3. Bật FCM cho Docker

Thêm vào `infrastructure/.env`:

```env
FCM_ENABLED=true
FIREBASE_CREDENTIALS_FILE=/run/secrets/duriancare/firebase-service-account.json
```

Compose mount `infrastructure/secrets` vào `/run/secrets/duriancare` ở chế độ chỉ đọc. Khởi động lại đúng một service, không cần `down`:

```bash
docker compose -p duriancare up -d --no-deps duriancare-notification-service
```

```bash
docker logs duriancare-notification-service 2>&1 | grep -i "FCM push"
```

Kết quả mong đợi: `FCM push is enabled for project <project-id>`. Nếu thấy `Firebase could not be initialised (...)`, service vẫn chạy nhưng không gửi push: kiểm tra đường dẫn và nội dung file.

## 4. Biến môi trường

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `FCM_ENABLED` | `false` | Bật/tắt push. |
| `FIREBASE_CREDENTIALS_FILE` | rỗng | Đường dẫn file service account (ưu tiên). |
| `FIREBASE_CREDENTIALS_BASE64` | rỗng | Service account mã hoá base64, cho môi trường không mount được file. |
| `FIREBASE_PROJECT_ID` | lấy từ credentials | Ghi đè project ID. |
| `FCM_ANDROID_CHANNEL_ID` | `duriancare-alerts` | Phải khớp `PUSH_CHANNEL_ID` trên Mobile. |
| `FCM_TTL` | `PT6H` | FCM giữ tin bao lâu khi máy offline. |
| `FCM_MAX_AGE` | `PT6H` | Thông báo cũ hơn mức này không push nữa. |
| `FCM_MAX_ATTEMPTS` | `5` | Số lần gửi tối đa. |
| `FCM_RETRY_BASE_DELAY` | `PT30S` | Backoff gốc, nhân đôi mỗi lần. |
| `FCM_RETRY_MAX_DELAY` | `PT15M` | Trần backoff. |
| `FCM_RETRY_SCAN_INTERVAL` | `PT30S` | Chu kỳ quét retry. |
| `FCM_CLAIM_TTL` | `PT5M` | Sau thời gian này claim `SENDING` bị coi là sender đã chết. |

Compose hiện chỉ truyền 4 biến đầu; các biến còn lại dùng mặc định, muốn đổi thì thêm vào khối `environment` của service.

**Chạy local không Docker:** đặt `FCM_ENABLED=true` và `FIREBASE_CREDENTIALS_FILE=<đường dẫn tuyệt đối>` trong `.env` của service.

**Production:** dùng secret manager của nền tảng, mount thành file và trỏ `FIREBASE_CREDENTIALS_FILE`. Nên tạo service account riêng cho production, thu hồi khoá cũ khi đổi người vận hành.

## 5. Build Android

Expo Go không hỗ trợ FCM từ SDK 53, phải dùng bản build. App cần `EXPO_PUBLIC_API_BASE_URL` trỏ tới IP Gateway mà điện thoại truy cập được (đã khai báo trong `.env` của Mobile; biến này được nhúng lúc build).

**APK qua EAS (khuyên dùng để cài điện thoại):**

```bash
npx eas-cli build --platform android --profile preview
```

**AAB cho Google Play:**

```bash
npx eas-cli build --platform android --profile production
```

**APK local** (cần JDK 21 và Android SDK, `google-services.json` ở gốc Mobile):

Phải build từ bản sao **ngoài OneDrive**: trong OneDrive, `react-native-worklets` lỗi `build.ninja still dirty`. `expo prebuild` tự đổi script `android`/`ios` trong `package.json`, nên kiểm tra và hoàn lại trước khi commit.

```bash
npx expo prebuild --platform android --clean
```

```bash
cd android && ./gradlew assembleRelease
```

File ra ở `android/app/build/outputs/apk/release/`. Bản release do Expo template ký bằng debug keystore, chỉ dùng để thử; phát hành thật phải cấu hình keystore riêng (EAS tự quản lý).

Sau khi thêm hoặc đổi `google-services.json`, phải chạy lại prebuild (`--clean`) rồi build: file này được nhúng lúc build, không đọc lúc chạy.

Cài lên điện thoại đang bật USB debugging:

```bash
adb install -r android/app/build/outputs/apk/release/app-release.apk
```
