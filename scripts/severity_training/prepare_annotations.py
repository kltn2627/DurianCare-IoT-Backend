from __future__ import annotations

from argparse import ArgumentParser
from pathlib import Path
import csv

from common import iter_images


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


def main() -> int:
    parser = ArgumentParser(description="Create a reviewable annotation CSV template.")
    parser.add_argument("--dataset-root", type=Path, required=True)
    parser.add_argument("--source-dataset", default="UNKNOWN")
    parser.add_argument("--output", type=Path, default=None)
    args = parser.parse_args()

    output = args.output or args.dataset_root / "metadata" / "annotations.csv"
    output.parent.mkdir(parents=True, exist_ok=True)
    rows = []
    for index, image_path in enumerate(sorted(iter_images(args.dataset_root / "images")), start=1):
        rel = image_path.relative_to(args.dataset_root).as_posix()
        rows.append(
            {
                "image_id": f"sev-{index:06d}",
                "image_path": rel,
                "disease": "",
                "annotation_type": "",
                "annotation_source": "MANUAL_REVIEW_REQUIRED",
                "annotator": "",
                "review_status": "PENDING",
                "severity_label": "",
                "severity_score": "",
                "affected_area_ratio": "",
                "source_dataset": args.source_dataset,
                "tree_group_id": "",
                "session_id": "",
                "notes": "",
            }
        )
    with output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote {output} ({len(rows)} rows)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
