from __future__ import annotations

from argparse import ArgumentParser
from pathlib import Path
import json


def main() -> int:
    parser = ArgumentParser(description="Severity inference smoke-test placeholder.")
    parser.add_argument("--image", type=Path, required=True)
    parser.add_argument("--model-dir", type=Path, default=Path("duriancare-ai-service/models/severity"))
    args = parser.parse_args()

    metadata = args.model_dir / "metrics.json"
    if not metadata.exists():
        print(
            json.dumps(
                {
                    "severity": None,
                    "evidenceSource": "UNAVAILABLE",
                    "reason": "No validated severity model artifact exists yet.",
                },
                indent=2,
            )
        )
        return 0
    raise SystemExit("Validated model loading is not implemented in this research scaffold yet.")


if __name__ == "__main__":
    raise SystemExit(main())
