# Reproducibility

## Baseline AI Audit

```powershell
python -c "from pathlib import Path; import hashlib; paths=[Path('duriancare-ai-service/models/leaf_detector_best.pt'),Path('duriancare-ai-service/models/mobilenetv2_classifier_high_acc.pth')]; [print(f'{p.name},{p.stat().st_size},{hashlib.sha256(p.read_bytes()).hexdigest()}') for p in paths]"
```

## Dataset Audit

```powershell
Get-ChildItem "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DLD_Dataset" -Recurse -File | Group-Object Extension
```

## Public Dataset Download And Audit

```powershell
python scripts/severity_training/download_public_datasets.py --dataset-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training"
python scripts/severity_training/audit_raw_datasets.py --dataset-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training"
```

## Annotation Candidates And Duplicate Audit

```powershell
python scripts/severity_training/build_annotation_candidates.py --source-classification-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DLD_Dataset" --dataset-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training"
python scripts/severity_training/detect_duplicates.py --candidates "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training\metadata\severity_annotation_candidates.csv" --output "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training\reports\duplicate_audit.json" --near-threshold 4
```

## Local Annotation UI

```powershell
python scripts/severity_training/annotation_web.py --dataset-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training" --port 8765
```

## Isolated Dataset Validation

```powershell
python scripts/severity_training/validate_dataset.py --dataset-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training"
```

## Training Gate

```powershell
python scripts/severity_training/training_gate.py --dataset-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training"
```

## Generate Dataset Report

```powershell
python scripts/severity_training/dataset_report.py --dataset-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training" --source-classification-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DLD_Dataset"
```

## Guarded Training

```powershell
python scripts/severity_training/train.py --dataset-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training"
```

Current expected result: refusal to train because no approved severity labels exist.
