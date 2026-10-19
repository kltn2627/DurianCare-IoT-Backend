from fastapi import APIRouter, HTTPException, Request, status
from starlette.concurrency import run_in_threadpool

from app.schemas.recommendation import DiseaseRecommendation
from app.services.recommendation_service import RecommendationService

router = APIRouter(prefix="/api/v1/recommendations", tags=["Care Recommendations"])


def get_recommendation_service(request: Request) -> RecommendationService:
    recommendation_service = getattr(request.app.state, "recommendation_service", None)
    if recommendation_service is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Recommendation service is not available",
        )
    return recommendation_service


@router.get("/{disease_code}")
async def get_recommendation(
    disease_code: str,
    request: Request,
) -> dict[str, object]:
    recommendation_service = get_recommendation_service(request)
    recommendation: DiseaseRecommendation | None = await run_in_threadpool(
        recommendation_service.build_recommendation,
        disease_code,
    )
    if recommendation is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No structured recommendation was found for this disease code",
        )
    return {
        "status": "success",
        "data": recommendation.model_dump(by_alias=True),
    }
