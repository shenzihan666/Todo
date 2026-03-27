from __future__ import annotations

import json
import uuid
from collections.abc import AsyncIterator
from typing import Annotated

import structlog
from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse

from app.api.deps import get_tenant_id
from app.schemas.agent import AgentChatRequest
from app.services.agent.agent_factory import build_agent

logger = structlog.get_logger(__name__)

router = APIRouter(tags=["agent"])


def _sse(event: str, data: str | dict) -> str:
    payload = data if isinstance(data, str) else json.dumps(data, ensure_ascii=False)
    return f"event: {event}\ndata: {payload}\n\n"


async def _stream_agent(
    tenant_id: uuid.UUID,
    message: str,
) -> AsyncIterator[str]:
    """Run the agent and yield SSE-formatted chunks."""
    agent = build_agent(tenant_id)

    try:
        async for chunk in agent.astream(
            {"messages": [{"role": "user", "content": message}]},
            stream_mode="messages",
            version="v2",
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
            if token.type == "tool":
                yield _sse(
                    "tool_result",
                    {
                        "tool": token.name,
                        "content": str(token.content)[:500],
                    },
                )

            # AI text content (skip tool-call-only messages)
            if (
                token.type == "ai"
                and token.content
                and not getattr(token, "tool_call_chunks", None)
            ):
                yield _sse("token", token.content)

        yield _sse("done", "")

    except Exception:
        logger.exception("agent_stream_error", tenant_id=str(tenant_id))
        yield _sse("error", "An internal error occurred while processing your request.")


@router.post("/chat")
async def agent_chat(
    body: AgentChatRequest,
    tenant_id: Annotated[uuid.UUID, Depends(get_tenant_id)],
) -> StreamingResponse:
    """Chat with the AI agent. Returns a Server-Sent Events stream."""
    logger.info("agent_chat_request", tenant_id=str(tenant_id), message_len=len(body.message))
    return StreamingResponse(
        _stream_agent(tenant_id, body.message),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
        },
    )
