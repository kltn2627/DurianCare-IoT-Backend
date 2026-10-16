from __future__ import annotations

from app.decision.decision_rules import (
    EXPORT_TEMPLATE,
    IMMEDIATE_ACTIONS,
    MONITORING_PLAN,
    determine_risk_level,
    normalize_disease_code,
    order_rule_lists,
)
from app.schemas.decision_support import DecisionSupport
from app.schemas.recommendation import DiseaseRecommendation


class DecisionMapper:
    def map(
        self,
        disease_code: str,
        confidence: float,
        recommendation: DiseaseRecommendation | None,
    ) -> DecisionSupport:
        normalized_disease_code = normalize_disease_code(disease_code)
        severity = recommendation.severity if recommendation is not None else None
        risk_level = determine_risk_level(
            normalized_disease_code,
            confidence,
            severity,
        )

        immediate_actions = self._build_immediate_actions(
            normalized_disease_code,
            recommendation,
        )
        biological_plan = self._build_biological_plan(recommendation)
        organic_plan = self._build_organic_plan(normalized_disease_code, recommendation)
        chemical_plan = self._build_chemical_plan(
            normalized_disease_code,
            recommendation,
            risk_level.value,
        )
        monitoring_plan = self._build_monitoring_plan(
            normalized_disease_code,
            confidence,
            recommendation,
        )
        export_readiness = self._build_export_readiness(
            normalized_disease_code,
            recommendation,
        )
        farmer_notes = self._build_farmer_notes(
            normalized_disease_code,
            confidence,
            recommendation,
            risk_level.value,
        )

        (
            immediate_actions,
            biological_plan,
            organic_plan,
            chemical_plan,
            monitoring_plan,
            export_readiness,
            farmer_notes,
        ) = order_rule_lists(
            immediate_actions,
            biological_plan,
            organic_plan,
            chemical_plan,
            monitoring_plan,
            export_readiness,
            farmer_notes,
        )

        return DecisionSupport(
            risk_level=risk_level,
            immediate_actions=immediate_actions,
            biological_plan=biological_plan,
            organic_plan=organic_plan,
            chemical_plan=chemical_plan,
            monitoring_plan=monitoring_plan,
            export_readiness=export_readiness,
            farmer_notes=farmer_notes,
        )

    def _build_immediate_actions(
        self,
        disease_code: str,
        recommendation: DiseaseRecommendation | None,
    ) -> list[str]:
        if disease_code == "HEALTHY_LEAF":
            return list(IMMEDIATE_ACTIONS["HEALTHY_LEAF"])
        actions = list(IMMEDIATE_ACTIONS.get(disease_code, []))
        if recommendation is not None and recommendation.favorable_conditions:
            actions.append(
                f"Giảm các điều kiện thuận lợi cho bệnh: {recommendation.favorable_conditions}"
            )
        return actions

    def _build_biological_plan(
        self,
        recommendation: DiseaseRecommendation | None,
    ) -> list[str]:
        if recommendation is None:
            return []
        return [
            self._prefix_action("Biological", item.text)
            for item in recommendation.biological_treatments
        ]

    def _build_organic_plan(
        self,
        disease_code: str,
        recommendation: DiseaseRecommendation | None,
    ) -> list[str]:
        if recommendation is None:
            return []
        plan = [
            self._prefix_action("Organic", item.text)
            for item in recommendation.organic_treatments
        ]
        if disease_code == "HEALTHY_LEAF" and not plan:
            plan.append(
                "Tiếp tục canh tác hữu cơ và giữ tán thông thoáng để duy trì lá khỏe."
            )
        return plan

    def _build_chemical_plan(
        self,
        disease_code: str,
        recommendation: DiseaseRecommendation | None,
        risk_level: str,
    ) -> list[str]:
        if disease_code == "HEALTHY_LEAF" or recommendation is None:
            return []
        if not recommendation.chemical_treatments:
            return []

        plan: list[str] = []
        if risk_level in {"HIGH", "CRITICAL"}:
            plan.append(
                "Chỉ dùng hóa chất khi biện pháp phòng ngừa và sinh học không đủ kiểm soát."
            )
        else:
            plan.append(
                "Hóa chất không phải lựa chọn đầu tiên; chỉ cân nhắc khi áp lực bệnh tăng."
            )

        for chemical_treatment in recommendation.chemical_treatments:
            line = chemical_treatment.treatment_text
            if chemical_treatment.safe_usage_note:
                line = f"{line} ({chemical_treatment.safe_usage_note})"
            plan.append(line)
            for recommended_product in chemical_treatment.recommended_products:
                product_line = f"- {recommended_product.product_name}: {recommended_product.active_ingredient_summary}"
                if recommended_product.usage_note:
                    product_line += f" {recommended_product.usage_note}"
                plan.append(product_line)
                for harvest_interval in recommended_product.harvest_intervals:
                    if harvest_interval.phi_days is not None:
                        plan.append(
                            f"PHI cho {harvest_interval.market_name}: {harvest_interval.phi_days} ngày."
                        )
                    elif harvest_interval.notes:
                        plan.append(
                            f"PHI cho {harvest_interval.market_name}: {harvest_interval.notes}"
                        )
        return plan

    def _build_monitoring_plan(
        self,
        disease_code: str,
        confidence: float,
        recommendation: DiseaseRecommendation | None,
    ) -> list[str]:
        if disease_code == "HEALTHY_LEAF":
            return list(MONITORING_PLAN["HEALTHY_LEAF"])

        plan = list(MONITORING_PLAN["DEFAULT"])
        if recommendation is not None:
            plan.insert(
                0,
                f"Theo dõi theo nhãn {recommendation.english_name} và kiểm tra lại sau mỗi đợt mưa hoặc lộc non.",
            )
        if confidence < 0.7:
            plan.append(
                "Độ tin cậy trung bình; nên chụp lại ảnh từ nhiều góc trước khi xử lý mạnh."
            )
        return plan

    def _build_export_readiness(
        self,
        disease_code: str,
        recommendation: DiseaseRecommendation | None,
    ) -> list[str]:
        if disease_code == "HEALTHY_LEAF":
            return list(EXPORT_TEMPLATE[:4]) + [
                "Duy trì quy trình VietGAP và lưu nhật ký chăm sóc để hỗ trợ truy xuất nguồn gốc.",
            ]

        export_readiness = list(EXPORT_TEMPLATE)
        if recommendation is not None:
            export_readiness.extend(
                [
                    f"{item.market_name}: {item.requirement_text}"
                    for item in recommendation.export_considerations
                ]
            )
        export_readiness.append(
            "Không phun sát ngày thu hoạch và chỉ dùng sản phẩm có nhãn hợp lệ."
        )
        return export_readiness

    def _build_farmer_notes(
        self,
        disease_code: str,
        confidence: float,
        recommendation: DiseaseRecommendation | None,
        risk_level: str,
    ) -> list[str]:
        notes: list[str] = []
        if recommendation is None:
            notes.append("Chưa có khuyến nghị chi tiết từ kho tri thức cho nhãn này.")
        else:
            notes.append(
                f"Kết quả khớp với {recommendation.vietnamese_name} ({recommendation.english_name})."
            )

        notes.append(f"Mức rủi ro hiện tại: {risk_level}.")
        if disease_code == "HEALTHY_LEAF":
            notes.append("Lá đang ở trạng thái tốt; ưu tiên duy trì phòng ngừa.")
        else:
            notes.append(
                "Không chọn hóa chất làm phương án đầu tiên; luôn ưu tiên phòng ngừa, cách ly và sinh học."
            )
        if confidence < 0.7:
            notes.append(
                "Độ tin cậy của dự đoán chưa cao; nên kiểm tra lại mẫu ảnh trước khi quyết định xử lý."
            )
        return notes

    @staticmethod
    def _prefix_action(prefix: str, text: str) -> str:
        return f"{prefix}: {text}"

