import argparse
import json
import mimetypes
import uuid
from pathlib import Path
from urllib import error, request


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Simulate an IoT camera uploading an image to DurianCare.",
    )
    parser.add_argument("image", type=Path)
    parser.add_argument(
        "--url",
        default="http://localhost:8000/api/v1/predict",
    )
    parser.add_argument("--device-id", default="ESP32-CAM-DEMO-001")
    return parser.parse_args()


def create_multipart_body(
    image_path: Path,
    device_id: str,
) -> tuple[bytes, str]:
    boundary = f"----DurianCare{uuid.uuid4().hex}"
    content_type = mimetypes.guess_type(image_path.name)[0] or "image/jpeg"
    parts = [
        (
            f"--{boundary}\r\n"
            'Content-Disposition: form-data; name="source"\r\n\r\n'
            "IOT_CAMERA\r\n"
        ).encode(),
        (
            f"--{boundary}\r\n"
            'Content-Disposition: form-data; name="device_id"\r\n\r\n'
            f"{device_id}\r\n"
        ).encode(),
        (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="image"; '
            f'filename="{image_path.name}"\r\n'
            f"Content-Type: {content_type}\r\n\r\n"
        ).encode(),
        image_path.read_bytes(),
        f"\r\n--{boundary}--\r\n".encode(),
    ]
    return b"".join(parts), boundary


def main() -> None:
    args = parse_args()
    if not args.image.is_file():
        raise FileNotFoundError(f"Image not found: {args.image}")

    body, boundary = create_multipart_body(args.image, args.device_id)
    upload_request = request.Request(
        args.url,
        data=body,
        method="POST",
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
    )
    try:
        with request.urlopen(upload_request, timeout=120) as response:
            result = json.loads(response.read().decode("utf-8"))
    except error.HTTPError as exception:
        detail = exception.read().decode("utf-8")
        raise RuntimeError(
            f"Prediction API returned HTTP {exception.code}: {detail}"
        ) from exception

    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
