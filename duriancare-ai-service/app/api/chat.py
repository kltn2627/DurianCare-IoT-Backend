from fastapi import APIRouter, HTTPException, Request, status
from starlette.concurrency import run_in_threadpool

from app.schemas.chat import ChatAnswerData, ChatAnswerResponse, ChatQuestion
from app.services.rag_service import RagQueryError, RagService

router = APIRouter(prefix="/api/v1/chat", tags=["Agricultural RAG Chat"])


def get_rag_service(request: Request) -> RagService:
    rag_service = getattr(request.app.state, "rag_service", None)
    if rag_service is None:
        rag_load_error = getattr(request.app.state, "rag_load_error", None)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=rag_load_error or "RAG service is not available",
        )
    return rag_service


@router.post("/ask", response_model=ChatAnswerResponse)
async def ask_question(
    payload: ChatQuestion,
    request: Request,
) -> ChatAnswerResponse:
    rag_service = get_rag_service(request)
    try:
        answer, sources = await run_in_threadpool(
            rag_service.ask,
            payload.question,
        )
    except RagQueryError as exception:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=str(exception),
        ) from exception

    return ChatAnswerResponse(
        status="success",
        data=ChatAnswerData(answer=answer, sources=sources),
    )
