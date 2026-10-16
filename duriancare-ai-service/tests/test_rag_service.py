from __future__ import annotations

import sys
import unittest
from types import SimpleNamespace
from unittest.mock import Mock

from pathlib import Path
from tempfile import NamedTemporaryFile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.core.config import settings
from app.services.rag_service import (
    INSUFFICIENT_KNOWLEDGE,
    IndexedChunk,
    RagService,
    RetrievalHit,
)


def build_chunk() -> IndexedChunk:
    return IndexedChunk(
        chunk_id="doc-1:0",
        document_id="doc-1",
        source="knowledge_base/diseases/algal-leaf-spot.md",
        source_type="md",
        checksum="abc123",
        language="vi",
        topic="disease",
        disease="ALGAL_LEAF_SPOT",
        section="overview",
        text="Bệnh đốm mắt cua thường xuất hiện khi lá ẩm kéo dài.",
        title="Bệnh đốm mắt cua",
        index=0,
        total=1,
    )


class RagServiceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.service = RagService(settings)
        self.service._initialized = True
        self.service._cache_client = None
        self.service._memory_cache = {}
        self.service._conversation_cache = {}
        self.service._documents = []
        self.service._chunks = []
        self.service._bm25 = None
        self.service._disease_catalog = [
            {
                "code": "ALGAL_LEAF_SPOT",
                "vietnamese_name": "Bệnh đốm mắt cua",
                "english_name": "Algal leaf spot",
                "scientific_name": "Cephaleuros virescens",
                "disease_summary": "Moisture-driven foliar disease.",
            }
        ]
        self.service._embedding_model_name = "test-embedding"

    def test_detect_language_supports_vietnamese_english_and_mixed(self) -> None:
        self.assertEqual(
            self.service._detect_language("Bệnh thán thư trên lá sầu riêng"),
            "vi",
        )
        self.assertEqual(
            self.service._detect_language("What causes anthracnose on durian leaves?"),
            "en",
        )
        self.assertEqual(
            self.service._detect_language("Bệnh anthracnose trên durian leaf"),
            "mixed",
        )

    def test_cache_key_changes_with_conversation_context(self) -> None:
        base = self.service._cache_key("question", None, None)
        with_disease = self.service._cache_key("question", "ALGAL_LEAF_SPOT", None)
        with_conversation = self.service._cache_key("question", None, "user-1")
        self.assertNotEqual(base, with_disease)
        self.assertNotEqual(base, with_conversation)

    def test_follow_up_detection(self) -> None:
        self.assertTrue(
            self.service._is_follow_up_question("co nen phun thuoc hom nay", "vi")
        )
        self.assertFalse(
            self.service._is_follow_up_question("what causes anthracnose", "en")
        )

    def test_conversation_memory_round_trip(self) -> None:
        self.service._cache_client = None
        self.service._save_conversation_memory(
            "user-1",
            "Tree has anthracnose",
            "Use grounded treatment",
            ["Knowledge Base"],
            ["ALGAL_LEAF_SPOT"],
            "en",
        )
        turns = self.service._load_conversation_memory("user-1")
        self.assertEqual(len(turns), 1)
        self.assertEqual(turns[0]["disease_codes"], ["ALGAL_LEAF_SPOT"])
        self.assertIn("Previous question", self.service._format_memory_context(turns, "en"))

    def test_conversation_memory_persists_entity_mentions(self) -> None:
        self.service._cache_client = None
        hit = RetrievalHit(
            chunk=IndexedChunk(
                chunk_id="doc-2:0",
                document_id="doc-2",
                source="knowledge_base/diseases/anthracnose.md",
                source_type="md",
                checksum="def456",
                language="en",
                topic="leaf disease",
                disease="ANTHRACNOSE",
                section="overview",
                text="Anthracnose is a common fungal leaf disease in durian orchards.",
                title="Anthracnose",
                index=0,
                total=1,
            ),
            score=0.9,
            origin="hybrid",
        )
        self.service._save_conversation_memory(
            "user-1",
            "Tree has anthracnose",
            "Use grounded treatment",
            ["Knowledge Base"],
            [],
            "en",
            latest_diagnosis="Anthracnose",
            latest_recommendation="Keep canopy dry",
            retrieved_hits=[hit],
            recommendation={
                "disease_code": "ALGAL_LEAF_SPOT",
                "english_name": "Algal leaf spot",
                "vietnamese_name": "Bệnh đốm mắt cua",
            },
        )
        turns = self.service._load_conversation_memory("user-1")
        self.assertEqual(len(turns), 1)
        self.assertIn("ANTHRACNOSE", turns[0]["mentioned_diseases"])
        self.assertEqual(turns[0]["latest_diagnosis"], "Anthracnose")
        self.assertEqual(turns[0]["latest_recommendation"], "Keep canopy dry")
        extracted = self.service._extract_memory_disease_codes(turns)
        self.assertIn("ANTHRACNOSE", extracted)

    def test_ask_returns_insufficient_knowledge_when_no_context(self) -> None:
        self.service._is_domain_question = Mock(return_value=True)
        self.service._load_conversation_memory = Mock(return_value=[])
        self.service._resolve_disease_codes = Mock(return_value=[])
        self.service._build_structured_knowledge = Mock(return_value=(None, []))
        self.service._retrieve_chunks = Mock(return_value=[])
        self.service._format_retrieved_context = Mock(return_value=("", []))
        self.service._format_memory_context = Mock(return_value="")
        self.service._format_structured_context = Mock(return_value="")
        self.service._can_use_gemini = Mock(return_value=False)
        self.service._compose_fallback_answer = Mock(return_value="fallback")
        self.service._append_sources = Mock(side_effect=lambda answer, sources, language: answer)
        self.service._cache_set = Mock()

        answer, sources = self.service.ask("Bệnh gì đây?", None, "user-1")

        self.assertEqual(answer, INSUFFICIENT_KNOWLEDGE)
        self.assertEqual(sources, ["Knowledge Base"])
        self.service._compose_fallback_answer.assert_not_called()

    def test_ask_uses_memory_context_and_gemini_path(self) -> None:
        self.service._is_domain_question = Mock(return_value=False)
        self.service._load_conversation_memory = Mock(
            return_value=[
                {
                    "question": "My tree has anthracnose",
                    "answer": "Keep canopy dry.",
                    "sources": ["Anthracnose.md"],
                    "disease_codes": ["ALGAL_LEAF_SPOT"],
                    "language": "en",
                }
            ]
        )
        self.service._resolve_disease_codes = Mock(return_value=["ALGAL_LEAF_SPOT"])
        self.service._build_structured_knowledge = Mock(
            return_value=(
                {
                    "disease_code": "ALGAL_LEAF_SPOT",
                    "vietnamese_name": "Bệnh đốm mắt cua",
                    "english_name": "Algal leaf spot",
                    "disease_summary": "Moisture-driven foliar disease.",
                    "prevention": [{"text": "Improve canopy ventilation"}],
                },
                ["FAO_IPM"],
            )
        )
        self.service._retrieve_chunks = Mock(
            return_value=[RetrievalHit(chunk=build_chunk(), score=0.9, origin="hybrid")]
        )
        self.service._format_retrieved_context = Mock(return_value=("retrieved context", ["Knowledge Base"]))
        self.service._format_memory_context = Mock(return_value="Conversation memory: previous turn")
        self.service._format_structured_context = Mock(return_value="structured context")
        self.service._can_use_gemini = Mock(return_value=True)

        captured = {}

        def fake_generate_grounded_answer(*, memory_context: str, **kwargs):
            captured["memory_context"] = memory_context
            return "grounded answer"

        self.service._generate_grounded_answer = fake_generate_grounded_answer
        self.service._append_sources = Mock(side_effect=lambda answer, sources, language: answer)
        self.service._cache_set = Mock()

        answer, sources = self.service.ask("Should I spray today?", None, "user-1")

        self.assertEqual(answer, "grounded answer")
        self.assertIn("Conversation memory", captured["memory_context"])
        self.assertTrue(sources)

    def test_ask_falls_back_without_gemini(self) -> None:
        self.service._is_domain_question = Mock(return_value=True)
        self.service._load_conversation_memory = Mock(return_value=[])
        self.service._resolve_disease_codes = Mock(return_value=["ALGAL_LEAF_SPOT"])
        self.service._build_structured_knowledge = Mock(return_value=(None, []))
        self.service._retrieve_chunks = Mock(
            return_value=[RetrievalHit(chunk=build_chunk(), score=0.8, origin="hybrid")]
        )
        self.service._format_retrieved_context = Mock(return_value=("retrieved context", ["Knowledge Base"]))
        self.service._format_memory_context = Mock(return_value="")
        self.service._format_structured_context = Mock(return_value="")
        self.service._can_use_gemini = Mock(return_value=False)
        self.service._compose_fallback_answer = Mock(return_value="deterministic fallback")
        self.service._append_sources = Mock(side_effect=lambda answer, sources, language: answer)
        self.service._cache_set = Mock()

        answer, sources = self.service.ask("Bệnh đốm mắt cua là gì?", None, "user-1")

        self.assertEqual(answer, "deterministic fallback")
        self.assertIn("Knowledge Base", sources)
        self.service._compose_fallback_answer.assert_called_once()

    def test_incremental_indexing_hash_changes_when_file_changes(self) -> None:
        with NamedTemporaryFile("w+", suffix=".md", delete=False, encoding="utf-8") as handle:
            temp_path = Path(handle.name)
            handle.write("First version")
            handle.flush()
            first_hash = self.service._hash_file(temp_path)
            handle.seek(0)
            handle.truncate(0)
            handle.write("Second version")
            handle.flush()

        try:
            second_hash = self.service._hash_file(temp_path)
        finally:
            temp_path.unlink(missing_ok=True)

        self.assertNotEqual(first_hash, second_hash)


if __name__ == "__main__":
    unittest.main()
