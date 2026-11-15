# DurianCare Severity / Progression Research Scaffold

This folder documents the future severity/progression model work.

Current scientific status: NOT TRAINABLE YET.

The existing local DLD dataset is classification-only. It has 6,002 images in folder format and no approved lesion masks, lesion boxes, affected-area ratios, or severity labels.

The isolated severity dataset now contains a tiny public seed source from Hugging Face with 5 durian leaf-rot images and source-provided image-level severity metadata. These records are staged as `NEEDS_EXPERT_REVIEW`, not `APPROVED`. The scripts in `scripts/severity_training/` therefore still refuse to train unless approved severity annotations exist.

Important rule: classifier confidence is not severity.

## Commands

```powershell
python scripts/severity_training/dataset_report.py --dataset-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training" --source-classification-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DLD_Dataset"
python scripts/severity_training/download_public_datasets.py --dataset-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training"
python scripts/severity_training/audit_raw_datasets.py --dataset-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training"
python scripts/severity_training/stage_mobiusi_seed.py --dataset-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training"
python scripts/severity_training/build_annotation_candidates.py --source-classification-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DLD_Dataset" --dataset-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training"
python scripts/severity_training/detect_duplicates.py --candidates "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training\metadata\severity_annotation_candidates.csv" --output "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training\reports\duplicate_audit.json" --groups-output "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training\reports\duplicate_groups.csv" --near-threshold 4
python scripts/severity_training/annotation_web.py --dataset-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training" --port 8765
python scripts/severity_training/validate_dataset.py --dataset-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training"
python scripts/severity_training/training_gate.py --dataset-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training"
python scripts/severity_training/train.py --dataset-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training"
```

Expected today: `train.py` exits without training because no approved severity annotations exist.

Use `annotation_cli.py` to create human review rows from `metadata/severity_annotation_candidates.csv`; keep uncertain rows pending until an expert approves them.
