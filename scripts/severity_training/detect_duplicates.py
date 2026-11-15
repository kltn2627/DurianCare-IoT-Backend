from __future__ import annotations

from argparse import ArgumentParser
from collections import defaultdict
from pathlib import Path
import csv

from PIL import Image

from common import sha256_file, write_json


def main() -> int:
    parser = ArgumentParser(description="Detect exact and perceptual duplicate groups for severity candidates.")
    parser.add_argument("--candidates", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--groups-output", type=Path, default=None)
    parser.add_argument("--near-threshold", type=int, default=5)
    args = parser.parse_args()

    rows = _read_csv(args.candidates)
    exact: dict[str, list[dict[str, str]]] = defaultdict(list)
    phashes: list[tuple[str, str, int]] = []
    errors = []
    for row in rows:
        path = Path(row.get("path") or row.get("image_path") or "")
        try:
            exact[sha256_file(path)].append(row)
            phashes.append((row["image_id"], str(path), _dhash(path)))
        except Exception as exc:
            errors.append({"image_id": row.get("image_id", ""), "path": str(path), "error": str(exc)})

    exact_groups = [
        [{"image_id": item["image_id"], "path": item.get("path") or item.get("image_path", "")} for item in group]
        for group in exact.values()
        if len(group) > 1
    ]
    near_groups = _near_groups(phashes, args.near_threshold)
    groups_output = args.groups_output or args.output.with_name("duplicate_groups.csv")
    group_rows = _group_rows(rows, exact_groups, near_groups)
    payload = {
        "candidateFile": str(args.candidates),
        "candidateCount": len(rows),
        "exactDuplicateGroupCount": len(exact_groups),
        "nearDuplicateGroupCount": len(near_groups),
        "nearThreshold": args.near_threshold,
        "errorCount": len(errors),
        "groupsFile": str(groups_output),
        "exactDuplicateGroups": exact_groups[:100],
        "nearDuplicateGroups": near_groups[:100],
        "errors": errors[:100],
    }
    write_json(args.output, payload)
    _write_groups_csv(groups_output, group_rows)
    _write_markdown(args.output.with_suffix(".md"), payload)
    print(f"Wrote {args.output}")
    print(f"exact_groups={len(exact_groups)} near_groups={len(near_groups)} errors={len(errors)}")
    return 0 if not errors else 1


def _read_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def _dhash(path: Path) -> int:
    with Image.open(path) as image:
        image = image.convert("L").resize((9, 8))
        pixels = list(image.tobytes())
    bits = 0
    for y in range(8):
        for x in range(8):
            left = pixels[y * 9 + x]
            right = pixels[y * 9 + x + 1]
            bits = (bits << 1) | int(left > right)
    return bits


def _near_groups(items: list[tuple[str, str, int]], threshold: int) -> list[list[dict[str, object]]]:
    parent = list(range(len(items)))

    def find(index: int) -> int:
        while parent[index] != index:
            parent[index] = parent[parent[index]]
            index = parent[index]
        return index

    def union(a: int, b: int) -> None:
        root_a = find(a)
        root_b = find(b)
        if root_a != root_b:
            parent[root_b] = root_a

    for i in range(len(items)):
        for j in range(i + 1, len(items)):
            distance = (items[i][2] ^ items[j][2]).bit_count()
            if distance <= threshold:
                union(i, j)

    grouped: dict[int, list[dict[str, object]]] = defaultdict(list)
    for index, (image_id, path, hash_value) in enumerate(items):
        grouped[find(index)].append({"image_id": image_id, "path": path, "dhash": f"{hash_value:016x}"})
    return [group for group in grouped.values() if len(group) > 1]


def _group_rows(
    rows: list[dict[str, str]],
    exact_groups: list[list[dict[str, str]]],
    near_groups: list[list[dict[str, object]]],
) -> list[dict[str, str]]:
    exact_by_id = {}
    near_by_id = {}
    for index, group in enumerate(exact_groups, start=1):
        for item in group:
            exact_by_id[item["image_id"]] = f"exact-{index:04d}"
    for index, group in enumerate(near_groups, start=1):
        for item in group:
            near_by_id[str(item["image_id"])] = f"near-{index:04d}"
    output = []
    for row in sorted(rows, key=lambda item: item.get("image_id", "")):
        image_id = row.get("image_id", "")
        output.append(
            {
                "image_id": image_id,
                "path": row.get("path") or row.get("image_path", ""),
                "source_dataset": row.get("source_dataset") or "UNKNOWN",
                "source_split": row.get("source_split", ""),
                "same_source_group": _same_source_group(row),
                "tree_group_id": row.get("tree_group_id", ""),
                "session_id": row.get("session_id", ""),
                "exact_duplicate_group": exact_by_id.get(image_id, ""),
                "near_duplicate_group": near_by_id.get(image_id, ""),
                "needs_split_review": "yes" if near_by_id.get(image_id) and not (row.get("tree_group_id") or row.get("session_id")) else "",
            }
        )
    return output


def _same_source_group(row: dict[str, str]) -> str:
    source = row.get("source_dataset") or "UNKNOWN"
    split = row.get("source_split") or ""
    path = Path(row.get("path") or row.get("image_path") or "")
    stem = path.stem.split(".rf.")[0]
    return f"{source}:{split}:{stem}"


def _write_groups_csv(path: Path, rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fields = [
        "image_id",
        "path",
        "source_dataset",
        "source_split",
        "same_source_group",
        "tree_group_id",
        "session_id",
        "exact_duplicate_group",
        "near_duplicate_group",
        "needs_split_review",
    ]
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def _write_markdown(path: Path, payload: dict[str, object]) -> None:
    lines = [
        "# Duplicate Audit",
        "",
        f"Candidates: {payload['candidateCount']}",
        f"Exact duplicate groups: {payload['exactDuplicateGroupCount']}",
        f"Near duplicate groups: {payload['nearDuplicateGroupCount']} at dHash threshold {payload['nearThreshold']}",
        f"Errors: {payload['errorCount']}",
        f"Group manifest: `{payload['groupsFile']}`",
        "",
        "The group manifest separates exact duplicates, near duplicates, same-source grouping, and unknown tree/session metadata.",
        "Near duplicates must not be split across train/val/test.",
        "",
    ]
    path.write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    raise SystemExit(main())
