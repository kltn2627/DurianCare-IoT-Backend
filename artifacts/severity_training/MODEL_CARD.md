# Model Card: DurianCare Severity / Progression Model

## Purpose

Provide additional disease burden evidence for longitudinal treatment follow-up.

## Current Artifact

No trained model artifact exists.

Expected future path:

`duriancare-ai-service/models/severity/`

Expected files after validation:

- `best.pt`
- `last.pt`
- `metrics.json`
- `training_config.yaml`
- `class_names.json`
- `README.md`
- `MODEL_HASHES.txt`

## Intended Use

Additive severity evidence after the frozen diagnosis classifier has identified a disease.

## Non-Intended Use

- Replacing the frozen diagnosis classifier.
- Treating classifier confidence as severity.
- Claiming clinical severity without validated expert annotations.
- Triggering treatment escalation when evidence is uncertain.

## Current Limitation

No approved severity dataset exists. The model is not trained and not integrated.

## Current Status

`BLOCKED BY HUMAN ANNOTATION`.

The local annotation UI and validation gates are ready, but no model artifact should exist until the training gate passes.
