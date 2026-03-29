from __future__ import annotations

import uuid
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


class AgentHistoryMessageOut(BaseModel):
    """One user/assistant turn for client chat history UI."""

    role: Literal["user", "assistant"]
    content: str = Field(..., min_length=1)


class AgentChatRequest(BaseModel):
    message: str = Field(..., min_length=1, description="User message to send to the agent")
    thread_id: uuid.UUID | None = Field(
        None,
        description="Existing conversation thread id; omit to start a new thread",
    )
    media_ids: list[uuid.UUID] = Field(
        default_factory=list,
        description="Reserved for future multimodal support",
    )
    require_confirmation: bool = Field(
        False,
        description=(
            "When true, write tools (create/update/delete) do not commit; "
            "the SSE stream ends with a proposed_actions event listing pending operations."
        ),
    )


class ProposedActionItem(BaseModel):
    """One deferred DB operation from a dry-run agent turn."""

    action: Literal["create", "update", "delete"]
    args: dict[str, Any] = Field(default_factory=dict)
    display_title: str
    display_scheduled_at: str | None = None


class ExecuteActionsRequest(BaseModel):
    actions: list[ProposedActionItem] = Field(default_factory=list)


class ExecuteActionsResponse(BaseModel):
    executed: int
    results: list[str]


class AgentChatEvent(BaseModel):
    """Single SSE event pushed to the client."""

    event: str = Field(
        ...,
        description=(
            "Event type: token, tool_call, tool_result, proposed_actions, done, error, thread"
        ),
    )
    data: str | dict = Field(..., description="Event payload")
    thread_id: str | None = Field(
        None,
        description="Set on thread event and echoed on other events when applicable",
    )


class ConversationOut(BaseModel):
    """Agent thread metadata."""

    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    title: str | None = None


class MemoryItemOut(BaseModel):
    """One long-term memory entry (store key under /memories/)."""

    key: str
    preview: str = Field("", description="Short text preview")


class MemoryPutBody(BaseModel):
    content: str = Field(..., min_length=1)
