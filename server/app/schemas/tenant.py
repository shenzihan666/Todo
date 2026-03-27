import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class TenantCreate(BaseModel):
    name: str = Field(..., min_length=1, max_length=255)


class TenantRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str
    created_at: datetime
    updated_at: datetime
