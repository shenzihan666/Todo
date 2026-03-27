import uuid
from typing import Annotated

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_session
from app.core.security import decode_access_token
from app.repositories.health_repository import HealthRepository
from app.repositories.media_repository import MediaRepository
from app.repositories.refresh_token_repository import RefreshTokenRepository
from app.repositories.tenant_repository import TenantRepository
from app.repositories.todo_repository import TodoRepository
from app.repositories.user_repository import UserRepository
from app.services.auth_service import AuthService
from app.services.health_service import HealthService
from app.services.media_service import MediaService
from app.services.tenant_service import TenantService
from app.services.todo_service import TodoService

_bearer_scheme = HTTPBearer()


def get_health_repository() -> HealthRepository:
    return HealthRepository()


async def get_health_service(
    repo: Annotated[HealthRepository, Depends(get_health_repository)],
) -> HealthService:
    return HealthService(repo)


# ── Auth dependencies ──────────────────────────────────────────────


def get_current_user(
    credentials: Annotated[HTTPAuthorizationCredentials, Depends(_bearer_scheme)],
) -> dict:
    """Decode JWT and return the payload dict. Raises 401 on any failure."""
    try:
        payload = decode_access_token(credentials.credentials)
    except jwt.ExpiredSignatureError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token has expired",
            headers={"WWW-Authenticate": "Bearer"},
        ) from None
    except jwt.PyJWTError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token",
            headers={"WWW-Authenticate": "Bearer"},
        ) from None
    return payload


def get_tenant_id(
    user: Annotated[dict, Depends(get_current_user)],
) -> uuid.UUID:
    """Extract tenant_id from the verified JWT payload."""
    try:
        return uuid.UUID(user["tenant_id"])
    except (KeyError, ValueError):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token missing tenant_id",
        ) from None


# ── Tenant ─────────────────────────────────────────────────────────


async def get_tenant_repository(
    session: Annotated[AsyncSession, Depends(get_session)],
) -> TenantRepository:
    return TenantRepository(session)


async def get_tenant_service(
    repo: Annotated[TenantRepository, Depends(get_tenant_repository)],
) -> TenantService:
    return TenantService(repo)


# ── Todo ───────────────────────────────────────────────────────────


async def get_todo_repository(
    session: Annotated[AsyncSession, Depends(get_session)],
    tenant_id: Annotated[uuid.UUID, Depends(get_tenant_id)],
) -> TodoRepository:
    return TodoRepository(session, tenant_id)


async def get_todo_service(
    repo: Annotated[TodoRepository, Depends(get_todo_repository)],
) -> TodoService:
    return TodoService(repo)


# ── Media (uploads) ────────────────────────────────────────────────


async def get_media_repository(
    session: Annotated[AsyncSession, Depends(get_session)],
    tenant_id: Annotated[uuid.UUID, Depends(get_tenant_id)],
) -> MediaRepository:
    return MediaRepository(session, tenant_id)


async def get_media_service(
    repo: Annotated[MediaRepository, Depends(get_media_repository)],
) -> MediaService:
    return MediaService(repo)


# ── Auth service ───────────────────────────────────────────────────


async def get_user_repository(
    session: Annotated[AsyncSession, Depends(get_session)],
) -> UserRepository:
    return UserRepository(session)


async def get_refresh_token_repository(
    session: Annotated[AsyncSession, Depends(get_session)],
) -> RefreshTokenRepository:
    return RefreshTokenRepository(session)


async def get_auth_service(
    user_repo: Annotated[UserRepository, Depends(get_user_repository)],
    tenant_repo: Annotated[TenantRepository, Depends(get_tenant_repository)],
    refresh_repo: Annotated[RefreshTokenRepository, Depends(get_refresh_token_repository)],
) -> AuthService:
    return AuthService(user_repo, tenant_repo, refresh_repo)
