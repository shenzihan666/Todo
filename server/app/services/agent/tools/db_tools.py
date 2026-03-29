from __future__ import annotations

import uuid
from datetime import UTC, datetime

import structlog

from app.core.database import SessionLocal
from app.repositories.todo_repository import TodoRepository
from app.schemas.todo import TodoCreate

logger = structlog.get_logger(__name__)


def _parse_scheduled_at_iso(value: str | None) -> datetime | None:
    """Parse tool argument into a timezone-aware datetime, or None if invalid."""
    if value is None or not str(value).strip():
        return None
    s = str(value).strip()
    if s.endswith("Z"):
        s = s[:-1] + "+00:00"
    try:
        dt = datetime.fromisoformat(s)
    except ValueError:
        logger.warning("scheduled_at_parse_failed", value=value)
        return None
    if dt.tzinfo is None:
        logger.warning("scheduled_at_must_be_timezone_aware", value=value)
        return None
    return dt.astimezone(UTC)


def build_db_tools(tenant_id: uuid.UUID) -> list:
    """Return DB-writing tool callables bound to *tenant_id*.

    Each tool manages its own DB session and commits independently so that
    writes are durable regardless of how the agent streaming lifecycle ends.
    """

    async def create_todo(
        title: str,
        description: str = "",
        scheduled_at: str | None = None,
    ) -> str:
        """Create a new todo item for the user.

        Args:
            title: A concise summary of the task (required).
            description: Optional extra details, deadline, or context.
            scheduled_at: Optional ISO 8601 instant (with timezone) for when the task applies.

        Returns:
            Confirmation message with the created todo's id and title.
        """
        parsed = _parse_scheduled_at_iso(scheduled_at)
        async with SessionLocal() as session:
            repo = TodoRepository(session, tenant_id)
            todo = await repo.create(
                TodoCreate(
                    title=title,
                    description=description or None,
                    scheduled_at=parsed,
                )
            )
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
