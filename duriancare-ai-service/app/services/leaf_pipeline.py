from __future__ import annotations

from dataclasses import asdict, dataclass, field
from io import BytesIO
import json
import math
import os
import time
from pathlib import Path
from typing import Any, Callable

from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter, ImageOps, ImageStat

from app.services.image_enhancement import (
    analyze_image_quality,
    build_detection_variants,
    estimate_esp32_profile,
)


@dataclass(frozen=True)
class ImageVariant:
    name: str
    image: Image.Image
    scale: float
    metrics: dict[str, float]


@dataclass(frozen=True)
class DetectionBox:
    left: int
    top: int
    right: int
    bottom: int
    confidence: float
    class_id: int
    class_name: str
    source_variant: str
    confidence_threshold: float
    iou_threshold: float
    scale: float
    area_ratio: float
    crop_quality: float


@dataclass(frozen=True)
class CropCandidate:
    image: Image.Image
    bbox: tuple[int, int, int, int]
    detection: DetectionBox
    quality_score: float
    acceptance_reason: str


@dataclass(frozen=True)
class DetectorAttempt:
    variant: str
    preprocessing: str
    confidence_threshold: float
    iou_threshold: float
    scale: float
    image_width: int
    image_height: int
    elapsed_ms: float
    detections: list[DetectionBox] = field(default_factory=list)


@dataclass(frozen=True)
class DetectionRun:
    variants: list[ImageVariant]
    attempts: list[DetectorAttempt]
    detections: list[DetectionBox]
    selected_crops: list[CropCandidate]
    failure_stage: str | None
    failure_reason: str | None
    debug_dir: Path | None


class AdaptiveLeafPipeline:
    def __init__(
        self,
        debug_root: Path | None = None,
        capture_hard_examples: bool = True,
        max_crops: int = 5,
        min_crop_side: int = 64,
        min_crop_area_ratio: float = 0.015,
        min_crop_quality: float = 0.26,
    ) -> None:
        self.debug_root = debug_root or Path("artifacts/debug_runs")
        self.capture_hard_examples = capture_hard_examples
        self.max_crops = max_crops
        self.min_crop_side = min_crop_side
        self.min_crop_area_ratio = min_crop_area_ratio
        self.min_crop_quality = min_crop_quality
        self.debug_enabled = os.getenv("AI_DEBUG", "false").lower() in {
            "1",
            "true",
            "yes",
        }
        self.hard_example_root = Path(
            os.getenv("AI_HARD_EXAMPLES_DIR", "datasets/hard_examples")
        )
        self.pipeline_mode = os.getenv("AI_PIPELINE_MODE", "production").lower()
        self.max_detection_variants = max(
            3,
            int(os.getenv("AI_PIPELINE_MAX_VARIANTS", "4")),
        )
        self.rescue_variant_limit = max(
            1,
            int(os.getenv("AI_PIPELINE_RESCUE_VARIANTS", "2")),
        )
        self.esp32_quality_floor = float(os.getenv("AI_ESP32_QUALITY_FLOOR", "0.28"))

    def run_detection_search(
        self,
        image: Image.Image,
        detector: Any,
        device_name: str,
        model_name: str,
        weights_path: str,
        base_confidence: float,
    ) -> DetectionRun:
        variants = self._build_variants(image)
        attempts: list[DetectorAttempt] = []
        detections: list[DetectionBox] = []

        detection_parameters = self._build_detection_parameters(base_confidence)
        image_profile = estimate_esp32_profile(image)
        selected_variants = variants[: self.max_detection_variants]
        rescue_variants = variants[
            self.max_detection_variants : self.max_detection_variants + self.rescue_variant_limit
        ]
        if image_profile.get("mode") == "esp32":
            selected_variants = sorted(
                selected_variants,
                key=lambda variant: (
                    variant.metrics.get("sharpness", 0.0),
                    variant.metrics.get("entropy", 0.0),
                    variant.metrics.get("brightness", 0.0),
                ),
                reverse=True,
            )

        for variant in selected_variants:
            for confidence_threshold, iou_threshold in detection_parameters:
                attempt, attempt_detections = self._run_detector(
                    image=image,
                    variant=variant,
                    detector=detector,
                    device_name=device_name,
                    confidence_threshold=confidence_threshold,
                    iou_threshold=iou_threshold,
                )
                attempts.append(attempt)
                detections.extend(attempt_detections)

        if not self._has_strong_candidates(detections) and rescue_variants:
            rescue_parameters = self._build_rescue_parameters(base_confidence)
            for variant in rescue_variants:
                for confidence_threshold, iou_threshold in rescue_parameters:
                    attempt, attempt_detections = self._run_detector(
                        image=image,
                        variant=variant,
                        detector=detector,
                        device_name=device_name,
                        confidence_threshold=confidence_threshold,
                        iou_threshold=iou_threshold,
                    )
                    attempts.append(attempt)
                    detections.extend(attempt_detections)

        deduplicated_detections = self._deduplicate_detections(detections)
        selected_crops = self._select_crops(image, deduplicated_detections)
        failure_stage = None
        failure_reason = None
        if not deduplicated_detections:
            failure_stage = "detector"
            failure_reason = "detector_no_bbox"
        elif not selected_crops:
            failure_stage = "validator"
            failure_reason = "crop_quality_issue"

        debug_dir = self._maybe_create_debug_dir()
        if debug_dir is not None:
            self._write_debug_artifacts(
                debug_dir=debug_dir,
                original_image=image,
                attempts=attempts,
                selected_crops=selected_crops,
                failure_stage=failure_stage,
                failure_reason=failure_reason,
                detector_model=model_name,
                detector_weights=weights_path,
                device_name=device_name,
            )

        if failure_stage is not None and self.capture_hard_examples:
            self._archive_hard_example(
                debug_dir=debug_dir,
                original_image=image,
                attempts=attempts,
                failure_stage=failure_stage,
                failure_reason=failure_reason,
            )

        return DetectionRun(
            variants=variants,
            attempts=attempts,
            detections=deduplicated_detections,
            selected_crops=selected_crops,
            failure_stage=failure_stage,
            failure_reason=failure_reason,
            debug_dir=debug_dir,
        )

    def score_crop(self, crop: Image.Image, bbox: tuple[int, int, int, int], image_size: tuple[int, int]) -> float:
        width, height = crop.size
        if width < self.min_crop_side or height < self.min_crop_side:
            return 0.0

        original_width, original_height = image_size
        area_ratio = (width * height) / max(1, original_width * original_height)
        if area_ratio < self.min_crop_area_ratio:
            return 0.0

        gray = crop.convert("L")
        edge_image = gray.filter(ImageFilter.FIND_EDGES)
        rgb_crop = crop.convert("RGB")
        color_stat = ImageStat.Stat(rgb_crop)
        gray_stat = ImageStat.Stat(gray)
        edge_stat = ImageStat.Stat(edge_image)

        brightness = _clamp01(1.0 - abs(gray_stat.mean[0] - 128.0) / 128.0)
        contrast = _clamp01(gray_stat.stddev[0] / 64.0)
        focus = _clamp01(edge_stat.var[0] / 1200.0)
        edge_density = _clamp01(edge_stat.mean[0] / 255.0)
        area_score = _clamp01(1.0 - abs(area_ratio - 0.22) / 0.22)
        center_score = self._center_score(bbox, image_size)
        shape_score = self._shape_score(width, height)
        green_score = self._green_ratio(rgb_crop)
        entropy_score = self._entropy_score(rgb_crop)
        saturation = _clamp01(ImageStat.Stat(rgb_crop.convert("HSV")).mean[1] / 255.0)
        channel_balance = _clamp01(
            1.0
            - abs(color_stat.mean[1] - max(color_stat.mean[0], color_stat.mean[2])) / 255.0
        )

        score = (
            0.21 * focus
            + 0.14 * contrast
            + 0.09 * brightness
            + 0.10 * edge_density
            + 0.09 * area_score
            + 0.10 * center_score
            + 0.10 * shape_score
            + 0.11 * green_score
            + 0.05 * entropy_score
            + 0.01 * saturation
            + 0.00 * channel_balance
        )
        return round(_clamp01(score), 4)

    def to_json(self, detection_run: DetectionRun) -> dict[str, Any]:
        return {
            "variants": [
                {
                    "name": variant.name,
                    "scale": variant.scale,
                    "width": variant.image.width,
                    "height": variant.image.height,
                    "metrics": variant.metrics,
                }
                for variant in detection_run.variants
            ],
            "attempts": [
                {
                    "variant": attempt.variant,
                    "preprocessing": attempt.preprocessing,
                    "confidenceThreshold": attempt.confidence_threshold,
                    "iouThreshold": attempt.iou_threshold,
                    "scale": attempt.scale,
                    "imageWidth": attempt.image_width,
                    "imageHeight": attempt.image_height,
                    "elapsedMs": round(attempt.elapsed_ms, 3),
                    "detections": [
                        asdict(detection) for detection in attempt.detections
                    ],
                }
                for attempt in detection_run.attempts
            ],
            "detections": [asdict(detection) for detection in detection_run.detections],
            "selectedCrops": [
                {
                    "bbox": candidate.bbox,
                    "qualityScore": candidate.quality_score,
                    "acceptanceReason": candidate.acceptance_reason,
                    "detection": asdict(candidate.detection),
                }
                for candidate in detection_run.selected_crops
            ],
            "failureStage": detection_run.failure_stage,
            "failureReason": detection_run.failure_reason,
        }

    def _build_detection_parameters(
        self,
        base_confidence: float,
    ) -> list[tuple[float, float]]:
        if self.pipeline_mode == "fast":
            thresholds = [
                base_confidence,
                max(0.08, round(base_confidence * 0.7, 3)),
            ]
            ious = [0.55, 0.70]
        elif self.pipeline_mode == "production":
            thresholds = [
                base_confidence,
                max(0.10, round(base_confidence * 0.78, 3)),
            ]
            ious = [0.55, 0.70]
        else:
            thresholds = [
                base_confidence,
                max(0.12, round(base_confidence * 0.8, 3)),
                max(0.08, round(base_confidence * 0.65, 3)),
            ]
            ious = [0.55, 0.65, 0.75]
        combinations: list[tuple[float, float]] = []
        for confidence_threshold in thresholds:
            for iou_threshold in ious:
                combinations.append(
                    (
                        round(_clamp01(confidence_threshold), 3),
                        round(_clamp01(iou_threshold), 3),
                    )
                )
        unique: list[tuple[float, float]] = []
        for item in combinations:
            if item not in unique:
                unique.append(item)
        return unique

    def _build_rescue_parameters(
        self,
        base_confidence: float,
    ) -> list[tuple[float, float]]:
        if self.pipeline_mode == "fast":
            factors = (0.55, 0.40)
            ious = [0.45, 0.60]
        elif self.pipeline_mode == "production":
            factors = (0.55, 0.45)
            ious = [0.45, 0.60]
        else:
            factors = (0.55, 0.45, 0.35)
            ious = [0.45, 0.55, 0.65]
        thresholds = [
            max(0.04, round(base_confidence * factor, 3))
            for factor in factors
        ]
        combinations: list[tuple[float, float]] = []
        for confidence_threshold in thresholds:
            for iou_threshold in ious:
                candidate = (
                    round(_clamp01(confidence_threshold), 3),
                    round(_clamp01(iou_threshold), 3),
                )
                if candidate not in combinations:
                    combinations.append(candidate)
        return combinations

    def _build_variants(self, image: Image.Image) -> list[ImageVariant]:
        ranked_variants = build_detection_variants(
            image,
            mode=self.pipeline_mode,
        )
        variants: list[ImageVariant] = [
            ImageVariant(
                name=variant.name,
                image=variant.image,
                scale=1.0,
                metrics=variant.metrics,
            )
            for variant in ranked_variants
        ]
        if not variants:
            metrics = self._measure_image(image)
            variants = [ImageVariant("original", image.convert("RGB"), 1.0, metrics)]
        return variants

    def _run_detector(
        self,
        image: Image.Image,
        variant: ImageVariant,
        detector: Any,
        device_name: str,
        confidence_threshold: float,
        iou_threshold: float,
    ) -> tuple[DetectorAttempt, list[DetectionBox]]:
        started = time.perf_counter()
        results = detector.predict(
            source=variant.image,
            conf=confidence_threshold,
            iou=iou_threshold,
            device=device_name,
            verbose=False,
        )
        elapsed_ms = (time.perf_counter() - started) * 1000.0
        detections: list[DetectionBox] = []
        class_names = {}
        if results:
            class_names = getattr(results[0], "names", {}) or getattr(detector, "names", {}) or {}
        if results and results[0].boxes is not None and len(results[0].boxes) > 0:
            boxes = results[0].boxes
            xyxy = boxes.xyxy.detach().cpu().tolist()
            confidence_values = boxes.conf.detach().cpu().tolist()
            class_values = boxes.cls.detach().cpu().tolist()
            for index, coordinates in enumerate(xyxy):
                left, top, right, bottom = self._scale_box_back(
                    coordinates,
                    variant.scale,
                    image.width,
                    image.height,
                )
                if right <= left or bottom <= top:
                    continue
                area_ratio = ((right - left) * (bottom - top)) / max(
                    1,
                    image.width * image.height,
                )
                crop = image.crop((left, top, right, bottom))
                crop_quality = self.score_crop(crop, (left, top, right, bottom), image.size)
                detections.append(
                    DetectionBox(
                        left=left,
                        top=top,
                        right=right,
                        bottom=bottom,
                        confidence=float(confidence_values[index]),
                        class_id=int(class_values[index]),
                        class_name=self._resolve_class_name(
                            int(class_values[index]),
                            class_names,
                        ),
                        source_variant=variant.name,
                        confidence_threshold=confidence_threshold,
                        iou_threshold=iou_threshold,
                        scale=variant.scale,
                        area_ratio=round(area_ratio, 6),
                        crop_quality=crop_quality,
                    )
                )
        attempt = DetectorAttempt(
            variant=variant.name,
            preprocessing=variant.name,
            confidence_threshold=confidence_threshold,
            iou_threshold=iou_threshold,
            scale=variant.scale,
            image_width=variant.image.width,
            image_height=variant.image.height,
            elapsed_ms=elapsed_ms,
            detections=detections,
        )
        return attempt, detections

    def _has_strong_candidates(self, detections: list[DetectionBox]) -> bool:
        if not detections:
            return False
        top_detection = max(
            detections,
            key=lambda detection: (detection.confidence * 0.7) + (detection.crop_quality * 0.3),
        )
        return (top_detection.confidence >= 0.18) and (top_detection.crop_quality >= self.min_crop_quality)

    def _deduplicate_detections(self, detections: list[DetectionBox]) -> list[DetectionBox]:
        sorted_detections = sorted(
            detections,
            key=lambda detection: (
                detection.confidence * 0.6
                + detection.crop_quality * 0.3
                + detection.area_ratio * 0.1
            ),
            reverse=True,
        )
        unique: list[DetectionBox] = []
        for candidate in sorted_detections:
            if any(self._iou(candidate, existing) >= 0.68 for existing in unique):
                continue
            unique.append(candidate)
        return unique

    def _select_crops(
        self,
        original_image: Image.Image,
        detections: list[DetectionBox],
    ) -> list[CropCandidate]:
        crops: list[CropCandidate] = []
        for detection in detections:
            crop = original_image.crop(
                (detection.left, detection.top, detection.right, detection.bottom)
            )
            width, height = crop.size
            if width < self.min_crop_side or height < self.min_crop_side:
                continue
            quality = self.score_crop(
                crop,
                (detection.left, detection.top, detection.right, detection.bottom),
                original_image.size,
            )
            if quality < self.min_crop_quality and detection.confidence < 0.30:
                continue
            score = round(
                _clamp01(
                    detection.confidence * 0.38
                    + quality * 0.24
                    + detection.area_ratio * 0.08
                    + self._center_score(
                        (detection.left, detection.top, detection.right, detection.bottom),
                        original_image.size,
                    )
                    * 0.12
                    + self._shape_score(width, height) * 0.08
                    + self._green_ratio(crop) * 0.06
                    + self._entropy_score(crop) * 0.04
                ),
                4,
            )
            crops.append(
                CropCandidate(
                    image=crop,
                    bbox=(detection.left, detection.top, detection.right, detection.bottom),
                    detection=detection,
                    quality_score=score,
                    acceptance_reason=(
                        "candidate_selected"
                        if quality >= self.min_crop_quality
                        else "candidate_kept_due_to_detector_confidence"
                    ),
                )
            )

        unique_crops: list[CropCandidate] = []
        for crop in sorted(
            crops,
            key=lambda item: (item.quality_score, item.detection.confidence),
            reverse=True,
        ):
            if any(self._iou_from_bbox(crop.bbox, existing.bbox) >= 0.72 for existing in unique_crops):
                continue
            unique_crops.append(crop)
            if len(unique_crops) >= self.max_crops:
                break
        return unique_crops

    def _maybe_create_debug_dir(self) -> Path | None:
        if not self.debug_enabled:
            return None
        timestamp = time.strftime("%Y-%m-%d_%H-%M-%S")
        suffix = f"{int(time.time() * 1000) % 1000:03d}"
        debug_dir = self.debug_root / f"{timestamp}_{suffix}"
        debug_dir.mkdir(parents=True, exist_ok=True)
        return debug_dir

    def _write_debug_artifacts(
        self,
        *,
        debug_dir: Path,
        original_image: Image.Image,
        attempts: list[DetectorAttempt],
        selected_crops: list[CropCandidate],
        failure_stage: str | None,
        failure_reason: str | None,
        detector_model: str,
        detector_weights: str,
        device_name: str,
    ) -> None:
        original_path = debug_dir / "original_image.jpg"
        self._save_image(original_image, original_path)

        yolo_result = {
            "model": detector_model,
            "weights": detector_weights,
            "device": device_name,
            "attempts": [
                {
                    "variant": attempt.variant,
                    "preprocessing": attempt.preprocessing,
                    "confidenceThreshold": attempt.confidence_threshold,
                    "iouThreshold": attempt.iou_threshold,
                    "scale": attempt.scale,
                    "imageWidth": attempt.image_width,
                    "imageHeight": attempt.image_height,
                    "processingTimeMs": round(attempt.elapsed_ms, 3),
                    "detections": [asdict(detection) for detection in attempt.detections],
                }
                for attempt in attempts
            ],
        }
        (debug_dir / "yolo_result.json").write_text(
            json.dumps(yolo_result, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

        self._draw_detections(
            original_image,
            attempts,
            debug_dir / "yolo_visualization.jpg",
        )

        crops_dir = debug_dir / "crops"
        crops_dir.mkdir(parents=True, exist_ok=True)
        for index, candidate in enumerate(selected_crops, start=1):
            self._save_image(candidate.image, crops_dir / f"crop_{index:02d}.jpg")

        (debug_dir / "validator_result.json").write_text(
            json.dumps(
                [
                    {
                        "crop": f"crop_{index:02d}.jpg",
                        "score": candidate.quality_score,
                        "threshold": self.min_crop_quality,
                        "accepted": candidate.quality_score >= self.min_crop_quality,
                        "reason": candidate.acceptance_reason,
                        "bbox": list(candidate.bbox),
                        "detectorConfidence": candidate.detection.confidence,
                        "variant": candidate.detection.source_variant,
                    }
                    for index, candidate in enumerate(selected_crops, start=1)
                ],
                ensure_ascii=False,
                indent=2,
            ),
            encoding="utf-8",
        )

        (debug_dir / "final_decision.json").write_text(
            json.dumps(
                {
                    "pipeline_stage_failed": failure_stage,
                    "failure_reason": failure_reason,
                },
                ensure_ascii=False,
                indent=2,
            ),
            encoding="utf-8",
        )

    def _archive_hard_example(
        self,
        *,
        debug_dir: Path | None,
        original_image: Image.Image,
        attempts: list[DetectorAttempt],
        failure_stage: str | None,
        failure_reason: str | None,
    ) -> None:
        hard_example_dir = self.hard_example_root / time.strftime("%Y-%m-%d_%H-%M-%S")
        hard_example_dir.mkdir(parents=True, exist_ok=True)
        self._save_image(original_image, hard_example_dir / "original_image.jpg")
        if debug_dir is not None:
            for file_name in ("yolo_result.json", "validator_result.json", "final_decision.json"):
                source = debug_dir / file_name
                if source.is_file():
                    (hard_example_dir / file_name).write_text(
                        source.read_text(encoding="utf-8"),
                        encoding="utf-8",
                    )
        else:
            (hard_example_dir / "final_decision.json").write_text(
                json.dumps(
                    {
                        "pipeline_stage_failed": failure_stage,
                        "failure_reason": failure_reason,
                        "attempts": [
                            {
                                "variant": attempt.variant,
                                "confidenceThreshold": attempt.confidence_threshold,
                                "iouThreshold": attempt.iou_threshold,
                                "detections": [asdict(detection) for detection in attempt.detections],
                            }
                            for attempt in attempts
                        ],
                    },
                    ensure_ascii=False,
                    indent=2,
                ),
                encoding="utf-8",
            )

    def _draw_detections(
        self,
        original_image: Image.Image,
        attempts: list[DetectorAttempt],
        output_path: Path,
    ) -> None:
        annotated = original_image.copy()
        draw = ImageDraw.Draw(annotated)
        colors = [
            "#ff3b30",
            "#34c759",
            "#0a84ff",
            "#ff9f0a",
            "#bf5af2",
            "#64d2ff",
        ]
        all_detections = [
            detection
            for attempt in attempts
            for detection in attempt.detections
        ]
        for index, detection in enumerate(all_detections, start=1):
            color = colors[(index - 1) % len(colors)]
            draw.rectangle(
                [detection.left, detection.top, detection.right, detection.bottom],
                outline=color,
                width=4,
            )
            label = (
                f"{index}:{detection.class_name} "
                f"{detection.confidence:.2f}/{detection.crop_quality:.2f}"
            )
            text_bbox = draw.textbbox((0, 0), label)
            text_width = text_bbox[2] - text_bbox[0]
            text_height = text_bbox[3] - text_bbox[1]
            text_x = detection.left
            text_y = max(0, detection.top - text_height - 4)
            draw.rectangle(
                [text_x, text_y, text_x + text_width + 8, text_y + text_height + 6],
                fill=color,
            )
            draw.text((text_x + 4, text_y + 2), label, fill="white")
        self._save_image(annotated, output_path)

    @staticmethod
    def _save_image(image: Image.Image, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        image.save(path, format="JPEG", quality=95)

    @staticmethod
    def _resize_with_scale(image: Image.Image, scale: float) -> Image.Image:
        if math.isclose(scale, 1.0):
            return image.copy()
        width = max(64, int(round(image.width * scale)))
        height = max(64, int(round(image.height * scale)))
        return image.resize((width, height), Image.Resampling.LANCZOS)

    @staticmethod
    def _adjust_gamma(image: Image.Image, gamma: float) -> Image.Image:
        gamma = max(0.5, min(1.8, gamma))
        inverse_gamma = 1.0 / gamma
        lookup = [
            min(255, int(round(((pixel / 255.0) ** inverse_gamma) * 255.0)))
            for pixel in range(256)
        ]
        return image.point(lookup * 3)

    @staticmethod
    def _measure_image(image: Image.Image) -> dict[str, float]:
        gray = image.convert("L")
        gray_stat = ImageStat.Stat(gray)
        edge_image = gray.filter(ImageFilter.FIND_EDGES)
        edge_stat = ImageStat.Stat(edge_image)
        return {
            "brightness": float(gray_stat.mean[0]),
            "contrast": float(gray_stat.stddev[0]),
            "focus": float(edge_stat.var[0]),
            "edge_density": float(edge_stat.mean[0]),
            "saturation": float(ImageStat.Stat(image.convert("HSV")).mean[1]),
        }

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
        return _clamp01(1.0 - min(1.0, (offset_x + offset_y) / 2.0))

    @staticmethod
    def _shape_score(width: int, height: int) -> float:
        if width <= 0 or height <= 0:
            return 0.0
        ratio = max(width, height) / max(1.0, min(width, height))
        return _clamp01(1.0 - min(1.0, abs(ratio - 2.2) / 2.2))

    @staticmethod
    def _green_ratio(image: Image.Image) -> float:
        red_channel, green_channel, blue_channel = image.convert("RGB").split()
        red_mean = ImageStat.Stat(red_channel).mean[0]
        green_mean = ImageStat.Stat(green_channel).mean[0]
        blue_mean = ImageStat.Stat(blue_channel).mean[0]
        numerator = max(0.0, green_mean - ((red_mean + blue_mean) / 2.0))
        denominator = max(1.0, green_mean + red_mean + blue_mean)
        return _clamp01((numerator / denominator) * 3.0)

    @staticmethod
    def _entropy_score(image: Image.Image) -> float:
        histogram = image.convert("L").histogram()
        total = float(sum(histogram))
        if total <= 0:
            return 0.0
        entropy = 0.0
        for count in histogram:
            if count <= 0:
                continue
            probability = count / total
            entropy -= probability * math.log2(probability)
        return _clamp01(entropy / 8.0)

    @staticmethod
    def _scale_box_back(
        coordinates: list[float],
        scale: float,
        image_width: int,
        image_height: int,
    ) -> tuple[int, int, int, int]:
        left = coordinates[0] / scale
        top = coordinates[1] / scale
        right = coordinates[2] / scale
        bottom = coordinates[3] / scale
        return (
            max(0, min(image_width, int(round(left)))),
            max(0, min(image_height, int(round(top)))),
            max(0, min(image_width, int(round(right)))),
            max(0, min(image_height, int(round(bottom)))),
        )

    @staticmethod
    def _resolve_class_name(
        class_id: int,
        class_names: dict[int, str] | list[str] | tuple[str, ...] | dict[str, str],
    ) -> str:
        if isinstance(class_names, dict):
            candidate = class_names.get(class_id)
            if candidate is None:
                candidate = class_names.get(str(class_id))
            if candidate is not None:
                return str(candidate)
        elif isinstance(class_names, (list, tuple)) and 0 <= class_id < len(class_names):
            return str(class_names[class_id])
        return "leaf" if class_id == 0 else f"class_{class_id}"

    @staticmethod
    def _iou(first: DetectionBox, second: DetectionBox) -> float:
        return AdaptiveLeafPipeline._iou_from_bbox(
            (first.left, first.top, first.right, first.bottom),
            (second.left, second.top, second.right, second.bottom),
        )

    @staticmethod
    def _iou_from_bbox(
        first: tuple[int, int, int, int],
        second: tuple[int, int, int, int],
    ) -> float:
        left = max(first[0], second[0])
        top = max(first[1], second[1])
        right = min(first[2], second[2])
        bottom = min(first[3], second[3])
        intersection = max(0, right - left) * max(0, bottom - top)
        if intersection <= 0:
            return 0.0
        first_area = max(1, (first[2] - first[0]) * (first[3] - first[1]))
        second_area = max(1, (second[2] - second[0]) * (second[3] - second[1]))
        union = first_area + second_area - intersection
        return intersection / union if union > 0 else 0.0


def _clamp01(value: float) -> float:
    return max(0.0, min(1.0, float(value)))
