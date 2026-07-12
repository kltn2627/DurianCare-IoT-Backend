from __future__ import annotations

from collections import defaultdict
from typing import Any
import re
import unicodedata

from app.repositories.knowledge_repository import KnowledgeBundle
from app.schemas.recommendation import (
    ActiveIngredientSummary,
    ChemicalTreatmentSummary,
    DiseaseRecommendation,
    ExportRequirementSummary,
    HarvestIntervalSummary,
    KnowledgeLineItem,
    MaximumResidueLimitSummary,
    ReferenceSourceSummary,
    RecommendedChemicalSummary,
)

VIETNAMESE_DISEASE_SUMMARIES: dict[str, str] = {
    "ALGAL_LEAF_SPOT": (
        "Bệnh đốm mắt cua là bệnh hại lá do tảo ký sinh Cephaleuros virescens gây ra. "
        "Bệnh thường phát triển mạnh trong điều kiện mưa nhiều, ẩm độ cao, tán cây rậm "
        "và lá ướt kéo dài; nếu lan rộng sẽ làm giảm quang hợp và suy yếu cây."
    ),
    "LEAF_BLIGHT": (
        "Bệnh cháy lá là bệnh hại lá do nấm Rhizoctonia solani AG1-ID gây ra. "
        "Bệnh thường bùng phát khi mưa ẩm kéo dài, tán cây rậm và lá ướt lâu; "
        "nếu không xử lý sớm, lá sẽ cháy khô, héo và rụng hàng loạt."
    ),
    "PHOMOPSIS_LEAF_SPOT": (
        "Bệnh đốm lá Phomopsis là bệnh hại lá do nấm Phomopsis durionis gây ra. "
        "Bệnh thường khởi phát âm thầm với các đốm nhỏ màu nâu đen, sau đó lan rộng "
        "nhanh trong điều kiện nóng ẩm và mưa tạt; nếu nặng có thể làm giảm chất lượng tán lá."
    ),
    "ALLOCARIDARA_ATTACK": (
        "Bọ chích hút lá sầu riêng là đối tượng chích hút nhựa non do Allocaridara "
        "malayensis gây ra. Côn trùng này thường làm lá non xoăn, vàng lá, chậm sinh trưởng "
        "và khiến đợt lộc non suy yếu rõ rệt."
    ),
    "HEALTHY_LEAF": (
        "Lá khỏe là trạng thái lá có màu xanh đồng đều, không đốm bệnh, không xoăn, "
        "không cháy mép và không có dấu hiệu chích hút; đây là mục tiêu quản lý của vườn sầu riêng."
    ),
}

VIETNAMESE_FAVORABLE_CONDITIONS: dict[str, str] = {
    "ALGAL_LEAF_SPOT": "Thời tiết nóng ẩm, mùa mưa, tán cây rậm và độ ẩm lá cao kéo dài.",
    "LEAF_BLIGHT": "Mùa mưa, ẩm độ cao, vườn thoát nước kém, tán rậm và lá ướt lâu.",
    "PHOMOPSIS_LEAF_SPOT": (
        "Thời tiết nóng ẩm, mưa nhiều, tán dày, nhiễm tiềm ẩn trong mô cây và phát tán theo mưa tạt."
    ),
    "ALLOCARIDARA_ATTACK": "Ra lộc liên tục, ít thiên địch, tán non dày và giám sát vườn chưa chặt.",
    "HEALTHY_LEAF": "Dinh dưỡng cân bằng, tán thông thoáng, vệ sinh vườn tốt và giám sát IPM đều đặn.",
}

PREVENTION_TEMPLATES: dict[str, list[tuple[int, str, str]]] = {
    "ALGAL_LEAF_SPOT": [
        (1, "Tỉa tán để tăng thông thoáng, giảm thời gian lá ướt sau mưa.", "FAO_IPM"),
        (2, "Hạn chế tưới phun lên tán; ưu tiên tưới gốc để giảm ẩm độ bề mặt lá.", "FAO_IPM"),
        (3, "Thu gom và loại bỏ lá, cành nhiễm bệnh để giảm nguồn lây trong vườn.", "HGIC_ALGAL_LEAF_SPOT"),
        (4, "Theo dõi sát sau các đợt mưa kéo dài để xử lý sớm khi vết bệnh mới xuất hiện.", "IR4_ALGAL_LEAF_SPOT"),
    ],
    "LEAF_BLIGHT": [
        (1, "Tỉa cành để mở tán, tăng nắng và làm khô lá nhanh sau mưa.", "FAO_IPM"),
        (2, "Loại bỏ lá bệnh và vật liệu thực vật nhiễm bệnh khỏi vườn ngay khi phát hiện.", "PUBMED_RHIZOCTONIA_DURIAN"),
        (3, "Giảm tưới phun lên tán và tránh giữ ẩm bề mặt lá vào buổi tối.", "SPCHCMC_LEAF_BLIGHT"),
        (4, "Theo dõi chặt sau mưa lớn để khoanh vùng sớm trước khi bệnh lan rộng.", "ISHS_LEAF_ASSAY"),
    ],
    "PHOMOPSIS_LEAF_SPOT": [
        (1, "Thu gom lá bệnh và cành nhiễm bệnh để hạn chế nguồn bệnh lưu tồn.", "ACTA_PHOMOPSIS_DURIONIS"),
        (2, "Mở tán để tăng ánh sáng và thông khí, giúp lá khô nhanh hơn sau mưa.", "UC_IPM_PHOMOPSIS"),
        (3, "Hạn chế làm xây xát lá non và tránh phun nước trực tiếp lên tán.", "FAO_IPM"),
        (4, "Kiểm tra lại lứa lá non sau mỗi đợt mưa lớn hoặc sương kéo dài.", "VIETNAM_PPD_LAW"),
    ],
    "ALLOCARIDARA_ATTACK": [
        (1, "Theo dõi đợt lộc non thường xuyên và cắt bỏ đợt bị hại nặng để giảm mật số.", "CABI_DURIAN_PSYLLID"),
        (2, "Bảo tồn thiên địch và hạn chế phun phổ rộng không cần thiết.", "FAO_IPM"),
        (3, "Đặt bẫy dính hoặc bẫy đèn để hỗ trợ giám sát mật số trưởng thành.", "CORNELL_BEAUVERIA"),
        (4, "Ghi chép đợt lộc và mật số để quyết định thời điểm xử lý tiếp theo.", "FAO_ENTOMOPATHOGENIC_FUNGI"),
    ],
    "HEALTHY_LEAF": [
        (1, "Duy trì dinh dưỡng cân đối và tưới tiêu hợp lý để giữ cây ổn định.", "VIETGAP_STANDARD"),
        (2, "Giữ tán thông thoáng và vệ sinh vườn định kỳ để giảm áp lực bệnh.", "FAO_IPM"),
        (3, "Tiếp tục giám sát IPM đều đặn trên lá non và lá già.", "GLOBALGAP_IFA"),
        (4, "Lưu nhật ký chăm sóc để hỗ trợ truy xuất nguồn gốc và đánh giá sức khỏe vườn.", "FAO_SUPERFRUIT_EXPORT"),
    ],
}


def map_knowledge_bundle_to_recommendation(
    bundle: KnowledgeBundle,
) -> DiseaseRecommendation:
    references = bundle.reference_sources
    symptoms = [_to_line_item(row, references) for row in bundle.symptoms]
    causes = [_to_line_item(row, references) for row in bundle.causes]
    seen_recommendation_texts: set[str] = set()
    biological_treatments = [
        item
        for item in (
            _to_line_item(row, references) for row in bundle.biological_treatments
        )
        if _register_recommendation_text(item.text, seen_recommendation_texts)
    ]
    organic_treatments = [
        item
        for item in (
            _to_line_item(row, references) for row in bundle.organic_treatments
        )
        if _register_recommendation_text(item.text, seen_recommendation_texts)
    ]
    prevention = _build_prevention_items(
        str(bundle.disease["code"]),
        references,
        seen_recommendation_texts,
    )
    maximum_residue_limits = [
        _to_mrl_summary(row, references) for row in bundle.maximum_residue_limits
    ]
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
        disease_summary=_localized_disease_summary(
            str(bundle.disease["code"]),
            _optional_string(bundle.disease.get("disease_summary")),
        ),
        favorable_conditions=_localized_favorable_conditions(
            str(bundle.disease["code"]),
            _optional_string(bundle.disease.get("favorable_conditions")),
        ),
        confidence_level=float(bundle.disease["confidence_level"]),
        symptoms=symptoms,
        causes=causes,
        prevention=prevention,
        biological_treatments=biological_treatments,
        organic_treatments=organic_treatments,
        chemical_treatments=chemical_treatments,
        maximum_residue_limits=maximum_residue_limits,
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


def _to_mrl_summary(
    row: dict[str, Any],
    references: dict[str, dict[str, Any]],
) -> MaximumResidueLimitSummary:
    return MaximumResidueLimitSummary(
        market_code=str(row["market_code"]),
        market_name=str(row["market_name"]),
        commodity_name=str(row["commodity_name"]),
        mrl_value=(
            float(row["mrl_value"])
            if row.get("mrl_value") is not None
            else None
        ),
        unit=str(row["unit"]),
        notes=_optional_string(row.get("notes")),
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
    title = _optional_string(row.get("publication_title")) or _optional_string(
        row.get("source_name")
    ) or str(row["source_code"])
    organization = _optional_string(row.get("publisher")) or _optional_string(
        row.get("source_name")
    ) or str(row["source_code"])
    publication_year = row.get("publication_year")
    if publication_year is None:
        raise ValueError(f"Reference source {source_code} is missing publication_year")
    return ReferenceSourceSummary(
        source_code=str(row["source_code"]),
        source_name=title,
        source_type=organization,
        title=title,
        organization=organization,
        year=int(publication_year),
        publication_title=_optional_string(row.get("publication_title")),
        publisher=_optional_string(row.get("publisher")),
        publication_year=int(publication_year),
        url=str(row.get("url") or ""),
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


def _build_prevention_items(
    disease_code: str,
    references: dict[str, dict[str, Any]],
    seen_recommendation_texts: set[str],
) -> list[KnowledgeLineItem]:
    templates = PREVENTION_TEMPLATES.get(disease_code, [])
    prevention: list[KnowledgeLineItem] = []
    for order, text, source_code in templates:
        normalized = _normalize_recommendation_text(text)
        if not normalized or normalized in seen_recommendation_texts:
            continue
        seen_recommendation_texts.add(normalized)
        prevention.append(
            KnowledgeLineItem(
                order=order,
                text=text,
                source=_reference(source_code, references),
                confidence_level=0.92,
            )
        )
    return prevention


def _register_recommendation_text(
    text: str,
    seen_recommendation_texts: set[str],
) -> bool:
    normalized = _normalize_recommendation_text(text)
    if not normalized or normalized in seen_recommendation_texts:
        return False
    seen_recommendation_texts.add(normalized)
    return True


def _normalize_recommendation_text(text: str) -> str:
    normalized = unicodedata.normalize("NFKC", text).casefold()
    normalized = unicodedata.normalize("NFD", normalized)
    normalized = "".join(
        character for character in normalized if not unicodedata.combining(character)
    )
    normalized = re.sub(r"[^a-z0-9]+", " ", normalized)
    normalized = re.sub(r"\s+", " ", normalized).strip()
    return normalized


def _localized_disease_summary(
    disease_code: str,
    fallback_summary: str | None,
) -> str:
    summary = VIETNAMESE_DISEASE_SUMMARIES.get(disease_code)
    if summary:
        return summary
    if fallback_summary:
        return fallback_summary
    return ""


def _localized_favorable_conditions(
    disease_code: str,
    fallback_conditions: str | None,
) -> str | None:
    localized = VIETNAMESE_FAVORABLE_CONDITIONS.get(disease_code)
    if localized:
        return localized
    return fallback_conditions
