import unittest

from app.mappers.recommendation_mapper import map_knowledge_bundle_to_recommendation
from app.repositories.knowledge_repository import KnowledgeBundle
from app.services.recommendation_service import RecommendationService


class FakeRepository:
    def __init__(self, bundle: KnowledgeBundle | None) -> None:
        self.bundle = bundle
        self.calls: list[str] = []

    def fetch_disease_bundle(self, disease_code: str) -> KnowledgeBundle | None:
        self.calls.append(disease_code)
        return self.bundle


def build_bundle() -> KnowledgeBundle:
    disease = {
        "code": "ALLOCARIDARA_ATTACK",
        "vietnamese_name": "Bọ chích hút lá sầu riêng",
        "english_name": "Durian psyllid attack",
        "scientific_name": "Allocaridara malayensis",
        "issue_type": "PEST",
        "severity": "HIGH",
        "disease_summary": "Sâu chích hút trên đọt non.",
        "favorable_conditions": "Đợt non ra mạnh, mật số cao.",
        "confidence_level": 0.94,
        "primary_source_code": "SRC_DISEASE",
    }
    return KnowledgeBundle(
        disease=disease,
        symptoms=[
            {
                "item_order": 1,
                "item_text": "Lá non quăn hoặc biến dạng.",
                "source_code": "SRC_SYMPTOM",
                "confidence_level": 0.93,
            }
        ],
        causes=[
            {
                "item_order": 1,
                "item_text": "Allocaridara malayensis là loài psyllid chích hút.",
                "source_code": "SRC_CAUSE",
                "confidence_level": 0.98,
            }
        ],
        biological_treatments=[
            {
                "item_order": 1,
                "item_text": "Dùng Beauveria bassiana bản địa nếu có nguồn kiểm chứng.",
                "mechanism": "Làm giảm mật số psyllid.",
                "source_code": "SRC_BIO",
                "confidence_level": 0.76,
            }
        ],
        organic_treatments=[
            {
                "item_order": 1,
                "item_text": "Theo dõi đợt non thường xuyên.",
                "safe_usage_note": "Quản lý mật số sớm giúp giảm nhu cầu phun thuốc.",
                "source_code": "SRC_ORG",
                "confidence_level": 0.90,
            }
        ],
        chemical_treatments=[
            {
                "id": "chemical-1",
                "item_order": 1,
                "item_text": "Có thể dùng cypermethrin nếu sản phẩm được phép lưu hành.",
                "safe_usage_note": "Xác minh nhãn đăng ký, liều và PHI.",
                "source_code": "SRC_CHEM",
                "confidence_level": 0.70,
            }
        ],
        recommended_chemicals=[
            {
                "id": "rc-1",
                "chemical_treatment_id": "chemical-1",
                "item_order": 1,
                "product_name": "Cypermethrin-based insecticide",
                "active_ingredient_summary": "Pyrethroid insecticide.",
                "usage_note": "Kiểm tra đăng ký và PHI trước khi dùng.",
                "source_code": "SRC_RC",
                "confidence_level": 0.70,
            }
        ],
        active_ingredients=[
            {
                "recommended_chemical_id": "rc-1",
                "id": "ai-1",
                "ingredient_name": "Cypermethrin",
                "chemical_group": "Pyrethroid",
                "source_code": "SRC_AI",
                "confidence_level": 0.90,
                "sort_order": 1,
            }
        ],
        harvest_intervals=[
            {
                "recommended_chemical_id": "rc-1",
                "market_code": "VN",
                "market_name": "Vietnam",
                "phi_days": None,
                "notes": "Với durian, phải kiểm tra nhãn hợp lệ.",
                "source_code": "SRC_PHI",
                "confidence_level": 0.80,
                "created_at": "2026-07-10T00:00:00Z",
                "updated_at": "2026-07-10T00:00:00Z",
            }
        ],
        maximum_residue_limits=[
            {
                "market_code": "EU",
                "market_name": "European Union",
                "commodity_name": "Durian",
                "mrl_value": 0.1,
                "unit": "mg/kg",
                "notes": "Xác minh theo database trước khi xuất khẩu.",
                "source_code": "SRC_MRL",
                "confidence_level": 0.91,
            }
        ],
        export_requirements=[
            {
                "disease_code": "ALLOCARIDARA_ATTACK",
                "market_code": "EU",
                "market_name": "European Union",
                "item_order": 1,
                "item_text": "Xác minh MRL theo thị trường trước khi đóng gói.",
                "warning_text": "Lot đã phun thuốc phải có traceability.",
                "source_code": "SRC_EXPORT",
                "confidence_level": 0.93,
            }
        ],
        reference_sources={
            "SRC_DISEASE": {
                "source_code": "SRC_DISEASE",
                "source_name": "Disease source",
                "source_type": "OFFICIAL",
                "title": "Disease source",
                "organization": "MAE",
                "year": 2024,
                "publication_title": "Disease source",
                "publisher": "MAE",
                "publication_year": 2024,
                "url": "https://example.com/disease",
                "confidence_level": 0.99,
                "notes": None,
            },
            "SRC_SYMPTOM": {
                "source_code": "SRC_SYMPTOM",
                "source_name": "Symptom source",
                "source_type": "PEER_REVIEWED",
                "title": "Symptom source",
                "organization": "Journal",
                "year": 2024,
                "publication_title": "Symptom source",
                "publisher": "Journal",
                "publication_year": 2024,
                "url": "https://example.com/symptom",
                "confidence_level": 0.96,
                "notes": None,
            },
            "SRC_CAUSE": {
                "source_code": "SRC_CAUSE",
                "source_name": "Cause source",
                "source_type": "PEER_REVIEWED",
                "title": "Cause source",
                "organization": "Journal",
                "year": 2024,
                "publication_title": "Cause source",
                "publisher": "Journal",
                "publication_year": 2024,
                "url": "https://example.com/cause",
                "confidence_level": 0.97,
                "notes": None,
            },
            "SRC_BIO": {
                "source_code": "SRC_BIO",
                "source_name": "Bio source",
                "source_type": "STANDARD",
                "title": "IPM guidance",
                "organization": "FAO",
                "year": 2025,
                "publication_title": "IPM guidance",
                "publisher": "FAO",
                "publication_year": 2025,
                "url": "https://example.com/bio",
                "confidence_level": 0.95,
                "notes": None,
            },
            "SRC_ORG": {
                "source_code": "SRC_ORG",
                "source_name": "Organic source",
                "source_type": "OFFICIAL",
                "title": "Canopy management",
                "organization": "MAE",
                "year": 2025,
                "publication_title": "Canopy management",
                "publisher": "MAE",
                "publication_year": 2025,
                "url": "https://example.com/org",
                "confidence_level": 0.95,
                "notes": None,
            },
            "SRC_CHEM": {
                "source_code": "SRC_CHEM",
                "source_name": "Chemical source",
                "source_type": "EXTENSION",
                "title": "Conservative fungicide advice",
                "organization": "Extension",
                "year": 2024,
                "publication_title": "Conservative fungicide advice",
                "publisher": "Extension",
                "publication_year": 2024,
                "url": "https://example.com/chem",
                "confidence_level": 0.90,
                "notes": None,
            },
            "SRC_RC": {
                "source_code": "SRC_RC",
                "source_name": "Recommendation source",
                "source_type": "DATABASE",
                "title": "Recommended products",
                "organization": "Database",
                "year": 2024,
                "publication_title": "Recommended products",
                "publisher": "Database",
                "publication_year": 2024,
                "url": "https://example.com/rc",
                "confidence_level": 0.88,
                "notes": None,
            },
            "SRC_AI": {
                "source_code": "SRC_AI",
                "source_name": "Ingredient source",
                "source_type": "DATABASE",
                "title": "Active ingredient registry",
                "organization": "Database",
                "year": 2024,
                "publication_title": "Active ingredient registry",
                "publisher": "Database",
                "publication_year": 2024,
                "url": "https://example.com/ai",
                "confidence_level": 0.88,
                "notes": None,
            },
            "SRC_PHI": {
                "source_code": "SRC_PHI",
                "source_name": "PHI source",
                "source_type": "OFFICIAL",
                "title": "Label verification",
                "organization": "Vietnam MAE",
                "year": 2024,
                "publication_title": "Label verification",
                "publisher": "Vietnam MAE",
                "publication_year": 2024,
                "url": "https://example.com/phi",
                "confidence_level": 0.85,
                "notes": None,
            },
            "SRC_EXPORT": {
                "source_code": "SRC_EXPORT",
                "source_name": "Export source",
                "source_type": "DATABASE",
                "title": "Residue rules",
                "organization": "EU",
                "year": 2026,
                "publication_title": "Residue rules",
                "publisher": "EU",
                "publication_year": 2026,
                "url": "https://example.com/export",
                "confidence_level": 0.93,
                "notes": None,
            },
            "SRC_MRL": {
                "source_code": "SRC_MRL",
                "source_name": "MRL source",
                "source_type": "DATABASE",
                "title": "MRL reference",
                "organization": "Database",
                "year": 2024,
                "publication_title": "MRL reference",
                "publisher": "Database",
                "publication_year": 2024,
                "url": "https://example.com/mrl",
                "confidence_level": 0.9,
                "notes": None,
            },
        },
    )


class RecommendationServiceTest(unittest.TestCase):
    def test_map_knowledge_bundle_to_recommendation(self) -> None:
        recommendation = map_knowledge_bundle_to_recommendation(build_bundle())

        self.assertEqual("ALLOCARIDARA_ATTACK", recommendation.disease_code)
        self.assertTrue(recommendation.disease_summary.startswith("Bọ chích hút"))
        self.assertTrue(recommendation.favorable_conditions.startswith("Ra lộc"))
        self.assertEqual(1, len(recommendation.symptoms))
        self.assertEqual(4, len(recommendation.prevention))
        self.assertEqual(
            len({item.text for item in recommendation.prevention}),
            len(recommendation.prevention),
        )
        self.assertEqual(1, len(recommendation.chemical_treatments))
        self.assertEqual(1, len(recommendation.chemical_treatments[0].recommended_products))
        self.assertEqual(1, len(recommendation.maximum_residue_limits))
        self.assertEqual(
            "Cypermethrin",
            recommendation.chemical_treatments[0].recommended_products[0]
            .active_ingredients[0]
            .ingredient_name,
        )
        self.assertEqual("EU", recommendation.export_considerations[0].market_code)
        self.assertEqual(11, len(recommendation.references))
        payload = recommendation.model_dump(by_alias=True)
        disease_reference = next(
            item
            for item in payload["references"]
            if item["sourceCode"] == "SRC_DISEASE"
        )
        self.assertEqual("Disease source", disease_reference["sourceName"])
        self.assertEqual("MAE", disease_reference["sourceType"])
        self.assertEqual("Disease source", disease_reference["title"])

    def test_service_caches_lookup_results(self) -> None:
        repository = FakeRepository(build_bundle())
        service = RecommendationService(repository)

        first = service.build_recommendation("allocaridara_attack")
        second = service.build_recommendation("ALLOCARIDARA_ATTACK")

        self.assertIsNotNone(first)
        self.assertIs(first, second)
        self.assertEqual(["ALLOCARIDARA_ATTACK"], repository.calls)

    def test_service_returns_none_for_unknown_disease(self) -> None:
        repository = FakeRepository(None)
        service = RecommendationService(repository)

        self.assertIsNone(service.build_recommendation("UNKNOWN"))
        self.assertEqual(["UNKNOWN"], repository.calls)


if __name__ == "__main__":
    unittest.main()
