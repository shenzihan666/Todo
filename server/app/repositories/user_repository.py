import uuid

from sqlalchemy import func, select

from app.models.user import User
from app.repositories.base import BaseRepository


class UserRepository(BaseRepository):
    async def create(
        self,
        username: str,
        hashed_password: str,
        tenant_id: uuid.UUID,
    ) -> User:
        user = User(
            username=username,
            hashed_password=hashed_password,
            tenant_id=tenant_id,
        )
        self.session.add(user)
        await self.session.flush()
        await self.session.refresh(user)
        return user

    async def get_by_id(self, user_id: uuid.UUID) -> User | None:
        return await self.session.get(User, user_id)

    async def get_by_username(self, username: str) -> User | None:
        result = await self.session.execute(
            select(User).where(func.lower(User.username) == username.lower()),
        )
        return result.scalar_one_or_none()
