import uuid

from fastapi import HTTPException, status

from app.models.tenant import Tenant
from app.repositories.tenant_repository import TenantRepository
from app.schemas.tenant import TenantCreate


class TenantService:
    def __init__(self, repo: TenantRepository) -> None:
        self._repo = repo

    async def create_tenant(self, data: TenantCreate) -> Tenant:
        return await self._repo.create(data)

    async def get_tenant(self, tenant_id: uuid.UUID) -> Tenant:
        tenant = await self._repo.get_by_id(tenant_id)
        if not tenant:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Tenant {tenant_id} not found",
            )
        return tenant
