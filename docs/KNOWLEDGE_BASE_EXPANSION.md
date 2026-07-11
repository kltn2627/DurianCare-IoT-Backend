# Knowledge Base Expansion for DurianCare AI

## 1. Phạm vi cập nhật

Milestone này chỉ mở rộng kho tri thức nông nghiệp cho module AI chẩn đoán bệnh lá sầu riêng.

- Không thay đổi mô hình AI
- Không thay đổi API hiện có
- Không thay đổi frontend
- Không thêm schema mới
- Không sửa luồng Recommendation Engine / Decision Support

Toàn bộ nội dung được bổ sung qua migration seed:

- `duriancare-ai-service/db/migration/V4__expand_knowledge_base.sql`

## 2. Mục tiêu dữ liệu

Kho tri thức được làm giàu để hệ thống có thể trả lời tốt hơn theo các nhóm nội dung:

- Canh tác bền vững
- Biện pháp sinh học
- Nông nghiệp hữu cơ
- Phòng ngừa bệnh
- Sử dụng hóa chất an toàn
- Sẵn sàng xuất khẩu
- Cảnh báo tồn dư thuốc

## 3. Nguồn dữ liệu sử dụng

Các bản ghi seed mới chỉ lấy từ nguồn có tính thẩm quyền hoặc bài báo chuyên ngành:

- FAO
- IR-4 Environmental Horticulture Program
- PubMed Central
- CABI
- UC IPM
- Cornell IPM
- Vietnam Plant Protection Department
- VietGAP
- GlobalG.A.P.

## 4. Thống kê cập nhật

Sau khi mở rộng kho tri thức:

- Số bệnh được bao phủ: `5`
- Số khuyến nghị sinh học mới: `29`
- Số khuyến nghị hữu cơ mới: `20`
- Số khuyến nghị hóa học mới: `6`
- Số quy tắc / ghi chú xuất khẩu mới: `10`
- Số nguồn tham chiếu mới: `7`
- Số mốc PHI / harvest interval mới: `5`

Các biến thể `monitoring`, `scouting`, `residue awareness` hiện được mã hóa trong:

- `kb_organic_treatments`
- `kb_export_requirements`

vì schema hiện tại chưa có bảng riêng cho giám sát đồng ruộng.

## 5. Danh mục bệnh được làm giàu

### 5.1 `ALGAL_LEAF_SPOT`

- Làm rõ bệnh liên quan đến điều kiện ẩm kéo dài, tán rậm, thiếu thông thoáng.
- Bổ sung hướng dẫn sinh học theo IPM.
- Bổ sung cảnh báo xuất khẩu và lưu ý tồn dư.

### 5.2 `LEAF_BLIGHT`

- Làm rõ tính chất bệnh lan nhanh trong điều kiện ấm ẩm.
- Bổ sung các hướng xử lý ưu tiên sinh học và hữu cơ.
- Bổ sung hóa chất chỉ khi thật cần thiết và phải kiểm tra PHI / MRL.

### 5.3 `PHOMOPSIS_LEAF_SPOT`

- Bổ sung mô tả về nhiễm tiềm ẩn và bùng phát theo mưa / ẩm cao.
- Làm giàu phần phòng ngừa, cắt tỉa, vệ sinh vườn.
- Bổ sung cảnh báo đặc biệt cho xuất khẩu.

### 5.4 `ALLOCARIDARA_ATTACK`

- Bổ sung kiến thức cho đối tượng chích hút.
- Ưu tiên biện pháp sinh học, thiên địch, và quản lý lộc non.
- Chỉ dùng hóa chất khi có căn cứ nhãn đăng ký tại Việt Nam.

### 5.5 `HEALTHY_LEAF`

- Bổ sung tri thức cho trạng thái mục tiêu của vườn.
- Mục đích là cho AI trả lời theo hướng duy trì sức khỏe lá, không chỉ chữa bệnh.
- Gắn thêm nội dung theo dõi sau mưa và theo dõi dinh dưỡng.

## 6. Cấu trúc seed mới

Migration `V4__expand_knowledge_base.sql` cập nhật các bảng sau:

- `kb_diseases`
- `kb_reference_sources`
- `kb_disease_symptoms`
- `kb_disease_causes`
- `kb_biological_treatments`
- `kb_organic_treatments`
- `kb_chemical_treatments`
- `kb_active_ingredients`
- `kb_recommended_chemicals`
- `kb_recommended_chemical_active_ingredients`
- `kb_harvest_intervals`
- `kb_export_requirements`

## 7. Chiến lược tham chiếu

Mỗi khuyến nghị đều gắn với:

- `source_name`
- `source_type`
- `publication_title`
- `url`
- `confidence_level`

Điều này giúp backend:

- ưu tiên nguồn có thẩm quyền
- tránh bịa kiến thức
- hỗ trợ trích dẫn khi hiển thị nội dung cho người dùng

## 8. Ví dụ phản hồi AI sau khi mở rộng kho tri thức

### 8.1 Khi AI dự đoán `ALGAL_LEAF_SPOT`

> Bệnh đốm tảo lá thường nặng hơn trong điều kiện ẩm kéo dài và tán lá rậm. Hệ thống khuyến nghị ưu tiên thông thoáng tán, giảm ẩm bề mặt lá, tăng giám sát sau mưa và chỉ dùng hóa chất khi thật cần thiết theo nhãn đăng ký và thời gian cách ly.

### 8.2 Khi AI dự đoán `LEAF_BLIGHT`

> Bệnh cháy lá có thể lan nhanh khi thời tiết ấm ẩm và lá giữ ướt lâu. Hệ thống khuyến nghị vệ sinh vườn, cắt bỏ phần nhiễm nặng, ưu tiên biện pháp sinh học / hữu cơ và kiểm tra PHI trước khi thu hoạch.

### 8.3 Khi AI dự đoán `PHOMOPSIS_LEAF_SPOT`

> Đốm lá Phomopsis có thể xuất hiện dưới dạng tổn thương nhỏ và phát triển mạnh khi mưa nhiều, ẩm cao. Hệ thống khuyến nghị tỉa tán, giảm splash dispersal, theo dõi tái phát sau mưa và chỉ chọn thuốc hóa học khi đã xác minh nhãn và MRL.

### 8.4 Khi AI dự đoán `ALLOCARIDARA_ATTACK`

> Tình trạng này là áp lực của côn trùng chích hút trên đợt non. Hệ thống khuyến nghị quản lý đọt non, bảo tồn thiên địch, dùng bẫy giám sát và chỉ cân nhắc hoạt chất đã được phép lưu hành khi mật số vượt ngưỡng.

### 8.5 Khi AI dự đoán `HEALTHY_LEAF`

> Lá đang ở trạng thái khỏe, nhưng vẫn cần duy trì IPM, dinh dưỡng cân bằng, tán thông thoáng và theo dõi định kỳ sau mưa để giữ ổn định chất lượng vườn.

## 9. Ghi chú triển khai

- Không thay đổi giao diện API của hệ thống.
- Không tái huấn luyện model.
- Không thêm bảng mới để tránh phá vỡ code hiện tại.
- Các nội dung giám sát và lưu ý xuất khẩu được nhúng vào seed hiện có để Recommendation Engine đọc được ngay.

