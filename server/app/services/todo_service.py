from fastapi import HTTPException, status

from app.models.todo import Todo
from app.repositories.todo_repository import TodoRepository
from app.schemas.todo import TodoCreate, TodoUpdate


class TodoService:
    def __init__(self, repo: TodoRepository) -> None:
        self._repo = repo

    async def list_todos(self) -> list[Todo]:
        return await self._repo.list_all()

    async def get_todo(self, todo_id: int) -> Todo:
        todo = await self._repo.get_by_id(todo_id)
        if not todo:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Todo {todo_id} not found",
            )
        return todo

    async def create_todo(self, data: TodoCreate) -> Todo:
        return await self._repo.create(data)

    async def update_todo(self, todo_id: int, data: TodoUpdate) -> Todo:
        todo = await self.get_todo(todo_id)
        return await self._repo.update(todo, data)

    async def delete_todo(self, todo_id: int) -> None:
        todo = await self.get_todo(todo_id)
        await self._repo.delete(todo)
