from __future__ import annotations

from dataclasses import dataclass
import math
from pathlib import Path
from typing import Callable, Iterable

try:
    import cv2
    import numpy as np
    from PIL import Image, ImageEnhance, ImageFilter, ImageOps, ImageStat
except ImportError:  # pragma: no cover - optional runtime dependency
    cv2 = None
    np = None
    Image = None
    ImageEnhance = None
    ImageFilter = None
    ImageOps = None
    ImageStat = None


@dataclass(frozen=True)
class ImageQualityProfile:
    brightness: float
    contrast: float
    blur: float
    saturation: float
    sharpness: float
    green_ratio: float
    entropy: float
    dynamic_range: float
    width: int
    height: int


@dataclass(frozen=True)
class EnhancementVariant:
    name: str
    image: Image.Image
    score: float
    metrics: dict[str, float]


def analyze_image_quality(image: Image.Image) -> ImageQualityProfile:
    pil_image = image.convert("RGB")
    width, height = pil_image.size
    if cv2 is None or np is None or ImageStat is None:
        return ImageQualityProfile(
            brightness=0.5,
            contrast=0.5,
            blur=0.0,
            saturation=0.5,
            sharpness=0.5,
            green_ratio=0.5,
            entropy=0.5,
            dynamic_range=0.5,
            width=width,
            height=height,
        )

    rgb_array = np.asarray(pil_image, dtype=np.uint8)
    gray = cv2.cvtColor(rgb_array, cv2.COLOR_RGB2GRAY)
    hsv = cv2.cvtColor(rgb_array, cv2.COLOR_RGB2HSV)

    brightness = float(gray.mean() / 255.0)
    contrast = float(gray.std() / 255.0)
    blur = float(cv2.Laplacian(gray, cv2.CV_64F).var())
    saturation = float(hsv[:, :, 1].mean() / 255.0)
    sharpness = float(min(1.0, blur / 120.0))
    green_ratio = float(_green_ratio(rgb_array))
    entropy = float(_entropy(gray))
    dynamic_range = float((gray.max() - gray.min()) / 255.0)

    return ImageQualityProfile(
        brightness=brightness,
        contrast=contrast,
        blur=blur,
        saturation=saturation,
        sharpness=sharpness,
        green_ratio=green_ratio,
        entropy=entropy,
        dynamic_range=dynamic_range,
        width=width,
        height=height,
    )


def build_enhancement_variants(
    image: Image.Image,
    *,
    mode: str = "balanced",
    include_original: bool = True,
) -> list[EnhancementVariant]:
    pil_image = image.convert("RGB")
    variants: list[EnhancementVariant] = []
    original_metrics = analyze_image_quality(pil_image)
    if include_original:
        variants.append(
            EnhancementVariant(
                name="original",
                image=pil_image,
                score=_score_quality(original_metrics),
                metrics=_profile_to_metrics(original_metrics),
            )
        )

    if cv2 is None or np is None:
        fallback = [
            ("contrast_stretch", _contrast_stretch_fallback(pil_image)),
            ("sharpen", pil_image.filter(ImageFilter.SHARPEN) if ImageFilter else pil_image),
            ("autocontrast", ImageOps.autocontrast(pil_image) if ImageOps else pil_image),
        ]
        for name, candidate in fallback:
            profile = analyze_image_quality(candidate)
            variants.append(
                EnhancementVariant(
                    name=name,
                    image=candidate,
                    score=_score_quality(profile),
                    metrics=_profile_to_metrics(profile),
                )
            )
        return _deduplicate_variants(variants)

    rgb_array = np.asarray(pil_image, dtype=np.uint8)
    candidate_builders: list[tuple[str, Callable[[], Image.Image]]] = [
        ("clahe", lambda: _apply_clahe(rgb_array)),
        ("gamma_0_85", lambda: _apply_gamma(rgb_array, 0.85)),
        ("gamma_1_15", lambda: _apply_gamma(rgb_array, 1.15)),
        ("awb", lambda: _apply_auto_white_balance(rgb_array)),
        ("gray_world", lambda: _apply_gray_world_white_balance(rgb_array)),
        ("contrast_stretch", lambda: _apply_contrast_stretch(rgb_array)),
        ("brightness_norm", lambda: _apply_brightness_normalization(rgb_array)),
        ("denoise", lambda: _apply_denoise(rgb_array)),
        ("bilateral", lambda: _apply_bilateral(rgb_array)),
        ("sharpen", lambda: _apply_unsharp_mask(rgb_array)),
        ("retinex", lambda: _apply_retinex(rgb_array)),
        ("shadow_removed", lambda: _apply_shadow_reduction(rgb_array)),
        ("highlight_suppressed", lambda: _apply_highlight_suppression(rgb_array)),
    ]

    if mode == "fast":
        candidate_builders = candidate_builders[:6]
    elif mode == "debug":
        candidate_builders = candidate_builders
    elif mode == "production":
        candidate_builders = candidate_builders[:8]

    for name, builder in candidate_builders:
        try:
            candidate = _ensure_pil(builder())
        except Exception:
            continue
        profile = analyze_image_quality(candidate)
        variants.append(
            EnhancementVariant(
                name=name,
                image=candidate,
                score=_score_quality(profile),
                metrics=_profile_to_metrics(profile),
            )
        )

    return _deduplicate_variants(variants)


def select_best_variant(
    image: Image.Image,
    *,
    mode: str = "balanced",
) -> EnhancementVariant:
    variants = build_enhancement_variants(image, mode=mode)
    if not variants:
        profile = analyze_image_quality(image)
        return EnhancementVariant(
            name="original",
            image=image.convert("RGB"),
            score=_score_quality(profile),
            metrics=_profile_to_metrics(profile),
        )
    return max(variants, key=lambda variant: (variant.score, variant.metrics.get("entropy", 0.0)))


def rank_variants(
    image: Image.Image,
    *,
    mode: str = "balanced",
) -> list[EnhancementVariant]:
    variants = build_enhancement_variants(image, mode=mode)
    return sorted(
        variants,
        key=lambda variant: (variant.score, variant.metrics.get("entropy", 0.0)),
        reverse=True,
    )


def resize_preserving_aspect_ratio(
    image: Image.Image,
    size: tuple[int, int],
    *,
    pad_color: tuple[int, int, int] = (0, 0, 0),
) -> Image.Image:
    target_width, target_height = size
    if target_width <= 0 or target_height <= 0:
        raise ValueError("size must be positive")
    pil_image = image.convert("RGB")
    source_width, source_height = pil_image.size
    if source_width == 0 or source_height == 0:
        return pil_image.resize(size, Image.Resampling.BICUBIC)

    scale = min(target_width / source_width, target_height / source_height)
    new_size = (
        max(1, int(round(source_width * scale))),
        max(1, int(round(source_height * scale))),
    )
    interpolation = Image.Resampling.LANCZOS if scale < 1.0 else Image.Resampling.BICUBIC
    resized = pil_image.resize(new_size, interpolation)
    canvas = Image.new("RGB", size, pad_color)
    offset = (
        (target_width - new_size[0]) // 2,
        (target_height - new_size[1]) // 2,
    )
    canvas.paste(resized, offset)
    return canvas


def prepare_classifier_view(
    image: Image.Image,
    size: tuple[int, int] = (224, 224),
    *,
    mode: str = "balanced",
) -> Image.Image:
    ranked = rank_variants(image, mode=mode)
    best = ranked[0].image if ranked else image.convert("RGB")
    return resize_preserving_aspect_ratio(best, size)


def resize_for_classifier(
    image: Image.Image,
    size: tuple[int, int] = (224, 224),
) -> Image.Image:
    return resize_preserving_aspect_ratio(image, size)


def build_classifier_views(
    image: Image.Image,
    *,
    mode: str = "balanced",
    max_views: int = 4,
) -> list[EnhancementVariant]:
    ranked = rank_variants(image, mode=mode)
    selected = ranked[: max(1, max_views)]
    if not selected:
        selected = [EnhancementVariant("original", image.convert("RGB"), 1.0, _profile_to_metrics(analyze_image_quality(image)))]
    return selected


def build_detection_variants(
    image: Image.Image,
    *,
    mode: str = "balanced",
) -> list[EnhancementVariant]:
    return rank_variants(image, mode=mode)


def estimate_esp32_profile(image: Image.Image) -> dict[str, float | bool | str]:
    profile = analyze_image_quality(image)
    low_resolution = min(profile.width, profile.height) < 720
    noisy = profile.blur < 18.0
    dark = profile.brightness < 0.32
    bright = profile.brightness > 0.78
    compressed = profile.entropy < 0.55 or profile.dynamic_range < 0.22
    return {
        "mode": "esp32" if (low_resolution or noisy or dark or compressed) else "standard",
        "lowResolution": low_resolution,
        "noisy": noisy,
        "dark": dark,
        "bright": bright,
        "compressed": compressed,
        "brightness": profile.brightness,
        "contrast": profile.contrast,
        "blur": profile.blur,
        "entropy": profile.entropy,
        "greenRatio": profile.green_ratio,
    }


def _apply_clahe(rgb_array: np.ndarray) -> Image.Image:
    lab = cv2.cvtColor(rgb_array, cv2.COLOR_RGB2LAB)
    l_channel, a_channel, b_channel = cv2.split(lab)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    l_channel = clahe.apply(l_channel)
    merged = cv2.merge((l_channel, a_channel, b_channel))
    return _ensure_pil(cv2.cvtColor(merged, cv2.COLOR_LAB2RGB))


def _apply_gamma(rgb_array: np.ndarray, gamma: float) -> Image.Image:
    gamma = max(0.45, min(2.4, float(gamma)))
    normalized = rgb_array.astype(np.float32) / 255.0
    corrected = np.power(normalized, 1.0 / gamma)
    return _ensure_pil(np.clip(corrected * 255.0, 0, 255).astype(np.uint8))


def _apply_auto_white_balance(rgb_array: np.ndarray) -> Image.Image:
    channels = [rgb_array[:, :, index].astype(np.float32) for index in range(3)]
    means = [channel.mean() for channel in channels]
    gray_mean = sum(means) / 3.0
    balanced = []
    for channel, mean in zip(channels, means):
        scale = gray_mean / max(mean, 1e-6)
        balanced.append(np.clip(channel * scale, 0, 255))
    merged = np.stack(balanced, axis=2).astype(np.uint8)
    return _ensure_pil(merged)


def _apply_gray_world_white_balance(rgb_array: np.ndarray) -> Image.Image:
    return _apply_auto_white_balance(rgb_array)


def _apply_contrast_stretch(rgb_array: np.ndarray) -> Image.Image:
    if cv2 is None:
        return _ensure_pil(rgb_array)
    min_value = float(np.percentile(rgb_array, 2))
    max_value = float(np.percentile(rgb_array, 98))
    if math.isclose(max_value, min_value):
        return _ensure_pil(rgb_array)
    stretched = np.clip((rgb_array - min_value) * 255.0 / (max_value - min_value), 0, 255)
    return _ensure_pil(stretched.astype(np.uint8))


def _apply_brightness_normalization(rgb_array: np.ndarray) -> Image.Image:
    hsv = cv2.cvtColor(rgb_array, cv2.COLOR_RGB2HSV)
    value = hsv[:, :, 2].astype(np.float32)
    mean = float(value.mean())
    target = 140.0
    scale = target / max(mean, 1e-6)
    value = np.clip(value * scale, 0, 255).astype(np.uint8)
    hsv[:, :, 2] = value
    return _ensure_pil(cv2.cvtColor(hsv, cv2.COLOR_HSV2RGB))


def _apply_denoise(rgb_array: np.ndarray) -> Image.Image:
    denoised = cv2.fastNlMeansDenoisingColored(
        rgb_array,
        None,
        3,
        3,
        7,
        21,
    )
    return _ensure_pil(denoised)


def _apply_bilateral(rgb_array: np.ndarray) -> Image.Image:
    filtered = cv2.bilateralFilter(rgb_array, d=5, sigmaColor=45, sigmaSpace=45)
    return _ensure_pil(filtered)


def _apply_unsharp_mask(rgb_array: np.ndarray) -> Image.Image:
    blurred = cv2.GaussianBlur(rgb_array, (0, 0), 1.1)
    sharpened = cv2.addWeighted(rgb_array, 1.15, blurred, -0.15, 0)
    return _ensure_pil(np.clip(sharpened, 0, 255).astype(np.uint8))


def _apply_retinex(rgb_array: np.ndarray) -> Image.Image:
    image = rgb_array.astype(np.float32) + 1.0
    blurred = cv2.GaussianBlur(image, (0, 0), 15.0)
    retinex = np.log(image) - np.log(blurred + 1.0)
    retinex = cv2.normalize(retinex, None, 0, 255, cv2.NORM_MINMAX)
    return _ensure_pil(np.clip(retinex, 0, 255).astype(np.uint8))


def _apply_shadow_reduction(rgb_array: np.ndarray) -> Image.Image:
    hsv = cv2.cvtColor(rgb_array, cv2.COLOR_RGB2HSV)
    h_channel, s_channel, v_channel = cv2.split(hsv)
    v_channel = cv2.equalizeHist(v_channel)
    merged = cv2.merge((h_channel, s_channel, v_channel))
    reduced = cv2.cvtColor(merged, cv2.COLOR_HSV2RGB)
    return _ensure_pil(reduced)


def _apply_highlight_suppression(rgb_array: np.ndarray) -> Image.Image:
    clipped = np.clip(rgb_array.astype(np.float32), 0, 235).astype(np.uint8)
    return _ensure_pil(clipped)


def _contrast_stretch_fallback(image: Image.Image) -> Image.Image:
    if ImageOps is None:
        return image.convert("RGB")
    return ImageOps.autocontrast(image.convert("RGB"))


def _green_ratio(rgb_array: np.ndarray) -> float:
    red = rgb_array[:, :, 0].astype(np.float32)
    green = rgb_array[:, :, 1].astype(np.float32)
    blue = rgb_array[:, :, 2].astype(np.float32)
    vegetation = (green > red * 1.02) & (green > blue * 0.95)
    return float(vegetation.mean())


def _entropy(gray_array: np.ndarray) -> float:
    histogram = np.bincount(gray_array.reshape(-1), minlength=256).astype(np.float64)
    probability = histogram / max(1.0, histogram.sum())
    probability = probability[probability > 0]
    if probability.size == 0:
        return 0.0
    entropy = -np.sum(probability * np.log2(probability))
    return float(entropy / 8.0)


def _profile_to_metrics(profile: ImageQualityProfile) -> dict[str, float]:
    return {
        "brightness": profile.brightness,
        "contrast": profile.contrast,
        "blur": profile.blur,
        "saturation": profile.saturation,
        "sharpness": profile.sharpness,
        "greenRatio": profile.green_ratio,
        "entropy": profile.entropy,
        "dynamicRange": profile.dynamic_range,
        "width": float(profile.width),
        "height": float(profile.height),
    }


def _score_quality(profile: ImageQualityProfile) -> float:
    resolution_score = min(profile.width, profile.height) / 1024.0
    resolution_score = max(0.0, min(1.0, resolution_score))
    brightness_score = 1.0 - min(abs(profile.brightness - 0.48) / 0.48, 1.0)
    contrast_score = max(0.0, min(1.0, profile.contrast / 0.22))
    blur_score = max(0.0, min(1.0, profile.blur / 110.0))
    saturation_score = max(0.0, min(1.0, profile.saturation / 0.35))
    entropy_score = max(0.0, min(1.0, profile.entropy))
    green_score = max(0.0, min(1.0, profile.green_ratio / 0.75))
    range_score = max(0.0, min(1.0, profile.dynamic_range / 0.65))
    score = (
        0.14 * resolution_score
        + 0.13 * brightness_score
        + 0.15 * contrast_score
        + 0.14 * blur_score
        + 0.10 * saturation_score
        + 0.13 * entropy_score
        + 0.10 * green_score
        + 0.11 * range_score
    )
    return float(max(0.0, min(1.0, score)))


def _deduplicate_variants(variants: Iterable[EnhancementVariant]) -> list[EnhancementVariant]:
    unique: list[EnhancementVariant] = []
    seen: set[str] = set()
    for variant in variants:
        if variant.name in seen:
            continue
        seen.add(variant.name)
        unique.append(variant)
    unique.sort(key=lambda variant: (variant.score, variant.metrics.get("entropy", 0.0)), reverse=True)
    return unique


def _ensure_pil(image: Image.Image | np.ndarray) -> Image.Image:
    if isinstance(image, Image.Image):
        return image.convert("RGB")
    return Image.fromarray(np.asarray(image, dtype=np.uint8)).convert("RGB")
