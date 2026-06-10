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
    openai_api_key: str | None
    openai_chat_model: str
    openai_embedding_model: str
    knowledge_base_path: Path
    chroma_path: Path
    rag_collection_name: str


settings = Settings(
    yolo_model=os.getenv("YOLO_MODEL_PATH", "yolov8m.pt"),
    classifier_weights=Path(
        os.getenv(
            "MOBILENET_WEIGHTS_PATH",
            str(SERVICE_ROOT / "models" / "mobilenetv2_classifier_high_acc.pth"),
        )
    ),
    yolo_confidence=float(os.getenv("YOLO_CONFIDENCE", "0.25")),
    yolo_crop_enabled=os.getenv(
        "ENABLE_YOLO_CROP",
        "false",
    ).lower() in {"1", "true", "yes"},
    openai_api_key=os.getenv("OPENAI_API_KEY"),
    openai_chat_model=os.getenv("OPENAI_CHAT_MODEL", "gpt-4.1-mini"),
    openai_embedding_model=os.getenv(
        "OPENAI_EMBEDDING_MODEL",
        "text-embedding-3-small",
    ),
    knowledge_base_path=Path(
        os.getenv("KNOWLEDGE_BASE_PATH", str(SERVICE_ROOT / "knowledge_base"))
    ),
    chroma_path=Path(
        os.getenv("CHROMA_PERSIST_PATH", str(SERVICE_ROOT / "vector_store"))
    ),
    rag_collection_name=os.getenv(
        "RAG_COLLECTION_NAME",
        "duriancare_knowledge",
    ),
)
