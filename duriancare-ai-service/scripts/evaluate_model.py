import os
import torch
from torch import nn
from torchvision import models, transforms
from PIL import Image
from sklearn.metrics import classification_report, confusion_matrix

# =====================================================================
# 1. CẤU HÌNH ĐƯỜNG DẪN THỰC TẾ (Duy kiểm tra lại xem đúng chưa nhé)
# =====================================================================
TEST_DIR = r"C:/Users/minhd/Code/DataSet/Durian_AI_Training/DLD_Dataset/test"
WEIGHTS_PATH = r"models/mobilenetv2_classifier_high_acc.pth"

# Tên 5 nhóm thư mục bệnh sầu riêng thực tế trong tập dataset của bạn
CLASSES = [
    "ALGAL_LEAF_SPOT",
    "ALLOCARIDARA_ATTACK",
    "HEALTHY_LEAF",
    "LEAF_BLIGHT",
    "PHOMOPSIS_LEAF_SPOT"
]

# Chuẩn hóa ảnh giống hệt lúc train
IMAGE_TRANSFORM = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
    transforms.Normalize(
        mean=[0.485, 0.456, 0.406],
        std=[0.229, 0.224, 0.225]
    )
])

# =====================================================================
# 2. KHỞI TẠO VÀ LOAD MODEL PYTORCH THẬT
# =====================================================================
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"💻 Đang chạy tính toán trên thiết bị: {device}")

if not os.path.exists(WEIGHTS_PATH):
    raise FileNotFoundError(f"Không tìm thấy file trọng số tại: {WEIGHTS_PATH}")

# Khởi dựng kiến trúc MobileNetV2 chuẩn theo bài train của bạn
model = models.mobilenet_v2(weights=None)
in_features = model.classifier[1].in_features
model.classifier[1] = nn.Linear(in_features, len(CLASSES))

# Load checkpoint an toàn
checkpoint = torch.load(WEIGHTS_PATH, map_location=device, weights_only=True)
if isinstance(checkpoint, dict) and "state_dict" in checkpoint:
    state_dict = checkpoint["state_dict"]
elif isinstance(checkpoint, dict) and "model_state_dict" in checkpoint:
    state_dict = checkpoint["model_state_dict"]
else:
    state_dict = checkpoint

# Dọn dẹp tiền tố nếu có
clean_state_dict = {k.removeprefix("module.").removeprefix("model."): v for k, v in state_dict.items()}
model.load_state_dict(clean_state_dict, strict=True)
model.to(device)
model.eval()

# =====================================================================
# 3. QUÉT DATASET VÀ ĐÁNH GIÁ
# =====================================================================
def run_evaluation():
    y_true = []
    y_pred = []

    print("🔄 AI đang bắt đầu quét thực tế qua tập dữ liệu kiểm thử...")

    for class_name in CLASSES:
        class_folder = os.path.join(TEST_DIR, class_name)
        if not os.path.exists(class_folder):
            print(f"⚠️ Cảnh báo: Không tìm thấy thư mục nhãn {class_name}, bỏ qua.")
            continue

        print(f"📁 Đang xử lý nhóm bệnh: {class_name}")
        for img_name in os.listdir(class_folder):
            img_path = os.path.join(class_folder, img_name)

            if img_name.lower().endswith(('.png', '.jpg', '.jpeg', '.webp')):
                try:
                    # 1. Lưu nhãn thực tế
                    y_true.append(class_name)

                    # 2. Đọc ảnh và đưa qua model dự đoán
                    with torch.no_grad():
                        pil_img = Image.open(img_path).convert("RGB")
                        tensor_img = IMAGE_TRANSFORM(pil_img).unsqueeze(0).to(device)

                        outputs = model(tensor_img)
                        pred_idx = outputs.argmax(dim=1).item()

                        # Lưu nhãn do AI đoán
                        y_pred.append(CLASSES[pred_idx])
                except Exception as e:
                    print(f"❌ Lỗi khi đọc ảnh {img_name}: {e}")

    if len(y_true) == 0:
        print("❌ Lỗi: Không tìm thấy bất kỳ hình ảnh hợp lệ nào trong đường dẫn test đã cấu hình!")
        return

    # =====================================================================
    # 4. IN BẢNG BÁO CÁO KẾT QUẢ ĐẸP MẮT
    # =====================================================================
    print("\n" + "="*65)
    print("📊 BÁO CÁO ĐỘ CHÍNH XÁC CHI TIẾT TỪNG LOẠI BỆNH (DURIANCARE AI)")
    print("="*65)
    print(classification_report(y_true, y_pred, target_names=CLASSES, zero_division=0))

    print("\n🔍 MA TRẬN NHẦM LẪN (CONFUSION MATRIX):")
    print(f"Thứ tự các nhãn: {CLASSES}\n")
    print(confusion_matrix(y_true, y_pred, labels=CLASSES))

if __name__ == "__main__":
    run_evaluation()