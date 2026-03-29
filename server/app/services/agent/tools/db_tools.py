from __future__ import annotations

import uuid
from datetime import UTC, datetime
from typing import Any

import structlog

from app.core.database import SessionLocal
from app.models.todo import Todo
from app.repositories.todo_repository import TodoRepository
from app.schemas.todo import TodoCreate, TodoUpdate

logger = structlog.get_logger(__name__)

_WEEKDAYS_CN = ("周一", "周二", "周三", "周四", "周五", "周六", "周日")


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


def _format_display_scheduled_at(dt: datetime | None) -> str | None:
    """Short Chinese weekday + HH:MM for confirmation UI."""
    if dt is None:
        return None
    wd = _WEEKDAYS_CN[dt.weekday()]
    return f"{wd} {dt.strftime('%H:%M')}"


def _format_todo_line(todo: Todo) -> str:
    sched = todo.scheduled_at.isoformat() if todo.scheduled_at else "(no time)"
    status = "done" if todo.completed else "pending"
    return f"  #{todo.id} [{status}] {todo.title} — scheduled: {sched}"


def _serialize_update_args(todo_id: int, payload: dict) -> dict[str, Any]:
    """JSON-serializable args for execute-actions."""
    out: dict[str, Any] = {"todo_id": todo_id}
    for k, v in payload.items():
        if k == "scheduled_at" and isinstance(v, datetime):
            out[k] = v.isoformat()
        else:
            out[k] = v
    return out


def build_db_tools(
    tenant_id: uuid.UUID,
    *,
    proposed_actions: list[dict[str, Any]] | None = None,
) -> list:
    """Return DB-writing tool callables bound to *tenant_id*.

    Each tool manages its own DB session and commits independently so that
    writes are durable regardless of how the agent streaming lifecycle ends.

    When *proposed_actions* is a list (dry-run / confirmation mode), write tools
    append ``ProposedActionItem``-shaped dicts and do not commit.
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
        if proposed_actions is not None:
            proposed_actions.append(
                {
                    "action": "create",
                    "args": {
                        "title": title,
                        "description": description or "",
                        "scheduled_at": scheduled_at,
                    },
                    "display_title": title,
                    "display_scheduled_at": (
                        _format_display_scheduled_at(parsed) if parsed else None
                    ),
                },
            )
            logger.info(
                "agent_tool_call_dry",
                tool="create_todo",
                tenant_id=str(tenant_id),
                title=title,
            )
            return f'(dry-run) Would create todo: "{title}"'

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
                parsed_sa = _parse_scheduled_at_iso(scheduled_at)
                if parsed_sa is None:
                    return "Invalid scheduled_at: use timezone-aware ISO 8601, or \"\" to clear."
                payload["scheduled_at"] = parsed_sa
        if not payload:
            return "No changes: pass at least one of title, description, completed, scheduled_at."

        data = TodoUpdate(**payload)
        async with SessionLocal() as session:
            repo = TodoRepository(session, tenant_id)
            todo = await repo.get_by_id(todo_id)
            if todo is None:
                return f"No todo with id {todo_id}."

            if proposed_actions is not None:
                display_sched = (
                    _format_display_scheduled_at(todo.scheduled_at) if todo.scheduled_at else None
                )
                proposed_actions.append(
                    {
                        "action": "update",
                        "args": _serialize_update_args(
                            todo_id,
                            data.model_dump(exclude_unset=True),
                        ),
                        "display_title": title if title is not None else todo.title,
                        "display_scheduled_at": display_sched,
                    },
                )
                logger.info(
                    "agent_tool_call_dry",
                    tool="update_todo",
                    tenant_id=str(tenant_id),
                    todo_id=str(todo_id),
                )
                return f'(dry-run) Would update todo #{todo_id}: "{todo.title}"'

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
            display_sched = (
                _format_display_scheduled_at(todo.scheduled_at) if todo.scheduled_at else None
            )

            if proposed_actions is not None:
                proposed_actions.append(
                    {
                        "action": "delete",
                        "args": {"todo_id": todo_id},
                        "display_title": title,
                        "display_scheduled_at": display_sched,
                    },
                )
                logger.info(
                    "agent_tool_call_dry",
                    tool="delete_todo",
                    tenant_id=str(tenant_id),
                    todo_id=str(todo_id),
                )
                return f'(dry-run) Would delete todo #{todo_id}: "{title}"'

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
