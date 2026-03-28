from __future__ import annotations

import uuid

import structlog

from app.core.database import SessionLocal
from app.repositories.todo_repository import TodoRepository
from app.schemas.todo import TodoCreate

logger = structlog.get_logger(__name__)


def build_db_tools(tenant_id: uuid.UUID) -> list:
    """Return DB-writing tool callables bound to *tenant_id*.

    Each tool manages its own DB session and commits independently so that
    writes are durable regardless of how the agent streaming lifecycle ends.
    """

    async def create_todo(title: str, description: str = "") -> str:
        """Create a new todo item for the user.

        Args:
            title: A concise summary of the task (required).
            description: Optional extra details, deadline, or context.

        Returns:
            Confirmation message with the created todo's id and title.
        """
        async with SessionLocal() as session:
            repo = TodoRepository(session, tenant_id)
            todo = await repo.create(TodoCreate(title=title, description=description or None))
            await session.commit()
            logger.info(
                "agent_tool_call",
                tool="create_todo",
                tenant_id=str(tenant_id),
                todo_id=str(todo.id),
                title=title,
            )
            return f'Created todo #{todo.id}: "{todo.title}"'

    return [create_todo]
