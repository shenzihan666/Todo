import uuid

import structlog

from app.core.exceptions import NotFoundError
from app.models.tenant import Tenant
from app.repositories.tenant_repository import TenantRepository
from app.schemas.tenant import TenantCreate

logger = structlog.get_logger(__name__)


class TenantService:
    def __init__(self, repo: TenantRepository) -> None:
        self._repo = repo

    async def create_tenant(self, data: TenantCreate) -> Tenant:
        tenant = await self._repo.create(data)
        logger.info("tenant_created", tenant_id=str(tenant.id))
        return tenant

    async def get_tenant(self, tenant_id: uuid.UUID) -> Tenant:
        tenant = await self._repo.get_by_id(tenant_id)
        if not tenant:
            raise NotFoundError("Tenant", str(tenant_id))
        return tenant
