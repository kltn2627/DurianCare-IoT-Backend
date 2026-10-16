from __future__ import annotations

from typing import Any
from uuid import UUID, uuid4

from psycopg import sql
from psycopg.rows import dict_row
from psycopg.types.json import Jsonb
from psycopg_pool import ConnectionPool


class PredictionHistoryRepository:
    def __init__(self, postgres_url: str, schema: str = "public") -> None:
        self.schema = schema
        self.pool = ConnectionPool(
            postgres_url,
            min_size=0,
            max_size=4,
            kwargs={"row_factory": dict_row},
            open=True,
        )

    def close(self) -> None:
        self.pool.close()

    def create(
        self,
        *,
        user_id: str,
        predicted_disease: str,
        confidence: float,
        confidence_text: str,
        source: str,
        used_detection_crop: bool,
        severity: str | None,
        device_id: str | None,
        image_url: str | None,
        image_path: str | None,
        image_object_key: str | None,
        original_filename: str | None,
        response_payload: dict[str, Any],
    ) -> dict[str, Any]:
        history_id = uuid4()
        with self.pool.connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    sql.SQL(
                        """
                        INSERT INTO {} (
                            id,
                            user_id,
                            image_url,
                            image_path,
                            source,
                            device_id,
                            predicted_disease,
                            confidence,
                            confidence_text,
                            severity,
                            status,
                            used_detection_crop,
                            diagnosed_at,
                            created_at,
                            image_object_key,
                            original_filename,
                            response_payload
                        )
                        VALUES (
                            %(id)s,
                            %(user_id)s,
                            %(image_url)s,
                            %(image_path)s,
                            %(source)s,
                            %(device_id)s,
                            %(predicted_disease)s,
                            %(confidence)s,
                            %(confidence_text)s,
                            %(severity)s,
                            'PENDING',
                            %(used_detection_crop)s,
                            CURRENT_TIMESTAMP,
                            CURRENT_TIMESTAMP,
                            %(image_object_key)s,
                            %(original_filename)s,
                            %(response_payload)s
                        )
                        RETURNING *
                        """
                    ).format(self._table("ai_prediction_history")),
                    {
                        "id": history_id,
                        "user_id": UUID(user_id),
                        "image_url": image_url,
                        "image_path": image_path,
                        "source": source,
                        "device_id": device_id,
                        "predicted_disease": predicted_disease,
                        "confidence": confidence,
                        "confidence_text": confidence_text,
                        "severity": severity,
                        "used_detection_crop": used_detection_crop,
                        "image_object_key": image_object_key,
                        "original_filename": original_filename,
                        "response_payload": Jsonb(response_payload),
                    },
                )
                record = cursor.fetchone()
            connection.commit()
        return dict(record or {"id": history_id})

    def list_for_user(
        self,
        user_id: str,
        *,
        query: str | None = None,
        status: str | None = None,
        limit: int = 5,
        offset: int = 0,
    ) -> tuple[list[dict[str, Any]], int]:
        filters = [
            sql.SQL("user_id = %(user_id)s"),
            sql.SQL("deleted_at IS NULL"),
        ]
        params: dict[str, Any] = {
            "user_id": UUID(user_id),
            "limit": limit,
            "offset": offset,
        }
        if query:
            filters.append(sql.SQL("predicted_disease ILIKE %(query)s"))
            params["query"] = f"%{query}%"
        if status:
            filters.append(sql.SQL("status = %(status)s"))
            params["status"] = status

        where_clause = sql.SQL(" AND ").join(filters)
        with self.pool.connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    sql.SQL("SELECT COUNT(*) AS total FROM {} WHERE {}").format(
                        self._table("ai_prediction_history"),
                        where_clause,
                    ),
                    params,
                )
                total = int((cursor.fetchone() or {}).get("total") or 0)
                cursor.execute(
                    sql.SQL(
                        """
                        SELECT *
                        FROM {}
                        WHERE {}
                        ORDER BY diagnosed_at DESC, created_at DESC
                        LIMIT %(limit)s OFFSET %(offset)s
                        """
                    ).format(self._table("ai_prediction_history"), where_clause),
                    params,
                )
                rows = [dict(row) for row in cursor.fetchall()]
        return rows, total

    def soft_delete(self, user_id: str, history_id: str) -> bool:
        with self.pool.connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    sql.SQL(
                        """
                        UPDATE {}
                        SET deleted_at = CURRENT_TIMESTAMP
                        WHERE id = %(history_id)s
                          AND user_id = %(user_id)s
                          AND deleted_at IS NULL
                        """
                    ).format(self._table("ai_prediction_history")),
                    {
                        "history_id": UUID(history_id),
                        "user_id": UUID(user_id),
                    },
                )
                deleted = cursor.rowcount > 0
            connection.commit()
        return deleted

    def get_image_path_for_user(
        self,
        user_id: str,
        history_id: str,
    ) -> str | None:
        with self.pool.connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    sql.SQL(
                        """
                        SELECT image_path
                        FROM {}
                        WHERE id = %(history_id)s
                          AND user_id = %(user_id)s
                          AND deleted_at IS NULL
                        """
                    ).format(self._table("ai_prediction_history")),
                    {
                        "history_id": UUID(history_id),
                        "user_id": UUID(user_id),
                    },
                )
                row = cursor.fetchone()
        if not row:
            return None
        image_path = row.get("image_path")
        return str(image_path) if image_path else None

    def _table(self, table_name: str) -> sql.Composed:
        return sql.Identifier(self.schema, table_name)
