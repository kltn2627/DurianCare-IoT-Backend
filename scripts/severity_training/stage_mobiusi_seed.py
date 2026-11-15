from __future__ import annotations

from argparse import ArgumentParser
from pathlib import Path
import csv
import json
import shutil

from common import sha256_file


FIELDS = [
    "image_id",
    "image_path",
    "disease",
    "annotation_type",
    "annotation_source",
    "annotator",
    "review_status",
    "severity_label",
    "severity_score",
    "affected_area_ratio",
    "source_dataset",
    "tree_group_id",
    "session_id",
    "notes",
]


SEVERITY_MAP = {
    "none": "NONE",
    "healthy": "NONE",
    "mild": "MILD",
    "moderate": "MODERATE",
    "severe": "SEVERE",
}


def main() -> int:
    parser = ArgumentParser(description="Stage the public Mobiusi durian severity seed dataset for expert review.")
    parser.add_argument("--dataset-root", type=Path, required=True)
    parser.add_argument("--raw-dir", type=Path, default=None)
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    raw_dir = args.raw_dir or args.dataset_root / "raw" / "dataset_001_mobiusi_durian_leaf_rot_hf"
    images_dir = args.dataset_root / "images" / "external" / "dataset_001_mobiusi_durian_leaf_rot_hf"
    annotations_path = args.dataset_root / "metadata" / "annotations.csv"
    images_dir.mkdir(parents=True, exist_ok=True)
    annotations_path.parent.mkdir(parents=True, exist_ok=True)

    existing_rows = _read_existing(annotations_path)
    staged_rows = []
    for metadata_path in sorted(raw_dir.glob("*.json")):
        if metadata_path.name == "SOURCE_MANIFEST.json":
            continue
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        source_image = raw_dir / metadata["file_name"]
        if not source_image.exists():
            raise FileNotFoundError(source_image)
        image_id = f"mobiusi-{metadata_path.stem}"
        rel_image = Path("images") / "external" / "dataset_001_mobiusi_durian_leaf_rot_hf" / source_image.name
        target_image = args.dataset_root / rel_image
        if args.force or not target_image.exists():
            shutil.copy2(source_image, target_image)

        staged_rows.append(
            {
                "image_id": image_id,
                "image_path": rel_image.as_posix(),
                "disease": "LEAF_BLIGHT",
                "annotation_type": "SOURCE_IMAGE_LEVEL_SEVERITY",
                "annotation_source": "MOBIUSI_HF_JSON_NEEDS_EXPERT_REVIEW",
                "annotator": "external_dataset",
                "review_status": "NEEDS_EXPERT_REVIEW",
                "severity_label": _normalize_severity(metadata.get("severity_level")),
                "severity_score": "",
                "affected_area_ratio": _parse_percentage(metadata.get("affected_area_percentage")),
                "source_dataset": "dataset_001_mobiusi_durian_leaf_rot_hf",
                "tree_group_id": "",
                "session_id": "",
                "notes": f"source_sha256={sha256_file(source_image)}; source_metadata={metadata_path.name}",
            }
        )

    merged = [row for row in existing_rows if row.get("source_dataset") != "dataset_001_mobiusi_durian_leaf_rot_hf"]
    merged.extend(staged_rows)
    with annotations_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(merged)
    print(f"Staged {len(staged_rows)} review-pending rows into {annotations_path}")
    return 0


def _read_existing(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        return []
    with path.open("r", encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def _normalize_severity(value: object) -> str:
    text = str(value or "").strip().lower()
    return SEVERITY_MAP.get(text, "UNCERTAIN")


def _parse_percentage(value: object) -> str:
    text = str(value or "").strip()
    digits = "".join(ch for ch in text if ch.isdigit() or ch == ".")
    if not digits:
        return ""
    percent = float(digits)
    if percent > 1:
        percent = percent / 100
    return f"{percent:.4f}".rstrip("0").rstrip(".")


if __name__ == "__main__":
    raise SystemExit(main())
