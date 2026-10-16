from __future__ import annotations

import os
import re
import textwrap
import sys
from dataclasses import asdict
from pathlib import Path

import psycopg

ROOT = Path(__file__).resolve().parents[1]
KB_ROOT = ROOT / "knowledge_base"
sys.path.insert(0, str(ROOT))

from app.core.config import settings
from app.repositories.knowledge_repository import KnowledgeRepository
from app.services.recommendation_service import RecommendationService


def slugify(value: str) -> str:
    slug = value.strip().lower()
    slug = re.sub(r"[^a-z0-9\u00C0-\u024F]+", "-", slug, flags=re.IGNORECASE)
    slug = re.sub(r"-+", "-", slug).strip("-")
    return slug or "document"


def ensure_directories() -> None:
    for relative in (
        "diseases",
        "pests",
        "cultivation",
        "harvest",
        "export",
        "regulations",
        "faq",
    ):
        (KB_ROOT / relative).mkdir(parents=True, exist_ok=True)


def fetch_source_map(repository: KnowledgeRepository) -> dict[str, dict[str, object]]:
    query = """
        SELECT source_code, source_name, source_type, publication_title, publisher, publication_year, url, confidence_level, notes
        FROM kb_reference_sources
        ORDER BY source_code
    """
    with repository.pool.connection() as connection:
        with connection.cursor() as cursor:
            cursor.execute(query)
            rows = [dict(row) for row in cursor.fetchall()]
    return {str(row["source_code"]): row for row in rows}


def render_sources(source_codes: list[str], source_map: dict[str, dict[str, object]]) -> str:
    lines = ["## Sources"]
    for source_code in sorted({code for code in source_codes if code}):
        source = source_map.get(source_code)
        if not source:
            continue
        title = source.get("publication_title") or source.get("source_name") or source_code
        url = source.get("url") or ""
        source_type = source.get("source_type") or "UNKNOWN"
        confidence = source.get("confidence_level")
        lines.append(
            f"- **{title}**"
            f" ({source_type}, confidence {confidence})"
            + (f" - {url}" if url else "")
        )
    return "\n".join(lines)


def render_disease_doc(bundle, recommendation, source_map: dict[str, dict[str, object]]) -> str:
    disease = bundle.disease or {}
    sections = [
        f"# {disease.get('vietnamese_name', 'Durian disease')}",
        "",
        f"- **Disease code:** {disease.get('code', 'UNKNOWN')}",
        f"- **English name:** {disease.get('english_name', 'Unknown')}",
        f"- **Scientific name:** {disease.get('scientific_name') or 'Unknown'}",
        f"- **Issue type:** {disease.get('issue_type') or 'UNKNOWN'}",
        f"- **Severity:** {disease.get('severity') or 'UNKNOWN'}",
        f"- **Summary:** {disease.get('disease_summary') or 'No summary available.'}",
        f"- **Favorable conditions:** {disease.get('favorable_conditions') or 'Not specified.'}",
        "",
        "## Symptoms",
    ]
    for item in bundle.symptoms[:8]:
        sections.append(f"- {item['item_text']}")

    sections.extend(
        [
            "",
            "## Causes",
        ]
    )
    for item in bundle.causes[:8]:
        sections.append(f"- {item['item_text']}")

    sections.extend(["", "## Prevention"])
    for item in recommendation.get("prevention", [])[:8]:
        sections.append(f"- {item['text']}")

    sections.extend(["", "## Biological treatment"])
    for item in bundle.biological_treatments[:8]:
        line = item["item_text"]
        if item.get("mechanism"):
            line += f" - {item['mechanism']}"
        sections.append(f"- {line}")

    sections.extend(["", "## Organic treatment"])
    for item in bundle.organic_treatments[:8]:
        line = item["item_text"]
        if item.get("safe_usage_note"):
            line += f" - {item['safe_usage_note']}"
        sections.append(f"- {line}")

    sections.extend(["", "## Chemical treatment"])
    for item in bundle.chemical_treatments[:8]:
        line = item["item_text"]
        if item.get("safe_usage_note"):
            line += f" - {item['safe_usage_note']}"
        sections.append(f"- {line}")

    if bundle.recommended_chemicals:
        sections.extend(["", "## Recommended chemicals"])
        for item in bundle.recommended_chemicals[:8]:
            line = item["product_name"]
            if item.get("usage_note"):
                line += f" - {item['usage_note']}"
            sections.append(f"- {line}")

    if bundle.active_ingredients:
        sections.extend(["", "## Active ingredients"])
        for item in bundle.active_ingredients[:8]:
            sections.append(f"- {item['ingredient_name']} ({item.get('chemical_group') or 'Unknown group'})")

    if bundle.harvest_intervals:
        sections.extend(["", "## Harvest interval (PHI)"])
        for item in bundle.harvest_intervals[:8]:
            sections.append(
                f"- {item['market_name']}: {item.get('phi_days') if item.get('phi_days') is not None else 'Check label'} days"
                + (f" - {item.get('notes')}" if item.get("notes") else "")
            )

    if bundle.export_requirements:
        sections.extend(["", "## Export requirements"])
        for item in bundle.export_requirements[:8]:
            sections.append(f"- {item['market_name']}: {item['item_text']}")
            if item.get("warning_text"):
                sections.append(f"  - Warning: {item['warning_text']}")

    source_codes = [disease.get("primary_source_code", "")] + [
        item.get("source_code", "")
        for collection in (
            bundle.symptoms,
            bundle.causes,
            bundle.biological_treatments,
            bundle.organic_treatments,
            bundle.chemical_treatments,
            bundle.recommended_chemicals,
            bundle.active_ingredients,
            bundle.harvest_intervals,
            bundle.export_requirements,
        )
        for item in collection
    ]
    sections.extend(["", render_sources(source_codes, source_map)])
    return "\n".join(sections).strip() + "\n"


def render_generic_doc(title: str, content: str, source_codes: list[str], source_map: dict[str, dict[str, object]]) -> str:
    sections = [
        f"# {title}",
        "",
        textwrap.dedent(content).strip(),
        "",
        render_sources(source_codes, source_map),
    ]
    return "\n".join(sections).strip() + "\n"


def build() -> None:
    ensure_directories()
    repository = KnowledgeRepository(settings.postgres_url, settings.knowledge_db_schema)
    recommendation_service = RecommendationService(repository)
    source_map = fetch_source_map(repository)

    with repository.pool.connection() as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT code
                FROM kb_diseases
                ORDER BY code
                """
            )
            disease_codes = [str(row["code"]) for row in cursor.fetchall()]

    for disease_code in disease_codes:
        bundle = repository.fetch_disease_bundle(disease_code)
        if bundle is None:
            continue
        recommendation = recommendation_service.build_recommendation(disease_code)
        if recommendation is None:
            continue
        path = KB_ROOT / "diseases" / f"{slugify(disease_code)}.md"
        path.write_text(render_disease_doc(bundle, recommendation.model_dump(), source_map), encoding="utf-8")

    generic_docs = {
        KB_ROOT / "diseases" / "anthracnose.md": (
            "Anthracnose",
            """
            Anthracnose is a common fungal leaf disease in durian orchards. Manage it by improving canopy ventilation, reducing prolonged leaf wetness, removing heavily infected tissues, and using only registered plant protection products according to the label and local regulations.
            Always verify residue and pre-harvest interval requirements before harvest.
            """,
            ["FAO_IPM", "GLOBALGAP_IFA", "VIETGAP_STANDARD"],
        ),
        KB_ROOT / "pests" / "thrips.md": (
            "Thrips",
            """
            Thrips management should combine scouting, weed control, canopy hygiene, and targeted intervention only when pest pressure becomes economically important.
            Preserve beneficial insects whenever possible and keep spray records for traceability.
            """,
            ["FAO_IPM", "GLOBALGAP_IFA"],
        ),
        KB_ROOT / "pests" / "mealybug.md": (
            "Mealybug",
            """
            Mealybug pressure can be reduced by sanitation, ant management, pruning of heavily infested tissues, and careful monitoring of new flushes.
            Biological and low-residue control options should be preferred before chemical escalation.
            """,
            ["FAO_IPM", "GLOBALGAP_IFA"],
        ),
        KB_ROOT / "pests" / "stem_borer.md": (
            "Stem borer",
            """
            Stem borer management depends on early detection, pruning of infested tissues, orchard sanitation, and protection of young tree trunks.
            Use chemical measures only when necessary and follow label directions strictly.
            """,
            ["FAO_IPM", "GLOBALGAP_IFA"],
        ),
        KB_ROOT / "cultivation" / "irrigation.md": (
            "Irrigation",
            """
            Keep root-zone moisture stable, avoid prolonged waterlogging, and minimize overhead irrigation during humid periods.
            Use drainage improvement, mulching, and canopy ventilation to reduce disease pressure.
            """,
            ["FAO_IPM", "GLOBALGAP_IFA", "VIETGAP_STANDARD"],
        ),
        KB_ROOT / "cultivation" / "pruning.md": (
            "Pruning and canopy management",
            """
            Remove diseased leaves and infected twigs early, disinfect tools, and open the canopy to improve light penetration and airflow.
            This reduces humidity inside the orchard and lowers the spread of foliar pathogens and pests.
            """,
            ["FAO_IPM", "GLOBALGAP_IFA", "VIETGAP_STANDARD"],
        ),
        KB_ROOT / "cultivation" / "fertilization.md": (
            "Fertilization",
            """
            Apply balanced nutrition based on tree stage and soil condition. Avoid excessive nitrogen that can trigger soft flushes and make canopies more disease-prone.
            Keep records of fertilizer type, rate, and timing for traceability.
            """,
            ["FAO_IPM", "GLOBALGAP_IFA", "VIETGAP_STANDARD"],
        ),
        KB_ROOT / "cultivation" / "flowering.md": (
            "Flowering management",
            """
            Support healthy flowering by keeping trees vigorous, reducing stress, and avoiding unnecessary pesticide applications during bloom.
            Monitor moisture, nutrition, and pest pressure so the tree can allocate energy to reproductive growth.
            """,
            ["FAO_IPM", "GLOBALGAP_IFA", "VIETGAP_STANDARD"],
        ),
        KB_ROOT / "harvest" / "harvest.md": (
            "Harvest",
            """
            Harvest only at the recommended maturity stage, handle fruit carefully to avoid bruising, and keep a clear lot-level traceability record.
            Respect pre-harvest intervals and export residue rules before harvest.
            """,
            ["FAO_SUPERFRUIT_EXPORT", "FAO_CODEX_MRL", "GLOBALGAP_IFA"],
        ),
        KB_ROOT / "harvest" / "storage.md": (
            "Storage",
            """
            Keep harvested fruit shaded, clean, and well-ventilated. Use rapid handling and cool chain practices where available to preserve quality.
            Separate lots by harvest date, orchard block, and treatment history.
            """,
            ["FAO_SUPERFRUIT_EXPORT", "GLOBALGAP_IFA", "VIETGAP_STANDARD"],
        ),
        KB_ROOT / "export" / "eu.md": (
            "European Union export",
            """
            For EU export, confirm the market-specific maximum residue limits, respect the strictest pre-harvest interval, and keep full spray records.
            If residue limits are uncertain, stop and verify the label and importer requirements before harvest.
            """,
            ["EU_PESTICIDES_DB", "FAO_CODEX_MRL", "GLOBALGAP_IFA"],
        ),
        KB_ROOT / "export" / "japan.md": (
            "Japan export",
            """
            For Japan export, verify residue limits before spraying and keep lot-level traceability.
            The orchard management plan should allow residue compliance checks before shipment.
            """,
            ["JAPAN_MRL_DB", "FAO_CODEX_MRL", "GLOBALGAP_IFA"],
        ),
        KB_ROOT / "export" / "usa.md": (
            "United States export",
            """
            For the United States market, maintain treatment records, monitor pre-harvest intervals, and verify the active ingredient is allowed for the intended export channel.
            Prefer the most conservative residue threshold when market rules differ.
            """,
            ["FAO_CODEX_MRL", "GLOBALGAP_IFA", "FAO_SUPERFRUIT_EXPORT"],
        ),
        KB_ROOT / "export" / "mrl.md": (
            "Maximum residue limits",
            """
            Maximum residue limits must be checked per active ingredient and target market. Use the strictest applicable market requirement when the same lot may move to multiple destinations.
            PHI and MRL compliance are part of export readiness and food safety.
            """,
            ["FAO_CODEX_MRL", "EU_PESTICIDES_DB", "JAPAN_MRL_DB", "GLOBALGAP_IFA"],
        ),
        KB_ROOT / "regulations" / "vietgap.md": (
            "VietGAP",
            """
            VietGAP emphasizes safe production, record keeping, traceability, worker safety, and responsible pesticide use.
            Keep spray logs, field notes, and harvest records available for inspection.
            """,
            ["VIETGAP_STANDARD", "VIETNAM_PPD_LAW"],
        ),
        KB_ROOT / "regulations" / "globalgap.md": (
            "GlobalG.A.P.",
            """
            GlobalG.A.P. focuses on farm assurance, traceability, food safety, and controlled use of plant protection products.
            Keep all input records, lot separation, and audit-ready documentation.
            """,
            ["GLOBALGAP_IFA"],
        ),
        KB_ROOT / "faq" / "common_questions.md": (
            "Common questions",
            """
            This assistant specializes in durian cultivation and agriculture.
            If a question is outside durian farming, plant pathology, food safety, export, or farm management, it should be politely declined.
            The assistant should cite sources and avoid inventing treatment claims.
            """,
            ["FAO_IPM", "GLOBALGAP_IFA", "VIETGAP_STANDARD"],
        ),
        KB_ROOT / "README.md": (
            "DurianCare Knowledge Base",
            """
            Directory structure:
            - diseases/: disease-specific guidance and treatment summaries
            - pests/: insect and pest management notes
            - cultivation/: irrigation, pruning, fertilization, flowering
            - harvest/: harvest and storage guidance
            - export/: market-specific residue and export guidance
            - regulations/: VietGAP and GlobalG.A.P. notes
            - faq/: frequently asked questions and guardrails

            Each markdown file should remain concise, cite trusted public sources, and be safe for retrieval-augmented generation.
            """,
            ["FAO_IPM", "FAO_CODEX_MRL", "GLOBALGAP_IFA", "VIETGAP_STANDARD"],
        ),
    }

    for path, (title, content, source_codes) in generic_docs.items():
        path.write_text(
            render_generic_doc(title, content, source_codes, source_map),
            encoding="utf-8",
        )

    repository.close()


if __name__ == "__main__":
    build()
