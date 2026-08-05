from __future__ import annotations

from app.services.image_enhancement import (
    ImageQualityProfile,
    analyze_image_quality,
    build_classifier_views,
    build_detection_variants,
    estimate_esp32_profile,
    prepare_classifier_view,
    resize_for_classifier,
    resize_preserving_aspect_ratio,
    select_best_variant,
)


def preprocess_esp32_camera_image(image):
    return select_best_variant(image, mode="balanced").image


__all__ = [
    "ImageQualityProfile",
    "analyze_image_quality",
    "build_classifier_views",
    "build_detection_variants",
    "estimate_esp32_profile",
    "prepare_classifier_view",
    "preprocess_esp32_camera_image",
    "resize_for_classifier",
    "resize_preserving_aspect_ratio",
]
