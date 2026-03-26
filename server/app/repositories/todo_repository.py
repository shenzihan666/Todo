from sqlalchemy import select

from app.models.todo import Todo
from app.repositories.base import BaseRepository
from app.schemas.todo import TodoCreate, TodoUpdate


class TodoRepository(BaseRepository):
    async def list_all(self) -> list[Todo]:
        result = await self.session.execute(
            select(Todo).order_by(Todo.created_at.desc()),
        )
        return list(result.scalars().all())

    async def get_by_id(self, todo_id: int) -> Todo | None:
        return await self.session.get(Todo, todo_id)

    async def create(self, data: TodoCreate) -> Todo:
        todo = Todo(title=data.title, description=data.description)
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
