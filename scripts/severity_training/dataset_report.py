from __future__ import annotations

from argparse import ArgumentParser
from collections import Counter
from pathlib import Path

from common import APPROVED, iter_images, read_annotation_csv


def main() -> int:
    parser = ArgumentParser(description="Generate a severity dataset report.")
    parser.add_argument("--dataset-root", type=Path, required=True)
    parser.add_argument("--source-classification-root", type=Path, default=None)
    parser.add_argument("--output", type=Path, default=Path("artifacts/severity_training/DATASET_REPORT.md"))
    args = parser.parse_args()

    root = args.dataset_root
    annotations = read_annotation_csv(root / "metadata" / "annotations.csv")
    approved = [record for record in annotations if record.review_status == APPROVED]
    severity_counts = Counter(record.severity_label or "<none>" for record in approved)
    disease_counts = Counter(record.disease or "<none>" for record in approved)

    lines = [
        "# DurianCare Severity Dataset Report",
        "",
        "## Isolated severity dataset",
        f"- Root: `{root}`",
        f"- Images staged: {sum(1 for _ in iter_images(root / 'images'))}",
        f"- Annotation records: {len(annotations)}",
        f"- Approved annotation records: {len(approved)}",
        "",
        "## Approved disease counts",
    ]
    if disease_counts:
        lines.extend(f"- {name}: {count}" for name, count in disease_counts.items())
    else:
        lines.append("- None. No approved severity annotations are available yet.")

    lines.extend(["", "## Approved severity counts"])
    if severity_counts:
        lines.extend(f"- {name}: {count}" for name, count in severity_counts.items())
    else:
        lines.append("- None. No approved severity annotations are available yet.")

    if args.source_classification_root:
        source = args.source_classification_root
        lines.extend(["", "## Existing classification dataset audit"])
        for split in ("train", "valid", "test"):
            split_dir = source / split
            if not split_dir.exists():
                continue
            lines.append(f"### {split}")
            for class_dir in sorted([p for p in split_dir.iterdir() if p.is_dir()]):
                count = sum(1 for _ in iter_images(class_dir))
                lines.append(f"- {class_dir.name}: {count}")

    lines.extend(
        [
            "",
            "## Scientific status",
            "The current local dataset is classification-only unless approved severity annotations are added.",
            "Do not train a severity/progression model from classifier labels or classifier confidence.",
        ]
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
