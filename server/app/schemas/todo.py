import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class TodoCreate(BaseModel):
    title: str = Field(..., min_length=1, max_length=512)
    description: str | None = None
    estimated_minutes: int | None = Field(None, ge=1, le=1440)
    scheduled_at: datetime | None = None


class TodoUpdate(BaseModel):
    title: str | None = Field(None, min_length=1, max_length=512)
    description: str | None = None
    completed: bool | None = None
    estimated_minutes: int | None = Field(None, ge=1, le=1440)
    scheduled_at: datetime | None = None


class TodoRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    tenant_id: uuid.UUID
    title: str
    description: str | None
    completed: bool
    estimated_minutes: int | None
    scheduled_at: datetime | None
    created_at: datetime
    updated_at: datetime
