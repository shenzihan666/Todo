from typing import Annotated

from fastapi import Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_session
from app.repositories.health_repository import HealthRepository
from app.repositories.todo_repository import TodoRepository
from app.services.health_service import HealthService
from app.services.todo_service import TodoService


def get_health_repository() -> HealthRepository:
    return HealthRepository()


async def get_health_service(
    repo: Annotated[HealthRepository, Depends(get_health_repository)],
) -> HealthService:
    return HealthService(repo)


async def get_todo_repository(
    session: Annotated[AsyncSession, Depends(get_session)],
) -> TodoRepository:
    return TodoRepository(session)


async def get_todo_service(
    repo: Annotated[TodoRepository, Depends(get_todo_repository)],
) -> TodoService:
    return TodoService(repo)
