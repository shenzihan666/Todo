import structlog

from app.core.exceptions import NotFoundError
from app.models.bill import Bill
from app.repositories.bill_repository import BillRepository
from app.schemas.bill import BillCreate, BillUpdate

logger = structlog.get_logger(__name__)


class BillService:
    def __init__(self, repo: BillRepository) -> None:
        self._repo = repo

    async def list_bills(self, limit: int = 100, offset: int = 0) -> list[Bill]:
        return await self._repo.list_all(limit=limit, offset=offset)

    async def get_bill(self, bill_id: int) -> Bill:
        bill = await self._repo.get_by_id(bill_id)
        if not bill:
            raise NotFoundError("Bill", bill_id)
        return bill

    async def create_bill(self, data: BillCreate) -> Bill:
        bill = await self._repo.create(data)
        logger.info(
            "bill_created",
            bill_id=bill.id,
            tenant_id=str(bill.tenant_id),
        )
        return bill

    async def update_bill(self, bill_id: int, data: BillUpdate) -> Bill:
        bill = await self.get_bill(bill_id)
        updated = await self._repo.update(bill, data)
        logger.info(
            "bill_updated",
            bill_id=updated.id,
            tenant_id=str(updated.tenant_id),
        )
        return updated

    async def delete_bill(self, bill_id: int) -> None:
        bill = await self.get_bill(bill_id)
        tenant_id = str(bill.tenant_id)
        await self._repo.delete(bill)
        logger.info("bill_deleted", bill_id=bill_id, tenant_id=tenant_id)
