from __future__ import annotations

from argparse import ArgumentParser
from pathlib import Path
import csv

from common import iter_images, sha256_file


FIELDS = [
    "image_id",
    "path",
    "disease_code",
    "severity_label",
    "severity_score",
    "annotation_status",
    "review_status",
    "annotator",
    "notes",
    "source_dataset",
    "source_split",
    "sha256",
]


def main() -> int:
    parser = ArgumentParser(description="Build severity annotation candidates from an existing classification dataset.")
    parser.add_argument("--source-classification-root", type=Path, required=True)
    parser.add_argument("--dataset-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=None)
    args = parser.parse_args()

    output = args.output or args.dataset_root / "metadata" / "severity_annotation_candidates.csv"
    output.parent.mkdir(parents=True, exist_ok=True)

    rows = []
    for index, image_path in enumerate(sorted(iter_images(args.source_classification_root)), start=1):
        disease = image_path.parent.name
        split = image_path.parent.parent.name if image_path.parent.parent != args.source_classification_root else ""
        rows.append(
            {
                "image_id": f"dld-{index:06d}",
                "path": str(image_path),
                "disease_code": disease,
                "severity_label": "",
                "severity_score": "",
                "annotation_status": "PENDING",
                "review_status": "PENDING",
                "annotator": "",
                "notes": "",
                "source_dataset": "DLD_Dataset",
                "source_split": split,
                "sha256": sha256_file(image_path),
            }
        )

    with output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote {output} ({len(rows)} candidates)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
