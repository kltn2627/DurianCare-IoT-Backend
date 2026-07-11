from __future__ import annotations

from collections import defaultdict
from typing import Any

from app.repositories.knowledge_repository import KnowledgeBundle
from app.schemas.recommendation import (
    ActiveIngredientSummary,
    ChemicalTreatmentSummary,
    DiseaseRecommendation,
    ExportRequirementSummary,
    HarvestIntervalSummary,
    KnowledgeLineItem,
    ReferenceSourceSummary,
    RecommendedChemicalSummary,
)


def map_knowledge_bundle_to_recommendation(
    bundle: KnowledgeBundle,
) -> DiseaseRecommendation:
    references = bundle.reference_sources
    symptoms = [_to_line_item(row, references) for row in bundle.symptoms]
    causes = [_to_line_item(row, references) for row in bundle.causes]
    biological_treatments = [
        _to_line_item(row, references) for row in bundle.biological_treatments
    ]
    organic_treatments = [
        _to_line_item(row, references) for row in bundle.organic_treatments
    ]
    prevention = list(organic_treatments)
    export_considerations = [
        _to_export_requirement(row, references)
        for row in bundle.export_requirements
    ]

    active_ingredients_by_recommended_chemical_id: dict[str, list[dict[str, Any]]] = (
        defaultdict(list)
    )
    for row in bundle.active_ingredients:
        active_ingredients_by_recommended_chemical_id[
            str(row["recommended_chemical_id"])
        ].append(row)

    harvest_intervals_by_recommended_chemical_id: dict[str, list[dict[str, Any]]] = (
        defaultdict(list)
    )
    for row in bundle.harvest_intervals:
        harvest_intervals_by_recommended_chemical_id[
            str(row["recommended_chemical_id"])
        ].append(row)

    recommended_chemicals_by_treatment_id: dict[str, list[dict[str, Any]]] = (
        defaultdict(list)
    )
    for row in bundle.recommended_chemicals:
        recommended_chemicals_by_treatment_id[
            str(row["chemical_treatment_id"])
        ].append(row)

    chemical_treatments = []
    for chemical_treatment in bundle.chemical_treatments:
        treatment_id = str(chemical_treatment["id"])
        recommended_products = []
        for recommended_chemical in recommended_chemicals_by_treatment_id.get(
            treatment_id,
            [],
        ):
            recommended_chemical_id = str(recommended_chemical["id"])
            recommended_products.append(
                _to_recommended_chemical(
                    recommended_chemical,
                    references,
                    active_ingredients_by_recommended_chemical_id.get(
                        recommended_chemical_id,
                        [],
                    ),
                    harvest_intervals_by_recommended_chemical_id.get(
                        recommended_chemical_id,
                        [],
                    ),
                )
            )
        chemical_treatments.append(
            ChemicalTreatmentSummary(
                treatment_order=int(chemical_treatment["item_order"]),
                treatment_text=str(chemical_treatment["item_text"]),
                safe_usage_note=str(chemical_treatment["safe_usage_note"]),
                source=_reference(
                    chemical_treatment.get("source_code"),
                    references,
                ),
                confidence_level=float(chemical_treatment["confidence_level"]),
                recommended_products=recommended_products,
            )
        )

    return DiseaseRecommendation(
        disease_code=str(bundle.disease["code"]),
        vietnamese_name=str(bundle.disease["vietnamese_name"]),
        english_name=str(bundle.disease["english_name"]),
        scientific_name=_optional_string(bundle.disease.get("scientific_name")),
        issue_type=str(bundle.disease["issue_type"]),
        severity=str(bundle.disease["severity"]),
        disease_summary=str(bundle.disease["disease_summary"]),
        favorable_conditions=_optional_string(
            bundle.disease.get("favorable_conditions")
        ),
        confidence_level=float(bundle.disease["confidence_level"]),
        symptoms=symptoms,
        causes=causes,
        prevention=prevention,
        biological_treatments=biological_treatments,
        organic_treatments=organic_treatments,
        chemical_treatments=chemical_treatments,
        export_considerations=export_considerations,
        references=_sorted_references(references),
    )


def _to_line_item(
    row: dict[str, Any],
    references: dict[str, dict[str, Any]],
) -> KnowledgeLineItem:
    return KnowledgeLineItem(
        order=int(row["item_order"]),
        text=str(row["item_text"]),
        source=_reference(row.get("source_code"), references),
        confidence_level=float(row["confidence_level"]),
    )


def _to_export_requirement(
    row: dict[str, Any],
    references: dict[str, dict[str, Any]],
) -> ExportRequirementSummary:
    return ExportRequirementSummary(
        market_code=str(row["market_code"]),
        market_name=str(row["market_name"]),
        requirement_order=int(row["item_order"]),
        requirement_text=str(row["item_text"]),
        warning_text=_optional_string(row.get("warning_text")),
        source=_reference(row.get("source_code"), references),
        confidence_level=float(row["confidence_level"]),
    )


def _to_recommended_chemical(
    row: dict[str, Any],
    references: dict[str, dict[str, Any]],
    active_ingredient_rows: list[dict[str, Any]],
    harvest_interval_rows: list[dict[str, Any]],
) -> RecommendedChemicalSummary:
    active_ingredients = [
        ActiveIngredientSummary(
            ingredient_name=str(active_ingredient["ingredient_name"]),
            chemical_group=_optional_string(active_ingredient.get("chemical_group")),
            source=_reference(active_ingredient.get("source_code"), references),
            confidence_level=float(active_ingredient["confidence_level"]),
        )
        for active_ingredient in active_ingredient_rows
    ]
    harvest_intervals = [
        HarvestIntervalSummary(
            market_code=str(harvest_interval["market_code"]),
            market_name=str(harvest_interval["market_name"]),
            phi_days=(
                int(harvest_interval["phi_days"])
                if harvest_interval.get("phi_days") is not None
                else None
            ),
            notes=_optional_string(harvest_interval.get("notes")),
            source=_reference(harvest_interval.get("source_code"), references),
            confidence_level=float(harvest_interval["confidence_level"]),
        )
        for harvest_interval in harvest_interval_rows
    ]
    return RecommendedChemicalSummary(
        recommendation_order=int(row["item_order"]),
        product_name=str(row["product_name"]),
        active_ingredient_summary=str(row["active_ingredient_summary"]),
        usage_note=_optional_string(row.get("usage_note")),
        source=_reference(row.get("source_code"), references),
        confidence_level=float(row["confidence_level"]),
        active_ingredients=active_ingredients,
        harvest_intervals=harvest_intervals,
    )


def _reference(
    source_code: str | None,
    references: dict[str, dict[str, Any]],
) -> ReferenceSourceSummary | None:
    if source_code is None:
        return None
    row = references.get(source_code)
    if row is None:
        return None
    return ReferenceSourceSummary(
        source_code=str(row["source_code"]),
        source_name=str(row["source_name"]),
        source_type=str(row["source_type"]),
        publication_title=_optional_string(row.get("publication_title")),
        publisher=_optional_string(row.get("publisher")),
        publication_year=(
            int(row["publication_year"])
            if row.get("publication_year") is not None
            else None
        ),
        url=_optional_string(row.get("url")),
        confidence_level=float(row["confidence_level"]),
        notes=_optional_string(row.get("notes")),
    )


def _sorted_references(
    references: dict[str, dict[str, Any]],
) -> list[ReferenceSourceSummary]:
    sorted_references: list[ReferenceSourceSummary] = []
    for source_code in sorted(references):
        reference = _reference(source_code, references)
        if reference is not None:
            sorted_references.append(reference)
    return sorted_references


def _optional_string(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None
