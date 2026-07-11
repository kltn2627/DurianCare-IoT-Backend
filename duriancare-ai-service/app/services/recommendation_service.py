from __future__ import annotations

from dataclasses import dataclass, field

from app.mappers.recommendation_mapper import (
    map_knowledge_bundle_to_recommendation,
)
from app.repositories.knowledge_repository import KnowledgeRepository
from app.schemas.recommendation import DiseaseRecommendation
@dataclass
class RecommendationService:
    repository: KnowledgeRepository
    _cache: dict[str, DiseaseRecommendation | None] = field(default_factory=dict)

    def build_recommendation(
        self,
        disease_code: str,
    ) -> DiseaseRecommendation | None:
        cache_key = disease_code.strip().upper()
        if not cache_key:
            return None
        if cache_key in self._cache:
            return self._cache[cache_key]

        bundle = self.repository.fetch_disease_bundle(cache_key)
        if bundle is None:
            self._cache[cache_key] = None
            return None

        recommendation = map_knowledge_bundle_to_recommendation(bundle)
        self._cache[cache_key] = recommendation
        return recommendation

    def clear_cache(self) -> None:
        self._cache.clear()
