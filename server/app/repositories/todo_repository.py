import uuid
from datetime import datetime

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.todo import Todo
from app.repositories.base import BaseRepository
from app.schemas.todo import TodoCreate, TodoUpdate


class TodoRepository(BaseRepository):
    def __init__(self, session: AsyncSession, tenant_id: uuid.UUID) -> None:
        super().__init__(session)
        self._tenant_id = tenant_id

    async def list_all(self, *, limit: int = 100, offset: int = 0) -> list[Todo]:
        result = await self.session.execute(
            select(Todo)
            .where(Todo.tenant_id == self._tenant_id)
            .order_by(Todo.created_at.desc())
            .limit(limit)
            .offset(offset),
        )
        return list(result.scalars().all())

    async def list_for_agent(
        self,
        *,
        scheduled_from: datetime | None = None,
        scheduled_to: datetime | None = None,
        limit: int = 100,
        offset: int = 0,
    ) -> list[Todo]:
        """List todos for the tenant, optionally filtered by ``scheduled_at`` window.

        When *scheduled_from* or *scheduled_to* is set, only rows with a non-null
        ``scheduled_at`` in the inclusive range are returned, ordered by time.
        Otherwise returns recent todos by ``created_at`` (newest first).
        """
        stmt = select(Todo).where(Todo.tenant_id == self._tenant_id)
        has_time = scheduled_from is not None or scheduled_to is not None
        if has_time:
            stmt = stmt.where(Todo.scheduled_at.isnot(None))
            if scheduled_from is not None:
                stmt = stmt.where(Todo.scheduled_at >= scheduled_from)
            if scheduled_to is not None:
                stmt = stmt.where(Todo.scheduled_at <= scheduled_to)
            stmt = stmt.order_by(Todo.scheduled_at.asc())
        else:
            stmt = stmt.order_by(Todo.created_at.desc())
        safe_limit = min(max(limit, 1), 500)
        safe_offset = max(offset, 0)
        stmt = stmt.limit(safe_limit).offset(safe_offset)
        result = await self.session.execute(stmt)
        return list(result.scalars().all())

    async def get_by_id(self, todo_id: int) -> Todo | None:
        todo = await self.session.get(Todo, todo_id)
        if todo is None or todo.tenant_id != self._tenant_id:
            return None
        return todo

    async def create(self, data: TodoCreate) -> Todo:
        todo = Todo(
            tenant_id=self._tenant_id,
            title=data.title,
            description=data.description,
            estimated_minutes=data.estimated_minutes,
            scheduled_at=data.scheduled_at,
        )
        self.session.add(todo)
        await self.session.flush()
        await self.session.refresh(todo)
        return todo

    async def update(self, todo: Todo, data: TodoUpdate) -> Todo:
        for field, value in data.model_dump(exclude_unset=True).items():
            setattr(todo, field, value)
        await self.session.flush()
        await self.session.refresh(todo)
        return todo

    async def delete(self, todo: Todo) -> None:
        await self.session.delete(todo)
        await self.session.flush()
