import unittest

from app.decision.decision_service import DecisionSupportService
from app.schemas.decision_support import RiskLevel
from app.schemas.prediction import (
    PredictionData,
    PredictionResponse,
    PredictionSource,
)
from app.schemas.recommendation import (
    ChemicalTreatmentSummary,
    DiseaseRecommendation,
    ExportRequirementSummary,
    KnowledgeLineItem,
    ReferenceSourceSummary,
    RecommendedChemicalSummary,
    HarvestIntervalSummary,
    ActiveIngredientSummary,
)


def build_reference() -> ReferenceSourceSummary:
    return ReferenceSourceSummary(
        source_code="SRC",
        source_name="Source",
        source_type="OFFICIAL",
        title="Source title",
        organization="MAE",
        year=2024,
        publication_title="Source title",
        publisher="MAE",
        publication_year=2024,
        url="https://example.com",
        confidence_level=0.98,
        notes=None,
    )


def build_recommendation(
    disease_code: str,
    severity: str,
    issue_type: str = "DISEASE",
) -> DiseaseRecommendation:
    reference = build_reference()
    return DiseaseRecommendation(
        disease_code=disease_code,
        vietnamese_name="Tên bệnh",
        english_name="Disease name",
        scientific_name="Scientific name",
        issue_type=issue_type,
        severity=severity,
        disease_summary="Tóm tắt bệnh",
        favorable_conditions="Ẩm độ cao",
        confidence_level=0.94,
        symptoms=[
            KnowledgeLineItem(
                order=1,
                text="Triệu chứng 1",
                source=reference,
                confidence_level=0.9,
            )
        ],
        causes=[
            KnowledgeLineItem(
                order=1,
                text="Nguyên nhân 1",
                source=reference,
                confidence_level=0.9,
            )
        ],
        prevention=[
            KnowledgeLineItem(
                order=1,
                text="Phòng ngừa 1",
                source=reference,
                confidence_level=0.9,
            )
        ],
        biological_treatments=[
            KnowledgeLineItem(
                order=1,
                text="Biological action",
                source=reference,
                confidence_level=0.85,
            )
        ],
        organic_treatments=[
            KnowledgeLineItem(
                order=1,
                text="Organic action",
                source=reference,
                confidence_level=0.88,
            )
        ],
        chemical_treatments=[
            ChemicalTreatmentSummary(
                treatment_order=1,
                treatment_text="Chemical action",
                safe_usage_note="Use only when necessary.",
                source=reference,
                confidence_level=0.76,
                recommended_products=[
                    RecommendedChemicalSummary(
                        recommendation_order=1,
                        product_name="Product A",
                        active_ingredient_summary="Ingredient summary",
                        usage_note="Check label and PHI.",
                        source=reference,
                        confidence_level=0.75,
                        active_ingredients=[
                            ActiveIngredientSummary(
                                ingredient_name="Copper oxychloride",
                                chemical_group="Copper compound",
                                source=reference,
                                confidence_level=0.75,
                            )
                        ],
                        harvest_intervals=[
                            HarvestIntervalSummary(
                                market_code="VN",
                                market_name="Vietnam",
                                phi_days=None,
                                notes="Check approved label.",
                                source=reference,
                                confidence_level=0.75,
                            )
                        ],
                    )
                ],
            )
        ],
        export_considerations=[
            ExportRequirementSummary(
                market_code="EU",
                market_name="European Union",
                requirement_order=1,
                requirement_text="Verify residue limits before shipment.",
                warning_text="Maintain lot records.",
                source=reference,
                confidence_level=0.92,
            )
        ],
        references=[reference],
    )


class DecisionSupportServiceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.service = DecisionSupportService()

    def test_healthy_leaf_returns_preventive_guidance_only(self) -> None:
        decision_support = self.service.build_decision_support(
            "HEALTHY_LEAF",
            0.99,
            None,
        )

        self.assertEqual(RiskLevel.LOW, decision_support.risk_level)
        self.assertTrue(decision_support.immediate_actions)
        self.assertEqual([], decision_support.biological_plan)
        self.assertEqual([], decision_support.chemical_plan)
        self.assertIn("Duy trì", decision_support.immediate_actions[0])

    def test_high_risk_disease_generates_full_action_plan(self) -> None:
        recommendation = build_recommendation("LEAF_BLIGHT", "HIGH")

        decision_support = self.service.build_decision_support(
            "LEAF_BLIGHT",
            0.91,
            recommendation,
        )

        self.assertEqual(RiskLevel.HIGH, decision_support.risk_level)
        self.assertTrue(decision_support.immediate_actions[0].startswith("Cách ly"))
        self.assertTrue(decision_support.biological_plan)
        self.assertTrue(decision_support.organic_plan)
        self.assertTrue(decision_support.chemical_plan[0].startswith("Chỉ dùng hóa chất"))

    def test_missing_recommendation_still_generates_fallback_guidance(self) -> None:
        decision_support = self.service.build_decision_support(
            "PHOMOPSIS_LEAF_SPOT",
            0.78,
            None,
        )

        self.assertEqual(RiskLevel.MEDIUM, decision_support.risk_level)
        self.assertTrue(decision_support.immediate_actions)
        self.assertEqual([], decision_support.biological_plan)
        self.assertEqual([], decision_support.chemical_plan)
        self.assertIn(
            "Chưa có khuyến nghị chi tiết",
            decision_support.farmer_notes[0],
        )

    def test_export_advice_includes_traceability_and_phi_notes(self) -> None:
        recommendation = build_recommendation("ALGAL_LEAF_SPOT", "MEDIUM")

        decision_support = self.service.build_decision_support(
            "ALGAL_LEAF_SPOT",
            0.88,
            recommendation,
        )

        self.assertIn("Observe PHI before harvest.", decision_support.export_readiness)
        self.assertTrue(
            any("European Union" in item for item in decision_support.export_readiness)
        )
        self.assertTrue(
            any("VietGAP" in item for item in decision_support.export_readiness)
        )

    def test_rule_ordering_keeps_chemicals_after_prevention(self) -> None:
        recommendation = build_recommendation("ALLOCARIDARA_ATTACK", "HIGH", issue_type="PEST")

        decision_support = self.service.build_decision_support(
            "ALLOCARIDARA_ATTACK",
            0.62,
            recommendation,
        )

        self.assertTrue(decision_support.immediate_actions[0].startswith("Theo dõi"))
        self.assertTrue(
            decision_support.chemical_plan[0].startswith("Chỉ dùng hóa chất")
        )
        self.assertIn("Không chọn hóa chất", decision_support.farmer_notes[2])

    def test_response_mapping_serializes_decision_support_with_camel_case_aliases(self) -> None:
        recommendation = build_recommendation("LEAF_BLIGHT", "HIGH")
        decision_support = self.service.build_decision_support(
            "LEAF_BLIGHT",
            0.9,
            recommendation,
        )
        prediction_data = PredictionData(
            predicted_disease="LEAF_BLIGHT",
            confidence="90.00%",
            source=PredictionSource.MOBILE,
            device_id=None,
            used_detection_crop=False,
            bounding_box=None,
            image=None,
            recommendation=recommendation,
            decision_support=decision_support,
        )
        response = PredictionResponse(status="success", data=prediction_data)
        dumped = response.model_dump(by_alias=True)

        self.assertIn("decisionSupport", dumped["data"])
        self.assertIn("riskLevel", dumped["data"]["decisionSupport"])
        self.assertEqual(
            "Tóm tắt bệnh",
            dumped["data"]["recommendation"]["diseaseSummary"],
        )
        self.assertEqual(
            "Source",
            dumped["data"]["recommendation"]["references"][0]["sourceName"],
        )


if __name__ == "__main__":
    unittest.main()

