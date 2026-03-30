import uuid
from datetime import UTC, datetime

from sqlalchemy import delete, select

from app.models.refresh_token import RefreshToken
from app.repositories.base import BaseRepository


class RefreshTokenRepository(BaseRepository):
    async def create(
        self,
        user_id: uuid.UUID,
        token_hash: str,
        expires_at: datetime,
    ) -> RefreshToken:
        row = RefreshToken(
            user_id=user_id,
            token_hash=token_hash,
            expires_at=expires_at,
        )
        self.session.add(row)
        await self.session.flush()
        return row

    async def get_by_hash(self, token_hash: str) -> RefreshToken | None:
        result = await self.session.execute(
            select(RefreshToken).where(
                RefreshToken.token_hash == token_hash,
                RefreshToken.expires_at > datetime.now(UTC),
            ),
        )
        return result.scalar_one_or_none()

    async def delete_by_hash(self, token_hash: str) -> None:
        await self.session.execute(
            delete(RefreshToken).where(RefreshToken.token_hash == token_hash),
        )
        await self.session.flush()

    async def delete_expired(self) -> int:
        """Remove rows with ``expires_at`` in the past. Returns deleted row count."""
        result = await self.session.execute(
            delete(RefreshToken).where(RefreshToken.expires_at <= datetime.now(UTC)),
        )
        return int(result.rowcount or 0)
