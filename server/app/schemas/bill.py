import uuid
from datetime import datetime
from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class BillCreate(BaseModel):
    title: str = Field(..., min_length=1, max_length=512)
    description: str | None = None
    amount: Decimal = Field(..., gt=0)
    type: Literal["income", "expense"]
    category: str | None = Field(None, max_length=100)
    billed_at: datetime | None = None


class BillUpdate(BaseModel):
    title: str | None = Field(None, min_length=1, max_length=512)
    description: str | None = None
    amount: Decimal | None = Field(None, gt=0)
    type: Literal["income", "expense"] | None = None
    category: str | None = Field(None, max_length=100)
    billed_at: datetime | None = None


class BillRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    tenant_id: uuid.UUID
    title: str
    description: str | None
    amount: Decimal
    type: str
    category: str | None
    billed_at: datetime | None
    created_at: datetime
    updated_at: datetime
