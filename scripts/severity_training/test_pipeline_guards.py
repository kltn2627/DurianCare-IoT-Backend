from __future__ import annotations

from pathlib import Path
from tempfile import TemporaryDirectory
import csv
import json
import subprocess
import sys
import unittest

from progression import evaluate_progression, treatment_action_for_progression


ROOT = Path(__file__).resolve().parents[2]
TRAINING_GATE = ROOT / "scripts" / "severity_training" / "training_gate.py"


class PipelineGuardTests(unittest.TestCase):
    def test_progression_requires_same_disease_and_protocol(self) -> None:
        self.assertEqual(
            evaluate_progression("LEAF_BLIGHT", "MODERATE", "LEAF_BLIGHT", "SEVERE", protocol_finalized=True),
            "WORSENED",
        )
        self.assertEqual(
            evaluate_progression("LEAF_BLIGHT", "SEVERE", "LEAF_BLIGHT", "MODERATE", protocol_finalized=True),
            "IMPROVED",
        )
        self.assertEqual(
            evaluate_progression("LEAF_BLIGHT", "MODERATE", "LEAF_BLIGHT", "MODERATE", protocol_finalized=True),
            "STABLE",
        )
        self.assertEqual(
            evaluate_progression("LEAF_BLIGHT", "MODERATE", "ALGAL_LEAF_SPOT", "SEVERE", protocol_finalized=True),
            "UNCERTAIN",
        )
        self.assertEqual(
            evaluate_progression("LEAF_BLIGHT", "MODERATE", "LEAF_BLIGHT", "SEVERE", protocol_finalized=False),
            "UNCERTAIN",
        )

    def test_kb_unavailable_falls_back_to_monitoring_only(self) -> None:
        self.assertEqual(treatment_action_for_progression("WORSENED", kb_available=False), "MONITORING_ONLY")

    def test_training_gate_blocks_no_approved_and_unfinalized_protocol(self) -> None:
        with TemporaryDirectory() as tmp:
            root = Path(tmp) / "dataset"
            artifacts = Path(tmp) / "artifacts"
            (root / "metadata").mkdir(parents=True)
            (root / "reports").mkdir()
            artifacts.mkdir()
            _write_annotations(root / "metadata" / "annotations.csv", [])
            (root / "reports" / "validation_summary.json").write_text(
                json.dumps({"annotationErrorCount": 0, "invalidImageCount": 0}),
                encoding="utf-8",
            )
            (artifacts / "ANNOTATION_GUIDE.md").write_text("NEEDS_EXPERT_SEVERITY_PROTOCOL", encoding="utf-8")
            result = subprocess.run(
                [
                    sys.executable,
                    str(TRAINING_GATE),
                    "--dataset-root",
                    str(root),
                    "--artifacts-root",
                    str(artifacts),
                ],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
            self.assertNotEqual(result.returncode, 0)
            payload = json.loads((root / "reports" / "training_gate.json").read_text(encoding="utf-8"))
            self.assertEqual(payload["status"], "DO_NOT_TRAIN")
            self.assertIn("PROTOCOL_NOT_FINALIZED", payload["reasonCodes"])
            self.assertIn("APPROVED_COUNT_TOO_LOW", payload["reasonCodes"])

    def test_training_gate_detects_duplicate_leakage(self) -> None:
        with TemporaryDirectory() as tmp:
            root = Path(tmp) / "dataset"
            artifacts = Path(tmp) / "artifacts"
            image_a = root / "images" / "a.jpg"
            image_b = root / "images" / "b.jpg"
            image_a.parent.mkdir(parents=True)
            image_a.write_bytes(b"a")
            image_b.write_bytes(b"b")
            (root / "metadata").mkdir()
            (root / "reports").mkdir()
            artifacts.mkdir()
            _write_annotations(
                root / "metadata" / "annotations.csv",
                [
                    ("a", str(image_a), "LEAF_BLIGHT", "MILD"),
                    ("b", str(image_b), "LEAF_BLIGHT", "MODERATE"),
                ],
            )
            _write_csv(root / "metadata" / "splits.csv", ["image_id", "split"], [["a", "train"], ["b", "test"]])
            _write_csv(
                root / "reports" / "duplicate_groups.csv",
                ["image_id", "near_duplicate_group", "same_source_group"],
                [["a", "near-0001", "src-a"], ["b", "near-0001", "src-b"]],
            )
            (root / "reports" / "validation_summary.json").write_text(
                json.dumps({"annotationErrorCount": 0, "invalidImageCount": 0}),
                encoding="utf-8",
            )
            (artifacts / "ANNOTATION_GUIDE.md").write_text("Protocol finalized for test.", encoding="utf-8")
            result = subprocess.run(
                [
                    sys.executable,
                    str(TRAINING_GATE),
                    "--dataset-root",
                    str(root),
                    "--artifacts-root",
                    str(artifacts),
                ],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
            self.assertNotEqual(result.returncode, 0)
            payload = json.loads((root / "reports" / "training_gate.json").read_text(encoding="utf-8"))
            self.assertIn("NEAR_DUPLICATE_LEAKAGE", payload["reasonCodes"])


def _write_annotations(path: Path, rows: list[tuple[str, str, str, str]]) -> None:
    fields = [
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
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        for image_id, image_path, disease, severity in rows:
            writer.writerow(
                {
                    "image_id": image_id,
                    "image_path": image_path,
                    "disease": disease,
                    "annotation_type": "test",
                    "annotation_source": "test",
                    "annotator": "test",
                    "review_status": "APPROVED",
                    "severity_label": severity,
                    "severity_score": "",
                    "affected_area_ratio": "",
                    "source_dataset": "test",
                    "tree_group_id": "",
                    "session_id": "",
                    "notes": "",
                }
            )


def _write_csv(path: Path, fields: list[str], rows: list[list[str]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(fields)
        writer.writerows(rows)


if __name__ == "__main__":
    unittest.main()
