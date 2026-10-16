from __future__ import annotations

from fastapi import APIRouter, HTTPException, Request, status
from starlette.concurrency import run_in_threadpool

from app.schemas.rag import (
    RagActionData,
    RagActionResponse,
    RagDeleteData,
    RagDeleteResponse,
    RagDocumentInfo,
    RagDocumentListResponse,
    RagStatisticsData,
    RagStatisticsResponse,
    RagStatusData,
    RagStatusResponse,
)
from app.services.rag_service import RagService

router = APIRouter(prefix="/api/v1/rag", tags=["RAG Admin"])


def get_rag_service(request: Request) -> RagService:
    rag_service = getattr(request.app.state, "rag_service", None)
    if rag_service is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="RAG service is not available",
        )
    return rag_service


@router.get("/status", response_model=RagStatusResponse)
async def get_status(request: Request) -> RagStatusResponse:
    rag_service = get_rag_service(request)
    return RagStatusResponse(
        status="success",
        data=RagStatusData.model_validate(rag_service.status()),
    )


@router.post("/reindex", response_model=RagActionResponse)
async def reindex(request: Request) -> RagActionResponse:
    rag_service = get_rag_service(request)
    result = await run_in_threadpool(rag_service.reindex, False)
    return RagActionResponse(
        status="success",
        data=RagActionData.model_validate(result),
    )


@router.post("/rebuild", response_model=RagActionResponse)
async def rebuild(request: Request) -> RagActionResponse:
    rag_service = get_rag_service(request)
    result = await run_in_threadpool(rag_service.rebuild)
    return RagActionResponse(
        status="success",
        data=RagActionData.model_validate(result),
    )


@router.get("/documents", response_model=RagDocumentListResponse)
async def documents(request: Request) -> RagDocumentListResponse:
    rag_service = get_rag_service(request)
    items = await run_in_threadpool(rag_service.list_documents)
    return RagDocumentListResponse(
        status="success",
        data=[RagDocumentInfo.model_validate(item) for item in items],
    )


@router.delete("/document/{document_id}", response_model=RagDeleteResponse)
async def delete_document(document_id: str, request: Request) -> RagDeleteResponse:
    rag_service = get_rag_service(request)
    result = await run_in_threadpool(rag_service.delete_document, document_id)
    return RagDeleteResponse(
        status="success",
        data=RagDeleteData.model_validate(
            {
                "deleted": result["deleted"],
                "document_id": result["document_id"],
            }
        ),
    )


@router.get("/statistics", response_model=RagStatisticsResponse)
async def statistics(request: Request) -> RagStatisticsResponse:
    rag_service = get_rag_service(request)
    result = await run_in_threadpool(rag_service.statistics)
    return RagStatisticsResponse(
        status="success",
        data=RagStatisticsData.model_validate(result),
    )
