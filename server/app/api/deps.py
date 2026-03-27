import uuid
from typing import Annotated

from fastapi import Depends, Header, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_session
from app.repositories.health_repository import HealthRepository
from app.repositories.tenant_repository import TenantRepository
from app.repositories.todo_repository import TodoRepository
from app.services.health_service import HealthService
from app.services.tenant_service import TenantService
from app.services.todo_service import TodoService


def get_health_repository() -> HealthRepository:
    return HealthRepository()


async def get_health_service(
    repo: Annotated[HealthRepository, Depends(get_health_repository)],
) -> HealthService:
    return HealthService(repo)


def get_tenant_id(x_tenant_id: Annotated[str, Header()]) -> uuid.UUID:
    try:
        return uuid.UUID(x_tenant_id)
    except ValueError:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid X-Tenant-ID header",
        ) from None


async def get_tenant_repository(
    session: Annotated[AsyncSession, Depends(get_session)],
) -> TenantRepository:
    return TenantRepository(session)


async def get_tenant_service(
    repo: Annotated[TenantRepository, Depends(get_tenant_repository)],
) -> TenantService:
    return TenantService(repo)


async def get_todo_repository(
    session: Annotated[AsyncSession, Depends(get_session)],
    tenant_id: Annotated[uuid.UUID, Depends(get_tenant_id)],
) -> TodoRepository:
    return TodoRepository(session, tenant_id)


async def get_todo_service(
    repo: Annotated[TodoRepository, Depends(get_todo_repository)],
) -> TodoService:
    return TodoService(repo)
