from __future__ import annotations

from argparse import ArgumentParser
from pathlib import Path

from common import APPROVED, read_annotation_csv


def main() -> int:
    parser = ArgumentParser(description="Guarded severity training entrypoint.")
    parser.add_argument("--dataset-root", type=Path, required=True)
    parser.add_argument("--model-dir", type=Path, default=Path("duriancare-ai-service/models/severity"))
    args = parser.parse_args()

    approved = [
        record
        for record in read_annotation_csv(args.dataset_root / "metadata" / "annotations.csv")
        if record.review_status == APPROVED
    ]
    if not approved:
        raise SystemExit(
            "No APPROVED severity annotations found. Refusing to train. "
            "Create/review real annotations first; confidence is not severity."
        )

    raise SystemExit(
        "Training implementation is intentionally pending model selection after annotation audit. "
        f"Approved records available: {len(approved)}. Write an experiment config before training."
    )


if __name__ == "__main__":
    raise SystemExit(main())
