from __future__ import annotations

from argparse import ArgumentParser
from collections import Counter, defaultdict
from pathlib import Path
import json

from PIL import Image, UnidentifiedImageError

from common import IMAGE_EXTENSIONS, sha256_file, write_json

try:
    import polars as pl
except ImportError:  # pragma: no cover - optional local dependency
    pl = None


MASK_HINTS = ("mask", "seg", "annotation", "label")


def main() -> int:
    parser = ArgumentParser(description="Audit raw downloaded severity-related datasets.")
    parser.add_argument("--dataset-root", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, default=None)
    args = parser.parse_args()

    raw_root = args.dataset_root / "raw"
    output_dir = args.output_dir or args.dataset_root / "reports"
    output_dir.mkdir(parents=True, exist_ok=True)

    datasets = []
    for dataset_dir in sorted(path for path in raw_root.glob("dataset_*") if path.is_dir()):
        datasets.append(_audit_dataset(dataset_dir))

    payload = {
        "rawRoot": str(raw_root),
        "datasetCount": len(datasets),
        "datasets": datasets,
    }
    json_path = output_dir / "raw_dataset_audit.json"
    md_path = output_dir / "raw_dataset_audit.md"
    write_json(json_path, payload)
    md_path.write_text(_markdown(payload), encoding="utf-8")
    print(f"Wrote {json_path}")
    print(f"Wrote {md_path}")
    return 0


def _audit_dataset(dataset_dir: Path) -> dict[str, object]:
    files = sorted(path for path in dataset_dir.rglob("*") if path.is_file())
    image_files = [path for path in files if path.suffix.lower() in IMAGE_EXTENSIONS]
    annotation_files = [path for path in files if path.suffix.lower() in {".json", ".csv", ".txt", ".xml", ".yaml", ".yml"}]
    parquet_files = [path for path in files if path.suffix.lower() == ".parquet"]
    mask_like_files = [
        path
        for path in image_files
        if any(hint in path.stem.lower() or hint in path.parent.name.lower() for hint in MASK_HINTS)
    ]

    invalid_images = []
    format_counts: Counter[str] = Counter()
    dimensions: Counter[str] = Counter()
    hashes: dict[str, list[str]] = defaultdict(list)
    for image_path in image_files:
        rel = image_path.relative_to(dataset_dir).as_posix()
        try:
            with Image.open(image_path) as image:
                image.verify()
            with Image.open(image_path) as image:
                width, height = image.size
                dimensions[f"{width}x{height}"] += 1
                format_counts[image.format or image_path.suffix.lower()] += 1
            hashes[sha256_file(image_path)].append(rel)
        except (OSError, ValueError, UnidentifiedImageError) as exc:
            invalid_images.append({"path": rel, "error": str(exc)})

    source_manifest = _read_json(dataset_dir / "SOURCE_MANIFEST.json")
    metadata_samples = _metadata_samples(annotation_files, dataset_dir)
    duplicates = [paths for paths in hashes.values() if len(paths) > 1]
    return {
        "datasetId": dataset_dir.name,
        "fileCount": len(files),
        "imageCount": len(image_files),
        "annotationFileCount": len(annotation_files),
        "maskLikeImageCount": len(mask_like_files),
        "invalidImageCount": len(invalid_images),
        "duplicateImageGroupCount": len(duplicates),
        "extensionCounts": dict(Counter(path.suffix.lower() or "<none>" for path in files)),
        "formatCounts": dict(format_counts),
        "topDimensionCounts": dict(dimensions.most_common(20)),
        "sourceManifest": source_manifest,
        "metadataSamples": metadata_samples,
        "parquetFiles": _parquet_summaries(parquet_files, dataset_dir),
        "invalidImages": invalid_images[:100],
        "duplicateImageGroups": duplicates[:50],
    }


def _read_json(path: Path) -> object | None:
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {"error": "invalid json"}


def _metadata_samples(paths: list[Path], root: Path) -> list[dict[str, object]]:
    samples = []
    for path in paths[:20]:
        rel = path.relative_to(root).as_posix()
        item: dict[str, object] = {"path": rel, "bytes": path.stat().st_size}
        if path.suffix.lower() == ".json":
            parsed = _read_json(path)
            item["keys"] = sorted(parsed.keys()) if isinstance(parsed, dict) else type(parsed).__name__
            item["sample"] = parsed
        samples.append(item)
    return samples


def _parquet_summaries(paths: list[Path], root: Path) -> list[dict[str, object]]:
    summaries = []
    for path in paths:
        rel = path.relative_to(root).as_posix()
        item: dict[str, object] = {"path": rel, "bytes": path.stat().st_size}
        if pl is None:
            item["status"] = "not inspected; polars unavailable"
        else:
            try:
                scan = pl.scan_parquet(path)
                item["rowCount"] = scan.select(pl.len()).collect().item()
                item["schema"] = {name: str(dtype) for name, dtype in scan.collect_schema().items()}
            except Exception as exc:  # pragma: no cover - records audit failure details
                item["status"] = f"parquet inspection failed: {exc}"
        summaries.append(item)
    return summaries


def _markdown(payload: dict[str, object]) -> str:
    lines = [
        "# Raw Dataset Audit",
        "",
        f"Raw root: `{payload['rawRoot']}`",
        f"Dataset count: {payload['datasetCount']}",
        "",
        "| Dataset | Files | Images | Annotation files | Mask-like images | Invalid images | Duplicate groups |",
        "|---|---:|---:|---:|---:|---:|---:|",
    ]
    for dataset in payload["datasets"]:
        lines.append(
            "| {datasetId} | {fileCount} | {imageCount} | {annotationFileCount} | "
            "{maskLikeImageCount} | {invalidImageCount} | {duplicateImageGroupCount} |".format(**dataset)
        )
    lines.extend(
        [
            "",
            "## Notes",
            "",
            "- Raw audits only confirm file presence, readability, metadata shape, dimensions, and duplicates.",
            "- They do not convert disease class confidence into severity.",
            "- Human severity labels must remain pending until reviewed by the annotation workflow.",
            "",
        ]
    )
    return "\n".join(lines)


if __name__ == "__main__":
    raise SystemExit(main())
