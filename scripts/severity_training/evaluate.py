from __future__ import annotations

from argparse import ArgumentParser
from pathlib import Path

from common import APPROVED, read_annotation_csv


def main() -> int:
    parser = ArgumentParser(description="Guarded severity evaluation entrypoint.")
    parser.add_argument("--dataset-root", type=Path, required=True)
    parser.add_argument("--predictions", type=Path, required=False)
    args = parser.parse_args()

    approved = [
        record
        for record in read_annotation_csv(args.dataset_root / "metadata" / "annotations.csv")
        if record.review_status == APPROVED
    ]
    if not approved:
        raise SystemExit(
            "No APPROVED severity annotations found. Refusing to evaluate severity metrics without ground truth."
        )
    if not args.predictions or not args.predictions.exists():
        raise SystemExit("Provide a predictions file produced by a documented severity model experiment.")
    raise SystemExit("Evaluation implementation is pending model experiment format selection.")


if __name__ == "__main__":
    raise SystemExit(main())
