import uuid

from app.models.tenant import Tenant
from app.repositories.base import BaseRepository
from app.schemas.tenant import TenantCreate


class TenantRepository(BaseRepository):
    async def create(self, data: TenantCreate) -> Tenant:
        tenant = Tenant(name=data.name)
        self.session.add(tenant)
        await self.session.flush()
        await self.session.refresh(tenant)
        return tenant

    async def get_by_id(self, tenant_id: uuid.UUID) -> Tenant | None:
        return await self.session.get(Tenant, tenant_id)
