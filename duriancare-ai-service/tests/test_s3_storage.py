import unittest
from dataclasses import replace

from app.core.config import settings
from app.services.s3_storage import S3ImageStorage, StorageError


class FakeS3Client:
    def __init__(self) -> None:
        self.objects: dict[str, dict] = {}

    def put_object(self, **kwargs) -> None:
        self.objects[kwargs["Key"]] = kwargs

    def get_object(self, **kwargs) -> dict:
        stored = self.objects[kwargs["Key"]]

        class Body:
            def read(self, size: int = -1) -> bytes:
                return stored["Body"] if size < 0 else stored["Body"][:size]

            def close(self) -> None:
                pass

        return {
            "Body": Body(),
            "ContentLength": len(stored["Body"]),
            "ContentType": stored["ContentType"],
        }

    def generate_presigned_url(
        self,
        operation: str,
        Params: dict,
        ExpiresIn: int,
    ) -> str:
        return f"https://signed.example/{Params['Key']}?expires={ExpiresIn}"


class S3ImageStorageTest(unittest.TestCase):
    def test_download_bounds_stream_read_when_content_length_is_missing(self) -> None:
        from unittest.mock import Mock
        body = Mock()
        body.read.return_value = b"image-bytes"
        self.client.get_object = Mock(return_value={"Body": body, "ContentType": "image/jpeg"})
        self.storage.download_image("disease-images/fixture.jpg")
        body.read.assert_called_once_with(self.storage.settings.max_image_size_bytes + 1)
        body.close.assert_called_once()

    def setUp(self) -> None:
        configured_settings = replace(
            settings,
            s3_enabled=True,
            s3_bucket_name="test-bucket",
        )
        self.client = FakeS3Client()
        self.storage = S3ImageStorage(configured_settings, self.client)

    def test_upload_and_download_image(self) -> None:
        stored = self.storage.upload_image(
            b"image-bytes",
            "image/jpeg",
            "leaf.jpg",
        )

        content, content_type = self.storage.download_image(stored.object_key)

        self.assertTrue(stored.object_key.startswith("disease-images/"))
        self.assertTrue(stored.object_key.endswith(".jpg"))
        self.assertEqual(b"image-bytes", content)
        self.assertEqual("image/jpeg", content_type)
        self.assertTrue(stored.url.startswith("https://signed.example/"))

    def test_rejects_object_key_outside_image_prefix(self) -> None:
        with self.assertRaises(StorageError):
            self.storage.download_image("another-prefix/leaf.jpg")


if __name__ == "__main__":
    unittest.main()
