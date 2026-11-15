from __future__ import annotations

from argparse import ArgumentParser


def main() -> int:
    parser = ArgumentParser(description="Convert reviewed annotations to a training format.")
    parser.parse_args()
    raise SystemExit(
        "No conversion is implemented yet because no validated annotation type has been selected. "
        "Choose segmentation/detection/classification/regression after the annotation audit."
    )


if __name__ == "__main__":
    raise SystemExit(main())
