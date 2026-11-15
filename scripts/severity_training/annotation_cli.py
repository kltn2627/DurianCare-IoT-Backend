from __future__ import annotations

from argparse import ArgumentParser
from datetime import datetime, timezone
from pathlib import Path
import csv
import subprocess
import sys


LABELS = {"NONE", "MILD", "MODERATE", "SEVERE", "UNCERTAIN", "SKIP", "QUIT"}
OUTPUT_FIELDS = [
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
    parser = ArgumentParser(description="Conservative CLI workflow for human severity annotation.")
    parser.add_argument("--candidates", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--annotator", required=True)
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--open-images", action="store_true", help="Open each image with the OS default viewer.")
    args = parser.parse_args()

    candidates = _read_csv(args.candidates)
    existing = _read_existing(args.output)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    session_id = datetime.now(timezone.utc).strftime("manual-%Y%m%dT%H%M%SZ")

    annotated = 0
    with args.output.open("a", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=OUTPUT_FIELDS)
        if handle.tell() == 0:
            writer.writeheader()
        for row in candidates:
            if row["image_id"] in existing:
                continue
            if args.limit and annotated >= args.limit:
                break
            image_path = Path(row["image_path"])
            print(f"\n{row['image_id']} | {row['disease']} | {image_path}")
            if args.open_images:
                _open_image(image_path)
            label = _prompt("Severity [NONE/MILD/MODERATE/SEVERE/UNCERTAIN/SKIP/QUIT]: ").upper()
            if label not in LABELS:
                print("Invalid label; marking as UNCERTAIN.")
                label = "UNCERTAIN"
            if label == "QUIT":
                break
            if label == "SKIP":
                continue
            affected_area_ratio = _prompt("Affected area ratio 0..1 (blank if not measured): ")
            notes = _prompt("Notes (blank ok): ")
            review_status = "NEEDS_EXPERT_REVIEW" if label != "UNCERTAIN" else "PENDING"
            writer.writerow(
                {
                    "image_id": row["image_id"],
                    "image_path": row["image_path"],
                    "disease": row["disease"],
                    "annotation_type": "HUMAN_VISUAL_SEVERITY",
                    "annotation_source": "MANUAL_CLI",
                    "annotator": args.annotator,
                    "review_status": review_status,
                    "severity_label": label,
                    "severity_score": "",
                    "affected_area_ratio": affected_area_ratio,
                    "source_dataset": row.get("source", "UNKNOWN"),
                    "tree_group_id": "",
                    "session_id": session_id,
                    "notes": notes,
                }
            )
            annotated += 1
    print(f"Annotated {annotated} rows into {args.output}")
    return 0


def _read_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def _read_existing(path: Path) -> set[str]:
    if not path.exists():
        return set()
    return {row["image_id"] for row in _read_csv(path) if row.get("image_id")}


def _prompt(message: str) -> str:
    return input(message).strip()


def _open_image(path: Path) -> None:
    if sys.platform.startswith("win"):
        subprocess.Popen(["powershell", "-NoProfile", "-Command", "Start-Process", "-LiteralPath", str(path)])
    elif sys.platform == "darwin":
        subprocess.Popen(["open", str(path)])
    else:
        subprocess.Popen(["xdg-open", str(path)])


if __name__ == "__main__":
    raise SystemExit(main())
