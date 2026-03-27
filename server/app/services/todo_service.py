import structlog

from app.core.exceptions import NotFoundError
from app.models.todo import Todo
from app.repositories.todo_repository import TodoRepository
from app.schemas.todo import TodoCreate, TodoUpdate

logger = structlog.get_logger(__name__)


class TodoService:
    def __init__(self, repo: TodoRepository) -> None:
        self._repo = repo

    async def list_todos(self, limit: int = 100, offset: int = 0) -> list[Todo]:
        return await self._repo.list_all(limit=limit, offset=offset)

    async def get_todo(self, todo_id: int) -> Todo:
        todo = await self._repo.get_by_id(todo_id)
        if not todo:
            raise NotFoundError("Todo", todo_id)
        return todo

    async def create_todo(self, data: TodoCreate) -> Todo:
        todo = await self._repo.create(data)
        logger.info(
            "todo_created",
            todo_id=todo.id,
            tenant_id=str(todo.tenant_id),
        )
        return todo

    async def update_todo(self, todo_id: int, data: TodoUpdate) -> Todo:
        todo = await self.get_todo(todo_id)
        updated = await self._repo.update(todo, data)
        logger.info(
            "todo_updated",
            todo_id=updated.id,
            tenant_id=str(updated.tenant_id),
        )
        return updated

    async def delete_todo(self, todo_id: int) -> None:
        todo = await self.get_todo(todo_id)
        tenant_id = str(todo.tenant_id)
        await self._repo.delete(todo)
        logger.info("todo_deleted", todo_id=todo_id, tenant_id=tenant_id)
