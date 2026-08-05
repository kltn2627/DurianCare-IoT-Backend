from __future__ import annotations

from dataclasses import dataclass

try:
    import cv2
    import numpy as np
    from PIL import Image
except ImportError:  # pragma: no cover - optional runtime dependency
    cv2 = None
    np = None
    Image = None


@dataclass(frozen=True)
class ImageQualityProfile:
    brightness: float
    contrast: float
    blur: float
    saturation: float
    width: int
    height: int


def analyze_image_quality(image: Image.Image) -> ImageQualityProfile:
    pil_image = image.convert("RGB")
    if cv2 is None or np is None:
        width, height = pil_image.size
        return ImageQualityProfile(
            brightness=0.5,
            contrast=0.5,
            blur=0.0,
            saturation=0.5,
            width=width,
            height=height,
        )

    rgb_array = np.array(pil_image, dtype=np.uint8)
    gray = cv2.cvtColor(rgb_array, cv2.COLOR_RGB2GRAY)
    hsv = cv2.cvtColor(rgb_array, cv2.COLOR_RGB2HSV)

    brightness = float(gray.mean() / 255.0)
    contrast = float(gray.std() / 255.0)
    blur = float(cv2.Laplacian(gray, cv2.CV_64F).var())
    saturation = float(hsv[:, :, 1].mean() / 255.0)
    height, width = gray.shape[:2]
    return ImageQualityProfile(
        brightness=brightness,
        contrast=contrast,
        blur=blur,
        saturation=saturation,
        width=width,
        height=height,
    )


def _pil_to_bgr(image: Image.Image) -> np.ndarray:
    rgb_array = np.array(image.convert("RGB"), dtype=np.uint8)
    return cv2.cvtColor(rgb_array, cv2.COLOR_RGB2BGR)


def _bgr_to_pil(image: np.ndarray) -> Image.Image:
    rgb_array = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    return Image.fromarray(rgb_array)


def _apply_light_denoising(image: np.ndarray) -> np.ndarray:
    try:
        return cv2.fastNlMeansDenoisingColored(
            image,
            None,
            2,
            2,
            7,
            21,
        )
    except Exception:
        return cv2.GaussianBlur(image, (3, 3), 0)


def _apply_light_contrast(image: np.ndarray) -> np.ndarray:
    return cv2.convertScaleAbs(image, alpha=1.1, beta=5)


def _apply_light_sharpen(image: np.ndarray) -> np.ndarray:
    blurred = cv2.GaussianBlur(image, (0, 0), 1.0)
    sharpened = cv2.addWeighted(image, 1.08, blurred, -0.08, 0)
    return np.clip(sharpened, 0, 255).astype(np.uint8)


def preprocess_esp32_camera_image(image: Image.Image) -> Image.Image:
    if cv2 is None or np is None or Image is None:
        return image.convert("RGB")

    working = _pil_to_bgr(image)
    working = _apply_light_denoising(working)
    working = _apply_light_contrast(working)
    working = _apply_light_sharpen(working)
    return _bgr_to_pil(working)


def resize_for_classifier(
    image: Image.Image,
    size: tuple[int, int] = (224, 224),
) -> Image.Image:
    if cv2 is None or np is None or Image is None:
        return image.convert("RGB").resize(size, Image.Resampling.BICUBIC)

    pil_image = image.convert("RGB")
    array = np.array(pil_image, dtype=np.uint8)
    interpolation = cv2.INTER_AREA
    if array.shape[1] < size[0] or array.shape[0] < size[1]:
        interpolation = cv2.INTER_CUBIC
    resized = cv2.resize(array, size, interpolation=interpolation)
    return Image.fromarray(resized)


def prepare_classifier_view(image: Image.Image) -> Image.Image:
    return resize_for_classifier(preprocess_esp32_camera_image(image))
