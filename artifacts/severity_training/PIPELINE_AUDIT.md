# Pipeline Audit

Date: 2026-10-08

## Checked

- `scripts/severity_training/annotation_web.py`
- `scripts/severity_training/annotation_cli.py`
- `scripts/severity_training/training_gate.py`
- `scripts/severity_training/detect_duplicates.py`
- `scripts/severity_training/evaluate.py`
- all severity training scripts by Python compile
- `artifacts/severity_training/`
- `metadata/annotations.csv`
- `metadata/severity_annotation_candidates.csv`
- staged Mobiusi images
- raw downloaded datasets and raw audit
- validation summary
- duplicate audit
- training gate result
- inference placeholder
- frozen AI model hashes documented in existing audit

## Issues Found

- Annotation web UI did not expose source filtering, filename search, remaining counts, uncertain counts, or near-duplicate group context.
- Annotation web saves did not include update timestamps.
- Duplicate audit did not write a stable per-image duplicate/source group manifest for split leakage checks.
- Training gate only checked approved count and basic severity class count.
- Split creation used tree/session/image ID only and did not use duplicate or source grouping.
- No executable tests existed for the main scientific guardrails.
- Annotation guide had allowed labels but not enough inclusion/exclusion guidance for consistent provisional review.

## Issues Fixed

- Added source filter, search, remaining/approved/uncertain/rejected counts, near-duplicate display, and stricter approval checks to the web UI.
- Added `updated_at_utc` to new web annotations.
- Added `duplicate_groups.csv` with exact duplicate, near duplicate, same-source, tree/session, and split-review fields.
- Expanded training gate with machine-readable reason codes:
  - `PROTOCOL_NOT_FINALIZED`
  - `APPROVED_COUNT_TOO_LOW`
  - `CLASS_MINIMUM_TOO_LOW`
  - `INVALID_ANNOTATIONS`
  - `CORRUPTED_IMAGES`
  - `MISSING_IMAGE`
  - `INVALID_LABEL`
  - `SPLIT_MISSING`
  - `SPLIT_MISSING_APPROVED_ROWS`
  - `SPLIT_INCOMPLETE`
  - `DUPLICATE_LEAKAGE`
  - `NEAR_DUPLICATE_LEAKAGE`
  - `SOURCE_LEAKAGE`
  - `CLASS_IMBALANCE`
- Updated split creation to group by tree/session, exact duplicate, near duplicate, same-source group, or image ID.
- Added progression helper and unit tests for protocol, duplicate leakage, progression, and KB fallback guardrails.
- Expanded annotation guide while keeping status `NEEDS_EXPERT_SEVERITY_PROTOCOL`.

## Remaining Issues

- Approved annotations remain 0.
- Severity protocol is not expert-finalized.
- No train/validation/test split exists because the split guard refuses with no approved annotations.
- No severity model exists, and `infer.py` correctly returns `severity: null`.
- AI/backend integration remains intentionally inactive for severity.
