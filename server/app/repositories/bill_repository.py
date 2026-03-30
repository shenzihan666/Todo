import uuid
from datetime import datetime

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.bill import Bill
from app.repositories.base import BaseRepository
from app.schemas.bill import BillCreate, BillUpdate


class BillRepository(BaseRepository):
    def __init__(self, session: AsyncSession, tenant_id: uuid.UUID) -> None:
        super().__init__(session)
        self._tenant_id = tenant_id

    async def list_all(self, *, limit: int = 100, offset: int = 0) -> list[Bill]:
        result = await self.session.execute(
            select(Bill)
            .where(Bill.tenant_id == self._tenant_id)
            .order_by(Bill.created_at.desc())
            .limit(limit)
            .offset(offset),
        )
        return list(result.scalars().all())

    async def list_for_agent(
        self,
        *,
        billed_from: datetime | None = None,
        billed_to: datetime | None = None,
        bill_type: str | None = None,
        limit: int = 100,
        offset: int = 0,
    ) -> list[Bill]:
        """List bills for the tenant, optionally filtered by ``billed_at`` window and/or type."""
        stmt = select(Bill).where(Bill.tenant_id == self._tenant_id)
        has_time = billed_from is not None or billed_to is not None
        if bill_type is not None and str(bill_type).strip():
            stmt = stmt.where(Bill.type == str(bill_type).strip().lower())
        if has_time:
            stmt = stmt.where(Bill.billed_at.isnot(None))
            if billed_from is not None:
                stmt = stmt.where(Bill.billed_at >= billed_from)
            if billed_to is not None:
                stmt = stmt.where(Bill.billed_at <= billed_to)
            stmt = stmt.order_by(Bill.billed_at.asc())
        else:
            stmt = stmt.order_by(Bill.created_at.desc())
        safe_limit = min(max(limit, 1), 500)
        safe_offset = max(offset, 0)
        stmt = stmt.limit(safe_limit).offset(safe_offset)
        result = await self.session.execute(stmt)
        return list(result.scalars().all())

    async def get_by_id(self, bill_id: int) -> Bill | None:
        bill = await self.session.get(Bill, bill_id)
        if bill is None or bill.tenant_id != self._tenant_id:
            return None
        return bill

    async def create(self, data: BillCreate) -> Bill:
        bill = Bill(
            tenant_id=self._tenant_id,
            title=data.title,
            description=data.description,
            amount=data.amount,
            type=data.type,
            category=data.category,
            billed_at=data.billed_at,
        )
        self.session.add(bill)
        await self.session.flush()
        await self.session.refresh(bill)
        return bill

    async def update(self, bill: Bill, data: BillUpdate) -> Bill:
        for field, value in data.model_dump(exclude_unset=True).items():
            setattr(bill, field, value)
        await self.session.flush()
        await self.session.refresh(bill)
        return bill

    async def delete(self, bill: Bill) -> None:
        await self.session.delete(bill)
        await self.session.flush()
