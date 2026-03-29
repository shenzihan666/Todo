import uuid

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.conversation import Conversation
from app.repositories.base import BaseRepository


class ConversationRepository(BaseRepository):
    def __init__(self, session: AsyncSession, tenant_id: uuid.UUID) -> None:
        super().__init__(session)
        self._tenant_id = tenant_id

    async def list_all(self, *, limit: int = 100, offset: int = 0) -> list[Conversation]:
        result = await self.session.execute(
            select(Conversation)
            .where(Conversation.tenant_id == self._tenant_id)
            .order_by(Conversation.updated_at.desc())
            .limit(limit)
            .offset(offset),
        )
        return list(result.scalars().all())

    async def get_by_id(self, conversation_id: uuid.UUID) -> Conversation | None:
        row = await self.session.get(Conversation, conversation_id)
        if row is None or row.tenant_id != self._tenant_id:
            return None
        return row

    async def create(
        self,
        *,
        conversation_id: uuid.UUID,
        title: str | None = None,
    ) -> Conversation:
        conv = Conversation(
            id=conversation_id,
            tenant_id=self._tenant_id,
            title=title,
        )
        self.session.add(conv)
        await self.session.flush()
        await self.session.refresh(conv)
        return conv

    async def delete(self, conv: Conversation) -> None:
        await self.session.delete(conv)
        await self.session.flush()
