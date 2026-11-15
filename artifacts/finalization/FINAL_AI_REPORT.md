# DurianCare-IoT Final AI Report

Date: 2026-10-11

## Frozen Models

| File | SHA256 |
| --- | --- |
| `leaf_detector_best.pt` | `AC2BD7F82F9FD1E054F496A90F21CE77EB9697B27A71A4ECDF6D7C47AC71408B` |
| `mobilenetv2_classifier_high_acc.pth` | `FC3DC5C92E43A9DBB196029FA56F9722ED91613D11AE7CCDADDA1093FD1E5AFE` |

The hashes match `artifacts/AI_FREEZE.md`.

## Source Verification

Python compilation of the AI service source passed.

## Severity Pipeline

Current severity-training status is `DO_NOT_TRAIN`.

Known dataset state:

- Staged severity images: 5
- DLD candidates: 6002
- Approved annotations: 0
- Exact duplicate groups: 0
- Near duplicate groups: 59
- Validation errors: 0

Training is correctly blocked until human annotation is complete and class minimums are met.

## Claim Boundary

The current system may claim disease detection/classification support according to the frozen AI documentation. It must not claim a trained severity model until the severity gate allows training and a real model is produced.
