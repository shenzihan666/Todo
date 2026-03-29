from __future__ import annotations

import uuid
from datetime import UTC, datetime

import structlog

from app.core.database import SessionLocal
from app.models.todo import Todo
from app.repositories.todo_repository import TodoRepository
from app.schemas.todo import TodoCreate, TodoUpdate

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


def _format_todo_line(todo: Todo) -> str:
    sched = todo.scheduled_at.isoformat() if todo.scheduled_at else "(no time)"
    status = "done" if todo.completed else "pending"
    return f"  #{todo.id} [{status}] {todo.title} — scheduled: {sched}"


def build_db_tools(tenant_id: uuid.UUID) -> list:
    """Return DB-writing tool callables bound to *tenant_id*.

    Each tool manages its own DB session and commits independently so that
    writes are durable regardless of how the agent streaming lifecycle ends.
    """

    async def list_todos(
        scheduled_from: str | None = None,
        scheduled_to: str | None = None,
        limit: int = 50,
    ) -> str:
        """List the user's todos, optionally within a scheduled time window.

        Use ISO 8601 with timezone for *scheduled_from* / *scheduled_to* (same as
        create_todo). When either bound is set, only todos with a non-null
        ``scheduled_at`` in that inclusive range are returned—suitable for phrases
        like \"this morning\" or \"this afternoon\" once resolved to instants.

        Args:
            scheduled_from: Optional lower bound (inclusive) for ``scheduled_at``.
            scheduled_to: Optional upper bound (inclusive) for ``scheduled_at``.
            limit: Max rows (1–200, default 50).

        Returns:
            A human-readable list of matching todos, or a note if none match.
        """
        sf = _parse_scheduled_at_iso(scheduled_from) if scheduled_from else None
        st = _parse_scheduled_at_iso(scheduled_to) if scheduled_to else None
        if scheduled_from and sf is None:
            return (
                "Invalid scheduled_from: use timezone-aware ISO 8601 "
                "(e.g. ending with Z or +08:00)."
            )
        if scheduled_to and st is None:
            return (
                "Invalid scheduled_to: use timezone-aware ISO 8601 "
                "(e.g. ending with Z or +08:00)."
            )
        if sf is not None and st is not None and sf > st:
            return "scheduled_from must be before or equal to scheduled_to."

        cap = min(max(int(limit), 1), 200)
        async with SessionLocal() as session:
            repo = TodoRepository(session, tenant_id)
            rows = await repo.list_for_agent(
                scheduled_from=sf,
                scheduled_to=st,
                limit=cap,
                offset=0,
            )
        logger.info(
            "agent_tool_call",
            tool="list_todos",
            tenant_id=str(tenant_id),
            count=len(rows),
        )
        if not rows:
            return "No matching todos."
        lines = ["Todos:"] + [_format_todo_line(t) for t in rows]
        return "\n".join(lines)

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

    async def update_todo(
        todo_id: int,
        title: str | None = None,
        description: str | None = None,
        completed: bool | None = None,
        scheduled_at: str | None = None,
    ) -> str:
        """Update an existing todo by id. Omit a field to leave it unchanged.

        Pass *scheduled_at* as an empty string to clear the scheduled time.

        Args:
            todo_id: Database id of the todo (from list_todos).
            title: New title, if changing.
            description: New description, if changing.
            completed: Mark completed or not, if changing.
            scheduled_at: New instant in ISO 8601 with timezone, or \"\" to clear.

        Returns:
            Confirmation or an error message.
        """
        payload: dict = {}
        if title is not None:
            payload["title"] = title
        if description is not None:
            payload["description"] = description
        if completed is not None:
            payload["completed"] = completed
        if scheduled_at is not None:
            if str(scheduled_at).strip() == "":
                payload["scheduled_at"] = None
            else:
                parsed = _parse_scheduled_at_iso(scheduled_at)
                if parsed is None:
                    return "Invalid scheduled_at: use timezone-aware ISO 8601, or \"\" to clear."
                payload["scheduled_at"] = parsed
        if not payload:
            return "No changes: pass at least one of title, description, completed, scheduled_at."

        data = TodoUpdate(**payload)
        async with SessionLocal() as session:
            repo = TodoRepository(session, tenant_id)
            todo = await repo.get_by_id(todo_id)
            if todo is None:
                return f"No todo with id {todo_id}."
            await repo.update(todo, data)
            await session.commit()
            logger.info(
                "agent_tool_call",
                tool="update_todo",
                tenant_id=str(tenant_id),
                todo_id=str(todo_id),
            )
            return f'Updated todo #{todo.id}: "{todo.title}"'

    async def delete_todo(todo_id: int) -> str:
        """Delete a todo by id. Use list_todos first if the id is unknown."""

        async with SessionLocal() as session:
            repo = TodoRepository(session, tenant_id)
            todo = await repo.get_by_id(todo_id)
            if todo is None:
                return f"No todo with id {todo_id}."
            title = todo.title
            await repo.delete(todo)
            await session.commit()
            logger.info(
                "agent_tool_call",
                tool="delete_todo",
                tenant_id=str(tenant_id),
                todo_id=str(todo_id),
            )
            return f'Deleted todo #{todo_id}: "{title}"'

    return [list_todos, create_todo, update_todo, delete_todo]
