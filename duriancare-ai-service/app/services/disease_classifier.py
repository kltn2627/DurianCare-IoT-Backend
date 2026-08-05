from __future__ import annotations

from collections import OrderedDict
from dataclasses import dataclass
import json
import math
import os
from pathlib import Path
from typing import Any, Mapping

try:
    import torch
    from PIL import Image
    from torch import nn
    from torchvision import models, transforms
    from ultralytics import YOLO
except ImportError:  # pragma: no cover - optional runtime dependency
    torch = None
    Image = None
    nn = None
    models = None
    transforms = None
    YOLO = None

from app.core.config import Settings
from app.services.image_enhancement import (
    analyze_image_quality,
    build_classifier_views,
    resize_for_classifier,
)
from app.services.leaf_pipeline import (
    AdaptiveLeafPipeline,
    CropCandidate,
    DetectionRun,
    ImageVariant,
)

CLASS_LABELS = (
    "ALGAL_LEAF_SPOT",
    "ALLOCARIDARA_ATTACK",
    "HEALTHY_LEAF",
    "LEAF_BLIGHT",
    "PHOMOPSIS_LEAF_SPOT",
)


class PredictionError(RuntimeError):
    def __init__(
        self,
        message: str,
        *,
        reason: str = "unknown",
        status_code: int = 422,
    ) -> None:
        super().__init__(message)
        self.reason = reason
        self.status_code = status_code


@dataclass(frozen=True)
class DiseasePrediction:
    label: str
    confidence: float
    used_detection_crop: bool
    bounding_box: tuple[int, int, int, int] | None
    top_predictions: list[dict[str, float | str]]
    low_confidence: bool = False


class DoubleModelDiseaseClassifier:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self.device = None
        self.device_name = "cpu"
        if torch is not None:
            self.device_name = "cuda" if torch.cuda.is_available() else "cpu"
            self.device = torch.device(self.device_name)
        self.detector: YOLO | None = None
        self.fallback_detector: YOLO | None = None
        self.classifier: nn.Module | None = None
        self.transform = None
        self.temperature = max(
            0.5,
            float(os.getenv("AI_TEMPERATURE", "1.0")),
        )
        self.min_prediction_confidence = max(
            0.0,
            min(1.0, float(os.getenv("AI_MIN_PREDICTION_CONFIDENCE", "0.0"))),
        )
        self.pipeline = AdaptiveLeafPipeline(
            debug_root=Path(os.getenv("AI_DEBUG_ROOT", "artifacts/debug_runs")),
            capture_hard_examples=os.getenv(
                "AI_CAPTURE_HARD_EXAMPLES",
                "true",
            ).lower()
            in {"1", "true", "yes"},
        )
        if transforms is not None:
            self.transform = transforms.Compose(
                [
                    transforms.Lambda(
                        lambda img: resize_for_classifier(img, (224, 224))
                    ),
                    transforms.ToTensor(),
                    transforms.Normalize(
                        mean=[0.485, 0.456, 0.406],
                        std=[0.229, 0.224, 0.225],
                    ),
                ]
            )

    def load_models(self) -> None:
        if any(dep is None for dep in (torch, Image, nn, models, transforms, YOLO)):
            raise RuntimeError(
                "AI runtime dependencies are not installed. Install torch, "
                "torchvision, ultralytics, and pillow."
            )
        try:
            if self.settings.yolo_crop_enabled:
                self.detector = YOLO(self.settings.yolo_model)
                self.detector.to(self.device)
                self._load_fallback_detector()
            self.classifier = self._load_classifier(
                self.settings.classifier_weights,
            )
        except Exception as exception:
            raise RuntimeError(
                f"Unable to load double-model pipeline: {exception}"
            ) from exception

    def predict(self, image: Image.Image) -> DiseasePrediction:
        if self.classifier is None:
            raise RuntimeError("MobileNetV2 classifier has not been loaded")
        if torch is None or self.transform is None:
            raise RuntimeError("AI runtime dependencies are not installed")
        if self.detector is None and self.settings.yolo_crop_enabled:
            raise RuntimeError("YOLO detector has not been loaded")

        try:
            detection_run = self._run_detection_pipeline(image)
            if not detection_run.selected_crops:
                self._write_success_or_failure_debug(
                    detection_run=detection_run,
                    prediction=None,
                    crop_predictions=[],
                    failure_stage=detection_run.failure_stage or "detector",
                    failure_reason=detection_run.failure_reason or "detector_no_bbox",
                )
                raise PredictionError(
                    "No durian leaf detected after adaptive preprocessing",
                    reason=detection_run.failure_reason or "detector_no_bbox",
                    status_code=422,
                )

            crop_predictions = self._classify_crops(detection_run.selected_crops)
            final_label, final_confidence, final_probabilities, top_predictions = self._ensemble_predictions(
                crop_predictions,
            )

            confidence_is_low = final_confidence < self.min_prediction_confidence
            self._write_success_or_failure_debug(
                detection_run=detection_run,
                prediction={
                    "label": final_label,
                    "confidence": final_confidence,
                    "probabilities": final_probabilities,
                    "topPredictions": top_predictions,
                    "lowConfidence": confidence_is_low,
                },
                crop_predictions=crop_predictions,
                failure_stage=None,
                failure_reason=None,
            )
            return DiseasePrediction(
                label=final_label,
                confidence=float(final_confidence * 100.0),
                used_detection_crop=True,
                bounding_box=detection_run.selected_crops[0].bbox,
                top_predictions=top_predictions,
                low_confidence=confidence_is_low,
            )
        except PredictionError:
            raise
        except Exception as exception:
            raise PredictionError(
                f"Double-model inference failed: {exception}",
                reason="unknown",
                status_code=500,
            ) from exception

    def _run_detection_pipeline(self, image: Image.Image) -> DetectionRun:
        if self.detector is None:
            return DetectionRun(
                variants=[],
                attempts=[],
                detections=[],
                selected_crops=[],
                failure_stage="detector",
                failure_reason="detector_unavailable",
                debug_dir=None,
            )
        if self.pipeline.debug_enabled:
            primary_run = self.pipeline.run_detection_search(
                image=image,
                detector=self.detector,
                device_name=self.device_name,
                model_name=self.settings.yolo_model,
                weights_path=str(self.settings.yolo_model),
                base_confidence=self.settings.yolo_confidence,
            )
        else:
            primary_run = self._run_single_detector_pass(
                image=image,
                detector=self.detector,
                model_name=self.settings.yolo_model,
                weights_path=str(self.settings.yolo_model),
                confidence_threshold=self.settings.yolo_confidence,
            )
        if primary_run.selected_crops:
            return primary_run

        fallback_detector = self._load_fallback_detector()
        if fallback_detector is None:
            return primary_run

        if self.pipeline.debug_enabled:
            fallback_run = self.pipeline.run_detection_search(
                image=image,
                detector=fallback_detector,
                device_name=self.device_name,
                model_name=self.settings.yolo_fallback_model,
                weights_path=str(self.settings.yolo_fallback_model),
                base_confidence=self.settings.yolo_confidence,
            )
        else:
            fallback_run = self._run_single_detector_pass(
                image=image,
                detector=fallback_detector,
                model_name=self.settings.yolo_fallback_model,
                weights_path=str(self.settings.yolo_fallback_model),
                confidence_threshold=self.settings.yolo_confidence,
            )
        if fallback_run.selected_crops:
            return fallback_run
        if len(fallback_run.detections) > len(primary_run.detections):
            return fallback_run

        rescue_detector = fallback_detector or self.detector
        if rescue_detector is not None:
            rescue_model_name = (
                self.settings.yolo_fallback_model
                if fallback_detector is not None
                else self.settings.yolo_model
            )
            rescue_weights_path = (
                str(self.settings.yolo_fallback_model)
                if fallback_detector is not None
                else str(self.settings.yolo_model)
            )
            rescue_run = self.pipeline.run_detection_search(
                image=image,
                detector=rescue_detector,
                device_name=self.device_name,
                model_name=rescue_model_name,
                weights_path=rescue_weights_path,
                base_confidence=self.settings.yolo_confidence,
            )
            if rescue_run.selected_crops:
                return rescue_run
            if len(rescue_run.detections) > len(fallback_run.detections):
                return rescue_run
        return primary_run

    def _run_single_detector_pass(
        self,
        *,
        image: Image.Image,
        detector: YOLO,
        model_name: str,
        weights_path: str,
        confidence_threshold: float,
    ) -> DetectionRun:
        variant = ImageVariant(name="raw", image=image, scale=1.0, metrics={})
        attempt, detections = self.pipeline._run_detector(
            image=image,
            variant=variant,
            detector=detector,
            device_name=self.device_name,
            confidence_threshold=confidence_threshold,
            iou_threshold=0.55,
        )
        deduplicated_detections = self.pipeline._deduplicate_detections(detections)
        selected_crops = self.pipeline._select_crops(image, deduplicated_detections)
        failure_stage = None
        failure_reason = None
        if not deduplicated_detections:
            failure_stage = "detector"
            failure_reason = "detector_no_bbox"
        elif not selected_crops:
            failure_stage = "validator"
            failure_reason = "crop_quality_issue"

        debug_dir = None
        if self.pipeline.debug_enabled:
            debug_dir = self.pipeline._maybe_create_debug_dir()
            if debug_dir is not None:
                self.pipeline._write_debug_artifacts(
                    debug_dir=debug_dir,
                    original_image=image,
                    attempts=[attempt],
                    selected_crops=selected_crops,
                    failure_stage=failure_stage,
                    failure_reason=failure_reason,
                    detector_model=model_name,
                    detector_weights=weights_path,
                    device_name=self.device_name,
                )

        return DetectionRun(
            variants=[variant],
            attempts=[attempt],
            detections=deduplicated_detections,
            selected_crops=selected_crops,
            failure_stage=failure_stage,
            failure_reason=failure_reason,
            debug_dir=debug_dir,
        )

    def _load_fallback_detector(self) -> YOLO | None:
        if self.fallback_detector is not None:
            return self.fallback_detector
        if not self.settings.yolo_crop_enabled:
            return None
        if self.settings.yolo_fallback_model == self.settings.yolo_model:
            return None
        try:
            fallback_detector = YOLO(self.settings.yolo_fallback_model)
            fallback_detector.to(self.device)
        except Exception:
            return None
        self.fallback_detector = fallback_detector
        return self.fallback_detector

    def _classify_crops(
        self,
        crop_candidates: list[CropCandidate],
    ) -> list[dict[str, Any]]:
        assert self.classifier is not None
        assert torch is not None

        records: list[dict[str, Any]] = []
        for candidate in crop_candidates:
            quality = analyze_image_quality(candidate.image)
            view_variants = build_classifier_views(
                candidate.image,
                mode=os.getenv("AI_PIPELINE_MODE", "balanced").lower(),
                max_views=4,
            )
            view_tensors = [
                self.transform(variant.image).to(self.device)
                for variant in view_variants
            ]
            if not view_tensors:
                view_tensors = [self.transform(candidate.image).to(self.device)]
            view_batch = torch.stack(view_tensors, dim=0)
            with torch.inference_mode():
                view_logits = self.classifier(view_batch)
                scaled_logits = view_logits / self.temperature
                view_probabilities = torch.softmax(scaled_logits, dim=1)

            quality_weights = []
            for variant in view_variants:
                view_quality = float(getattr(variant, "score", 1.0))
                if (
                    quality.brightness < 0.35
                    or quality.contrast < 0.10
                    or quality.blur < 35.0
                    or quality.saturation < 0.20
                ):
                    view_quality *= 1.10
                quality_weights.append(max(0.05, view_quality))

            view_weights = torch.tensor(
                quality_weights or [1.0],
                dtype=torch.float32,
                device=self.device,
            )

            view_weights = view_weights / view_weights.sum().clamp_min(1e-6)
            probability_vector = (
                view_probabilities * view_weights.unsqueeze(1)
            ).sum(dim=0)
            logits_vector = (
                view_logits * view_weights.unsqueeze(1)
            ).sum(dim=0)
            confidence, predicted_index = probability_vector.max(dim=0)
            top_values, top_indexes = torch.topk(
                probability_vector,
                k=min(5, len(CLASS_LABELS)),
            )
            records.append(
                {
                    "bbox": list(candidate.bbox),
                    "detectorConfidence": candidate.detection.confidence,
                    "cropQuality": candidate.quality_score,
                    "qualityProfile": {
                        "brightness": quality.brightness,
                        "contrast": quality.contrast,
                        "blur": quality.blur,
                        "saturation": quality.saturation,
                        "sharpness": quality.sharpness,
                        "greenRatio": quality.green_ratio,
                        "entropy": quality.entropy,
                        "dynamicRange": quality.dynamic_range,
                        "width": quality.width,
                        "height": quality.height,
                    },
                    "viewWeights": {
                        view_variants[index].name: float(view_weights[index].item())
                        for index in range(len(view_variants))
                    },
                    "viewVariants": [
                        {
                            "name": getattr(variant, "name", "unknown"),
                            "score": float(getattr(variant, "score", 1.0)),
                            "metrics": dict(getattr(variant, "metrics", {})),
                        }
                        for variant in view_variants
                    ],
                    "weight": self._crop_weight(candidate),
                    "predictedIndex": int(predicted_index.item()),
                    "predictedLabel": CLASS_LABELS[int(predicted_index.item())],
                    "confidence": float(confidence.item()),
                    "top5": [
                        {
                            "class": CLASS_LABELS[int(top_indexes[i].item())],
                            "prob": float(top_values[i].item()),
                        }
                        for i in range(len(top_values))
                    ],
                    "rawLogits": [float(value) for value in logits_vector.tolist()],
                    "softmax": [float(value) for value in probability_vector.tolist()],
                }
            )
        return records

    def _ensemble_predictions(
        self,
        crop_predictions: list[dict[str, Any]],
    ) -> tuple[str, float, list[float], list[dict[str, float | str]]]:
        assert torch is not None

        if not crop_predictions:
            raise PredictionError(
                "No valid crop predictions were produced",
                reason="crop_quality_issue",
                status_code=422,
            )

        probability_stack = torch.tensor(
            [record["softmax"] for record in crop_predictions],
            dtype=torch.float32,
        )
        base_weights = torch.tensor(
            [max(0.05, float(record["weight"])) for record in crop_predictions],
            dtype=torch.float32,
        )
        entropy_scores = []
        for record in crop_predictions:
            probabilities = torch.tensor(record["softmax"], dtype=torch.float32)
            entropy_scores.append(float(self._normalized_entropy(probabilities)))
        entropy_tensor = torch.tensor(
            [max(0.05, 1.0 - score) for score in entropy_scores],
            dtype=torch.float32,
        )
        normalized_weights = base_weights * entropy_tensor
        normalized_weights = normalized_weights / normalized_weights.sum().clamp_min(1e-6)
        ensemble_probabilities = (
            probability_stack * normalized_weights.unsqueeze(1)
        ).sum(dim=0)
        final_confidence, final_index = ensemble_probabilities.max(dim=0)
        top_values, top_indexes = torch.topk(
            ensemble_probabilities,
            k=min(3, len(CLASS_LABELS)),
        )
        top_predictions = [
            {
                "label": CLASS_LABELS[int(top_indexes[i].item())],
                "confidence": float(top_values[i].item() * 100.0),
            }
            for i in range(len(top_values))
        ]
        return (
            CLASS_LABELS[int(final_index.item())],
            float(final_confidence.item()),
            [float(value) for value in ensemble_probabilities.tolist()],
            top_predictions,
        )

    def _crop_weight(self, candidate: CropCandidate) -> float:
        return max(
            0.05,
            min(
                1.0,
                (
                    candidate.detection.confidence * 0.42
                    + candidate.quality_score * 0.38
                    + candidate.detection.area_ratio * 0.10
                    + self._center_score(candidate.bbox, candidate.image.size) * 0.05
                    + self._shape_score(*candidate.image.size) * 0.05
                ),
            ),
        )

    @staticmethod
    def _center_score(
        bbox: tuple[int, int, int, int],
        image_size: tuple[int, int],
    ) -> float:
        image_width, image_height = image_size
        left, top, right, bottom = bbox
        center_x = (left + right) / 2.0
        center_y = (top + bottom) / 2.0
        offset_x = abs(center_x - image_width / 2.0) / max(1.0, image_width / 2.0)
        offset_y = abs(center_y - image_height / 2.0) / max(1.0, image_height / 2.0)
        return max(0.0, min(1.0, 1.0 - min(1.0, (offset_x + offset_y) / 2.0)))

    @staticmethod
    def _shape_score(width: int, height: int) -> float:
        if width <= 0 or height <= 0:
            return 0.0
        ratio = max(width, height) / max(1.0, min(width, height))
        return max(0.0, min(1.0, 1.0 - min(1.0, abs(ratio - 2.2) / 2.2)))

    @staticmethod
    def _top_predictions_from_probabilities(
        probabilities: list[float],
    ) -> list[dict[str, float | str]]:
        assert torch is not None
        probability_tensor = torch.tensor(probabilities, dtype=torch.float32)
        top_values, top_indexes = torch.topk(
            probability_tensor,
            k=min(3, len(CLASS_LABELS)),
        )
        return [
            {
                "label": CLASS_LABELS[int(top_indexes[i].item())],
                "confidence": float(top_values[i].item() * 100.0),
            }
            for i in range(len(top_values))
        ]

    @staticmethod
    def _normalized_entropy(probabilities: "torch.Tensor") -> float:
        assert torch is not None
        clipped = probabilities.clamp_min(1e-8)
        entropy = -(clipped * clipped.log()).sum()
        normalized = entropy / math.log(len(CLASS_LABELS))
        return float(max(0.0, min(1.0, normalized.item())))

    def _write_success_or_failure_debug(
        self,
        *,
        detection_run: DetectionRun,
        prediction: dict[str, Any] | None,
        crop_predictions: list[dict[str, Any]],
        failure_stage: str | None,
        failure_reason: str | None,
    ) -> None:
        debug_dir = detection_run.debug_dir
        if debug_dir is None:
            return

        classifier_result = {
            "temperature": self.temperature,
            "cropPredictions": crop_predictions,
        }
        if prediction is not None:
            classifier_result["finalPrediction"] = prediction
        (debug_dir / "classifier_result.json").write_text(
            json.dumps(classifier_result, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

        (debug_dir / "final_decision.json").write_text(
            json.dumps(
                {
                    "pipeline_stage_failed": failure_stage,
                    "failure_reason": failure_reason,
                    "prediction": prediction,
                },
                ensure_ascii=False,
                indent=2,
            ),
            encoding="utf-8",
        )

    def _load_classifier(self, weights_path: Path) -> nn.Module:
        if torch is None or models is None or nn is None:
            raise RuntimeError("AI runtime dependencies are not installed")
        if not weights_path.is_file():
            raise FileNotFoundError(
                f"MobileNetV2 weights not found: {weights_path}"
            )

        classifier = models.mobilenet_v2(weights=None)
        in_features = classifier.classifier[1].in_features
        classifier.classifier[1] = nn.Linear(in_features, len(CLASS_LABELS))

        checkpoint = torch.load(
            weights_path,
            map_location=self.device,
            weights_only=False,
        )
        state_dict = self._extract_state_dict(checkpoint)
        classifier.load_state_dict(state_dict, strict=True)
        classifier.to(self.device)
        classifier.eval()
        return classifier

    def _crop_first_detection(
        self,
        image: Image.Image,
    ) -> tuple[Image.Image, tuple[int, int, int, int] | None]:
        detection_run = self._run_detection_pipeline(image)
        if not detection_run.selected_crops:
            return image, None
        first_candidate = detection_run.selected_crops[0]
        return first_candidate.image, first_candidate.bbox

    @staticmethod
    def _extract_state_dict(
        checkpoint: Any,
    ) -> OrderedDict[str, torch.Tensor] | Mapping[str, torch.Tensor]:
        if not isinstance(checkpoint, Mapping):
            raise RuntimeError("Unsupported MobileNetV2 checkpoint format")

        state_dict = checkpoint
        for key in ("state_dict", "model_state_dict"):
            candidate = checkpoint.get(key)
            if isinstance(candidate, Mapping):
                state_dict = candidate
                break

        cleaned_state_dict: OrderedDict[str, torch.Tensor] = OrderedDict()
        for key, value in state_dict.items():
            if not isinstance(key, str) or not isinstance(value, torch.Tensor):
                continue
            normalized_key = key
            for prefix in ("module.", "model."):
                if normalized_key.startswith(prefix):
                    normalized_key = normalized_key[len(prefix) :]
            cleaned_state_dict[normalized_key] = value

        if not cleaned_state_dict:
            raise RuntimeError("MobileNetV2 checkpoint contains no model weights")
        return cleaned_state_dict

    @staticmethod
    def _clamp_box(
        coordinates: list[float],
        image_width: int,
        image_height: int,
    ) -> tuple[int, int, int, int]:
        left = max(0, min(image_width, int(coordinates[0])))
        top = max(0, min(image_height, int(coordinates[1])))
        right = max(0, min(image_width, int(coordinates[2])))
        bottom = max(0, min(image_height, int(coordinates[3])))
        return left, top, right, bottom
