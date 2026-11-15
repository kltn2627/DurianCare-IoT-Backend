# Existing AI Audit

## Frozen Baseline

- Leaf detector: `duriancare-ai-service/models/leaf_detector_best.pt`
- Detector SHA256: `ac2bd7f82f9fd1e054f496a90f21ce77eb9697b27a71a4ecdf6d7c47ac71408b`
- Disease classifier: `duriancare-ai-service/models/mobilenetv2_classifier_high_acc.pth`
- Classifier SHA256: `fc3dc5c92e43a9dbb196029fa56f9722ed91613d11ae7ccdadda1093fd1e5afe`

## Current Prediction Output

The current `/api/v1/predict` response includes:

- `predictedDisease`
- `confidence`
- `source`
- `deviceId`
- `usedDetectionCrop`
- `boundingBox`
- optional stored image info
- KB recommendation
- decision support
- `topPredictions`

Internally the classifier also computes:

- selected crop bbox
- detector confidence
- crop quality
- quality profile including brightness, contrast, blur, saturation, sharpness, green ratio, entropy, dimensions
- view weights / variants
- raw logits and softmax per crop in debug artifacts

## Not Present

- No lesion mask.
- No disease-lesion bounding boxes.
- No affected-area ratio.
- No validated severity score.
- No longitudinal progression model.

## Scientific Implication

The current model is a disease classifier with a generic leaf detector. It cannot be used as a validated severity/progression model without new annotations and validation.
