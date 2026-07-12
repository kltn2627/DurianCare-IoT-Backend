from enum import Enum
from typing import Literal

from pydantic import BaseModel, Field

from app.schemas.decision_support import CamelModel, DecisionSupport
from app.schemas.recommendation import DiseaseRecommendation


class PredictionSource(str, Enum):
    MOBILE = "MOBILE"
    WEB = "WEB"
    IOT_CAMERA = "IOT_CAMERA"


class BoundingBox(CamelModel):
    left: int
    top: int
    right: int
    bottom: int


class StoredImageInfo(CamelModel):
    object_key: str
    url: str


class S3PredictionRequest(BaseModel):
    object_key: str = Field(min_length=1, max_length=1024)
    source: PredictionSource = PredictionSource.IOT_CAMERA
    device_id: str | None = Field(default=None, max_length=150)


class PredictionData(CamelModel):
    predicted_disease: str
    confidence: str
    source: PredictionSource
    device_id: str | None = None
    used_detection_crop: bool
    bounding_box: BoundingBox | None = None
    image: StoredImageInfo | None = None
    recommendation: DiseaseRecommendation | None = None
    decision_support: DecisionSupport | None = Field(
        default=None,
        alias="decisionSupport",
    )


class PredictionResponse(CamelModel):
    status: Literal["success"]
    data: PredictionData
