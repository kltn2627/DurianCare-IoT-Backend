import os
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import quote_plus

SERVICE_ROOT = Path(__file__).resolve().parents[2]
REPO_ROOT = Path(__file__).resolve().parents[3]


def load_environment_file(dotenv_path: Path) -> None:
    if not dotenv_path.is_file():
        return

    for raw_line in dotenv_path.read_text(encoding="utf-8-sig").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if not key or key in os.environ:
            continue
        if (
            len(value) >= 2
            and value[0] == value[-1]
            and value[0] in {'"', "'"}
        ):
            value = value[1:-1]
        os.environ[key] = value


for dotenv_path in (SERVICE_ROOT / ".env", REPO_ROOT / ".env"):
    load_environment_file(dotenv_path)


@dataclass(frozen=True)
class Settings:
    yolo_model: str
    yolo_fallback_model: str
    classifier_weights: Path
    yolo_confidence: float
    yolo_crop_enabled: bool
    rag_enabled: bool
    gemini_api_key: str | None
    gemini_chat_model: str
    gemini_embedding_model: str
    knowledge_base_path: Path
    chroma_path: Path
    rag_collection_name: str
    rag_chunk_size: int
    rag_chunk_overlap: int
    rag_top_k: int
    rag_max_context_tokens: int
    rag_cache_ttl_seconds: int
    rag_memory_enabled: bool
    rag_memory_ttl_seconds: int
    rag_memory_max_turns: int
    rag_embedding_fallback_model: str
    rag_embedding_secondary_model: str
    s3_enabled: bool
    aws_region: str
    s3_bucket_name: str | None
    s3_image_prefix: str
    s3_presigned_url_expiration_seconds: int
    max_image_size_bytes: int
    postgres_url: str
    knowledge_db_schema: str
    redis_host: str
    redis_port: int
    redis_password: str
    redis_db: int


def resolve_service_path(environment_name: str, default_relative_path: str) -> Path:
    configured_path = Path(
        os.getenv(environment_name, default_relative_path)
    ).expanduser()
    if configured_path.is_absolute():
        return configured_path.resolve()
    return (SERVICE_ROOT / configured_path).resolve()


def resolve_postgres_url() -> str:
    configured_url = os.getenv("POSTGRES_URL") or os.getenv("AI_POSTGRES_URL")
    if configured_url:
        return configured_url.removeprefix("jdbc:")

    host = os.getenv("POSTGRES_HOST", "localhost")
    port = os.getenv("POSTGRES_PORT", "5432")
    database = os.getenv("POSTGRES_DB", "duriancare")
    username = os.getenv("POSTGRES_USER", "duriancare")
    password = os.getenv("POSTGRES_PASSWORD", "")
    return (
        "postgresql://"
        f"{quote_plus(username)}:{quote_plus(password)}@{host}:{port}/{database}"
    )


settings = Settings(
    yolo_model=str(
        resolve_service_path("YOLO_MODEL_PATH", "models/leaf_detector_best.pt")
    ),
    yolo_fallback_model=str(
        resolve_service_path("YOLO_FALLBACK_MODEL_PATH", "yolov8s.pt")
    ),
    classifier_weights=resolve_service_path(
        "MOBILENET_WEIGHTS_PATH",
        "models/mobilenetv2_classifier_high_acc.pth",
    ),
    yolo_confidence=float(os.getenv("YOLO_CONFIDENCE", "0.25")),
    yolo_crop_enabled=os.getenv(
        "ENABLE_YOLO_CROP",
        "true",
    ).lower() in {"1", "true", "yes"},
    rag_enabled=os.getenv("RAG_ENABLED", "true").lower() in {"1", "true", "yes"},
    gemini_api_key=os.getenv("GEMINI_API_KEY"),
    gemini_chat_model=os.getenv("GEMINI_CHAT_MODEL", "gemini-2.5-flash"),
    gemini_embedding_model=os.getenv(
        "GEMINI_EMBEDDING_MODEL",
        "text-embedding-004",
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
    rag_chunk_size=int(os.getenv("RAG_CHUNK_SIZE", "800")),
    rag_chunk_overlap=int(os.getenv("RAG_CHUNK_OVERLAP", "100")),
    rag_top_k=int(os.getenv("RAG_TOP_K", "4")),
    rag_max_context_tokens=int(os.getenv("RAG_MAX_CONTEXT_TOKENS", "3000")),
    rag_cache_ttl_seconds=int(os.getenv("RAG_CACHE_TTL_SECONDS", "3600")),
    rag_memory_enabled=os.getenv("RAG_MEMORY_ENABLED", "true").lower()
    in {"1", "true", "yes"},
    rag_memory_ttl_seconds=int(os.getenv("RAG_MEMORY_TTL_SECONDS", "86400")),
    rag_memory_max_turns=int(os.getenv("RAG_MEMORY_MAX_TURNS", "6")),
    rag_embedding_fallback_model=os.getenv(
        "RAG_EMBEDDING_FALLBACK_MODEL",
        "BAAI/bge-m3",
    ),
    rag_embedding_secondary_model=os.getenv(
        "RAG_EMBEDDING_SECONDARY_MODEL",
        "intfloat/multilingual-e5-large",
    ),
    s3_enabled=os.getenv("S3_ENABLED", "false").lower()
    in {"1", "true", "yes"},
    aws_region=os.getenv("AWS_REGION", "ap-southeast-1"),
    s3_bucket_name=os.getenv("AWS_S3_BUCKET") or None,
    s3_image_prefix=os.getenv("AWS_S3_IMAGE_PREFIX", "disease-images").strip("/"),
    s3_presigned_url_expiration_seconds=int(
        os.getenv("AWS_S3_PRESIGNED_URL_EXPIRATION_SECONDS", "3600")
    ),
    max_image_size_bytes=int(os.getenv("MAX_IMAGE_SIZE_BYTES", "10485760")),
    postgres_url=resolve_postgres_url(),
    knowledge_db_schema=os.getenv("KNOWLEDGE_DB_SCHEMA", "public"),
    redis_host=os.getenv("REDIS_HOST", "localhost"),
    redis_port=int(os.getenv("REDIS_PORT", "6379")),
    redis_password=os.getenv("REDIS_PASSWORD", ""),
    redis_db=int(os.getenv("REDIS_DB", "0")),
)
