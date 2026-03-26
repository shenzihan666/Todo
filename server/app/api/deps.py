from typing import Annotated

from fastapi import Depends

from app.repositories.health_repository import HealthRepository
from app.services.health_service import HealthService


def get_health_repository() -> HealthRepository:
    return HealthRepository()


async def get_health_service(
    repo: Annotated[HealthRepository, Depends(get_health_repository)],
) -> HealthService:
    return HealthService(repo)
