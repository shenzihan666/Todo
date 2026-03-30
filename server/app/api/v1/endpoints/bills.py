from typing import Annotated

from fastapi import APIRouter, Depends, Query, status

from app.api.deps import get_bill_service
from app.schemas.bill import BillCreate, BillRead, BillUpdate
from app.services.bill_service import BillService

router = APIRouter(prefix="/bills", tags=["bills"])


@router.get("", response_model=list[BillRead])
async def list_bills(
    service: Annotated[BillService, Depends(get_bill_service)],
    limit: Annotated[int, Query(ge=1, le=500)] = 100,
    offset: Annotated[int, Query(ge=0)] = 0,
) -> list[BillRead]:
    return await service.list_bills(limit=limit, offset=offset)


@router.post("", response_model=BillRead, status_code=status.HTTP_201_CREATED)
async def create_bill(
    data: BillCreate,
    service: Annotated[BillService, Depends(get_bill_service)],
) -> BillRead:
    return await service.create_bill(data)


@router.get("/{bill_id}", response_model=BillRead)
async def get_bill(
    bill_id: int,
    service: Annotated[BillService, Depends(get_bill_service)],
) -> BillRead:
    return await service.get_bill(bill_id)


@router.patch("/{bill_id}", response_model=BillRead)
async def update_bill(
    bill_id: int,
    data: BillUpdate,
    service: Annotated[BillService, Depends(get_bill_service)],
) -> BillRead:
    return await service.update_bill(bill_id, data)


@router.delete("/{bill_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_bill(
    bill_id: int,
    service: Annotated[BillService, Depends(get_bill_service)],
) -> None:
    await service.delete_bill(bill_id)
