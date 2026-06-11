from enum import Enum
from typing import Literal

from pydantic import BaseModel


class PredictionSource(str, Enum):
    MOBILE = "MOBILE"
    WEB = "WEB"
    IOT_CAMERA = "IOT_CAMERA"


class BoundingBox(BaseModel):
    left: int
    top: int
    right: int
    bottom: int


class PredictionData(BaseModel):
    predicted_disease: str
    confidence: str
    source: PredictionSource
    device_id: str | None = None
    used_detection_crop: bool
    bounding_box: BoundingBox | None = None


class PredictionResponse(BaseModel):
    status: Literal["success"]
    data: PredictionData
