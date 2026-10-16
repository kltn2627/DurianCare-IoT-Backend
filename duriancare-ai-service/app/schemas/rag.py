from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class RagStatusData(BaseModel):
    database_ready: bool = Field(alias="databaseReady")
    embedding_ready: bool = Field(alias="embeddingReady")
    embedding_model: str | None = Field(default=None, alias="embeddingModel")
    vector_store_ready: bool = Field(alias="vectorStoreReady")
    rag_ready: bool = Field(alias="ragReady")
    gemini_ready: bool = Field(alias="geminiReady")
    llm_ready: bool = Field(alias="llmReady")
    recommendation_ready: bool = Field(alias="recommendationReady")
    cache_ready: bool = Field(alias="cacheReady")
    cache_status: str = Field(alias="cacheStatus")
    document_count: int = Field(alias="documentCount")
    chunk_count: int = Field(alias="chunkCount")
    documents_indexed: int = Field(alias="documentsIndexed")
    indexed_documents: list[str] = Field(default_factory=list, alias="indexedDocuments")
    indexed_files: int = Field(alias="indexedFiles")
    last_indexed_at: str | None = Field(default=None, alias="lastIndexedAt")
    disabled_reason: str | None = Field(default=None, alias="disabledReason")
    fatal_error: str | None = Field(default=None, alias="fatalError")
    language_support: list[str] = Field(default_factory=list, alias="languageSupport")


class RagStatusResponse(BaseModel):
    status: Literal["success"]
    data: RagStatusData


class RagDocumentInfo(BaseModel):
    document_id: str = Field(alias="document_id")
    source: str
    source_type: str = Field(alias="source_type")
    checksum: str
    language: str
    topic: str
    disease: str | None = None
    section: str
    title: str
    indexed_at: str = Field(alias="indexed_at")
    file_size: int = Field(alias="file_size")
    chunk_ids: list[str] = Field(default_factory=list, alias="chunk_ids")
    chunk_count: int = Field(alias="chunk_count")


class RagDocumentListResponse(BaseModel):
    status: Literal["success"]
    data: list[RagDocumentInfo]


class RagActionData(BaseModel):
    force: bool = False
    documents_scanned: int = Field(alias="documentsScanned")
    documents_changed: int = Field(alias="documentsChanged")
    documents_removed: int = Field(alias="documentsRemoved")


class RagActionResponse(BaseModel):
    status: Literal["success"]
    data: RagActionData


class RagDeleteData(BaseModel):
    deleted: bool
    document_id: str = Field(alias="document_id")


class RagDeleteResponse(BaseModel):
    status: Literal["success"]
    data: RagDeleteData


class RagStatisticsData(BaseModel):
    database_ready: bool = Field(alias="databaseReady")
    embedding_ready: bool = Field(alias="embeddingReady")
    embedding_model: str | None = Field(default=None, alias="embeddingModel")
    vector_store_ready: bool = Field(alias="vectorStoreReady")
    rag_ready: bool = Field(alias="ragReady")
    gemini_ready: bool = Field(alias="geminiReady")
    llm_ready: bool = Field(alias="llmReady")
    recommendation_ready: bool = Field(alias="recommendationReady")
    cache_ready: bool = Field(alias="cacheReady")
    cache_status: str = Field(alias="cacheStatus")
    document_count: int = Field(alias="documentCount")
    chunk_count: int = Field(alias="chunkCount")
    documents_indexed: int = Field(alias="documentsIndexed")
    indexed_documents: list[str] = Field(default_factory=list, alias="indexedDocuments")
    source_types: dict[str, int] = Field(default_factory=dict, alias="sourceTypes")
    languages: dict[str, int] = Field(default_factory=dict)
    topics: dict[str, int] = Field(default_factory=dict)
    diseases: dict[str, int] = Field(default_factory=dict)
    last_indexed_at: str | None = Field(default=None, alias="lastIndexedAt")


class RagStatisticsResponse(BaseModel):
    status: Literal["success"]
    data: RagStatisticsData
