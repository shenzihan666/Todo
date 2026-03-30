from __future__ import annotations

import uuid
from collections.abc import Awaitable, Callable
from decimal import Decimal
from typing import Any

import structlog

from app.core.database import SessionLocal
from app.models.bill import Bill
from app.repositories.bill_repository import BillRepository
from app.schemas.bill import BillCreate, BillUpdate
from app.services.agent.bill_parsing import normalize_bill_type, parse_amount
from app.services.agent.tools.db_tools import (
    _format_display_scheduled_at,
    _parse_scheduled_at_iso_raw,
    parse_scheduled_at_iso,
)

logger = structlog.get_logger(__name__)


def format_display_amount(amount: Decimal, bill_type: str) -> str:
    """Short label for confirmation UI (Chinese prefix + currency)."""
    t = bill_type.lower()
    prefix = "收入" if t == "income" else "支出"
    return f"{prefix} ¥{amount:.2f}"


def _format_bill_line(bill: Bill) -> str:
    sched = bill.billed_at.isoformat() if bill.billed_at else "(no time)"
    cat = bill.category or "(no category)"
    return (
        f"  #{bill.id} [{bill.type}] {bill.title} — "
        f"¥{bill.amount:.2f} — category: {cat} — billed: {sched}"
    )


def _serialize_bill_update_args(bill_id: int, payload: dict) -> dict[str, Any]:
    out: dict[str, Any] = {"bill_id": bill_id}
    for k, v in payload.items():
        if k == "billed_at" and hasattr(v, "isoformat"):
            out[k] = v.isoformat()
        else:
            out[k] = v
    return out


def build_bill_tools(
    tenant_id: uuid.UUID,
    *,
    proposed_actions: list[dict[str, Any]] | None = None,
) -> list[Callable[..., Awaitable[str]]]:
    """Return bill CRUD tools bound to *tenant_id* (mirrors ``build_db_tools``)."""

    async def list_bills(
        billed_from: str | None = None,
        billed_to: str | None = None,
        bill_type: str | None = None,
        limit: int = 50,
    ) -> str:
        """List bills, optionally within a billed time window and/or by type.

        Args:
            billed_from: Optional lower bound (inclusive) for ``billed_at`` (timezone-aware ISO).
            billed_to: Optional upper bound (inclusive) for ``billed_at``.
            bill_type: Optional ``income`` or ``expense``.
            limit: Max rows (1–200, default 50).
        """
        bf = parse_scheduled_at_iso(billed_from) if billed_from else None
        bt = parse_scheduled_at_iso(billed_to) if billed_to else None
        if billed_from and bf is None:
            return (
                "Invalid billed_from: use timezone-aware ISO 8601 (e.g. ending with Z or +08:00)."
            )
        if billed_to and bt is None:
            return "Invalid billed_to: use timezone-aware ISO 8601 (e.g. ending with Z or +08:00)."
        if bf is not None and bt is not None and bf > bt:
            return "billed_from must be before or equal to billed_to."

        normalized_type = normalize_bill_type(bill_type)
        if bill_type is not None and normalized_type is None:
            return "Invalid bill_type: use income or expense."

        cap = min(max(int(limit), 1), 200)
        async with SessionLocal() as session:
            repo = BillRepository(session, tenant_id)
            rows = await repo.list_for_agent(
                billed_from=bf,
                billed_to=bt,
                bill_type=normalized_type,
                limit=cap,
                offset=0,
            )
        logger.info(
            "agent_tool_call",
            tool="list_bills",
            tenant_id=str(tenant_id),
            billed_from=billed_from,
            billed_to=billed_to,
            bill_type=bill_type,
            limit=cap,
            count=len(rows),
        )
        if not rows:
            return "No matching bills."
        lines = ["Bills:"] + [_format_bill_line(b) for b in rows]
        return "\n".join(lines)

    async def create_bill(
        title: str,
        amount: str | float | int,
        bill_type: str,
        category: str = "",
        description: str = "",
        billed_at: str | None = None,
    ) -> str:
        """Create a bill (income or expense).

        IMPORTANT: Only pass ``billed_at`` when the user stated a **specific clock
        time** (e.g. "下午4点", "4pm"). If the user only said a vague period without
        an hour, call ``ask_user_questions`` first instead of calling this tool.
        Similarly, if the user did not state an amount, ask before creating.

        Args:
            title: Short label (required).
            amount: Positive number (required).
            bill_type: ``income`` or ``expense``.
            category: Optional category label.
            description: Optional notes.
            billed_at: Optional ISO 8601 instant with timezone. Must come from an
                explicit user-stated time, never guessed.
        """
        parsed_type = normalize_bill_type(bill_type)
        if parsed_type is None:
            return "Invalid type: use income or expense."
        amt = parse_amount(amount)
        if amt is None:
            return "Invalid amount: must be a positive number."

        raw_billed = _parse_scheduled_at_iso_raw(billed_at)
        billed_db = parse_scheduled_at_iso(billed_at) if billed_at else None
        if billed_at and str(billed_at).strip() and billed_db is None:
            return "Invalid billed_at: use timezone-aware ISO 8601, or omit."

        if proposed_actions is not None:
            proposed_actions.append(
                {
                    "action": "create",
                    "target": "bill",
                    "args": {
                        "title": title,
                        "amount": str(amt),
                        "type": parsed_type,
                        "category": category or "",
                        "description": description or "",
                        "billed_at": billed_at,
                    },
                    "display_title": title,
                    "display_amount": format_display_amount(amt, parsed_type),
                    "display_scheduled_at": (
                        _format_display_scheduled_at(raw_billed) if raw_billed else None
                    ),
                },
            )
            logger.info(
                "agent_tool_call_dry",
                tool="create_bill",
                tenant_id=str(tenant_id),
                title=title,
                amount=str(amt),
                bill_type=parsed_type,
                category=category or "",
                description=description or "",
                billed_at=billed_at,
            )
            return f'(dry-run) Would create bill: "{title}"'

        async with SessionLocal() as session:
            repo = BillRepository(session, tenant_id)
            bill = await repo.create(
                BillCreate(
                    title=title,
                    description=description or None,
                    amount=amt,
                    type=parsed_type,  # type: ignore[arg-type]
                    category=category or None,
                    billed_at=billed_db,
                )
            )
            await session.commit()
            logger.info(
                "agent_tool_call",
                tool="create_bill",
                tenant_id=str(tenant_id),
                bill_id=str(bill.id),
                title=title,
                amount=str(amt),
                bill_type=parsed_type,
                category=category or "",
                description=description or "",
                billed_at=billed_at,
            )
            return f'Created bill #{bill.id}: "{bill.title}" ({bill.type} ¥{bill.amount})'

    async def update_bill(
        bill_id: int,
        title: str | None = None,
        amount: str | float | int | None = None,
        bill_type: str | None = None,
        category: str | None = None,
        description: str | None = None,
        billed_at: str | None = None,
    ) -> str:
        """Update a bill by id. Omit a field to leave it unchanged.

        Pass *billed_at* as an empty string to clear the billed time.
        """
        payload: dict = {}
        raw_billed = None
        clearing_billed = False
        if title is not None:
            payload["title"] = title
        if description is not None:
            payload["description"] = description
        if bill_type is not None:
            pt = normalize_bill_type(bill_type)
            if pt is None:
                return "Invalid type: use income or expense."
            payload["type"] = pt
        if category is not None:
            payload["category"] = category
        if amount is not None:
            amt = parse_amount(amount)
            if amt is None:
                return "Invalid amount: must be a positive number."
            payload["amount"] = amt
        if billed_at is not None:
            if str(billed_at).strip() == "":
                payload["billed_at"] = None
                clearing_billed = True
            else:
                raw_billed = _parse_scheduled_at_iso_raw(billed_at)
                parsed_billed = parse_scheduled_at_iso(billed_at)
                if parsed_billed is None:
                    return 'Invalid billed_at: use timezone-aware ISO 8601, or "" to clear.'
                payload["billed_at"] = parsed_billed

        if not payload:
            return "No changes: pass at least one field to update."

        data = BillUpdate(**payload)
        async with SessionLocal() as session:
            repo = BillRepository(session, tenant_id)
            bill = await repo.get_by_id(bill_id)
            if bill is None:
                return f"No bill with id {bill_id}."

            if proposed_actions is not None:
                display_amt = format_display_amount(
                    data.amount if data.amount is not None else bill.amount,
                    (data.type if data.type is not None else bill.type),
                )
                if billed_at is not None:
                    if clearing_billed:
                        display_time: str | None = None
                    else:
                        display_time = (
                            _format_display_scheduled_at(raw_billed) if raw_billed else None
                        )
                else:
                    display_time = (
                        _format_display_scheduled_at(bill.billed_at) if bill.billed_at else None
                    )
                proposed_actions.append(
                    {
                        "action": "update",
                        "target": "bill",
                        "args": _serialize_bill_update_args(
                            bill_id,
                            data.model_dump(exclude_unset=True),
                        ),
                        "display_title": title if title is not None else bill.title,
                        "display_amount": display_amt,
                        "display_scheduled_at": display_time,
                    },
                )
                logger.info(
                    "agent_tool_call_dry",
                    tool="update_bill",
                    tenant_id=str(tenant_id),
                    bill_id=bill_id,
                    title=title,
                    amount=str(amount) if amount is not None else None,
                    bill_type=bill_type,
                    category=category,
                    description=description,
                    billed_at=billed_at,
                )
                return f'(dry-run) Would update bill #{bill_id}: "{bill.title}"'

            await repo.update(bill, data)
            await session.commit()
            logger.info(
                "agent_tool_call",
                tool="update_bill",
                tenant_id=str(tenant_id),
                bill_id=bill_id,
                title=title,
                amount=str(amount) if amount is not None else None,
                bill_type=bill_type,
                category=category,
                description=description,
                billed_at=billed_at,
            )
            return f'Updated bill #{bill.id}: "{bill.title}"'

    async def delete_bill(bill_id: int) -> str:
        """Delete a bill by id. Use list_bills first if the id is unknown."""

        async with SessionLocal() as session:
            repo = BillRepository(session, tenant_id)
            bill = await repo.get_by_id(bill_id)
            if bill is None:
                return f"No bill with id {bill_id}."
            title = bill.title
            display_amt = format_display_amount(bill.amount, bill.type)
            display_time = _format_display_scheduled_at(bill.billed_at) if bill.billed_at else None

            if proposed_actions is not None:
                proposed_actions.append(
                    {
                        "action": "delete",
                        "target": "bill",
                        "args": {"bill_id": bill_id},
                        "display_title": title,
                        "display_amount": display_amt,
                        "display_scheduled_at": display_time,
                    },
                )
                logger.info(
                    "agent_tool_call_dry",
                    tool="delete_bill",
                    tenant_id=str(tenant_id),
                    bill_id=bill_id,
                    bill_title=title,
                )
                return f'(dry-run) Would delete bill #{bill_id}: "{title}"'

            await repo.delete(bill)
            await session.commit()
            logger.info(
                "agent_tool_call",
                tool="delete_bill",
                tenant_id=str(tenant_id),
                bill_id=bill_id,
                bill_title=title,
            )
            return f'Deleted bill #{bill_id}: "{title}"'

    return [list_bills, create_bill, update_bill, delete_bill]
