import uuid

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
