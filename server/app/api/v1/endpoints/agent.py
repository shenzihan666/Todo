from __future__ import annotations

import json
import uuid
from collections.abc import AsyncIterator
from typing import Annotated

import structlog
from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse
from langchain_core.messages import AIMessage, AIMessageChunk, ToolMessage, ToolMessageChunk

from app.api.deps import get_tenant_id
from app.schemas.agent import AgentChatRequest
from app.services.agent.agent_factory import build_agent

logger = structlog.get_logger(__name__)

router = APIRouter(tags=["agent"])


def _sse(event: str, data: str | dict) -> str:
    payload = data if isinstance(data, str) else json.dumps(data, ensure_ascii=False)
    return f"event: {event}\ndata: {payload}\n\n"


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
) -> AsyncIterator[str]:
    """Run the agent and yield SSE-formatted chunks."""
    agent = build_agent(tenant_id)
    full_reply_parts: list[str] = []

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
            reply_char_len=len(full_text),
            reply_text=full_text,
        )
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
