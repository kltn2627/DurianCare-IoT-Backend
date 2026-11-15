from __future__ import annotations

from argparse import ArgumentParser
from collections import Counter, defaultdict
from pathlib import Path
import sys

from PIL import Image, UnidentifiedImageError

from common import APPROVED, iter_images, read_annotation_csv, sha256_file, write_json

ALLOWED_REVIEW_STATUSES = {"PENDING", "NEEDS_REVIEW", "NEEDS_EXPERT_REVIEW", "UNCERTAIN", "REJECTED", "APPROVED"}
ALLOWED_SEVERITY_LABELS = {"NONE", "MILD", "MODERATE", "SEVERE", "UNCERTAIN"}


def main() -> int:
    parser = ArgumentParser(description="Validate DurianCare severity dataset without inventing labels.")
    parser.add_argument("--dataset-root", type=Path, required=True)
    parser.add_argument(
        "--annotations",
        type=Path,
        default=None,
        help="CSV annotation file. Defaults to <dataset-root>/metadata/annotations.csv.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("artifacts/severity_training/validation_summary.json"),
    )
    args = parser.parse_args()

    root = args.dataset_root
    annotations_path = args.annotations or root / "metadata" / "annotations.csv"
    images = sorted(iter_images(root / "images"))
    hashes: dict[str, list[str]] = defaultdict(list)
    invalid_images: list[dict[str, str]] = []
    format_counts: Counter[str] = Counter()
    dimension_counts: Counter[str] = Counter()

    for image_path in images:
        rel = image_path.relative_to(root).as_posix()
        try:
            with Image.open(image_path) as image:
                image.verify()
            with Image.open(image_path) as image:
                width, height = image.size
                format_counts[image.format or image_path.suffix.lower()] += 1
                dimension_counts[f"{width}x{height}"] += 1
            hashes[sha256_file(image_path)].append(rel)
        except (UnidentifiedImageError, OSError, ValueError) as exc:
            invalid_images.append({"image": rel, "error": str(exc)})

    duplicate_groups = [paths for paths in hashes.values() if len(paths) > 1]
    records = read_annotation_csv(annotations_path)
    approved = [record for record in records if record.review_status == APPROVED]
    unapproved = [record for record in records if record.review_status != APPROVED]
    missing_annotation_images = []
    annotated_paths = {record.image_path for record in records}
    for image_path in images:
        rel = image_path.relative_to(root).as_posix()
        if rel not in annotated_paths:
            missing_annotation_images.append(rel)

    annotation_errors: list[dict[str, str]] = []
    for record in records:
        if record.review_status not in ALLOWED_REVIEW_STATUSES:
            annotation_errors.append({"image_id": record.image_id, "error": f"invalid review_status {record.review_status}"})
        if record.severity_label and record.severity_label not in ALLOWED_SEVERITY_LABELS:
            annotation_errors.append({"image_id": record.image_id, "error": f"invalid severity_label {record.severity_label}"})
        if record.severity_score is not None and not 0.0 <= record.severity_score <= 1.0:
            annotation_errors.append({"image_id": record.image_id, "error": "severity_score outside [0,1]"})
        if record.affected_area_ratio is not None and not 0.0 <= record.affected_area_ratio <= 1.0:
            annotation_errors.append({"image_id": record.image_id, "error": "affected_area_ratio outside [0,1]"})
        if record.image_path and not _resolve_image_path(root, record.image_path).exists():
            annotation_errors.append({"image_id": record.image_id, "error": f"image_path not found: {record.image_path}"})
        if record.review_status == APPROVED and not (
            record.severity_label or record.severity_score is not None or record.affected_area_ratio is not None
        ):
            annotation_errors.append({"image_id": record.image_id, "error": "approved record has no severity evidence"})

    payload = {
        "datasetRoot": str(root),
        "annotationFile": str(annotations_path),
        "imageCount": len(images),
        "invalidImageCount": len(invalid_images),
        "duplicateGroupCount": len(duplicate_groups),
        "annotationCount": len(records),
        "approvedAnnotationCount": len(approved),
        "unapprovedAnnotationCount": len(unapproved),
        "missingAnnotationImageCount": len(missing_annotation_images),
        "annotationErrorCount": len(annotation_errors),
        "formatCounts": dict(format_counts),
        "topDimensionCounts": dict(dimension_counts.most_common(20)),
        "duplicateGroups": duplicate_groups[:50],
        "invalidImages": invalid_images[:100],
        "annotationErrors": annotation_errors[:100],
    }
    write_json(args.output, payload)
    print(f"Wrote {args.output}")
    print(f"Images={len(images)} approved_annotations={len(approved)} annotation_errors={len(annotation_errors)}")
    return 0 if not invalid_images and not annotation_errors else 1


def _resolve_image_path(root: Path, image_path: str) -> Path:
    path = Path(image_path)
    if path.is_absolute():
        return path
    return root / path


if __name__ == "__main__":
    sys.exit(main())
