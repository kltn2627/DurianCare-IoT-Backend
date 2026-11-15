# Annotation Guide

Status: `NEEDS_EXPERT_SEVERITY_PROTOCOL`.

The local tooling supports provisional image-level severity review, but the clinical severity scale must be confirmed by a domain expert before labels are marked `APPROVED`.

This guide intentionally defines operational labels for review consistency, not a validated clinical standard. Until an agronomy/pathology reviewer signs off, all saved labels must remain `NEEDS_REVIEW`, `NEEDS_EXPERT_REVIEW`, `UNCERTAIN`, or `REJECTED`.

## Allowed Labels

- `NONE`
- `MILD`
- `MODERATE`
- `SEVERE`
- `UNCERTAIN`

## Provisional Technical Label Definitions

- `NONE`: the leaf appears healthy for the disease being reviewed, with no visible lesion burden attributable to that disease.
- `MILD`: visible symptoms are present but limited, localized, and do not dominate the visible leaf area.
- `MODERATE`: symptoms are clearly present across a meaningful portion of the visible leaf area, but the leaf is not dominated by severe necrosis or collapse.
- `SEVERE`: symptoms dominate the visible leaf area, with extensive necrosis, blight, rot, or tissue damage.
- `UNCERTAIN`: the reviewer cannot assign a reliable label because the image is ambiguous, low quality, multi-leaf, occluded, inconsistent with the disease class, or outside the protocol.

These definitions are not enough to unlock `APPROVED`; they are a provisional labeling aid.

## Inclusion Rules

- The image must show durian leaf material clearly enough for review.
- The disease class shown in the candidate metadata must be used only as disease context, not as severity.
- A severity label should reflect visible symptom burden, not classifier confidence.
- If multiple leaves are visible and severity differs, use `UNCERTAIN` unless an expert protocol defines how to aggregate.

## Exclusion Rules

- Reject images that are corrupted, missing, non-leaf, unrelated to durian, or impossible to review.
- Use `UNCERTAIN` rather than guessing when symptoms are unclear, lighting is misleading, or disease identity conflicts with visible evidence.
- Do not infer severity from folder name, disease name, confidence score, filename, or dataset split.

## Examples

- `HEALTHY_LEAF` folder image with no visible symptoms: provisional `NONE`, pending review.
- `LEAF_BLIGHT` image with a few small localized lesions: provisional `MILD`, pending review.
- `LEAF_BLIGHT` image with large visible blighted regions but remaining healthy tissue: provisional `MODERATE`, pending review.
- `LEAF_BLIGHT` image dominated by necrotic or collapsed tissue: provisional `SEVERE`, pending review.
- Cropped, blurry, multi-leaf, or contradictory image: `UNCERTAIN` or `REJECTED`.

## Review Statuses

- `PENDING`: not reviewed.
- `NEEDS_REVIEW`: provisional human label, not training ground truth.
- `NEEDS_EXPERT_REVIEW`: source or reviewer label requires expert confirmation.
- `UNCERTAIN`: reviewer cannot assign a reliable severity label.
- `REJECTED`: unsuitable image or annotation.
- `APPROVED`: expert-approved training ground truth.

Only `APPROVED` records may enter train/validation/test splits.

## Local Web UI

```powershell
python scripts/severity_training/annotation_web.py --dataset-root "C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DataSet\Durian_Severity_Training" --port 8765
```

Open:

```text
http://127.0.0.1:8765
```

The UI shows the image, disease class, annotation progress, filters, severity controls, notes, previous/next navigation, and keyboard shortcuts.

`APPROVED` writes are disabled by default. Start with `--enable-approve` only after the expert protocol is finalized.

## Keyboard Shortcuts

- `1`: NONE
- `2`: MILD
- `3`: MODERATE
- `4`: SEVERE
- `U`: UNCERTAIN
- `R`: REJECT
- `S`: save
- `N`: next
- `P`: previous
- `A`: approve, only when server starts with `--enable-approve`

## Quality Rules

Reject or mark uncertain when the image is corrupted, blurry, not a durian leaf, contains multiple ambiguous leaves, has no visible disease evidence, or cannot be mapped to the protocol.

Near-duplicate groups must stay in the same dataset split.
