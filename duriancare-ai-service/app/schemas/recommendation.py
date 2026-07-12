from __future__ import annotations

from pydantic import Field

from app.schemas.decision_support import CamelModel


class ReferenceSourceSummary(CamelModel):
    source_code: str
    source_name: str
    source_type: str
    title: str
    organization: str
    year: int
    publication_title: str | None = None
    publisher: str | None = None
    publication_year: int | None = None
    url: str | None = None
    confidence_level: float = Field(ge=0.0, le=1.0)
    notes: str | None = None


class KnowledgeLineItem(CamelModel):
    order: int
    text: str
    source: ReferenceSourceSummary | None = None
    confidence_level: float = Field(ge=0.0, le=1.0)


class ActiveIngredientSummary(CamelModel):
    ingredient_name: str
    chemical_group: str | None = None
    source: ReferenceSourceSummary | None = None
    confidence_level: float = Field(ge=0.0, le=1.0)


class HarvestIntervalSummary(CamelModel):
    market_code: str
    market_name: str
    phi_days: int | None = None
    notes: str | None = None
    source: ReferenceSourceSummary | None = None
    confidence_level: float = Field(ge=0.0, le=1.0)


class MaximumResidueLimitSummary(CamelModel):
    market_code: str
    market_name: str
    commodity_name: str
    mrl_value: float | None = None
    unit: str
    notes: str | None = None
    source: ReferenceSourceSummary | None = None
    confidence_level: float = Field(ge=0.0, le=1.0)


class RecommendedChemicalSummary(CamelModel):
    recommendation_order: int
    product_name: str
    active_ingredient_summary: str
    usage_note: str | None = None
    source: ReferenceSourceSummary | None = None
    confidence_level: float = Field(ge=0.0, le=1.0)
    active_ingredients: list[ActiveIngredientSummary] = Field(default_factory=list)
    harvest_intervals: list[HarvestIntervalSummary] = Field(default_factory=list)


class ChemicalTreatmentSummary(CamelModel):
    treatment_order: int
    treatment_text: str
    safe_usage_note: str
    source: ReferenceSourceSummary | None = None
    confidence_level: float = Field(ge=0.0, le=1.0)
    recommended_products: list[RecommendedChemicalSummary] = Field(default_factory=list)


class ExportRequirementSummary(CamelModel):
    market_code: str
    market_name: str
    requirement_order: int
    requirement_text: str
    warning_text: str | None = None
    source: ReferenceSourceSummary | None = None
    confidence_level: float = Field(ge=0.0, le=1.0)


class DiseaseRecommendation(CamelModel):
    disease_code: str
    vietnamese_name: str
    english_name: str
    scientific_name: str | None = None
    issue_type: str
    severity: str
    disease_summary: str
    favorable_conditions: str | None = None
    confidence_level: float = Field(ge=0.0, le=1.0)
    symptoms: list[KnowledgeLineItem] = Field(default_factory=list)
    causes: list[KnowledgeLineItem] = Field(default_factory=list)
    prevention: list[KnowledgeLineItem] = Field(default_factory=list)
    biological_treatments: list[KnowledgeLineItem] = Field(default_factory=list)
    organic_treatments: list[KnowledgeLineItem] = Field(default_factory=list)
    chemical_treatments: list[ChemicalTreatmentSummary] = Field(default_factory=list)
    maximum_residue_limits: list[MaximumResidueLimitSummary] = Field(
        default_factory=list
    )
    export_considerations: list[ExportRequirementSummary] = Field(default_factory=list)
    references: list[ReferenceSourceSummary] = Field(default_factory=list)
