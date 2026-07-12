from contextlib import asynccontextmanager
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

logger = logging.getLogger(__name__)


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


app = FastAPI(
    title="DurianCare AI Service",
    version="0.3.0",
    lifespan=lifespan,
)
app.include_router(prediction_router)
app.include_router(chat_router)
app.include_router(rag_router)
app.include_router(admin_rag_router)


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
