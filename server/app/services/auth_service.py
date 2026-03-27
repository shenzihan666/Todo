from datetime import UTC, datetime, timedelta

import structlog

from app.core.config import settings
from app.core.exceptions import AuthenticationError, ConflictError
from app.core.security import (
    create_access_token,
    generate_refresh_token,
    hash_password,
    hash_refresh_token,
    verify_password,
)
from app.repositories.refresh_token_repository import RefreshTokenRepository
from app.repositories.tenant_repository import TenantRepository
from app.repositories.user_repository import UserRepository
from app.schemas.auth import LoginRequest, RegisterRequest, TokenResponse
from app.schemas.tenant import TenantCreate

logger = structlog.get_logger(__name__)


class AuthService:
    def __init__(
        self,
        user_repo: UserRepository,
        tenant_repo: TenantRepository,
        refresh_repo: RefreshTokenRepository,
    ) -> None:
        self._user_repo = user_repo
        self._tenant_repo = tenant_repo
        self._refresh_repo = refresh_repo

    async def register(self, data: RegisterRequest) -> TokenResponse:
        existing = await self._user_repo.get_by_username(data.username)
        if existing:
            raise ConflictError("Username already taken")

        tenant = await self._tenant_repo.create(TenantCreate(name=data.username))
        user = await self._user_repo.create(
            username=data.username,
            hashed_password=hash_password(data.password),
            tenant_id=tenant.id,
        )

        logger.info(
            "user_registered",
            user_id=str(user.id),
            tenant_id=str(tenant.id),
        )
        return await self._issue_tokens(user.id, tenant.id, user.username)

    async def login(self, data: LoginRequest) -> TokenResponse:
        user = await self._user_repo.get_by_username(data.username)
        if not user or not verify_password(data.password, user.hashed_password):
            logger.warning("login_failed", reason="invalid_credentials")
            raise AuthenticationError("Invalid username or password")

        logger.info(
            "user_login",
            user_id=str(user.id),
            tenant_id=str(user.tenant_id),
        )
        return await self._issue_tokens(user.id, user.tenant_id, user.username)

    async def refresh(self, raw_token: str) -> TokenResponse:
        token_hash = hash_refresh_token(raw_token)
        stored = await self._refresh_repo.get_by_hash(token_hash)
        if not stored:
            raise AuthenticationError("Invalid or expired refresh token")

        user = await self._user_repo.get_by_id(stored.user_id)
        if not user:
            raise AuthenticationError("User no longer exists")

        # Issue new tokens first, then revoke old refresh (avoids losing refresh on failure).
        tokens = await self._issue_tokens(user.id, user.tenant_id, user.username)
        await self._refresh_repo.delete_by_hash(token_hash)
        return tokens

    async def logout(self, raw_token: str) -> None:
        token_hash = hash_refresh_token(raw_token)
        await self._refresh_repo.delete_by_hash(token_hash)

    async def _issue_tokens(self, user_id, tenant_id, username: str) -> TokenResponse:
        access_token = create_access_token(
            user_id=user_id,
            tenant_id=tenant_id,
            username=username,
        )

        raw_refresh = generate_refresh_token()
        await self._refresh_repo.create(
            user_id=user_id,
            token_hash=hash_refresh_token(raw_refresh),
            expires_at=datetime.now(UTC) + timedelta(days=settings.jwt_refresh_expire_days),
        )

        return TokenResponse(
            access_token=access_token,
            refresh_token=raw_refresh,
            tenant_id=tenant_id,
            username=username,
        )
