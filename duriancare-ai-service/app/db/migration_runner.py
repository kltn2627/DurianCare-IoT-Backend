from __future__ import annotations

import logging
import time
from pathlib import Path

import psycopg
from psycopg import sql

logger = logging.getLogger(__name__)

MIGRATION_TABLE_NAME = "ai_schema_migrations"
ADVISORY_LOCK_KEY = 741260193


class MigrationRunnerError(RuntimeError):
    pass


def run_ai_database_migrations(
    postgres_url: str,
    schema: str,
    migration_dir: Path | None = None,
    max_attempts: int = 10,
    retry_delay_seconds: float = 2.0,
) -> list[str]:
    migration_dir = migration_dir or Path(__file__).resolve().parents[2] / "db" / "migration"
    scripts = sorted(
        path
        for path in migration_dir.glob("V*__*.sql")
        if path.is_file()
    )
    if not scripts:
        logger.warning("No AI migration scripts found in %s", migration_dir)
        return []

    last_error: Exception | None = None
    for attempt in range(1, max_attempts + 1):
        try:
            return _run_migrations_once(postgres_url, schema, scripts)
        except Exception as exception:
            last_error = exception
            logger.warning(
                "AI migration attempt %s/%s failed: %s",
                attempt,
                max_attempts,
                exception,
            )
            if attempt < max_attempts:
                time.sleep(retry_delay_seconds)

    raise MigrationRunnerError(
        f"Failed to run AI migrations after {max_attempts} attempts"
    ) from last_error


def _run_migrations_once(
    postgres_url: str,
    schema: str,
    scripts: list[Path],
) -> list[str]:
    applied_versions: list[str] = []
    with psycopg.connect(postgres_url, autocommit=False) as connection:
        connection.execute("SELECT pg_advisory_lock(%s)", (ADVISORY_LOCK_KEY,))
        try:
            connection.execute(
                sql.SQL("CREATE SCHEMA IF NOT EXISTS {}").format(
                    sql.Identifier(schema)
                )
            )
            connection.execute(
                sql.SQL("SET search_path TO {}, public").format(
                    sql.Identifier(schema)
                )
            )
            connection.execute(
                sql.SQL(
                    """
                    CREATE TABLE IF NOT EXISTS {} (
                        version VARCHAR(50) PRIMARY KEY,
                        script_name TEXT NOT NULL,
                        applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """
                ).format(sql.Identifier(MIGRATION_TABLE_NAME))
            )

            applied_versions_set = {
                row[0]
                for row in connection.execute(
                    sql.SQL("SELECT version FROM {} ORDER BY version").format(
                        sql.Identifier(MIGRATION_TABLE_NAME)
                    )
                ).fetchall()
            }

            for script_path in scripts:
                version = _extract_version(script_path.name)
                if version in applied_versions_set:
                    logger.info("Skipping already applied AI migration %s", script_path.name)
                    continue

                script_text = script_path.read_text(encoding="utf-8")
                if not script_text.strip():
                    logger.info("Skipping empty AI migration %s", script_path.name)
                    continue

                with connection.transaction():
                    connection.execute(script_text)
                    connection.execute(
                        sql.SQL(
                            "INSERT INTO {} (version, script_name) VALUES (%s, %s)"
                        ).format(sql.Identifier(MIGRATION_TABLE_NAME)),
                        (version, script_path.name),
                    )
                applied_versions.append(script_path.name)
                logger.info("Applied AI migration %s", script_path.name)

            connection.commit()
        finally:
            try:
                connection.execute("SELECT pg_advisory_unlock(%s)", (ADVISORY_LOCK_KEY,))
            except Exception:
                logger.debug("Unable to release AI migration advisory lock", exc_info=True)
    return applied_versions


def _extract_version(script_name: str) -> str:
    prefix, _, _ = script_name.partition("__")
    return prefix.removeprefix("V")
