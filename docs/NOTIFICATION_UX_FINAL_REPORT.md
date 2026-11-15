# Notification UX — Final Report (Web + Mobile, 31/10/2026)

Backend notification (Kafka, dedup `sourceEventId`, retry, recovery, quyền người nhận) giữ nguyên; chỉ hoàn thiện giao diện.
API dùng: list, unread, count, markRead, markAllRead, delete (đã có sẵn). Không thêm API mới.

## Web (`DurianCare-IoT-Web-Client`)

| Hạng mục | Trước | Sau |
|---|---|---|
| Đánh dấu tất cả đã đọc (hộp thư + chuông) | Chạy ngay, không xác nhận | `ConfirmDialog` "Xác nhận đánh dấu tất cả đã đọc / Bạn có chắc…?" [Hủy][Xác nhận]; toast "Đã đánh dấu tất cả thông báo là đã đọc." |
| Đánh dấu đã đọc | Không phản hồi | Toast thành công; nút đổi thành "Đã đọc" |
| Xóa | Modal tự viết | `ConfirmDialog` tone danger, nút có spinner khi đang xóa; toast "Đã xóa thông báo." |
| Lỗi thao tác | Thay cả danh sách bằng hộp lỗi | Toast lỗi "Không thể cập nhật thông báo. Vui lòng thử lại." — danh sách vẫn hiển thị |
| Lỗi tải | Hộp lỗi | Hộp lỗi + nút "Thử lại" |
| Double-click | `markRead`/mở không chặn | Mọi thao tác chặn khi `busyId` khác null; nút confirm disabled khi đang chạy |
| Loại thông báo | Mã thô (`DEVICE`) | Nhãn tiếng Việt ("Thiết bị IoT") |
| Thẻ | Hiện id nội bộ | Hiện nguồn: thiết bị/cây từ metadata |
| Làm mới | Không trạng thái | Disabled + icon xoay khi đang tải |
| Đồng bộ chuông/hộp thư | Event `duriancare:notifications-changed` (đã có) | Giữ nguyên; chuông dùng cùng dialog/toast |

Thành phần mới: `components/feedback/ConfirmDialog.tsx` (focus nút Hủy, Escape/backdrop hủy, khóa khi busy, `role=alertdialog`);
`Toast` thêm `tone="error"` (`role=alert`, 7 s). Không dùng `window.confirm/alert` trong luồng thông báo. Không có `console.log` trong `src`.

## Mobile (`DurianCare-IoT-Mobile-App`)

| Hạng mục | Trước | Sau |
|---|---|---|
| Xác nhận | `Alert.alert` native | `ConfirmModal` trong app (cùng giao diện Android/iOS, spinner khi busy) |
| Kết quả | Không có | `ToastBanner` thành công (4 s) / lỗi (6 s) |
| Lỗi thao tác | Thay danh sách | Toast lỗi, danh sách giữ nguyên; hộp lỗi tải có nút "Thử lại" |
| Double-tap | Chỉ disabled sau re-render | `busyRef` chặn đồng bộ trước khi React re-render |
| Làm mới | Nút | Thêm kéo-để-làm-mới (`RefreshControl`) |
| Loại / nguồn | Mã thô + id | Nhãn tiếng Việt + thiết bị/cây |
| Chi tiết | `Alert.alert` xóa, lỗi bằng hộp đỏ | `ConfirmModal` + toast; hiển thị "Thông báo không còn trong hộp thư." sau khi xóa |

## Kiểm thử

- Web: `__tests__/iot-hardening.test.ts` (nhãn loại, nguồn), toàn bộ jest 159/159; `tsc` OK.
- Mobile: `tests/iot-hardening.test.cjs` kiểm tra không còn `Alert.alert`, có `ConfirmModal`/`ToastBanner`/`busyRef`, đúng nội dung xác nhận/toast; 18/18; `tsc` OK; `expo export` Android OK.
- Chưa kiểm thử trên thiết bị/trình duyệt thật bằng thao tác tay (không tạo thông báo vào tài khoản thật).

## Ngoài phạm vi (ghi nhận)

`window.confirm` còn ở các module khác (cây, khu canh tác, hồ sơ, chat, kiến thức) — không thuộc luồng thông báo.
