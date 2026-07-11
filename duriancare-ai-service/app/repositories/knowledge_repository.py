from __future__ import annotations

from collections.abc import Iterable, Sequence
from dataclasses import dataclass
from typing import Any

from psycopg import sql
from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool


@dataclass(frozen=True)
class KnowledgeBundle:
    disease: dict[str, Any] | None
    symptoms: list[dict[str, Any]]
    causes: list[dict[str, Any]]
    biological_treatments: list[dict[str, Any]]
    organic_treatments: list[dict[str, Any]]
    chemical_treatments: list[dict[str, Any]]
    recommended_chemicals: list[dict[str, Any]]
    active_ingredients: list[dict[str, Any]]
    harvest_intervals: list[dict[str, Any]]
    export_requirements: list[dict[str, Any]]
    reference_sources: dict[str, dict[str, Any]]


class KnowledgeRepositoryError(RuntimeError):
    pass


class KnowledgeRepository:
    def __init__(
        self,
        postgres_url: str,
        schema: str = "public",
        pool: ConnectionPool | None = None,
    ) -> None:
        self.schema = schema
        self.pool = pool or ConnectionPool(
            conninfo=postgres_url,
            min_size=1,
            max_size=4,
            kwargs={
                "autocommit": True,
                "row_factory": dict_row,
            },
        )

    def close(self) -> None:
        self.pool.close()

    def fetch_disease_bundle(self, disease_code: str) -> KnowledgeBundle | None:
        try:
            disease = self._fetch_one(
                sql.SQL(
                    """
                    SELECT
                        d.code,
                        d.vietnamese_name,
                        d.english_name,
                        d.scientific_name,
                        d.issue_type,
                        d.severity,
                        d.disease_summary,
                        d.favorable_conditions,
                        d.confidence_level,
                        d.primary_source_code
                    FROM {table} AS d
                    WHERE d.code = %s
                    """
                ).format(table=self._qualified("kb_diseases")),
                (disease_code,),
            )
            if disease is None:
                return None

            symptoms = self._fetch_all(
                sql.SQL(
                    """
                    SELECT
                        symptom_order AS item_order,
                        symptom_text AS item_text,
                        source_code,
                        confidence_level
                    FROM {table}
                    WHERE disease_code = %s
                    ORDER BY symptom_order ASC
                    """
                ).format(table=self._qualified("kb_disease_symptoms")),
                (disease_code,),
            )
            causes = self._fetch_all(
                sql.SQL(
                    """
                    SELECT
                        cause_order AS item_order,
                        cause_text AS item_text,
                        source_code,
                        confidence_level
                    FROM {table}
                    WHERE disease_code = %s
                    ORDER BY cause_order ASC
                    """
                ).format(table=self._qualified("kb_disease_causes")),
                (disease_code,),
            )
            biological_treatments = self._fetch_all(
                sql.SQL(
                    """
                    SELECT
                        treatment_order AS item_order,
                        treatment_text AS item_text,
                        mechanism,
                        source_code,
                        confidence_level
                    FROM {table}
                    WHERE disease_code = %s
                    ORDER BY treatment_order ASC
                    """
                ).format(table=self._qualified("kb_biological_treatments")),
                (disease_code,),
            )
            organic_treatments = self._fetch_all(
                sql.SQL(
                    """
                    SELECT
                        treatment_order AS item_order,
                        treatment_text AS item_text,
                        safe_usage_note,
                        source_code,
                        confidence_level
                    FROM {table}
                    WHERE disease_code = %s
                    ORDER BY treatment_order ASC
                    """
                ).format(table=self._qualified("kb_organic_treatments")),
                (disease_code,),
            )
            chemical_treatments = self._fetch_all(
                sql.SQL(
                    """
                    SELECT
                        id,
                        treatment_order AS item_order,
                        treatment_text AS item_text,
                        safe_usage_note,
                        source_code,
                        confidence_level
                    FROM {table}
                    WHERE disease_code = %s
                    ORDER BY treatment_order ASC
                    """
                ).format(table=self._qualified("kb_chemical_treatments")),
                (disease_code,),
            )
            chemical_treatment_ids = [row["id"] for row in chemical_treatments]
            recommended_chemicals = self._fetch_all(
                sql.SQL(
                    """
                    SELECT
                        id,
                        chemical_treatment_id,
                        recommendation_order AS item_order,
                        product_name,
                        active_ingredient_summary,
                        usage_note,
                        source_code,
                        confidence_level
                    FROM {table}
                    WHERE chemical_treatment_id = ANY(%s)
                    ORDER BY chemical_treatment_id ASC, recommendation_order ASC
                    """
                ).format(table=self._qualified("kb_recommended_chemicals")),
                (chemical_treatment_ids or [None],),
            ) if chemical_treatment_ids else []
            recommended_chemical_ids = [row["id"] for row in recommended_chemicals]
            active_ingredients = self._fetch_all(
                sql.SQL(
                    """
                    SELECT
                        rcai.recommended_chemical_id,
                        ai.id,
                        ai.ingredient_name,
                        ai.chemical_group,
                        ai.source_code,
                        ai.confidence_level,
                        rcai.sort_order
                    FROM {join_table} AS rcai
                    INNER JOIN {ingredient_table} AS ai
                        ON ai.id = rcai.active_ingredient_id
                    WHERE rcai.recommended_chemical_id = ANY(%s)
                    ORDER BY rcai.recommended_chemical_id ASC, rcai.sort_order ASC
                    """
                ).format(
                    join_table=self._qualified("kb_recommended_chemical_active_ingredients"),
                    ingredient_table=self._qualified("kb_active_ingredients"),
                ),
                (recommended_chemical_ids or [None],),
            ) if recommended_chemical_ids else []
            harvest_intervals = self._fetch_all(
                sql.SQL(
                    """
                    SELECT
                        hi.recommended_chemical_id,
                        hi.market_code,
                        em.market_name,
                        hi.phi_days,
                        hi.notes,
                        hi.source_code,
                        hi.confidence_level,
                        hi.created_at,
                        hi.updated_at
                    FROM {interval_table} AS hi
                    INNER JOIN {market_table} AS em
                        ON em.market_code = hi.market_code
                    WHERE hi.recommended_chemical_id = ANY(%s)
                    ORDER BY hi.recommended_chemical_id ASC, em.market_code ASC
                    """
                ).format(
                    interval_table=self._qualified("kb_harvest_intervals"),
                    market_table=self._qualified("kb_export_markets"),
                ),
                (recommended_chemical_ids or [None],),
            ) if recommended_chemical_ids else []
            export_requirements = self._fetch_all(
                sql.SQL(
                    """
                    SELECT
                        er.disease_code,
                        er.market_code,
                        em.market_name,
                        er.requirement_order AS item_order,
                        er.requirement_text AS item_text,
                        er.warning_text,
                        er.source_code,
                        er.confidence_level
                    FROM {requirement_table} AS er
                    INNER JOIN {market_table} AS em
                        ON em.market_code = er.market_code
                    WHERE er.disease_code = %s
                    ORDER BY er.market_code ASC, er.requirement_order ASC
                    """
                ).format(
                    requirement_table=self._qualified("kb_export_requirements"),
                    market_table=self._qualified("kb_export_markets"),
                ),
                (disease_code,),
            )

            source_codes = self._collect_source_codes(
                [{"source_code": disease.get("primary_source_code")}],
                symptoms,
                causes,
                biological_treatments,
                organic_treatments,
                chemical_treatments,
                recommended_chemicals,
                active_ingredients,
                harvest_intervals,
                export_requirements,
            )
            reference_sources = self._fetch_reference_sources(source_codes)

            return KnowledgeBundle(
                disease=disease,
                symptoms=symptoms,
                causes=causes,
                biological_treatments=biological_treatments,
                organic_treatments=organic_treatments,
                chemical_treatments=chemical_treatments,
                recommended_chemicals=recommended_chemicals,
                active_ingredients=active_ingredients,
                harvest_intervals=harvest_intervals,
                export_requirements=export_requirements,
                reference_sources=reference_sources,
            )
        except Exception as exception:
            raise KnowledgeRepositoryError(
                f"Failed to load knowledge base for {disease_code}: {exception}"
            ) from exception

    def _fetch_reference_sources(
        self,
        source_codes: set[str],
    ) -> dict[str, dict[str, Any]]:
        if not source_codes:
            return {}
        rows = self._fetch_all(
            sql.SQL(
                """
                SELECT
                    source_code,
                    source_name,
                    source_type,
                    publication_title,
                    publisher,
                    publication_year,
                    url,
                    confidence_level,
                    notes
                FROM {table}
                WHERE source_code = ANY(%s)
                ORDER BY source_code ASC
                """
            ).format(table=self._qualified("kb_reference_sources")),
            (sorted(source_codes),),
        )
        return {row["source_code"]: row for row in rows}

    def _collect_source_codes(
        self,
        *sections: Iterable[dict[str, Any]] | list[dict[str, Any]],
    ) -> set[str]:
        source_codes: set[str] = set()
        for section in sections:
            for row in section:
                source_code = row.get("source_code")
                if isinstance(source_code, str) and source_code.strip():
                    source_codes.add(source_code)
        return source_codes

    def _fetch_all(
        self,
        query: sql.Composed,
        params: Sequence[Any] = (),
    ) -> list[dict[str, Any]]:
        with self.pool.connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(query, params)
                return [dict(row) for row in cursor.fetchall()]

    def _fetch_one(
        self,
        query: sql.Composed,
        params: Sequence[Any] = (),
    ) -> dict[str, Any] | None:
        with self.pool.connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(query, params)
                row = cursor.fetchone()
                return dict(row) if row is not None else None

    def _qualified(self, table_name: str) -> sql.Identifier:
        return sql.Identifier(self.schema, table_name)
