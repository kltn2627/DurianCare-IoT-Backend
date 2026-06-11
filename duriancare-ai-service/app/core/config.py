import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

SERVICE_ROOT = Path(__file__).resolve().parents[2]
load_dotenv(SERVICE_ROOT / ".env")


@dataclass(frozen=True)
class Settings:
    yolo_model: str
    classifier_weights: Path
    yolo_confidence: float
    yolo_crop_enabled: bool
    gemini_api_key: str | None
    gemini_chat_model: str
    gemini_embedding_model: str
    knowledge_base_path: Path
    chroma_path: Path
    rag_collection_name: str


def resolve_service_path(environment_name: str, default_relative_path: str) -> Path:
    configured_path = Path(
        os.getenv(environment_name, default_relative_path)
    ).expanduser()
    if configured_path.is_absolute():
        return configured_path.resolve()
    return (SERVICE_ROOT / configured_path).resolve()


settings = Settings(
    yolo_model=os.getenv("YOLO_MODEL_PATH", "yolov8m.pt"),
    classifier_weights=resolve_service_path(
        "MOBILENET_WEIGHTS_PATH",
        "models/mobilenetv2_classifier_high_acc.pth",
    ),
    yolo_confidence=float(os.getenv("YOLO_CONFIDENCE", "0.25")),
    yolo_crop_enabled=os.getenv(
        "ENABLE_YOLO_CROP",
        "false",
    ).lower() in {"1", "true", "yes"},
    gemini_api_key=os.getenv("GEMINI_API_KEY"),
    gemini_chat_model=os.getenv("GEMINI_CHAT_MODEL", "gemini-2.5-flash"),
    gemini_embedding_model=os.getenv(
        "GEMINI_EMBEDDING_MODEL",
        "gemini-embedding-001",
    ),
    knowledge_base_path=resolve_service_path(
        "KNOWLEDGE_BASE_PATH",
        "knowledge_base",
    ),
    chroma_path=resolve_service_path(
        "CHROMA_PERSIST_PATH",
        "vector_store",
    ),
    rag_collection_name=os.getenv(
        "RAG_COLLECTION_NAME",
        "duriancare_knowledge",
    ),
)
