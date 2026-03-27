from __future__ import annotations

import uuid

from pydantic import BaseModel, Field


class AgentChatRequest(BaseModel):
    message: str = Field(..., min_length=1, description="User message to send to the agent")
    media_ids: list[uuid.UUID] = Field(
        default_factory=list,
        description="Reserved for future multimodal support",
    )


class AgentChatEvent(BaseModel):
    """Single SSE event pushed to the client."""

    event: str = Field(
        ...,
        description="Event type: token, tool_call, tool_result, done, error",
    )
    data: str | dict = Field(..., description="Event payload")
