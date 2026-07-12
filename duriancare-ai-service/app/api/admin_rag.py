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

router = APIRouter(prefix="/admin/rag", tags=["RAG Admin"])


def _require_admin(request: Request) -> None:
    role = (
        request.headers.get("X-Auth-Role")
        or request.headers.get("x-auth-role")
        or request.headers.get("X-Role")
        or request.headers.get("x-role")
        or ""
    ).strip().upper()
    if role != "ADMIN":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Admin privileges are required",
        )


def get_rag_service(request: Request) -> RagService:
    rag_service = getattr(request.app.state, "rag_service", None)
    if rag_service is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="RAG service is not available",
        )
    return rag_service


@router.get("/status", response_model=RagStatusResponse)
async def status_view(request: Request) -> RagStatusResponse:
    _require_admin(request)
    rag_service = get_rag_service(request)
    return RagStatusResponse(
        status="success",
        data=RagStatusData.model_validate(rag_service.status()),
    )


@router.post("/reindex", response_model=RagActionResponse)
async def reindex(request: Request) -> RagActionResponse:
    _require_admin(request)
    rag_service = get_rag_service(request)
    result = await run_in_threadpool(rag_service.reindex, False)
    return RagActionResponse(
        status="success",
        data=RagActionData.model_validate(result),
    )


@router.post("/reload", response_model=RagActionResponse)
async def reload_documents(request: Request) -> RagActionResponse:
    _require_admin(request)
    rag_service = get_rag_service(request)
    result = await run_in_threadpool(rag_service.reindex, False)
    return RagActionResponse(
        status="success",
        data=RagActionData.model_validate(result),
    )


@router.get("/documents", response_model=RagDocumentListResponse)
async def documents(request: Request) -> RagDocumentListResponse:
    _require_admin(request)
    rag_service = get_rag_service(request)
    items = await run_in_threadpool(rag_service.list_documents)
    return RagDocumentListResponse(
        status="success",
        data=[RagDocumentInfo.model_validate(item) for item in items],
    )


@router.delete("/cache", response_model=RagActionResponse)
async def clear_cache(request: Request) -> RagActionResponse:
    _require_admin(request)
    rag_service = get_rag_service(request)
    result = await run_in_threadpool(rag_service.clear_cache)
    return RagActionResponse(
        status="success",
        data=RagActionData.model_validate(result),
    )


@router.get("/statistics", response_model=RagStatisticsResponse)
async def statistics(request: Request) -> RagStatisticsResponse:
    _require_admin(request)
    rag_service = get_rag_service(request)
    result = await run_in_threadpool(rag_service.statistics)
    return RagStatisticsResponse(
        status="success",
        data=RagStatisticsData.model_validate(result),
    )
