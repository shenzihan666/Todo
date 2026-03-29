"""Agent conversation (thread) metadata; checkpoints live in LangGraph tables."""

from __future__ import annotations

import uuid

from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import NotFoundError
from app.repositories.conversation_repository import ConversationRepository
from app.services.agent.memory_infra import get_checkpointer, memory_infra_initialized


class ConversationService:
    def __init__(self, session: AsyncSession, tenant_id: uuid.UUID) -> None:
        self._repo = ConversationRepository(session, tenant_id)
        self._tenant_id = tenant_id

    async def ensure_thread(
        self,
        thread_id: uuid.UUID | None,
        *,
        title: str | None = None,
    ) -> uuid.UUID:
        """Create a new conversation row or verify an existing one belongs to this tenant."""
        if thread_id is None:
            new_id = uuid.uuid4()
            await self._repo.create(conversation_id=new_id, title=title)
            return new_id

        conv = await self._repo.get_by_id(thread_id)
        if conv is None:
            raise NotFoundError("conversation", str(thread_id))
        return conv.id

    async def list_threads(self, *, limit: int = 100, offset: int = 0):
        return await self._repo.list_all(limit=limit, offset=offset)

    async def delete_thread(self, thread_id: uuid.UUID) -> None:
        conv = await self._repo.get_by_id(thread_id)
        if conv is None:
            raise NotFoundError("conversation", str(thread_id))

        cp = get_checkpointer()
        if memory_infra_initialized() and cp is not None:
            await cp.adelete_thread(str(thread_id))

        await self._repo.delete(conv)
