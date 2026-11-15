from __future__ import annotations

from argparse import ArgumentParser
from pathlib import Path
import shutil

from common import iter_images


def main() -> int:
    parser = ArgumentParser(description="Stage licensed local images into the isolated severity dataset.")
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--dataset-root", type=Path, required=True)
    parser.add_argument("--copy", action="store_true", help="Copy images instead of only printing the plan.")
    args = parser.parse_args()

    images = sorted(iter_images(args.source_root))
    target_dir = args.dataset_root / "images" / args.source_root.name
    print(f"Source images: {len(images)}")
    print(f"Target: {target_dir}")
    if not args.copy:
        print("Dry run only. Re-run with --copy after license review.")
        return 0
    target_dir.mkdir(parents=True, exist_ok=True)
    for image in images:
        relative = image.relative_to(args.source_root)
        target = target_dir / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(image, target)
    print(f"Copied {len(images)} images")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
