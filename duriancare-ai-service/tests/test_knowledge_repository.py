import unittest
from contextlib import contextmanager

from app.repositories.knowledge_repository import KnowledgeRepository


class FakeCursor:
    def __init__(self, pool) -> None:
        self.pool = pool
        self._current_response = None

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb) -> bool:
        return False

    def execute(self, query, params) -> None:
        self.pool.executed.append((str(query), params))
        if not self.pool.responses:
            raise AssertionError("No fake response available for query")
        self._current_response = self.pool.responses.pop(0)

    def fetchone(self):
        if self._current_response is None:
            return None
        if isinstance(self._current_response, list):
            return self._current_response[0] if self._current_response else None
        return self._current_response

    def fetchall(self):
        if self._current_response is None:
            return []
        if isinstance(self._current_response, list):
            return self._current_response
        return [self._current_response]


class FakeConnection:
    def __init__(self, pool) -> None:
        self.pool = pool

    def cursor(self):
        return FakeCursor(self.pool)


class FakePool:
    def __init__(self, responses) -> None:
        self.responses = list(responses)
        self.executed: list[tuple[str, tuple]] = []

    @contextmanager
    def connection(self):
        yield FakeConnection(self)

    def close(self) -> None:
        return None


class KnowledgeRepositoryTest(unittest.TestCase):
    def test_fetch_disease_bundle_reads_and_maps_all_sections(self) -> None:
        responses = [
            {
                "code": "LEAF_BLIGHT",
                "vietnamese_name": "Bệnh cháy lá",
                "english_name": "Leaf blight",
                "scientific_name": "Rhizoctonia solani AG1-ID",
                "issue_type": "DISEASE",
                "severity": "HIGH",
                "disease_summary": "Mảng cháy nâu trên lá.",
                "favorable_conditions": "Ẩm độ cao và tán kém thoáng.",
                "confidence_level": 0.97,
                "primary_source_code": "SRC_DISEASE",
            },
            [
                {
                    "item_order": 1,
                    "item_text": "Mảng cháy lớn màu nâu nhạt.",
                    "source_code": "SRC_SYMPTOM",
                    "confidence_level": 0.96,
                }
            ],
            [
                {
                    "item_order": 1,
                    "item_text": "Rhizoctonia solani AG1-ID là tác nhân gây bệnh.",
                    "source_code": "SRC_CAUSE",
                    "confidence_level": 0.98,
                }
            ],
            [
                {
                    "item_order": 1,
                    "item_text": "Ưu tiên tác nhân đối kháng đã được sàng lọc.",
                    "mechanism": "Giảm phụ thuộc vào hóa chất.",
                    "source_code": "SRC_BIO",
                    "confidence_level": 0.78,
                }
            ],
            [
                {
                    "item_order": 1,
                    "item_text": "Tỉa cành để tăng thông thoáng.",
                    "safe_usage_note": "Vệ sinh vườn giúp lá khô nhanh hơn.",
                    "source_code": "SRC_ORG",
                    "confidence_level": 0.92,
                }
            ],
            [
                {
                    "id": "chemical-1",
                    "item_order": 1,
                    "item_text": "Chỉ dùng thuốc gốc đồng khi bệnh nặng.",
                    "safe_usage_note": "Phun đúng nhãn và theo PHI.",
                    "source_code": "SRC_CHEM",
                    "confidence_level": 0.84,
                }
            ],
            [
                {
                    "id": "rc-1",
                    "chemical_treatment_id": "chemical-1",
                    "item_order": 1,
                    "product_name": "Copper fungicide",
                    "active_ingredient_summary": "Copper-based fungicide family.",
                    "usage_note": "Chỉ dùng khi nhãn và pháp lý cho phép.",
                    "source_code": "SRC_RC",
                    "confidence_level": 0.82,
                }
            ],
            [
                {
                    "recommended_chemical_id": "rc-1",
                    "id": "ai-1",
                    "ingredient_name": "Copper oxychloride",
                    "chemical_group": "Copper compound",
                    "source_code": "SRC_AI",
                    "confidence_level": 0.82,
                    "sort_order": 1,
                }
            ],
            [
                {
                    "recommended_chemical_id": "rc-1",
                    "market_code": "VN",
                    "market_name": "Vietnam",
                    "phi_days": None,
                    "notes": "Kiểm tra nhãn trước khi dùng.",
                    "source_code": "SRC_PHI",
                    "confidence_level": 0.80,
                    "created_at": "2026-07-10T00:00:00Z",
                    "updated_at": "2026-07-10T00:00:00Z",
                }
            ],
            [
                {
                    "disease_code": "LEAF_BLIGHT",
                    "market_code": "VN",
                    "market_name": "Vietnam",
                    "item_order": 1,
                    "item_text": "Ưu tiên vệ sinh và ghi chép PHI.",
                    "warning_text": "Không phun lặp nếu chưa có nhãn hợp lệ.",
                    "source_code": "SRC_EXPORT",
                    "confidence_level": 0.90,
                }
            ],
            [
                {
                    "source_code": "SRC_DISEASE",
                    "source_name": "Disease source",
                    "source_type": "OFFICIAL",
                    "publication_title": "Main disease source",
                    "publisher": "MAE",
                    "publication_year": 2024,
                    "url": "https://example.com/disease",
                    "confidence_level": 0.99,
                    "notes": "Primary source",
                },
                {
                    "source_code": "SRC_SYMPTOM",
                    "source_name": "Symptom source",
                    "source_type": "PEER_REVIEWED",
                    "publication_title": "Leaf symptoms",
                    "publisher": "Journal",
                    "publication_year": 2023,
                    "url": "https://example.com/symptom",
                    "confidence_level": 0.96,
                    "notes": None,
                },
                {
                    "source_code": "SRC_CAUSE",
                    "source_name": "Cause source",
                    "source_type": "PEER_REVIEWED",
                    "publication_title": "Leaf causes",
                    "publisher": "Journal",
                    "publication_year": 2023,
                    "url": "https://example.com/cause",
                    "confidence_level": 0.97,
                    "notes": None,
                },
                {
                    "source_code": "SRC_BIO",
                    "source_name": "Biological source",
                    "source_type": "STANDARD",
                    "publication_title": "IPM guidance",
                    "publisher": "FAO",
                    "publication_year": 2025,
                    "url": "https://example.com/bio",
                    "confidence_level": 0.95,
                    "notes": None,
                },
                {
                    "source_code": "SRC_ORG",
                    "source_name": "Organic source",
                    "source_type": "OFFICIAL",
                    "publication_title": "Canopy management",
                    "publisher": "MAE",
                    "publication_year": 2025,
                    "url": "https://example.com/org",
                    "confidence_level": 0.95,
                    "notes": None,
                },
                {
                    "source_code": "SRC_CHEM",
                    "source_name": "Chemical source",
                    "source_type": "EXTENSION",
                    "publication_title": "Conservative fungicide advice",
                    "publisher": "Extension",
                    "publication_year": 2024,
                    "url": "https://example.com/chem",
                    "confidence_level": 0.90,
                    "notes": None,
                },
                {
                    "source_code": "SRC_RC",
                    "source_name": "Recommendation source",
                    "source_type": "DATABASE",
                    "publication_title": "Recommended products",
                    "publisher": "Database",
                    "publication_year": 2024,
                    "url": "https://example.com/rc",
                    "confidence_level": 0.88,
                    "notes": None,
                },
                {
                    "source_code": "SRC_AI",
                    "source_name": "Ingredient source",
                    "source_type": "DATABASE",
                    "publication_title": "Active ingredient registry",
                    "publisher": "Database",
                    "publication_year": 2024,
                    "url": "https://example.com/ai",
                    "confidence_level": 0.88,
                    "notes": None,
                },
                {
                    "source_code": "SRC_PHI",
                    "source_name": "PHI source",
                    "source_type": "OFFICIAL",
                    "publication_title": "Label verification",
                    "publisher": "Vietnam MAE",
                    "publication_year": 2024,
                    "url": "https://example.com/phi",
                    "confidence_level": 0.85,
                    "notes": None,
                },
                {
                    "source_code": "SRC_EXPORT",
                    "source_name": "Export source",
                    "source_type": "DATABASE",
                    "publication_title": "Residue rules",
                    "publisher": "EU",
                    "publication_year": 2026,
                    "url": "https://example.com/export",
                    "confidence_level": 0.93,
                    "notes": None,
                },
            ],
        ]
        repository = KnowledgeRepository(
            postgres_url="postgresql://example",
            schema="public",
            pool=FakePool(responses),
        )

        bundle = repository.fetch_disease_bundle("LEAF_BLIGHT")

        self.assertIsNotNone(bundle)
        assert bundle is not None
        self.assertEqual("LEAF_BLIGHT", bundle.disease["code"])
        self.assertEqual(1, len(bundle.symptoms))
        self.assertEqual(1, len(bundle.recommended_chemicals))
        self.assertEqual(10, len(bundle.reference_sources))
        self.assertEqual(
            "Disease source",
            bundle.reference_sources["SRC_DISEASE"]["source_name"],
        )

    def test_fetch_disease_bundle_returns_none_for_missing_disease(self) -> None:
        repository = KnowledgeRepository(
            postgres_url="postgresql://example",
            schema="public",
            pool=FakePool([None]),
        )

        self.assertIsNone(repository.fetch_disease_bundle("UNKNOWN"))


if __name__ == "__main__":
    unittest.main()
