from io import BytesIO
from pathlib import Path
from uuid import uuid4
import logging

from fastapi import (
    APIRouter,
    File,
    Form,
    HTTPException,
    Query,
    Request,
    UploadFile,
    status,
)
from PIL import Image, ImageOps, UnidentifiedImageError
from starlette.responses import FileResponse
from starlette.concurrency import run_in_threadpool
from starlette.datastructures import Headers

from app.schemas.prediction import (
    BoundingBox,
    PredictionAlternative,
    PredictionData,
    PredictionHistoryImage,
    PredictionHistoryItem,
    PredictionHistoryResponse,
    PredictionResponse,
    PredictionSource,
    S3PredictionRequest,
    StoredImageInfo,
)
from app.decision.decision_service import DecisionSupportService
from app.services.s3_storage import S3ImageStorage, StorageError, StoredImage
from app.services.disease_classifier import (
    DoubleModelDiseaseClassifier,
    PredictionError,
)
from app.services.recommendation_service import RecommendationService
from app.repositories.prediction_history_repository import PredictionHistoryRepository

router = APIRouter(tags=["Disease Prediction"])
logger = logging.getLogger(__name__)


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


def get_recommendation_service(request: Request) -> RecommendationService | None:
    return getattr(request.app.state, "recommendation_service", None)


def get_decision_service(request: Request) -> DecisionSupportService:
    decision_service = getattr(request.app.state, "decision_service", None)
    if decision_service is None:
        decision_service = DecisionSupportService()
    return decision_service


def get_history_repository(request: Request) -> PredictionHistoryRepository | None:
    return getattr(request.app.state, "prediction_history_repository", None)


def authenticated_user_id(request: Request) -> str | None:
    user_id = request.headers.get("x-auth-user-id")
    return user_id.strip() if user_id and user_id.strip() else None


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
            status_code=getattr(
                exception,
                "status_code",
                status.HTTP_500_INTERNAL_SERVER_ERROR,
            ),
            detail=str(exception),
        ) from exception

    recommendation = None
    recommendation_service = get_recommendation_service(request)
    if recommendation_service is not None:
        try:
            recommendation = await run_in_threadpool(
                recommendation_service.build_recommendation,
                prediction.label,
            )
        except Exception as exception:
            logger.warning(
                "Failed to build recommendation for %s: %s",
                prediction.label,
                exception,
                exc_info=True,
            )

    decision_service = get_decision_service(request)
    try:
        decision_support = await run_in_threadpool(
            decision_service.build_decision_support,
            prediction.label,
            float(prediction.confidence),
            recommendation,
        )
    except Exception as exception:
        logger.warning(
            "Failed to build decision support for %s: %s",
            prediction.label,
            exception,
            exc_info=True,
        )
        decision_support = None

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

    response = PredictionResponse(
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
            recommendation=recommendation,
            decision_support=decision_support,
            top_predictions=[
                PredictionAlternative(
                    label=item["label"],
                    confidence=float(item["confidence"]),
                )
                for item in getattr(prediction, "top_predictions", [])
            ],
        ),
    )
    try:
        await persist_prediction_history(
            request=request,
            response=response,
            image=image,
            image_content=content,
            stored_image=stored_image,
        )
    except Exception:
        logger.exception("Failed to save prediction history")
    return response


async def persist_prediction_history(
    *,
    request: Request,
    response: PredictionResponse,
    image: UploadFile,
    image_content: bytes,
    stored_image: StoredImage | None,
) -> None:
    user_id = authenticated_user_id(request)
    if not user_id:
        return

    history_repository = get_history_repository(request)
    if history_repository is None:
        logger.warning("Prediction history repository is not available")
        return

    image_path = None
    image_url = stored_image.url if stored_image is not None else None
    image_object_key = stored_image.object_key if stored_image is not None else None
    if image_url is None:
        image_path = await run_in_threadpool(
            save_history_image,
            image_content,
            image.filename,
            image.content_type,
        )

    recommendation = response.data.recommendation
    severity = None
    if recommendation is not None and recommendation.severity is not None:
        severity_value = recommendation.severity
        severity = (
            severity_value.value
            if hasattr(severity_value, "value")
            else str(severity_value)
        )

    await run_in_threadpool(
        history_repository.create,
        user_id=user_id,
        predicted_disease=response.data.predicted_disease,
        confidence=parse_confidence_percent(response.data.confidence),
        confidence_text=response.data.confidence,
        source=response.data.source.value,
        used_detection_crop=response.data.used_detection_crop,
        severity=severity,
        device_id=response.data.device_id,
        image_url=image_url,
        image_path=image_path,
        image_object_key=image_object_key,
        original_filename=image.filename,
        response_payload=response.model_dump(mode="json", by_alias=True),
    )


def save_history_image(
    image_content: bytes,
    filename: str | None,
    content_type: str | None,
) -> str:
    suffix = Path(filename or "").suffix.lower()
    if suffix not in {".jpg", ".jpeg", ".png", ".webp"}:
        suffix = ".png" if content_type == "image/png" else ".jpg"
    directory = Path("artifacts") / "prediction-history-images"
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / f"{uuid4()}{suffix}"
    path.write_bytes(image_content)
    return path.as_posix()


def parse_confidence_percent(value: str) -> float:
    try:
        return float(value.replace("%", "").strip())
    except ValueError:
        return 0.0


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


@router.get("/api/v1/predict/history", response_model=PredictionHistoryResponse)
async def list_prediction_history(
    request: Request,
    q: str | None = Query(default=None, max_length=120),
    status_filter: str | None = Query(default=None, alias="status", max_length=30),
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=5, ge=1, le=50, alias="pageSize"),
) -> PredictionHistoryResponse:
    user_id = authenticated_user_id(request)
    if not user_id:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authenticated user is required",
        )
    history_repository = get_history_repository(request)
    if history_repository is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Prediction history database is not available",
        )

    rows, total = await run_in_threadpool(
        history_repository.list_for_user,
        user_id,
        query=q,
        status=status_filter,
        limit=page_size,
        offset=(page - 1) * page_size,
    )
    return PredictionHistoryResponse(
        items=[to_history_item(row) for row in rows],
        total=total,
        page=page,
        page_size=page_size,
    )


@router.delete("/api/v1/predict/history/{history_id}")
async def delete_prediction_history(
    history_id: str,
    request: Request,
) -> dict[str, bool]:
    user_id = authenticated_user_id(request)
    if not user_id:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authenticated user is required",
        )
    history_repository = get_history_repository(request)
    if history_repository is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Prediction history database is not available",
        )
    deleted = await run_in_threadpool(
        history_repository.soft_delete,
        user_id,
        history_id,
    )
    if not deleted:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Prediction history record was not found",
        )
    return {"ok": True}


@router.get("/api/v1/predict/history/images/{history_id}")
async def get_prediction_history_image(
    history_id: str,
    request: Request,
) -> FileResponse:
    user_id = authenticated_user_id(request)
    if not user_id:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authenticated user is required",
        )
    history_repository = get_history_repository(request)
    if history_repository is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Prediction history database is not available",
        )
    image_path = await run_in_threadpool(
        history_repository.get_image_path_for_user,
        user_id,
        history_id,
    )
    if not image_path:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Prediction history image was not found",
        )
    resolved_path = Path(image_path).resolve()
    allowed_root = (Path("artifacts") / "prediction-history-images").resolve()
    if allowed_root not in resolved_path.parents:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Prediction history image was not found",
        )
    if not resolved_path.is_file():
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Prediction history image was not found",
        )
    return FileResponse(resolved_path)


def to_history_item(row: dict[str, object]) -> PredictionHistoryItem:
    payload = row.get("response_payload")
    payload_dict = payload if isinstance(payload, dict) else {}
    data_payload = payload_dict.get("data") if isinstance(payload_dict.get("data"), dict) else {}
    data_payload = {
        "predictedDisease": row.get("predicted_disease"),
        "confidence": row.get("confidence_text"),
        "source": row.get("source"),
        "deviceId": row.get("device_id"),
        "usedDetectionCrop": row.get("used_detection_crop"),
        **data_payload,
    }
    data = PredictionData.model_validate(data_payload)
    image = PredictionHistoryImage(
        url=str(row["image_url"]) if row.get("image_url") else None,
        path=str(row["image_path"]) if row.get("image_path") else None,
        object_key=str(row["image_object_key"]) if row.get("image_object_key") else None,
    )
    diagnosed_at = row.get("diagnosed_at") or row.get("created_at")
    return PredictionHistoryItem(
        id=str(row["id"]),
        diagnosed_at=(
            diagnosed_at.isoformat()
            if hasattr(diagnosed_at, "isoformat")
            else str(diagnosed_at)
        ),
        image=image if image.url or image.path or image.object_key else None,
        predicted_disease=str(row["predicted_disease"]),
        confidence=float(row["confidence"]),
        confidence_text=str(row["confidence_text"]),
        severity=str(row["severity"]) if row.get("severity") is not None else None,
        status=str(row["status"]),
        source=PredictionSource(str(row["source"])),
        device_id=str(row["device_id"]) if row.get("device_id") is not None else None,
        used_detection_crop=bool(row["used_detection_crop"]),
        original_filename=(
            str(row["original_filename"])
            if row.get("original_filename") is not None
            else None
        ),
        data=data,
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
