import argparse
import json
from pathlib import Path

import torch
from torch import nn
from torch.utils.data import DataLoader
from torchvision import datasets, models, transforms

CLASS_NAMES = [
    "ALGAL_LEAF_SPOT",
    "ALLOCARIDARA_ATTACK",
    "HEALTHY_LEAF",
    "LEAF_BLIGHT",
    "PHOMOPSIS_LEAF_SPOT",
]

IMAGE_TRANSFORM = transforms.Compose(
    [
        transforms.Resize((224, 224)),
        transforms.ToTensor(),
        transforms.Normalize(
            mean=[0.485, 0.456, 0.406],
            std=[0.229, 0.224, 0.225],
        ),
    ]
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Evaluate the DurianCare MobileNetV2 checkpoint.",
    )
    parser.add_argument(
        "--data-dir",
        type=Path,
        required=True,
        help="ImageFolder test directory containing the five class folders.",
    )
    parser.add_argument(
        "--weights",
        type=Path,
        default=Path("models/mobilenetv2_classifier_high_acc.pth"),
    )
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--workers", type=int, default=0)
    return parser.parse_args()


def normalize_state_dict(checkpoint: object) -> dict[str, torch.Tensor]:
    if isinstance(checkpoint, dict):
        for key in ("state_dict", "model_state_dict"):
            nested = checkpoint.get(key)
            if isinstance(nested, dict):
                checkpoint = nested
                break
    if not isinstance(checkpoint, dict):
        raise ValueError("Checkpoint does not contain a valid state_dict")
    return {
        key.removeprefix("module.").removeprefix("model."): value
        for key, value in checkpoint.items()
    }


def load_model(weights: Path, device: torch.device) -> nn.Module:
    if not weights.is_file():
        raise FileNotFoundError(f"Weights file not found: {weights}")
    model = models.mobilenet_v2(weights=None)
    in_features = model.classifier[1].in_features
    model.classifier[1] = nn.Linear(in_features, len(CLASS_NAMES))
    checkpoint = torch.load(weights, map_location=device, weights_only=True)
    model.load_state_dict(normalize_state_dict(checkpoint), strict=True)
    return model.to(device).eval()


def calculate_metrics(confusion: torch.Tensor) -> dict[str, object]:
    true_positive = confusion.diag().float()
    predicted_total = confusion.sum(dim=0).float()
    actual_total = confusion.sum(dim=1).float()
    precision = true_positive / predicted_total.clamp_min(1)
    recall = true_positive / actual_total.clamp_min(1)
    f1 = 2 * precision * recall / (precision + recall).clamp_min(1e-12)
    total = confusion.sum().item()
    accuracy = true_positive.sum().item() / total if total else 0.0

    per_class = {
        class_name: {
            "precision": round(precision[index].item(), 4),
            "recall": round(recall[index].item(), 4),
            "f1": round(f1[index].item(), 4),
            "support": int(actual_total[index].item()),
        }
        for index, class_name in enumerate(CLASS_NAMES)
    }
    return {
        "accuracy": round(accuracy, 4),
        "macro_precision": round(precision.mean().item(), 4),
        "macro_recall": round(recall.mean().item(), 4),
        "macro_f1": round(f1.mean().item(), 4),
        "per_class": per_class,
        "confusion_matrix": confusion.tolist(),
    }


def main() -> None:
    args = parse_args()
    dataset = datasets.ImageFolder(args.data_dir, transform=IMAGE_TRANSFORM)
    if dataset.classes != CLASS_NAMES:
        raise ValueError(
            "Class folders must be named and ordered exactly as follows: "
            + ", ".join(CLASS_NAMES)
            + f". Found: {dataset.classes}"
        )

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = load_model(args.weights, device)
    loader = DataLoader(
        dataset,
        batch_size=args.batch_size,
        shuffle=False,
        num_workers=args.workers,
        pin_memory=device.type == "cuda",
    )
    confusion = torch.zeros(
        (len(CLASS_NAMES), len(CLASS_NAMES)),
        dtype=torch.int64,
    )

    with torch.inference_mode():
        for images, labels in loader:
            predictions = model(images.to(device)).argmax(dim=1).cpu()
            for actual, predicted in zip(labels, predictions, strict=True):
                confusion[actual, predicted] += 1

    report = {
        "device": device.type,
        "samples": len(dataset),
        "classes": CLASS_NAMES,
        **calculate_metrics(confusion),
    }
    print(json.dumps(report, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
