from pydantic import BaseModel, Field


class HealthOkResponse(BaseModel):
    status: str = Field(..., examples=["ok"])
    db: str = Field(..., examples=["connected"])


class HealthErrorResponse(BaseModel):
    status: str = Field(..., examples=["error"])
    db: str = Field(..., examples=["offline"])
