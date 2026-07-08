from io import BytesIO
from pathlib import Path

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
from starlette.datastructures import Headers

from app.schemas.prediction import (
    BoundingBox,
    PredictionData,
    PredictionResponse,
    PredictionSource,
    S3PredictionRequest,
    StoredImageInfo,
)
from app.services.s3_storage import S3ImageStorage, StorageError, StoredImage
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


def get_storage(request: Request) -> S3ImageStorage:
    storage = getattr(request.app.state, "s3_storage", None)
    if storage is None or not storage.enabled:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="S3 image storage is not configured",
        )
    return storage


def decode_image(content: bytes) -> Image.Image:
    try:
        with Image.open(BytesIO(content)) as uploaded_image:
            return ImageOps.exif_transpose(uploaded_image).convert("RGB")
    except (UnidentifiedImageError, OSError, ValueError) as exception:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Uploaded file is not a valid image",
        ) from exception


async def read_image(
    image: UploadFile,
    max_image_size_bytes: int,
) -> tuple[Image.Image, bytes]:
    if not image.content_type or not image.content_type.startswith("image/"):
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="An image file is required",
        )

    content = await image.read(max_image_size_bytes + 1)
    if not content:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Uploaded image is empty",
        )

    if len(content) > max_image_size_bytes:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=f"Image exceeds the {max_image_size_bytes} byte limit",
        )
    return decode_image(content), content


async def execute_prediction(
    request: Request,
    image: UploadFile,
    source: PredictionSource,
    device_id: str | None,
    stored_image: StoredImage | None = None,
) -> PredictionResponse:
    classifier = get_classifier(request)
    settings = request.app.state.settings
    pil_image, content = await read_image(image, settings.max_image_size_bytes)

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

    storage = getattr(request.app.state, "s3_storage", None)
    if stored_image is None and storage is not None and storage.enabled:
        try:
            stored_image = await run_in_threadpool(
                storage.upload_image,
                content,
                image.content_type or "image/jpeg",
                image.filename,
            )
        except StorageError as exception:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
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
            image=(
                StoredImageInfo(
                    object_key=stored_image.object_key,
                    url=stored_image.url,
                )
                if stored_image is not None
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


@router.post("/api/v1/predict/from-s3", response_model=PredictionResponse)
async def predict_from_s3(
    payload: S3PredictionRequest,
    request: Request,
) -> PredictionResponse:
    if payload.source == PredictionSource.IOT_CAMERA and not payload.device_id:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="device_id is required when source is IOT_CAMERA",
        )

    storage = get_storage(request)
    try:
        content, content_type = await run_in_threadpool(
            storage.download_image,
            payload.object_key,
        )
        url = await run_in_threadpool(
            storage.generate_presigned_url,
            payload.object_key,
        )
    except StorageError as exception:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=str(exception),
        ) from exception

    upload = UploadFile(
        filename=Path(payload.object_key).name,
        file=BytesIO(content),
        headers=Headers({"content-type": content_type}),
    )
    return await execute_prediction(
        request,
        upload,
        payload.source,
        payload.device_id,
        StoredImage(object_key=payload.object_key, url=url),
    )


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
