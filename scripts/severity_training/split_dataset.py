from __future__ import annotations

from argparse import ArgumentParser
from collections import defaultdict
from pathlib import Path
import csv
import random

from common import APPROVED, read_annotation_csv


def main() -> int:
    parser = ArgumentParser(description="Group-aware train/val/test split for approved severity annotations.")
    parser.add_argument("--dataset-root", type=Path, required=True)
    parser.add_argument("--seed", type=int, default=20261007)
    parser.add_argument("--train-ratio", type=float, default=0.70)
    parser.add_argument("--val-ratio", type=float, default=0.15)
    parser.add_argument("--output", type=Path, default=None)
    parser.add_argument("--manifest-dir", type=Path, default=None)
    args = parser.parse_args()

    annotations = [
        record
        for record in read_annotation_csv(args.dataset_root / "metadata" / "annotations.csv")
        if record.review_status == APPROVED
    ]
    if not annotations:
        raise SystemExit("No APPROVED annotations found. Refusing to create a fake split.")

    duplicate_groups = _read_csv(args.dataset_root / "reports" / "duplicate_groups.csv")
    duplicate_by_image = {row["image_id"]: row for row in duplicate_groups if row.get("image_id")}
    groups: dict[str, list] = defaultdict(list)
    for record in annotations:
        duplicate = duplicate_by_image.get(record.image_id, {})
        group = (
            record.tree_group_id
            or record.session_id
            or duplicate.get("exact_duplicate_group")
            or duplicate.get("near_duplicate_group")
            or duplicate.get("same_source_group")
            or record.image_id
        )
        groups[group].append(record)

    group_ids = sorted(groups)
    random.Random(args.seed).shuffle(group_ids)
    train_cut = int(len(group_ids) * args.train_ratio)
    val_cut = train_cut + int(len(group_ids) * args.val_ratio)
    split_by_group = {}
    for index, group_id in enumerate(group_ids):
        if index < train_cut:
            split_by_group[group_id] = "train"
        elif index < val_cut:
            split_by_group[group_id] = "val"
        else:
            split_by_group[group_id] = "test"

    output = args.output or args.dataset_root / "metadata" / "splits.csv"
    output.parent.mkdir(parents=True, exist_ok=True)
    rows = []
    with output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=["image_id", "image_path", "split", "group_id", "disease", "severity_label", "source_dataset"],
        )
        writer.writeheader()
        for record in annotations:
            duplicate = duplicate_by_image.get(record.image_id, {})
            group = (
                record.tree_group_id
                or record.session_id
                or duplicate.get("exact_duplicate_group")
                or duplicate.get("near_duplicate_group")
                or duplicate.get("same_source_group")
                or record.image_id
            )
            row = {
                "image_id": record.image_id,
                "image_path": record.image_path,
                "split": split_by_group[group],
                "group_id": group,
                "disease": record.disease,
                "severity_label": record.severity_label or "",
                "source_dataset": record.source_dataset,
            }
            writer.writerow(row)
            rows.append(row)
    manifest_dir = args.manifest_dir or Path("artifacts/severity_training/manifests")
    _write_manifests(manifest_dir, rows)
    print(f"Wrote {output}")
    print(f"Wrote split manifests to {manifest_dir}")
    return 0


def _read_csv(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        return []
    with path.open("r", encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def _write_manifests(manifest_dir: Path, rows: list[dict[str, str]]) -> None:
    manifest_dir.mkdir(parents=True, exist_ok=True)
    for split in ("train", "val", "test"):
        split_rows = [row for row in rows if row["split"] == split]
        with (manifest_dir / f"{split}.csv").open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(
                handle,
                fieldnames=["image_id", "image_path", "split", "group_id", "disease", "severity_label", "source_dataset"],
            )
            writer.writeheader()
            writer.writerows(split_rows)


if __name__ == "__main__":
    raise SystemExit(main())
