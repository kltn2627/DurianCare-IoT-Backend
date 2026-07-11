from __future__ import annotations

from dataclasses import dataclass, field

from app.decision.decision_mapper import DecisionMapper
from app.schemas.decision_support import DecisionSupport
from app.schemas.recommendation import DiseaseRecommendation


@dataclass
class DecisionSupportService:
    mapper: DecisionMapper = field(default_factory=DecisionMapper)
    _cache: dict[tuple[str, float], DecisionSupport] = field(default_factory=dict)

    def build_decision_support(
        self,
        disease_code: str,
        confidence: float,
        recommendation: DiseaseRecommendation | None,
    ) -> DecisionSupport:
        cache_key = (disease_code.strip().upper(), round(float(confidence), 4))
        if cache_key in self._cache:
            return self._cache[cache_key]

        decision_support = self.mapper.map(
            disease_code=disease_code,
            confidence=confidence,
            recommendation=recommendation,
        )
        self._cache[cache_key] = decision_support
        return decision_support

    def clear_cache(self) -> None:
        self._cache.clear()

