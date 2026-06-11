from io import BytesIO

from fastapi import (
    APIRouter,
    File,
    Form,
    HTTPException,
    Request,
    UploadFile,
    status,
)
from PIL import Image, ImageOps, UnidentifiedImageError
from starlette.concurrency import run_in_threadpool

from app.schemas.prediction import (
    BoundingBox,
    PredictionData,
    PredictionResponse,
    PredictionSource,
)
from app.services.disease_classifier import (
    DoubleModelDiseaseClassifier,
    PredictionError,
)

router = APIRouter(tags=["Disease Prediction"])


def get_classifier(request: Request) -> DoubleModelDiseaseClassifier:
    classifier = getattr(request.app.state, "disease_classifier", None)
    if classifier is None:
        model_load_error = getattr(request.app.state, "model_load_error", None)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=model_load_error or "Disease prediction models are not available",
        )
    return classifier


async def read_image(image: UploadFile) -> Image.Image:
    if not image.content_type or not image.content_type.startswith("image/"):
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="An image file is required",
        )

    content = await image.read()
    if not content:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Uploaded image is empty",
        )

    try:
        with Image.open(BytesIO(content)) as uploaded_image:
            return ImageOps.exif_transpose(uploaded_image).convert("RGB")
    except (UnidentifiedImageError, OSError, ValueError) as exception:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Uploaded file is not a valid image",
        ) from exception


async def execute_prediction(
    request: Request,
    image: UploadFile,
    source: PredictionSource,
    device_id: str | None,
) -> PredictionResponse:
    classifier = get_classifier(request)
    pil_image = await read_image(image)

    try:
        prediction = await run_in_threadpool(
            classifier.predict,
            pil_image,
        )
    except PredictionError as exception:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(exception),
        ) from exception

    return PredictionResponse(
        status="success",
        data=PredictionData(
            predicted_disease=prediction.label,
            confidence=f"{prediction.confidence:.2f}%",
            source=source,
            device_id=device_id,
            used_detection_crop=prediction.used_detection_crop,
            bounding_box=(
                BoundingBox(
                    left=prediction.bounding_box[0],
                    top=prediction.bounding_box[1],
                    right=prediction.bounding_box[2],
                    bottom=prediction.bounding_box[3],
                )
                if prediction.bounding_box is not None
                else None
            ),
        ),
    )


@router.post("/api/v1/predict", response_model=PredictionResponse)
async def predict(
    request: Request,
    image: UploadFile = File(...),
    source: PredictionSource = Form(PredictionSource.MOBILE),
    device_id: str | None = Form(None, max_length=150),
) -> PredictionResponse:
    if source == PredictionSource.IOT_CAMERA and not device_id:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="device_id is required when source is IOT_CAMERA",
        )
    return await execute_prediction(request, image, source, device_id)


@router.post(
    "/api/ai/diagnoses",
    response_model=PredictionResponse,
    deprecated=True,
)
async def diagnose_legacy(
    request: Request,
    image: UploadFile = File(...),
) -> PredictionResponse:
    return await execute_prediction(
        request,
        image,
        PredictionSource.MOBILE,
        None,
    )
