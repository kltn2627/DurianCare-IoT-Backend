from __future__ import annotations

from argparse import ArgumentParser
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import quote
import csv
import json
import shutil

import requests

from common import sha256_file, write_json


DATASETS = {
    "dataset_001_mobiusi_durian_leaf_rot_hf": {
        "kind": "hf_flat_files",
        "api_url": "https://huggingface.co/api/datasets/Mobiusi/Durian-Plantation-Disease-Leaf-Rot-Detection-Image-Dataset",
        "download_base": "https://huggingface.co/datasets/Mobiusi/Durian-Plantation-Disease-Leaf-Rot-Detection-Image-Dataset/resolve/main/",
        "source_url": "https://huggingface.co/datasets/Mobiusi/Durian-Plantation-Disease-Leaf-Rot-Detection-Image-Dataset",
        "license": "cc-by-nc-sa-4.0",
        "tier": "durian severity metadata, tiny sample",
    },
    "dataset_002_leafnet_leaf_disease_segmentation_hf": {
        "kind": "hf_selected_files",
        "api_url": "https://huggingface.co/api/datasets/LeafNet75/Leaf-Disease-Segmentation-masks",
        "download_base": "https://huggingface.co/datasets/LeafNet75/Leaf-Disease-Segmentation-masks/resolve/main/",
        "source_url": "https://huggingface.co/datasets/LeafNet75/Leaf-Disease-Segmentation-masks",
        "license": "apache-2.0",
        "tier": "other-species disease segmentation transfer only",
        "allow_suffixes": {".md", ".parquet"},
    },
    "dataset_003_zenodo_plant_leaves_segmentation_overview": {
        "kind": "direct_urls",
        "source_url": "https://zenodo.org/records/14707857",
        "license": "open dataset, verify Zenodo record before redistribution",
        "tier": "other-species leaf and spot segmentation reference",
        "files": {
            "dataset_overview.csv": "https://zenodo.org/records/14707857/files/dataset_overview.csv?download=1",
        },
        "large_files": {
            "dataset.zip": "https://zenodo.org/records/14707857/files/dataset.zip?download=1",
        },
    },
}


def main() -> int:
    parser = ArgumentParser(description="Download public severity-related datasets into immutable raw folders.")
    parser.add_argument("--dataset-root", type=Path, required=True)
    parser.add_argument("--include-large", action="store_true", help="Also download large optional archives.")
    parser.add_argument("--force", action="store_true", help="Replace existing downloaded files.")
    args = parser.parse_args()

    raw_root = args.dataset_root / "raw"
    reports_root = args.dataset_root / "reports"
    raw_root.mkdir(parents=True, exist_ok=True)
    reports_root.mkdir(parents=True, exist_ok=True)

    manifest: list[dict[str, object]] = []
    for dataset_id, config in DATASETS.items():
        target = raw_root / dataset_id
        target.mkdir(parents=True, exist_ok=True)
        if config["kind"] == "hf_flat_files":
            files = _hf_siblings(config)
            selected = [
                name
                for name in files
                if name == "README.md" or Path(name).suffix.lower() in {".jpg", ".jpeg", ".png", ".json"}
            ]
            downloaded = _download_named_files(selected, config["download_base"], target, args.force)
        elif config["kind"] == "hf_selected_files":
            files = _hf_siblings(config)
            allowed = config.get("allow_suffixes", set())
            selected = [name for name in files if name == "README.md" or Path(name).suffix.lower() in allowed]
            downloaded = _download_named_files(selected, config["download_base"], target, args.force)
        elif config["kind"] == "direct_urls":
            file_map = dict(config["files"])
            if args.include_large:
                file_map.update(config.get("large_files", {}))
            downloaded = _download_url_map(file_map, target, args.force)
        else:
            raise ValueError(f"Unsupported dataset kind: {config['kind']}")

        entry = {
            "dataset_id": dataset_id,
            "source_url": config["source_url"],
            "license": config["license"],
            "tier": config["tier"],
            "downloaded_at_utc": datetime.now(timezone.utc).isoformat(),
            "file_count": len(downloaded),
            "files": downloaded,
        }
        manifest.append(entry)
        (target / "SOURCE_MANIFEST.json").write_text(json.dumps(entry, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    write_json(reports_root / "download_manifest.json", manifest)
    _write_csv(reports_root / "download_manifest.csv", manifest)
    print(f"Wrote {reports_root / 'download_manifest.json'}")
    return 0


def _hf_siblings(config: dict[str, object]) -> list[str]:
    response = requests.get(str(config["api_url"]), timeout=60)
    response.raise_for_status()
    payload = response.json()
    return sorted(sibling["rfilename"] for sibling in payload.get("siblings", []) if "rfilename" in sibling)


def _download_named_files(files: list[str], base_url: str, target: Path, force: bool) -> list[dict[str, object]]:
    downloaded = []
    for name in files:
        url = base_url + quote(name)
        local_path = target / name
        downloaded.append(_download_one(url, local_path, force))
    return downloaded


def _download_url_map(file_map: dict[str, str], target: Path, force: bool) -> list[dict[str, object]]:
    return [_download_one(url, target / name, force) for name, url in sorted(file_map.items())]


def _download_one(url: str, local_path: Path, force: bool) -> dict[str, object]:
    local_path.parent.mkdir(parents=True, exist_ok=True)
    if force or not local_path.exists():
        with requests.get(url, stream=True, timeout=120) as response:
            response.raise_for_status()
            with local_path.open("wb") as handle:
                shutil.copyfileobj(response.raw, handle)
    return {
        "path": local_path.name,
        "bytes": local_path.stat().st_size,
        "sha256": sha256_file(local_path),
        "source_url": url,
    }


def _write_csv(path: Path, manifest: list[dict[str, object]]) -> None:
    rows = []
    for entry in manifest:
        rows.append(
            {
                "dataset_id": entry["dataset_id"],
                "source_url": entry["source_url"],
                "license": entry["license"],
                "tier": entry["tier"],
                "file_count": entry["file_count"],
                "downloaded_at_utc": entry["downloaded_at_utc"],
            }
        )
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)


if __name__ == "__main__":
    raise SystemExit(main())
