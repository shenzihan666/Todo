from typing import Annotated

from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse

from app.api.deps import get_health_service
from app.schemas.health import HealthErrorResponse, HealthOkResponse
from app.services.health_service import HealthService

router = APIRouter(tags=["health"])


@router.get(
    "/health",
    response_model=HealthOkResponse,
    responses={503: {"model": HealthErrorResponse}},
)
async def health(
    service: Annotated[HealthService, Depends(get_health_service)],
):
    ok, db_status = await service.get_health_status()
    if not ok:
        return JSONResponse(
            status_code=503,
            content={"status": "error", "db": db_status},
        )
    return HealthOkResponse(status="ok", db=db_status)


@router.get("/health/live", response_model=dict)
async def liveness() -> dict[str, str]:
    """Kubernetes-style liveness: process is up."""
    return {"status": "alive"}
