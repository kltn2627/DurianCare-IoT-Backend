from __future__ import annotations

from app.schemas.decision_support import RiskLevel

HEALTHY_LEAF_CODE = "HEALTHY_LEAF"

IMMEDIATE_ACTIONS = {
    "HEALTHY_LEAF": [
        "Duy trì tán cây thông thoáng và chế độ dinh dưỡng cân bằng.",
        "Tiếp tục kiểm tra lá mới theo lịch định kỳ để phát hiện sớm bất thường.",
        "Giữ vệ sinh vườn và tránh tạo điều kiện ẩm kéo dài trên tán.",
    ],
    "ALGAL_LEAF_SPOT": [
        "Cách ly các lá có vết bệnh rõ rệt để giảm lây lan sang lá non.",
        "Tỉa bớt tán dày để lá nhanh khô sau mưa hoặc tưới.",
        "Ưu tiên vệ sinh vườn và theo dõi các vết bệnh mới sau mỗi đợt mưa.",
    ],
    "LEAF_BLIGHT": [
        "Cách ly phần lá bị cháy và thu gom lá bệnh nặng ra khỏi vườn.",
        "Kiểm tra độ thoáng của tán và cải thiện thoát nước quanh gốc.",
        "Không làm ướt tán kéo dài trong giai đoạn ẩm độ cao.",
    ],
    "PHOMOPSIS_LEAF_SPOT": [
        "Thu gom lá bệnh và loại khỏi khu vực tán để giảm nguồn bệnh lưu tồn.",
        "Giảm ẩm trên lá bằng cách giữ tán thông thoáng.",
        "Theo dõi lá non và lá trưởng thành sau các đợt mưa kéo dài.",
    ],
    "ALLOCARIDARA_ATTACK": [
        "Theo dõi đợt lộc non và cách ly phần bị chích hút nặng nếu cần.",
        "Giảm mật số sớm bằng giám sát thường xuyên ngay khi cây ra đọt non.",
        "Bảo tồn thiên địch và tránh can thiệp hóa chất quá sớm.",
    ],
}

MONITORING_PLAN = {
    "HEALTHY_LEAF": [
        "Kiểm tra vườn theo lịch định kỳ trong 7 ngày tới.",
        "Chụp lại lá từ nhiều góc nếu xuất hiện đốm hoặc biến dạng mới.",
        "Duy trì ghi chép chăm sóc để so sánh với lần kiểm tra tiếp theo.",
    ],
    "DEFAULT": [
        "Kiểm tra lại cây sau 3-5 ngày để đánh giá tiến triển.",
        "Theo dõi thêm các lá mới, mép lá và mặt dưới lá.",
        "Nếu triệu chứng lan nhanh, chụp lại ảnh mới để đánh giá lại.",
    ],
}

EXPORT_TEMPLATE = [
    "Observe PHI before harvest.",
    "Maintain pesticide application records.",
    "Follow VietGAP practices.",
    "Verify destination-country residue regulations.",
    "Prefer biological control whenever possible.",
    "Avoid spraying close to harvest.",
]


def normalize_disease_code(disease_code: str) -> str:
    return disease_code.strip().upper()


def determine_risk_level(
    disease_code: str,
    confidence: float,
    severity: str | None,
) -> RiskLevel:
    normalized_code = normalize_disease_code(disease_code)
    if normalized_code == HEALTHY_LEAF_CODE:
        return RiskLevel.LOW

    severity_value = (severity or "MEDIUM").strip().upper()
    if severity_value == RiskLevel.CRITICAL.value:
        return RiskLevel.CRITICAL
    if severity_value == RiskLevel.HIGH.value:
        return RiskLevel.HIGH
    if severity_value == RiskLevel.MEDIUM.value:
        return RiskLevel.MEDIUM

    if confidence < 0.65:
        return RiskLevel.MEDIUM
    if confidence < 0.80:
        return RiskLevel.MEDIUM
    return RiskLevel.LOW


def order_rule_lists(
    immediate_actions: list[str],
    biological_plan: list[str],
    organic_plan: list[str],
    chemical_plan: list[str],
    monitoring_plan: list[str],
    export_readiness: list[str],
    farmer_notes: list[str],
) -> tuple[list[str], list[str], list[str], list[str], list[str], list[str], list[str]]:
    return (
        immediate_actions,
        biological_plan,
        organic_plan,
        chemical_plan,
        monitoring_plan,
        export_readiness,
        farmer_notes,
    )
