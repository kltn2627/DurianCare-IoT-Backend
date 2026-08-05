import unittest

from PIL import Image

from app.services.leaf_pipeline import AdaptiveLeafPipeline, DetectionBox


class LeafPipelineTest(unittest.TestCase):
    def setUp(self) -> None:
        self.pipeline = AdaptiveLeafPipeline(
            debug_root=None,
            capture_hard_examples=False,
            min_crop_quality=0.25,
        )

    def test_build_variants_adds_adaptive_enhancements_for_dark_images(self) -> None:
        dark_image = Image.new("RGB", (512, 512), color=(18, 20, 22))

        variants = self.pipeline._build_variants(dark_image)

        self.assertGreaterEqual(len(variants), 2)
        self.assertEqual("original", variants[0].name)
        self.assertTrue(
            any(
                variant.name in {"autocontrast", "gamma_up_1_2"}
                for variant in variants[1:]
            )
        )

    def test_deduplicate_detections_prefers_highest_quality_box(self) -> None:
        detections = [
            DetectionBox(
                left=10,
                top=10,
                right=210,
                bottom=210,
                confidence=0.92,
                class_id=0,
                class_name="leaf",
                source_variant="original",
                confidence_threshold=0.25,
                iou_threshold=0.55,
                scale=1.0,
                area_ratio=0.16,
                crop_quality=0.48,
            ),
            DetectionBox(
                left=14,
                top=14,
                right=206,
                bottom=206,
                confidence=0.78,
                class_id=0,
                class_name="leaf",
                source_variant="enhanced",
                confidence_threshold=0.20,
                iou_threshold=0.55,
                scale=1.0,
                area_ratio=0.15,
                crop_quality=0.51,
            ),
            DetectionBox(
                left=260,
                top=260,
                right=430,
                bottom=430,
                confidence=0.81,
                class_id=0,
                class_name="leaf",
                source_variant="enhanced",
                confidence_threshold=0.20,
                iou_threshold=0.55,
                scale=1.0,
                area_ratio=0.11,
                crop_quality=0.44,
            ),
        ]

        unique = self.pipeline._deduplicate_detections(detections)

        self.assertEqual(2, len(unique))
        self.assertEqual(0.92, unique[0].confidence)
        self.assertEqual((260, 260, 430, 430), (unique[1].left, unique[1].top, unique[1].right, unique[1].bottom))

    def test_score_crop_rejects_tiny_crop(self) -> None:
        tiny_crop = Image.new("RGB", (32, 32), color=(50, 120, 50))

        score = self.pipeline.score_crop(tiny_crop, (0, 0, 32, 32), (512, 512))

        self.assertEqual(0.0, score)


if __name__ == "__main__":
    unittest.main()
