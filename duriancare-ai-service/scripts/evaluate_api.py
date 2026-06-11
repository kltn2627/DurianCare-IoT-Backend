import argparse
import json
from pathlib import Path

from send_camera_image import create_multipart_body
from urllib import error, request

CLASS_NAMES = [
    "ALGAL_LEAF_SPOT",
    "ALLOCARIDARA_ATTACK",
    "HEALTHY_LEAF",
    "LEAF_BLIGHT",
    "PHOMOPSIS_LEAF_SPOT",
]
IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Evaluate the complete DurianCare prediction HTTP pipeline.",
    )
    parser.add_argument("--data-dir", type=Path, required=True)
    parser.add_argument(
        "--url",
        default="http://localhost:8000/api/v1/predict",
    )
    parser.add_argument("--device-id", default="EVALUATION-CAMERA")
    parser.add_argument(
        "--limit-per-class",
        type=int,
        default=0,
        help="Use zero to evaluate every image.",
    )
    return parser.parse_args()


def predict(image_path: Path, url: str, device_id: str) -> tuple[str, bool]:
    body, boundary = create_multipart_body(image_path, device_id)
    prediction_request = request.Request(
        url,
        data=body,
        method="POST",
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
    )
    try:
        with request.urlopen(prediction_request, timeout=120) as response:
            result = json.loads(response.read().decode("utf-8"))
    except error.HTTPError as exception:
        detail = exception.read().decode("utf-8")
        raise RuntimeError(
            f"HTTP {exception.code} for {image_path.name}: {detail}"
        ) from exception
    data = result["data"]
    return data["predicted_disease"], data["used_detection_crop"]


def calculate_report(confusion: list[list[int]]) -> dict[str, object]:
    total = sum(sum(row) for row in confusion)
    correct = sum(confusion[index][index] for index in range(len(CLASS_NAMES)))
    class_metrics: dict[str, dict[str, float | int]] = {}
    f1_scores: list[float] = []

    for index, class_name in enumerate(CLASS_NAMES):
        true_positive = confusion[index][index]
        predicted_total = sum(row[index] for row in confusion)
        actual_total = sum(confusion[index])
        precision = true_positive / predicted_total if predicted_total else 0.0
        recall = true_positive / actual_total if actual_total else 0.0
        f1 = (
            2 * precision * recall / (precision + recall)
            if precision + recall
            else 0.0
        )
        f1_scores.append(f1)
        class_metrics[class_name] = {
            "precision": round(precision, 4),
            "recall": round(recall, 4),
            "f1": round(f1, 4),
            "support": actual_total,
        }

    return {
        "samples": total,
        "accuracy": round(correct / total, 4) if total else 0.0,
        "macro_f1": round(sum(f1_scores) / len(f1_scores), 4),
        "per_class": class_metrics,
        "confusion_matrix": confusion,
    }


def main() -> None:
    args = parse_args()
    confusion = [
        [0 for _ in CLASS_NAMES]
        for _ in CLASS_NAMES
    ]
    crop_total = 0
    crop_correct = 0
    original_total = 0
    original_correct = 0

    for actual_index, class_name in enumerate(CLASS_NAMES):
        class_directory = args.data_dir / class_name
        if not class_directory.is_dir():
            raise FileNotFoundError(
                f"Missing class directory: {class_directory}"
            )
        images = sorted(
            path
            for path in class_directory.iterdir()
            if path.is_file() and path.suffix.lower() in IMAGE_SUFFIXES
        )
        if args.limit_per_class > 0:
            images = images[: args.limit_per_class]

        for image_index, image_path in enumerate(images, start=1):
            predicted_label, used_crop = predict(
                image_path,
                args.url,
                args.device_id,
            )
            predicted_index = CLASS_NAMES.index(predicted_label)
            confusion[actual_index][predicted_index] += 1
            is_correct = predicted_label == class_name
            if used_crop:
                crop_total += 1
                crop_correct += int(is_correct)
            else:
                original_total += 1
                original_correct += int(is_correct)
            print(
                f"[{class_name} {image_index}/{len(images)}] "
                f"{image_path.name} -> {predicted_label} "
                f"(crop={used_crop})",
                flush=True,
            )

    report = calculate_report(confusion)
    report["detection_crop"] = {
        "images": crop_total,
        "accuracy": round(crop_correct / crop_total, 4) if crop_total else None,
    }
    report["original_image_fallback"] = {
        "images": original_total,
        "accuracy": (
            round(original_correct / original_total, 4)
            if original_total
            else None
        ),
    }
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
