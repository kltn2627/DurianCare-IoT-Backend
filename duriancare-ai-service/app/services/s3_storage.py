from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4

import boto3
from botocore.exceptions import BotoCoreError, ClientError

from app.core.config import Settings


class StorageError(RuntimeError):
    pass


@dataclass(frozen=True)
class StoredImage:
    object_key: str
    url: str


class S3ImageStorage:
    _ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}

    def __init__(self, settings: Settings, client=None):
        self.settings = settings
        self.enabled = settings.s3_enabled
        self.bucket_name = settings.s3_bucket_name
        self.client = client

        if self.enabled and not self.bucket_name:
            raise ValueError("AWS_S3_BUCKET is required when S3_ENABLED=true")
        if self.enabled and self.client is None:
            self.client = boto3.client("s3", region_name=settings.aws_region)

    def upload_image(
        self,
        content: bytes,
        content_type: str,
        original_filename: str | None,
    ) -> StoredImage:
        self._require_enabled()
        object_key = self._build_object_key(original_filename)
        try:
            self.client.put_object(
                Bucket=self.bucket_name,
                Key=object_key,
                Body=content,
                ContentType=content_type,
                ServerSideEncryption="AES256",
            )
            return StoredImage(
                object_key=object_key,
                url=self.generate_presigned_url(object_key),
            )
        except (BotoCoreError, ClientError) as exception:
            raise StorageError("Could not upload image to S3") from exception

    def download_image(self, object_key: str) -> tuple[bytes, str]:
        self._require_enabled()
        self._validate_object_key(object_key)
        try:
            response = self.client.get_object(
                Bucket=self.bucket_name,
                Key=object_key,
            )
            content_length = response.get("ContentLength")
            if (
                content_length is not None
                and content_length > self.settings.max_image_size_bytes
            ):
                raise StorageError("S3 image exceeds the configured size limit")
            content = response["Body"].read()
            if len(content) > self.settings.max_image_size_bytes:
                raise StorageError("S3 image exceeds the configured size limit")
            content_type = response.get("ContentType", "application/octet-stream")
            return content, content_type
        except (BotoCoreError, ClientError) as exception:
            raise StorageError("Could not download image from S3") from exception

    def generate_presigned_url(self, object_key: str) -> str:
        self._require_enabled()
        self._validate_object_key(object_key)
        try:
            return self.client.generate_presigned_url(
                "get_object",
                Params={"Bucket": self.bucket_name, "Key": object_key},
                ExpiresIn=self.settings.s3_presigned_url_expiration_seconds,
            )
        except (BotoCoreError, ClientError) as exception:
            raise StorageError("Could not generate S3 image URL") from exception

    def _build_object_key(self, original_filename: str | None) -> str:
        extension = Path(original_filename or "").suffix.lower()
        if extension not in self._ALLOWED_EXTENSIONS:
            extension = ".jpg"
        date_path = datetime.now(timezone.utc).strftime("%Y/%m/%d")
        prefix = self.settings.s3_image_prefix
        return f"{prefix}/{date_path}/{uuid4().hex}{extension}"

    def _validate_object_key(self, object_key: str) -> None:
        expected_prefix = f"{self.settings.s3_image_prefix}/"
        if not object_key.startswith(expected_prefix) or ".." in object_key:
            raise StorageError("Invalid S3 object key")

    def _require_enabled(self) -> None:
        if not self.enabled or self.client is None or not self.bucket_name:
            raise StorageError("S3 image storage is not configured")
