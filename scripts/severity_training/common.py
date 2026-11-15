from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Iterable
import csv
import hashlib
import json

IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}
APPROVED = "APPROVED"


@dataclass(frozen=True)
class AnnotationRecord:
    image_id: str
    image_path: str
    disease: str
    annotation_type: str
    annotation_source: str
    annotator: str
    review_status: str
    severity_label: str | None
    severity_score: float | None
    affected_area_ratio: float | None
    source_dataset: str
    tree_group_id: str | None
    session_id: str | None
    notes: str | None


def iter_images(root: Path) -> Iterable[Path]:
    for path in root.rglob("*"):
        if path.is_file() and path.suffix.lower() in IMAGE_EXTENSIONS:
            yield path


def sha256_file(path: Path, chunk_size: int = 1024 * 1024) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(chunk_size), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_annotation_csv(path: Path) -> list[AnnotationRecord]:
    if not path.exists():
        return []
    with path.open("r", encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))
    records: list[AnnotationRecord] = []
    for row in rows:
        severity_score = _optional_float(row.get("severity_score"))
        affected_area_ratio = _optional_float(row.get("affected_area_ratio"))
        records.append(
            AnnotationRecord(
                image_id=(row.get("image_id") or "").strip(),
                image_path=(row.get("image_path") or "").strip(),
                disease=(row.get("disease") or "").strip(),
                annotation_type=(row.get("annotation_type") or "").strip(),
                annotation_source=(row.get("annotation_source") or "").strip(),
                annotator=(row.get("annotator") or "").strip(),
                review_status=(row.get("review_status") or "").strip().upper(),
                severity_label=_blank_to_none(row.get("severity_label")),
                severity_score=severity_score,
                affected_area_ratio=affected_area_ratio,
                source_dataset=(row.get("source_dataset") or "").strip(),
                tree_group_id=_blank_to_none(row.get("tree_group_id")),
                session_id=_blank_to_none(row.get("session_id")),
                notes=_blank_to_none(row.get("notes")),
            )
        )
    return records


def write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _blank_to_none(value: str | None) -> str | None:
    if value is None:
        return None
    value = value.strip()
    return value or None


def _optional_float(value: str | None) -> float | None:
    value = _blank_to_none(value)
    if value is None:
        return None
    return float(value)
