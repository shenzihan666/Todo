import secrets
import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, Header, status

from app.api.deps import get_tenant_id, get_tenant_service
from app.core.config import settings
from app.core.exceptions import ForbiddenError
from app.schemas.tenant import TenantCreate, TenantRead
from app.services.tenant_service import TenantService

router = APIRouter(prefix="/tenants", tags=["tenants"])


@router.post(
    "",
    response_model=TenantRead,
    status_code=status.HTTP_201_CREATED,
)
async def create_tenant(
    data: TenantCreate,
    service: Annotated[TenantService, Depends(get_tenant_service)],
    x_tenant_bootstrap_key: Annotated[str | None, Header()] = None,
):
    """Create a tenant without a user account (bootstrap only).

    Disabled when `tenant_bootstrap_api_key` is unset. When set, requires
    header `X-Tenant-Bootstrap-Key` to match. Prefer `POST /auth/register` for normal sign-up.
    """
    if not settings.tenant_bootstrap_api_key:
        raise ForbiddenError("Tenant creation disabled; use POST /auth/register")
    if not secrets.compare_digest(
        x_tenant_bootstrap_key or "",
        settings.tenant_bootstrap_api_key,
    ):
        raise ForbiddenError("Invalid or missing X-Tenant-Bootstrap-Key")
    return await service.create_tenant(data)


@router.get("/{tenant_id}", response_model=TenantRead)
async def get_tenant(
    tenant_id: uuid.UUID,
    service: Annotated[TenantService, Depends(get_tenant_service)],
    jwt_tenant_id: Annotated[uuid.UUID, Depends(get_tenant_id)],
):
    if tenant_id != jwt_tenant_id:
        raise ForbiddenError("Tenant ID does not match authenticated user")
    return await service.get_tenant(tenant_id)
