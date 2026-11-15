# Dataset Sources

## Downloaded Sources

| ID | Source | Local status | License / access | Use |
|---|---|---|---|---|
| dataset_001 | Mobiusi Durian Plantation Disease Leaf Rot Detection Image Dataset | Downloaded: 5 JPG + JSON metadata | CC-BY-NC-SA-4.0 | Durian severity seed data, staged as `NEEDS_EXPERT_REVIEW` |
| dataset_002 | LeafNet75 Leaf Disease Segmentation Masks | Downloaded: 588 image-mask rows in Parquet | Apache-2.0 | Other-species segmentation transfer/reference only |
| dataset_003 | Zenodo Plant Leaves Image Segmentation Dataset | Downloaded: overview CSV only | Verify Zenodo record terms | Other-species leaf/spot segmentation reference |
| dataset_004 | DiaMOS Plant severity dataset | Downloaded: description PDF only | Verify Zenodo record terms | Other-species severity protocol reference |

## Not Downloaded Automatically

- Roboflow Durian Leaf Disease Semantic Seg: public page is visible, but export requires configured Roboflow/API access. Do not bypass.
- Kaggle Durian Leaf Disease Dataset: requires Kaggle access configuration. Do not bypass.
- DiaMOS `Pear.zip`: 13.1 GB archive; not downloaded automatically because it is too large for an unattended continuation.
- Zenodo `dataset.zip` for plant leaves segmentation: 890.6 MB; downloader supports `--include-large` if bandwidth/storage are approved.

## Local Candidate Source

`DLD_Dataset` contains 6,002 durian classification images. It is usable as human severity annotation input only. The disease class folder is not a severity label.
