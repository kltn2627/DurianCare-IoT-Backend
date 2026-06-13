from collections import OrderedDict
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

import torch
from PIL import Image
from torch import nn
from torchvision import models, transforms
from ultralytics import YOLO

from app.core.config import Settings

CLASS_LABELS = (
    "ALGAL_LEAF_SPOT",
    "ALLOCARIDARA_ATTACK",
    "HEALTHY_LEAF",
    "LEAF_BLIGHT",
    "PHOMOPSIS_LEAF_SPOT",
)


class PredictionError(RuntimeError):
    pass


@dataclass(frozen=True)
class DiseasePrediction:
    label: str
    confidence: float
    used_detection_crop: bool
    bounding_box: tuple[int, int, int, int] | None


class DoubleModelDiseaseClassifier:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self.device = torch.device(
            "cuda" if torch.cuda.is_available() else "cpu"
        )
        self.detector: YOLO | None = None
        self.classifier: nn.Module | None = None
        self.transform = transforms.Compose(
            [
                transforms.Resize((224, 224)),
                transforms.ToTensor(),
                transforms.Normalize(
                    mean=[0.485, 0.456, 0.406],
                    std=[0.229, 0.224, 0.225],
                ),
            ]
        )

    def load_models(self) -> None:
        try:
            if self.settings.yolo_crop_enabled:
                self.detector = YOLO(self.settings.yolo_model)
                self.detector.to(self.device)
            self.classifier = self._load_classifier(
                self.settings.classifier_weights
            )
        except Exception as exception:
            raise RuntimeError(
                f"Unable to load double-model pipeline: {exception}"
            ) from exception

    def predict(self, image: Image.Image) -> DiseasePrediction:
        if self.classifier is None:
            raise RuntimeError("MobileNetV2 classifier has not been loaded")

        try:
            leaf_image, bounding_box = self._crop_first_detection(image)
            input_tensor = self.transform(leaf_image).unsqueeze(0).to(self.device)

            with torch.inference_mode():
                logits = self.classifier(input_tensor)
                probabilities = torch.softmax(logits, dim=1)
                confidence, predicted_index = probabilities.max(dim=1)

            label = CLASS_LABELS[int(predicted_index.item())]
            return DiseasePrediction(
                label=label,
                confidence=float(confidence.item() * 100),
                used_detection_crop=bounding_box is not None,
                bounding_box=bounding_box,
            )
        except Exception as exception:
            raise PredictionError(
                f"Double-model inference failed: {exception}"
            ) from exception

    def _crop_first_detection(
        self,
        image: Image.Image,
    ) -> tuple[Image.Image, tuple[int, int, int, int] | None]:
        if self.detector is None:
            return image, None

        results = self.detector.predict(
            source=image,
            conf=self.settings.yolo_confidence,
            device=self.device.type,
            verbose=False,
        )
        if not results or results[0].boxes is None or len(results[0].boxes) == 0:
            return image, None

        coordinates = results[0].boxes.xyxy[0].detach().cpu().tolist()
        left, top, right, bottom = self._clamp_box(
            coordinates,
            image.width,
            image.height,
        )
        if right <= left or bottom <= top:
            return image, None
        bounding_box = (left, top, right, bottom)
        return image.crop(bounding_box), bounding_box

    def _load_classifier(self, weights_path: Path) -> nn.Module:
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
