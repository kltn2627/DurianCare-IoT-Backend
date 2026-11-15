from __future__ import annotations

from argparse import ArgumentParser
from collections import Counter, defaultdict
from pathlib import Path
import csv
import json

from common import APPROVED, read_annotation_csv, write_json


MIN_APPROVED = 120
MIN_PER_SEVERITY = 20
MIN_SEVERITY_CLASSES = 2
VALID_LABELS = {"NONE", "MILD", "MODERATE", "SEVERE"}


def main() -> int:
    parser = ArgumentParser(description="Decide whether the severity dataset is training-ready.")
    parser.add_argument("--dataset-root", type=Path, required=True)
    parser.add_argument("--artifacts-root", type=Path, default=Path("artifacts/severity_training"))
    parser.add_argument("--output", type=Path, default=None)
    args = parser.parse_args()

    root = args.dataset_root
    records = read_annotation_csv(root / "metadata" / "annotations.csv")
    splits = _read_csv(root / "metadata" / "splits.csv")
    duplicate_groups = _read_csv(root / "reports" / "duplicate_groups.csv")
    duplicate_by_image = {row["image_id"]: row for row in duplicate_groups if row.get("image_id")}
    validation = _read_json(root / "reports" / "validation_summary.json")
    protocol_finalized = _protocol_finalized(args.artifacts_root / "ANNOTATION_GUIDE.md")

    approved = [record for record in records if record.review_status == APPROVED]
    severity_counts = Counter(record.severity_label or "<missing>" for record in approved)
    disease_counts = Counter(record.disease or "<missing>" for record in approved)
    source_counts = Counter(record.source_dataset or "<missing>" for record in approved)
    blockers: list[dict[str, str]] = []

    if not protocol_finalized:
        _block(blockers, "PROTOCOL_NOT_FINALIZED", "ANNOTATION_GUIDE.md still marks NEEDS_EXPERT_SEVERITY_PROTOCOL")
    if validation.get("annotationErrorCount", 0):
        _block(blockers, "INVALID_ANNOTATIONS", f"validation reported {validation['annotationErrorCount']} annotation errors")
    if validation.get("invalidImageCount", 0):
        _block(blockers, "CORRUPTED_IMAGES", f"validation reported {validation['invalidImageCount']} invalid images")
    if len(approved) < MIN_APPROVED:
        _block(blockers, "APPROVED_COUNT_TOO_LOW", f"approved_count {len(approved)} < minimum {MIN_APPROVED}")

    valid_severity = {label: count for label, count in severity_counts.items() if label in VALID_LABELS}
    if len(valid_severity) < MIN_SEVERITY_CLASSES:
        _block(blockers, "CLASS_MINIMUM_TOO_LOW", f"severity_classes {len(valid_severity)} < minimum {MIN_SEVERITY_CLASSES}")
    for label in sorted(VALID_LABELS):
        count = valid_severity.get(label, 0)
        if 0 < count < MIN_PER_SEVERITY:
            _block(blockers, f"{label}_CLASS_TOO_SMALL", f"{label} count {count} < minimum {MIN_PER_SEVERITY}")

    missing_images = [record.image_id for record in approved if not _resolve(root, record.image_path).exists()]
    if missing_images:
        _block(blockers, "MISSING_IMAGE", f"{len(missing_images)} approved records point to missing images")

    invalid_labels = [record.image_id for record in approved if record.severity_label not in VALID_LABELS]
    if invalid_labels:
        _block(blockers, "INVALID_LABEL", f"{len(invalid_labels)} approved records have invalid severity labels")

    split_result = _split_checks(approved, splits, duplicate_by_image)
    blockers.extend(split_result["blockers"])

    max_class = max(valid_severity.values(), default=0)
    min_class = min(valid_severity.values(), default=0)
    imbalance_ratio = round(max_class / min_class, 3) if min_class else None
    if imbalance_ratio and imbalance_ratio > 4:
        _block(blockers, "CLASS_IMBALANCE", f"max/min severity ratio {imbalance_ratio} > 4")

    payload = {
        "status": "TRAINING_READY" if not blockers else "DO_NOT_TRAIN",
        "approvedImageCount": len(approved),
        "totalAnnotationCount": len(records),
        "severityCounts": dict(severity_counts),
        "diseaseCounts": dict(disease_counts),
        "sourceCounts": dict(source_counts),
        "imbalanceRatio": imbalance_ratio,
        "minimums": {
            "approved": MIN_APPROVED,
            "perSeverity": MIN_PER_SEVERITY,
            "severityClasses": MIN_SEVERITY_CLASSES,
        },
        "protocolFinalized": protocol_finalized,
        "splitSummary": split_result["summary"],
        "reasonCodes": [blocker["code"] for blocker in blockers],
        "blockers": blockers,
    }
    output = args.output or root / "reports" / "training_gate.json"
    write_json(output, payload)
    print(f"Wrote {output}")
    print(payload["status"])
    for blocker in blockers:
        print(f"- {blocker['code']}: {blocker['detail']}")
    return 0 if not blockers else 1


def _block(blockers: list[dict[str, str]], code: str, detail: str) -> None:
    blockers.append({"code": code, "detail": detail})


def _read_csv(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        return []
    with path.open("r", encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def _read_json(path: Path) -> dict[str, object]:
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def _protocol_finalized(path: Path) -> bool:
    if not path.exists():
        return False
    text = path.read_text(encoding="utf-8")
    return "NEEDS_EXPERT_SEVERITY_PROTOCOL" not in text


def _resolve(root: Path, image_path: str) -> Path:
    path = Path(image_path)
    return path if path.is_absolute() else root / path


def _split_checks(records: list, splits: list[dict[str, str]], duplicates: dict[str, dict[str, str]]) -> dict[str, object]:
    blockers: list[dict[str, str]] = []
    summary = {"train": 0, "val": 0, "test": 0, "missing": 0}
    if not records:
        return {"blockers": blockers, "summary": summary}
    split_by_image = {row.get("image_id", ""): row.get("split", "") for row in splits}
    approved_ids = {record.image_id for record in records}
    if not splits:
        _block(blockers, "SPLIT_MISSING", "metadata/splits.csv does not exist")
        return {"blockers": blockers, "summary": summary}
    for record in records:
        split = split_by_image.get(record.image_id, "")
        if split not in {"train", "val", "test"}:
            summary["missing"] += 1
        else:
            summary[split] += 1
    if summary["missing"]:
        _block(blockers, "SPLIT_MISSING_APPROVED_ROWS", f"{summary['missing']} approved records are not assigned to train/val/test")
    for required in ("train", "val", "test"):
        if summary[required] == 0:
            _block(blockers, "SPLIT_INCOMPLETE", f"{required} split has 0 approved records")

    for key in ("exact_duplicate_group", "near_duplicate_group", "same_source_group"):
        groups: dict[str, set[str]] = defaultdict(set)
        for image_id in approved_ids:
            group = duplicates.get(image_id, {}).get(key, "")
            split = split_by_image.get(image_id, "")
            if group and split:
                groups[group].add(split)
        leaked = [group for group, group_splits in groups.items() if len(group_splits) > 1]
        if leaked:
            code = {
                "exact_duplicate_group": "DUPLICATE_LEAKAGE",
                "near_duplicate_group": "NEAR_DUPLICATE_LEAKAGE",
                "same_source_group": "SOURCE_LEAKAGE",
            }[key]
            _block(blockers, code, f"{len(leaked)} {key} values cross train/val/test")
    return {"blockers": blockers, "summary": summary}


if __name__ == "__main__":
    raise SystemExit(main())
