from __future__ import annotations

import json
import uuid
from collections.abc import AsyncIterator
from datetime import UTC, datetime
from typing import Annotated, Any

import structlog
from fastapi import APIRouter, Depends, HTTPException, Response
from fastapi.responses import StreamingResponse
from langchain_core.messages import (
    AIMessage,
    AIMessageChunk,
    HumanMessage,
    ToolMessage,
    ToolMessageChunk,
)
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_tenant_id
from app.core.config import settings
from app.core.database import get_session
from app.core.exceptions import NotFoundError
from app.repositories.bill_repository import BillRepository
from app.repositories.media_repository import MediaRepository
from app.repositories.todo_repository import TodoRepository
from app.schemas.agent import (
    AgentChatRequest,
    AgentHistoryMessageOut,
    ConversationOut,
    ExecuteActionsRequest,
    ExecuteActionsResponse,
    MediaRef,
    MemoryItemOut,
    MemoryPutBody,
)
from app.schemas.bill import BillCreate, BillUpdate
from app.schemas.todo import TodoCreate, TodoUpdate
from app.services.agent.agent_factory import build_agent
from app.services.agent.memory_infra import memory_infra_initialized
from app.services.agent.memory_provider import StoreMemoryProvider
from app.services.agent.resolve_media import ResolvedMedia, resolve_media_for_agent
from app.services.agent.tools.bill_tools import _normalize_bill_type, _parse_amount
from app.services.agent.tools.db_tools import parse_scheduled_at_iso
from app.services.conversation_service import ConversationService

logger = structlog.get_logger(__name__)

router = APIRouter(tags=["agent"])


def _sse(event: str, data: str | dict) -> str:
    payload = data if isinstance(data, str) else json.dumps(data, ensure_ascii=False)
    return f"event: {event}\ndata: {payload}\n\n"


def _user_message_with_reference_utc(message: str) -> str:
    """Prefix user text with server UTC so the agent can resolve 'today' / 'tomorrow'."""
    ref = datetime.now(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    return f"[Reference UTC time: {ref}]\n\n{message}"


def _message_content_as_text(content: object) -> str:
    """Normalize LangChain message content (str or multimodal blocks) to plain text."""
    if content is None:
        return ""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for block in content:
            if isinstance(block, str):
                parts.append(block)
            elif isinstance(block, dict) and block.get("type") == "text":
                parts.append(str(block.get("text", "")))
        return "".join(parts)
    return str(content)


async def _media_refs_for_human_message(
    session: AsyncSession,
    tenant_id: uuid.UUID,
    msg: HumanMessage,
) -> list[MediaRef]:
    """Resolve ``media_ids`` stored on the HumanMessage for history API."""
    kw = getattr(msg, "additional_kwargs", None) or {}
    raw_ids = kw.get("media_ids")
    if not raw_ids:
        return []
    repo = MediaRepository(session, tenant_id)
    refs: list[MediaRef] = []
    for x in raw_ids:
        try:
            mid = uuid.UUID(str(x))
        except ValueError:
            continue
        row = await repo.get_by_id(mid)
        if row is not None:
            refs.append(MediaRef(id=row.id, content_type=row.content_type))
    return refs


async def _history_messages_from_state(
    session: AsyncSession,
    tenant_id: uuid.UUID,
    raw_messages: list[Any],
) -> list[AgentHistoryMessageOut]:
    """Map LangGraph state messages to user/assistant rows for the mobile chat UI."""
    out: list[AgentHistoryMessageOut] = []
    for msg in raw_messages:
        if isinstance(msg, HumanMessage):
            text = _message_content_as_text(msg.content).strip()
            media_refs = await _media_refs_for_human_message(session, tenant_id, msg)
            if not text and not media_refs:
                continue
            out.append(
                AgentHistoryMessageOut(
                    role="user",
                    content=text,
                    media=media_refs,
                ),
            )
        elif isinstance(msg, AIMessage):
            text = _message_content_as_text(msg.content).strip()
            if text:
                out.append(AgentHistoryMessageOut(role="assistant", content=text))
    return out


def _build_human_message(message: str, resolved: list[ResolvedMedia]) -> HumanMessage:
    """Build text or multimodal user message for the agent graph."""
    prefixed = _user_message_with_reference_utc(message)
    if not resolved:
        return HumanMessage(content=prefixed)
    blocks: list[dict[str, Any]] = [{"type": "text", "text": prefixed}]
    for m in resolved:
        data_url = f"data:{m.content_type};base64,{m.base64_data}"
        blocks.append(
            {
                "type": "image_url",
                "image_url": {"url": data_url},
            },
        )
    return HumanMessage(
        content=blocks,
        additional_kwargs={"media_ids": [str(m.media_id) for m in resolved]},
    )


def _bill_update_from_execute_args(payload: dict[str, Any]) -> BillUpdate:
    """Build ``BillUpdate`` from ``/execute-actions`` JSON args (no ``bill_id``)."""
    cleaned: dict[str, Any] = {}
    for key in ("title", "description", "amount", "type", "category", "billed_at"):
        if key not in payload:
            continue
        val = payload[key]
        if key == "billed_at":
            if val is None or (isinstance(val, str) and not str(val).strip()):
                cleaned[key] = None
            else:
                parsed = parse_scheduled_at_iso(str(val))
                if parsed is None:
                    raise HTTPException(
                        status_code=400,
                        detail="Invalid billed_at for update.",
                    )
                cleaned[key] = parsed
        elif key == "amount":
            amt = _parse_amount(val)
            if amt is None:
                raise HTTPException(status_code=400, detail="Invalid amount for update.")
            cleaned[key] = amt
        elif key == "type":
            pt = _normalize_bill_type(str(val))
            if pt is None:
                raise HTTPException(status_code=400, detail="Invalid type for update.")
            cleaned[key] = pt
        else:
            cleaned[key] = val
    if not cleaned:
        raise HTTPException(status_code=400, detail="No fields to update.")
    return BillUpdate(**cleaned)


def _todo_update_from_execute_args(payload: dict[str, Any]) -> TodoUpdate:
    """Build ``TodoUpdate`` from ``/execute-actions`` JSON args (no ``todo_id``)."""
    cleaned: dict[str, Any] = {}
    for key in ("title", "description", "completed", "scheduled_at"):
        if key not in payload:
            continue
        val = payload[key]
        if key == "scheduled_at":
            if val is None or (isinstance(val, str) and not str(val).strip()):
                cleaned[key] = None
            else:
                parsed = parse_scheduled_at_iso(str(val))
                if parsed is None:
                    raise HTTPException(
                        status_code=400,
                        detail="Invalid scheduled_at for update.",
                    )
                cleaned[key] = parsed
        elif key == "completed":
            cleaned[key] = bool(val)
        else:
            cleaned[key] = val
    if not cleaned:
        raise HTTPException(status_code=400, detail="No fields to update.")
    return TodoUpdate(**cleaned)


async def _stream_agent(
    tenant_id: uuid.UUID,
    message: str,
    *,
    thread_id: str | None,
    require_confirmation: bool = False,
    resolved_media: list[ResolvedMedia] | None = None,
) -> AsyncIterator[str]:
    """Run the agent and yield SSE-formatted chunks."""
    resolved = resolved_media or []
    proposed: list[dict[str, Any]] | None = [] if require_confirmation else None
    clarifications: list[str] = []
    agent = build_agent(
        tenant_id,
        proposed_actions=proposed,
        clarification_questions=clarifications,
    )
    full_reply_parts: list[str] = []

    stream_config: dict | None = None
    if thread_id:
        stream_config = {
            "configurable": {
                "thread_id": thread_id,
            },
        }
        yield _sse("thread", {"thread_id": thread_id})

    human = _build_human_message(message, resolved)

    try:
        async for chunk in agent.astream(
            {"messages": [human]},
            stream_mode="messages",
            version="v2",
            config=stream_config,
        ):
            if chunk.get("type") != "messages":
                continue

            token, _metadata = chunk["data"]

            # Tool call chunks
            if hasattr(token, "tool_call_chunks") and token.tool_call_chunks:
                for tc in token.tool_call_chunks:
                    name = tc.get("name")
                    if name:
                        yield _sse("tool_call", {"tool": name})

            # Tool result messages
            if isinstance(token, (ToolMessage, ToolMessageChunk)):
                yield _sse(
                    "tool_result",
                    {
                        "tool": token.name,
                        "content": _message_content_as_text(token.content)[:500],
                    },
                )

            # AI text: streaming uses AIMessageChunk, whose .type is "AIMessageChunk", not "ai"
            if isinstance(token, (AIMessage, AIMessageChunk)):
                if getattr(token, "tool_call_chunks", None):
                    continue
                text = _message_content_as_text(token.content)
                if text:
                    full_reply_parts.append(text)
                    yield _sse("token", text)

        full_text = "".join(full_reply_parts)
        logger.info(
            "agent_llm_response",
            tenant_id=str(tenant_id),
            thread_id=thread_id,
            reply_char_len=len(full_text),
            reply_text=full_text,
        )
        if proposed:
            yield _sse("proposed_actions", {"actions": proposed})
        if clarifications:
            yield _sse("clarification", {"questions": clarifications})
        yield _sse("done", "")

    except Exception:
        logger.exception("agent_stream_error", tenant_id=str(tenant_id), thread_id=thread_id)
        yield _sse("error", "An internal error occurred while processing your request.")


@router.post("/chat")
async def agent_chat(
    body: AgentChatRequest,
    tenant_id: Annotated[uuid.UUID, Depends(get_tenant_id)],
    session: Annotated[AsyncSession, Depends(get_session)],
) -> StreamingResponse:
    """Chat with the AI agent. Returns a Server-Sent Events stream."""
    logger.info(
        "agent_chat_request",
        tenant_id=str(tenant_id),
        message_len=len(body.message),
        has_thread_id=body.thread_id is not None,
        media_count=len(body.media_ids),
    )

    resolved_media = await resolve_media_for_agent(session, tenant_id, list(body.media_ids))

    resolved_thread: str | None = None
    if settings.agent_memory_enabled and memory_infra_initialized():
        svc = ConversationService(session, tenant_id)
        try:
            tid = await svc.ensure_thread(body.thread_id)
        except NotFoundError:
            raise
        resolved_thread = str(tid)

    return StreamingResponse(
        _stream_agent(
            tenant_id,
            body.message,
            thread_id=resolved_thread,
            require_confirmation=body.require_confirmation,
            resolved_media=resolved_media,
        ),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
        },
    )


@router.post("/execute-actions", response_model=ExecuteActionsResponse)
async def execute_agent_actions(
    body: ExecuteActionsRequest,
    tenant_id: Annotated[uuid.UUID, Depends(get_tenant_id)],
    session: Annotated[AsyncSession, Depends(get_session)],
) -> ExecuteActionsResponse:
    """Apply confirmed todo operations from a prior dry-run agent turn."""
    if not body.actions:
        return ExecuteActionsResponse(executed=0, results=[])

    todo_repo = TodoRepository(session, tenant_id)
    bill_repo = BillRepository(session, tenant_id)
    results: list[str] = []
    for item in body.actions:
        if item.target == "bill":
            if item.action == "create":
                a = item.args
                title = str(a.get("title", "")).strip()
                if not title:
                    raise HTTPException(status_code=400, detail="create bill action missing title")
                amt = _parse_amount(a.get("amount"))
                if amt is None:
                    raise HTTPException(
                        status_code=400,
                        detail="create bill action missing valid amount",
                    )
                bt = _normalize_bill_type(str(a.get("type", "")))
                if bt is None:
                    raise HTTPException(status_code=400, detail="create bill action missing type")
                raw_desc = a.get("description")
                if raw_desc is None or (isinstance(raw_desc, str) and not str(raw_desc).strip()):
                    description = None
                else:
                    description = str(raw_desc)
                raw_cat = a.get("category")
                if raw_cat is None or (isinstance(raw_cat, str) and not str(raw_cat).strip()):
                    category = None
                else:
                    category = str(raw_cat)
                ba_raw = a.get("billed_at")
                if ba_raw is None or (isinstance(ba_raw, str) and not str(ba_raw).strip()):
                    billed_at = None
                else:
                    billed_at = parse_scheduled_at_iso(str(ba_raw))
                    if billed_at is None:
                        raise HTTPException(
                            status_code=400,
                            detail="Invalid billed_at for create bill.",
                        )
                bill = await bill_repo.create(
                    BillCreate(
                        title=title,
                        description=description,
                        amount=amt,
                        type=bt,  # type: ignore[arg-type]
                        category=category,
                        billed_at=billed_at,
                    ),
                )
                await session.commit()
                results.append(
                    f'Created bill #{bill.id}: "{bill.title}" ({bill.type} ¥{bill.amount})',
                )
            elif item.action == "update":
                a = item.args
                bill_id_raw = a.get("bill_id")
                if bill_id_raw is None:
                    raise HTTPException(
                        status_code=400,
                        detail="update bill action missing bill_id",
                    )
                bill_id = int(bill_id_raw)
                payload = {k: v for k, v in a.items() if k != "bill_id"}
                bill = await bill_repo.get_by_id(bill_id)
                if bill is None:
                    raise HTTPException(status_code=404, detail=f"No bill with id {bill_id}.")
                bu = _bill_update_from_execute_args(payload)
                updated = await bill_repo.update(bill, bu)
                await session.commit()
                results.append(f'Updated bill #{updated.id}: "{updated.title}"')
            elif item.action == "delete":
                a = item.args
                bill_id_raw = a.get("bill_id")
                if bill_id_raw is None:
                    raise HTTPException(
                        status_code=400,
                        detail="delete bill action missing bill_id",
                    )
                bill_id = int(bill_id_raw)
                bill = await bill_repo.get_by_id(bill_id)
                if bill is None:
                    raise HTTPException(status_code=404, detail=f"No bill with id {bill_id}.")
                title = bill.title
                await bill_repo.delete(bill)
                await session.commit()
                results.append(f'Deleted bill #{bill_id}: "{title}"')
            else:
                raise HTTPException(status_code=400, detail=f"Unknown action {item.action!r}")
            continue

        if item.action == "create":
            a = item.args
            title = str(a.get("title", "")).strip()
            if not title:
                raise HTTPException(status_code=400, detail="create action missing title")
            raw_desc = a.get("description")
            if raw_desc is None or (isinstance(raw_desc, str) and not str(raw_desc).strip()):
                description = None
            else:
                description = str(raw_desc)
            sa_raw = a.get("scheduled_at")
            scheduled_at = parse_scheduled_at_iso(str(sa_raw)) if sa_raw else None
            todo = await todo_repo.create(
                TodoCreate(title=title, description=description, scheduled_at=scheduled_at),
            )
            await session.commit()
            results.append(f'Created todo #{todo.id}: "{todo.title}"')
        elif item.action == "update":
            a = item.args
            todo_id_raw = a.get("todo_id")
            if todo_id_raw is None:
                raise HTTPException(status_code=400, detail="update action missing todo_id")
            todo_id = int(todo_id_raw)
            payload = {k: v for k, v in a.items() if k != "todo_id"}
            todo = await todo_repo.get_by_id(todo_id)
            if todo is None:
                raise HTTPException(status_code=404, detail=f"No todo with id {todo_id}.")
            tu = _todo_update_from_execute_args(payload)
            updated = await todo_repo.update(todo, tu)
            await session.commit()
            results.append(f'Updated todo #{updated.id}: "{updated.title}"')
        elif item.action == "delete":
            a = item.args
            todo_id_raw = a.get("todo_id")
            if todo_id_raw is None:
                raise HTTPException(status_code=400, detail="delete action missing todo_id")
            todo_id = int(todo_id_raw)
            todo = await todo_repo.get_by_id(todo_id)
            if todo is None:
                raise HTTPException(status_code=404, detail=f"No todo with id {todo_id}.")
            title = todo.title
            await todo_repo.delete(todo)
            await session.commit()
            results.append(f'Deleted todo #{todo_id}: "{title}"')
        else:
            raise HTTPException(status_code=400, detail=f"Unknown action {item.action!r}")

    return ExecuteActionsResponse(executed=len(results), results=results)


@router.get("/threads", response_model=list[ConversationOut])
async def list_agent_threads(
    tenant_id: Annotated[uuid.UUID, Depends(get_tenant_id)],
    session: Annotated[AsyncSession, Depends(get_session)],
    limit: int = 100,
    offset: int = 0,
) -> list[ConversationOut]:
    if not settings.agent_memory_enabled or not memory_infra_initialized():
        return []
    svc = ConversationService(session, tenant_id)
    rows = await svc.list_threads(limit=limit, offset=offset)
    return [ConversationOut.model_validate(r) for r in rows]


@router.get("/threads/{thread_id}/history", response_model=list[AgentHistoryMessageOut])
async def get_agent_thread_history(
    thread_id: uuid.UUID,
    tenant_id: Annotated[uuid.UUID, Depends(get_tenant_id)],
    session: Annotated[AsyncSession, Depends(get_session)],
) -> list[AgentHistoryMessageOut]:
    """Return user/assistant text turns from the LangGraph checkpoint for this thread."""
    if not settings.agent_memory_enabled or not memory_infra_initialized():
        return []
    svc = ConversationService(session, tenant_id)
    try:
        await svc.ensure_thread(thread_id)
    except NotFoundError:
        raise HTTPException(status_code=404, detail="Conversation not found") from None

    agent = build_agent(tenant_id)
    config: dict[str, Any] = {"configurable": {"thread_id": str(thread_id)}}
    try:
        snap = await agent.aget_state(config)
    except ValueError:
        return []
    values = snap.values or {}
    raw = values.get("messages")
    if not isinstance(raw, list):
        return []
    return await _history_messages_from_state(session, tenant_id, raw)


@router.delete("/threads/{thread_id}", status_code=204)
async def delete_agent_thread(
    thread_id: uuid.UUID,
    tenant_id: Annotated[uuid.UUID, Depends(get_tenant_id)],
    session: Annotated[AsyncSession, Depends(get_session)],
) -> Response:
    if not settings.agent_memory_enabled or not memory_infra_initialized():
        raise HTTPException(status_code=503, detail="Agent memory is not available")
    svc = ConversationService(session, tenant_id)
    try:
        await svc.delete_thread(thread_id)
    except NotFoundError:
        raise
    return Response(status_code=204)


@router.get("/memory", response_model=list[MemoryItemOut])
async def list_agent_memory(
    tenant_id: Annotated[uuid.UUID, Depends(get_tenant_id)],
    prefix: str = "/memories/",
) -> list[MemoryItemOut]:
    if not settings.agent_memory_enabled or not memory_infra_initialized():
        return []
    provider = StoreMemoryProvider()
    items = await provider.list_for_prefix(tenant_id, prefix=prefix)
    return [MemoryItemOut(key=i.key, preview=i.content) for i in items]


@router.put("/memory/{key:path}", status_code=204)
async def put_agent_memory(
    key: str,
    body: MemoryPutBody,
    tenant_id: Annotated[uuid.UUID, Depends(get_tenant_id)],
) -> Response:
    if not settings.agent_memory_enabled or not memory_infra_initialized():
        raise HTTPException(status_code=503, detail="Agent memory is not available")
    provider = StoreMemoryProvider()
    await provider.store(tenant_id, key, body.content)
    return Response(status_code=204)


@router.delete("/memory/{key:path}", status_code=204)
async def delete_agent_memory(
    key: str,
    tenant_id: Annotated[uuid.UUID, Depends(get_tenant_id)],
) -> Response:
    if not settings.agent_memory_enabled or not memory_infra_initialized():
        raise HTTPException(status_code=503, detail="Agent memory is not available")
    provider = StoreMemoryProvider()
    await provider.delete(tenant_id, key)
    return Response(status_code=204)
