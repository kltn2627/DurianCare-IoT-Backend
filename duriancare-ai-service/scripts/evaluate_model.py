import os
import torch
from torch import nn
from torchvision import models, transforms
from PIL import Image
from sklearn.metrics import classification_report, confusion_matrix, roc_curve, auc
import matplotlib.pyplot as plt
import seaborn as sns
import numpy as np

# =====================================================================
# 1. CẤU HÌNH ĐƯỜNG DẪN THỰC TẾ
# =====================================================================
TEST_DIR = r"C:/Users/minhd/Code/DataSet/Durian_AI_Training/DLD_Dataset/test"
WEIGHTS_PATH = r"models/mobilenetv2_classifier_high_acc.pth"

CLASSES = [
    "ALGAL_LEAF_SPOT",
    "ALLOCARIDARA_ATTACK",
    "HEALTHY_LEAF",
    "LEAF_BLIGHT",
    "PHOMOPSIS_LEAF_SPOT"
]

IMAGE_TRANSFORM = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
])

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

# =====================================================================
# 2. KHỞI TẠO VÀ LOAD MODEL PYTORCH
# =====================================================================
model = models.mobilenet_v2(weights=None)
in_features = model.classifier[1].in_features
model.classifier[1] = nn.Linear(in_features, len(CLASSES))

checkpoint = torch.load(WEIGHTS_PATH, map_location=device, weights_only=True)
state_dict = checkpoint["state_dict"] if isinstance(checkpoint, dict) and "state_dict" in checkpoint else (checkpoint["model_state_dict"] if isinstance(checkpoint, dict) and "model_state_dict" in checkpoint else checkpoint)
clean_state_dict = {k.removeprefix("module.").removeprefix("model."): v for k, v in state_dict.items()}
model.load_state_dict(clean_state_dict, strict=True)
model.to(device)
model.eval()

# =====================================================================
# 3. QUÉT DATASET VÀ THU THẬP XÁC SUẤT ĐOÁN BỆNH
# =====================================================================
def run_evaluation():
    y_true_idx = []
    y_pred_idx = []
    y_score_list = [] # Lưu xác suất mềm (Probabilities) phục vụ vẽ đường cong ROC riêng từng bệnh

    print("🔄 AI đang bắt đầu quét thực tế qua tập dữ liệu kiểm thử để thu thập dữ liệu vẽ biểu đồ...")

    for class_idx, class_name in enumerate(CLASSES):
        class_folder = os.path.join(TEST_DIR, class_name)
        if not os.path.exists(class_folder):
            continue

        for img_name in os.listdir(class_folder):
            img_path = os.path.join(class_folder, img_name)
            if img_name.lower().endswith(('.png', '.jpg', '.jpeg', '.webp')):
                try:
                    with torch.no_grad():
                        pil_img = Image.open(img_path).convert("RGB")
                        tensor_img = IMAGE_TRANSFORM(pil_img).unsqueeze(0).to(device)

                        outputs = model(tensor_img)
                        # Tính xác suất mềm bằng hàm Softmax
                        probabilities = torch.softmax(outputs, dim=1).cpu().numpy()[0]
                        pred_idx = np.argmax(probabilities)

                        y_true_idx.append(class_idx)
                        y_pred_idx.append(pred_idx)
                        y_score_list.append(probabilities)
                except Exception as e:
                    pass

    y_true = np.array(y_true_idx)
    y_pred = np.array(y_pred_idx)
    y_scores = np.array(y_score_list)

    # -----------------------------------------------------------------
    # BIỂU ĐỒ 1: MA TRẬN NHẦM LẪN TỔNG THỂ (CONFUSION MATRIX HEATMAP)
    # -----------------------------------------------------------------
    print("🎨 1. Đang vẽ Ma trận nhầm lẫn tổng thể...")
    cm = confusion_matrix(y_true, y_pred)
    plt.figure(figsize=(9, 7))
    sns.heatmap(cm, annot=True, fmt="d", cmap="Greens", xticklabels=CLASSES, yticklabels=CLASSES)
    plt.title("MA TRẬN NHẦM LẪN ĐÁNH GIÁ MÔ HÌNH DURIANCARE AI", fontsize=12, fontweight='bold', pad=15)
    plt.xlabel("Nhãn Dự Đoán (Predicted Labels)", labelpad=10)
    plt.ylabel("Nhãn Thực Tế (True Labels)", labelpad=10)
    plt.xticks(rotation=25, ha='right')
    plt.tight_layout()
    plt.savefig("01_duriancare_confusion_matrix.png", dpi=300)
    plt.close()

    # -----------------------------------------------------------------
    # BIỂU ĐỒ 2: ĐƯỜNG CONG ROC TỔNG HỢP CHO TỪNG LOẠI BỆNH
    # (Đây chính là đống sơ đồ riêng cho từng loại bệnh mà Duy nhắc tới!)
    # -----------------------------------------------------------------
    print("🎨 2. Đang vẽ Đồ thị đường cong ROC phân tách từng loại bệnh...")
    plt.figure(figsize=(10, 8))

    # Vẽ đường cong ROC cho từng lớp bệnh
    for i in range(len(CLASSES)):
        # Chuyển về bài toán phân loại nhị phân (Bệnh i vs Các bệnh còn lại)
        fpr, tpr, _ = roc_curve(y_true == i, y_scores[:, i])
        roc_auc = auc(fpr, tpr)
        plt.plot(fpr, tpr, lw=2, label=f'Đường ROC của {CLASSES[i]} (AUC = {roc_auc:.3f})')

    plt.plot([0, 1], [0, 1], color='navy', lw=1.5, linestyle='--')
    plt.xlim([0.0, 1.0])
    plt.ylim([0.0, 1.05])
    plt.xlabel('Tỷ lệ Dương tính giả (False Positive Rate)', labelpad=10)
    plt.ylabel('Tỷ lệ Dương tính thật (True Positive Rate)', labelpad=10)
    plt.title('ĐƯỜNG CONG ĐẶC TÍNH HOẠT ĐỘNG THU THẬP (ROC CURVES) TỪNG PHÂN LOẠI BỆNH', fontsize=12, fontweight='bold', pad=15)
    plt.legend(loc="lower right")
    plt.grid(True, linestyle=':', alpha=0.6)
    plt.tight_layout()
    plt.savefig("02_duriancare_multi_class_roc.png", dpi=300)
    plt.close()

    # -----------------------------------------------------------------
    # BIỂU ĐỒ 3 ĐẾN 7: VẼ RIÊNG TỪNG BIỂU ĐỒ CỘT CHO MỖI LOẠI BỆNH
    # -----------------------------------------------------------------
    print("🎨 3. Đang bóc tách xuất riêng lẻ biểu đồ hiệu năng từng loại bệnh...")
    report_dict = classification_report(y_true, y_pred, target_names=CLASSES, output_dict=True)

    for idx, class_name in enumerate(CLASSES):
        metrics = ['Precision', 'Recall', 'F1-Score']
        values = [report_dict[class_name]['precision'], report_dict[class_name]['recall'], report_dict[class_name]['f1-score']]

        plt.figure(figsize=(6, 4))
        bars = plt.bar(metrics, values, color=['#2ca02c', '#ff7f0e', '#1f77b4'], width=0.5)
        plt.ylim([0.0, 1.1])
        plt.grid(axis='y', linestyle='--', alpha=0.5)

        # Hiện số trên đầu cột
        for bar in bars:
            yval = bar.get_height()
            plt.text(bar.get_x() + bar.get_width()/2.0, yval + 0.02, f"{yval:.2f}", ha='center', va='bottom', fontweight='bold')

        plt.title(f"HIỆU NĂNG PHÂN LOẠI CHI TIẾT: {class_name}", fontsize=11, fontweight='bold')
        plt.ylabel("Điểm số (Score)")
        plt.tight_layout()
        plt.savefig(f"03_metrics_chi_tiet_{class_name}.png", dpi=300)
        plt.close()

    print("🎉 Hoàn thành! Kiểm tra ngay thư mục của bạn để nhận trọn bộ file ảnh sơ đồ siêu đẹp nhen Duy!")

if __name__ == "__main__":
    run_evaluation()