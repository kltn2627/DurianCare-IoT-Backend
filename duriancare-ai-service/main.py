from contextlib import asynccontextmanager
from datetime import datetime, timezone
import hashlib
import json
import os
import logging
from pathlib import Path

from fastapi import FastAPI

from app.api.chat import router as chat_router
from app.api.admin_rag import router as admin_rag_router
from app.api.rag import router as rag_router
from app.api.predict import router as prediction_router
from app.db.migration_runner import run_ai_database_migrations
from app.decision.decision_service import DecisionSupportService
from app.core.config import settings
from app.repositories.knowledge_repository import KnowledgeRepository
from app.services.disease_classifier import DoubleModelDiseaseClassifier
from app.services.recommendation_service import RecommendationService
from app.services.rag_service import RagService
from app.services.s3_storage import S3ImageStorage
from app.core.config import REPO_ROOT, SERVICE_ROOT

logger = logging.getLogger(__name__)

RUNTIME_HASH_FILES = (
    SERVICE_ROOT / "app" / "api" / "predict.py",
    SERVICE_ROOT / "app" / "services" / "disease_classifier.py",
    SERVICE_ROOT / "app" / "services" / "leaf_pipeline.py",
)


def _sha256_file(path: Path) -> str | None:
    if not path.is_file():
        return None
    digest = hashlib.sha256()
    with path.open("rb") as file_handle:
        for chunk in iter(lambda: file_handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _combined_sha256(paths: tuple[Path, ...]) -> str | None:
    digests = [_sha256_file(path) for path in paths]
    if any(digest is None for digest in digests):
        return None
    digest = hashlib.sha256()
    for item in digests:
        digest.update(item.encode("utf-8"))
    return digest.hexdigest()


def _resolve_git_commit(repo_root: Path) -> str | None:
    git_dir = repo_root / ".git"
    head_path = git_dir / "HEAD"
    if not head_path.is_file():
        return os.getenv("AI_GIT_COMMIT") or os.getenv("GIT_COMMIT")

    head_value = head_path.read_text(encoding="utf-8").strip()
    if not head_value:
        return os.getenv("AI_GIT_COMMIT") or os.getenv("GIT_COMMIT")

    if not head_value.startswith("ref: "):
        return head_value

    ref_name = head_value.removeprefix("ref: ").strip()
    ref_path = git_dir / ref_name
    if ref_path.is_file():
        ref_value = ref_path.read_text(encoding="utf-8").strip()
        if ref_value:
            return ref_value

    packed_refs_path = git_dir / "packed-refs"
    if packed_refs_path.is_file():
        for raw_line in packed_refs_path.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#") or line.startswith("^"):
                continue
            commit_hash, packed_ref = line.split(" ", 1)
            if packed_ref.strip() == ref_name:
                return commit_hash.strip()

    return os.getenv("AI_GIT_COMMIT") or os.getenv("GIT_COMMIT")


def _build_runtime_info(classifier: DoubleModelDiseaseClassifier | None) -> dict[str, object]:
    detector_path = Path(settings.yolo_model)
    classifier_path = Path(settings.classifier_weights)
    validator_path: Path | None = None
    if classifier is not None and getattr(classifier.pipeline, "debug_root", None) is not None:
        validator_candidate = SERVICE_ROOT / "app" / "services" / "leaf_validator.py"
        if validator_candidate.is_file():
            validator_path = validator_candidate.resolve()

    runtime_info: dict[str, object] = {
        "git_commit": _resolve_git_commit(REPO_ROOT),
        "detector_model": str(detector_path.resolve()),
        "detector_model_filename": detector_path.name,
        "detector_sha256": _sha256_file(detector_path.resolve()),
        "code_sha256": _combined_sha256(RUNTIME_HASH_FILES),
        "build_time": os.getenv(
            "AI_BUILD_TIME",
            datetime.now(timezone.utc).isoformat(),
        ),
        "ai_version": app.version,
        "cwd": os.getcwd(),
        "detector_abs_path": str(detector_path.resolve()),
        "classifier_abs_path": str(classifier_path.resolve()),
        "validator_abs_path": str(validator_path) if validator_path is not None else None,
        "yolo_version": "YOLOv8",
        "ultralytics_version": None,
        "torch_version": None,
        "device": classifier.device_name if classifier is not None else "unavailable",
        "enable_yolo_crop": settings.yolo_crop_enabled,
        "ai_debug": os.getenv("AI_DEBUG", "false"),
        "yolo_model_path": os.getenv("YOLO_MODEL_PATH", settings.yolo_model),
        "classifier_weight_path": str(classifier_path.resolve()),
        "predict_py_sha256": _sha256_file(SERVICE_ROOT / "app" / "api" / "predict.py"),
        "disease_classifier_py_sha256": _sha256_file(
            SERVICE_ROOT / "app" / "services" / "disease_classifier.py"
        ),
        "leaf_pipeline_py_sha256": _sha256_file(
            SERVICE_ROOT / "app" / "services" / "leaf_pipeline.py"
        ),
    }

    try:
        import torch  # type: ignore
        import ultralytics  # type: ignore

        runtime_info["ultralytics_version"] = ultralytics.__version__
        runtime_info["torch_version"] = torch.__version__
    except Exception:
        runtime_info["ultralytics_version"] = "unavailable"
        runtime_info["torch_version"] = "unavailable"

    if classifier is not None:
        detector = getattr(classifier, "detector", None)
        if detector is not None:
            runtime_info["detector_abs_path"] = str(
                Path(getattr(detector, "ckpt_path", detector_path)).resolve()
            )
        runtime_info["device"] = getattr(classifier, "device_name", "cpu")

    return runtime_info


@asynccontextmanager
async def lifespan(app: FastAPI):
    classifier = DoubleModelDiseaseClassifier(settings)
    app.state.disease_classifier = None
    app.state.model_load_error = None
    app.state.rag_service = None
    app.state.rag_load_error = None
    app.state.settings = settings
    app.state.recommendation_service = None
    app.state.recommendation_load_error = None
    app.state.recommendation_repository = None
    app.state.decision_service = None
    app.state.decision_load_error = None
    app.state.s3_storage = None
    app.state.s3_load_error = None
    app.state.ai_migration_error = None
    app.state.ai_migration_versions = []
    try:
        app.state.ai_migration_versions = run_ai_database_migrations(
            settings.postgres_url,
            settings.knowledge_db_schema,
            migration_dir=Path(__file__).resolve().parent / "db" / "migration",
        )
    except Exception as exception:
        logger.exception("Failed to run AI database migrations")
        app.state.ai_migration_error = str(exception)
        raise

    try:
        classifier.load_models()
        app.state.disease_classifier = classifier
    except Exception as exception:
        logger.exception("Failed to load disease prediction models")
        app.state.model_load_error = str(exception)

    rag_service = RagService(settings)
    try:
        rag_service.initialize()
        app.state.rag_service = rag_service
    except Exception as exception:
        logger.exception("Failed to initialize RAG service")
        app.state.rag_load_error = str(exception)

    try:
        app.state.s3_storage = S3ImageStorage(settings)
    except Exception as exception:
        logger.exception("Failed to initialize S3 image storage")
        app.state.s3_load_error = str(exception)

    try:
        recommendation_repository = KnowledgeRepository(
            settings.postgres_url,
            settings.knowledge_db_schema,
        )
        app.state.recommendation_repository = recommendation_repository
        app.state.recommendation_service = RecommendationService(
            recommendation_repository,
        )
    except Exception as exception:
        logger.exception("Failed to initialize recommendation service")
        app.state.recommendation_load_error = str(exception)

    try:
        app.state.decision_service = DecisionSupportService()
    except Exception as exception:
        logger.exception("Failed to initialize decision support service")
        app.state.decision_load_error = str(exception)

    runtime_info = _build_runtime_info(app.state.disease_classifier)
    app.state.runtime_info = runtime_info
    logger.info("AI runtime info: %s", json.dumps(runtime_info, ensure_ascii=False, sort_keys=True))
    print(
        f"AI runtime info: {json.dumps(runtime_info, ensure_ascii=False, sort_keys=True)}",
        flush=True,
    )
    logger.info(
        "AI runtime assets: cwd=%s detector_model=%s detector_file=%s detector_sha256=%s "
        "predict_py_sha256=%s disease_classifier_sha256=%s leaf_pipeline_sha256=%s "
        "classifier_abs_path=%s validator_abs_path=%s device=%s enable_yolo_crop=%s ai_debug=%s "
        "yolo_version=%s ultralytics_version=%s torch_version=%s",
        runtime_info.get("cwd"),
        runtime_info.get("detector_model"),
        runtime_info.get("detector_model_filename"),
        runtime_info.get("detector_sha256"),
        runtime_info.get("predict_py_sha256"),
        runtime_info.get("disease_classifier_py_sha256"),
        runtime_info.get("leaf_pipeline_py_sha256"),
        runtime_info.get("classifier_abs_path"),
        runtime_info.get("validator_abs_path"),
        runtime_info.get("device"),
        runtime_info.get("enable_yolo_crop"),
        runtime_info.get("ai_debug"),
        runtime_info.get("yolo_version"),
        runtime_info.get("ultralytics_version"),
        runtime_info.get("torch_version"),
    )
    print(
        "AI runtime assets: "
        f"cwd={runtime_info.get('cwd')} "
        f"detector_model={runtime_info.get('detector_model')} "
        f"detector_file={runtime_info.get('detector_model_filename')} "
        f"detector_sha256={runtime_info.get('detector_sha256')} "
        f"predict_py_sha256={runtime_info.get('predict_py_sha256')} "
        f"disease_classifier_py_sha256={runtime_info.get('disease_classifier_py_sha256')} "
        f"leaf_pipeline_py_sha256={runtime_info.get('leaf_pipeline_py_sha256')} "
        f"classifier_abs_path={runtime_info.get('classifier_abs_path')} "
        f"validator_abs_path={runtime_info.get('validator_abs_path')} "
        f"device={runtime_info.get('device')} "
        f"enable_yolo_crop={runtime_info.get('enable_yolo_crop')} "
        f"ai_debug={runtime_info.get('ai_debug')} "
        f"yolo_version={runtime_info.get('yolo_version')} "
        f"ultralytics_version={runtime_info.get('ultralytics_version')} "
        f"torch_version={runtime_info.get('torch_version')}",
        flush=True,
    )

    yield
    app.state.disease_classifier = None
    app.state.rag_service = None
    recommendation_repository = getattr(app.state, "recommendation_repository", None)
    if recommendation_repository is not None:
        recommendation_repository.close()
    app.state.recommendation_repository = None
    app.state.recommendation_service = None
    app.state.decision_service = None
    app.state.s3_storage = None
    app.state.runtime_info = None


app = FastAPI(
    title="DurianCare AI Service",
    version="0.3.0",
    lifespan=lifespan,
)
app.include_router(prediction_router)
app.include_router(chat_router)
app.include_router(rag_router)
app.include_router(admin_rag_router)


@app.get("/api/v1/runtime-info")
def runtime_info() -> dict[str, object]:
    runtime_info_state = getattr(app.state, "runtime_info", None)
    runtime_info_state = (
        runtime_info_state
        if runtime_info_state is not None
        else _build_runtime_info(getattr(app.state, "disease_classifier", None))
    )
    return {
        "git_commit": runtime_info_state.get("git_commit"),
        "detector_model": runtime_info_state.get("detector_model"),
        "detector_sha256": runtime_info_state.get("detector_sha256"),
        "code_sha256": runtime_info_state.get("code_sha256"),
        "build_time": runtime_info_state.get("build_time"),
        "ai_version": runtime_info_state.get("ai_version"),
    }


@app.get("/actuator/health")
def health() -> dict[str, object]:
    classifier = getattr(app.state, "disease_classifier", None)
    rag_service = getattr(app.state, "rag_service", None)
    recommendation_service = getattr(app.state, "recommendation_service", None)
    decision_service = getattr(app.state, "decision_service", None)
    rag_status = rag_service.status() if rag_service is not None else {}
    if classifier is not None and rag_service is not None:
        service_status = "UP"
    elif classifier is not None or rag_service is not None:
        service_status = "DEGRADED"
    else:
        service_status = "DOWN"

    response: dict[str, str | bool] = {
        "status": service_status,
        "service": "duriancare-ai-service",
        "modelsLoaded": classifier is not None,
        "device": classifier.device.type if classifier is not None else "unavailable",
        "databaseReady": bool(rag_status.get("databaseReady")),
        "embeddingReady": bool(rag_status.get("embeddingReady")),
        "embeddingModel": rag_status.get("embeddingModel"),
        "vectorStoreReady": bool(rag_status.get("vectorStoreReady")),
        "ragReady": bool(rag_status.get("ragReady")),
        "geminiReady": bool(rag_status.get("geminiReady")),
        "llmReady": bool(rag_status.get("llmReady")),
        "recommendationReady": recommendation_service is not None,
        "decisionSupportReady": decision_service is not None,
        "cacheReady": bool(rag_status.get("cacheReady")),
        "cacheStatus": rag_status.get("cacheStatus", "disabled"),
        "documentsIndexed": rag_status.get("documentsIndexed", 0),
        "indexedDocuments": rag_status.get("indexedDocuments", []),
        "s3StorageEnabled": bool(
            getattr(app.state, "s3_storage", None)
            and app.state.s3_storage.enabled
        ),
    }
    model_load_error = getattr(app.state, "model_load_error", None)
    if model_load_error:
        response["modelLoadError"] = model_load_error
    rag_load_error = getattr(app.state, "rag_load_error", None)
    if rag_load_error:
        response["ragLoadError"] = rag_load_error
    recommendation_load_error = getattr(
        app.state,
        "recommendation_load_error",
        None,
    )
    if recommendation_load_error:
        response["recommendationLoadError"] = recommendation_load_error
    decision_load_error = getattr(app.state, "decision_load_error", None)
    if decision_load_error:
        response["decisionLoadError"] = decision_load_error
    s3_load_error = getattr(app.state, "s3_load_error", None)
    if s3_load_error:
        response["s3LoadError"] = s3_load_error
    return response
