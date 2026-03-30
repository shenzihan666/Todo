import uuid

from pydantic import BaseModel, ConfigDict, Field


class AccessTokenPayload(BaseModel):
    """Validated JWT access token claims (subset used by the API)."""

    model_config = ConfigDict(extra="ignore")

    sub: str
    tenant_id: str
    username: str


class RegisterRequest(BaseModel):
    username: str = Field(..., min_length=1, max_length=150)
    password: str = Field(..., min_length=6, max_length=128)


class LoginRequest(BaseModel):
    username: str = Field(..., min_length=1, max_length=150)
    password: str = Field(..., min_length=1, max_length=128)


class RefreshRequest(BaseModel):
    refresh_token: str


class TokenResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"  # noqa: S105
    tenant_id: uuid.UUID
    username: str
