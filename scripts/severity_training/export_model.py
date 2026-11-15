from __future__ import annotations

from argparse import ArgumentParser
from pathlib import Path
import hashlib


def main() -> int:
    parser = ArgumentParser(description="Export a validated severity model and record hashes.")
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, default=Path("duriancare-ai-service/models/severity"))
    args = parser.parse_args()

    if not args.model.exists():
        raise SystemExit(f"Model file not found: {args.model}")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    target = args.output_dir / "best.pt"
    target.write_bytes(args.model.read_bytes())
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    (args.output_dir / "MODEL_HASHES.txt").write_text(f"{target.name}  {digest}\n", encoding="utf-8")
    print(f"Exported {target}")
    print(digest)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
