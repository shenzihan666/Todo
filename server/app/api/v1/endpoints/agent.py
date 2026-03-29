from __future__ import annotations

import json
import uuid
from collections.abc import AsyncIterator
from datetime import UTC, datetime
from typing import Annotated

import structlog
from fastapi import APIRouter, Depends, HTTPException, Response
from fastapi.responses import StreamingResponse
from langchain_core.messages import AIMessage, AIMessageChunk, ToolMessage, ToolMessageChunk
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_tenant_id
from app.core.config import settings
from app.core.database import get_session
from app.core.exceptions import NotFoundError
from app.schemas.agent import (
    AgentChatRequest,
    ConversationOut,
    MemoryItemOut,
    MemoryPutBody,
)
from app.services.agent.agent_factory import build_agent
from app.services.agent.memory_infra import memory_infra_initialized
from app.services.agent.memory_provider import StoreMemoryProvider
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


async def _stream_agent(
    tenant_id: uuid.UUID,
    message: str,
    *,
    thread_id: str | None,
) -> AsyncIterator[str]:
    """Run the agent and yield SSE-formatted chunks."""
    agent = build_agent(tenant_id)
    full_reply_parts: list[str] = []

    stream_config: dict | None = None
    if thread_id:
        stream_config = {
            "configurable": {
                "thread_id": thread_id,
            },
        }
        yield _sse("thread", {"thread_id": thread_id})

    try:
        async for chunk in agent.astream(
            {"messages": [{"role": "user", "content": _user_message_with_reference_utc(message)}]},
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
    )

    resolved_thread: str | None = None
    if settings.agent_memory_enabled and memory_infra_initialized():
        svc = ConversationService(session, tenant_id)
        try:
            tid = await svc.ensure_thread(body.thread_id)
        except NotFoundError:
            raise
        resolved_thread = str(tid)

    return StreamingResponse(
        _stream_agent(tenant_id, body.message, thread_id=resolved_thread),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
        },
    )


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
