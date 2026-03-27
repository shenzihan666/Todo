import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, status

from app.api.deps import get_tenant_service
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
):
    return await service.create_tenant(data)


@router.get("/{tenant_id}", response_model=TenantRead)
async def get_tenant(
    tenant_id: uuid.UUID,
    service: Annotated[TenantService, Depends(get_tenant_service)],
):
    return await service.get_tenant(tenant_id)
