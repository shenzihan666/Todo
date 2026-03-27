from __future__ import annotations

import uuid

import structlog
from deepagents import create_deep_agent
from langchain_openai import ChatOpenAI

from app.core.config import settings
from app.services.agent.prompts import DEFAULT_SYSTEM_PROMPT
from app.services.agent.tools.db_tools import build_db_tools
from app.services.agent.tools.search_tools import build_search_tool

logger = structlog.get_logger(__name__)


def _build_llm() -> ChatOpenAI:
    return ChatOpenAI(
        base_url=settings.agent_llm_base_url,
        model=settings.agent_llm_model,
        api_key=settings.agent_llm_api_key,
    )


def _get_system_prompt() -> str:
    return settings.agent_system_prompt or DEFAULT_SYSTEM_PROMPT


def build_agent(tenant_id: uuid.UUID):
    """Build a stateless DeepAgent for a single request.

    Returns a compiled LangGraph ``CompiledStateGraph`` ready to ``.invoke()``
    or ``.stream()``.  Each call creates a lightweight graph — the LLM client
    is a thin HTTP wrapper, not a model load.

    For stateful migration, add ``checkpointer=<PostgresSaver>`` here and pass
    ``thread_id`` via ``config["configurable"]`` at call-site.
    """
    llm = _build_llm()
    prompt = _get_system_prompt()

    tools = build_db_tools(tenant_id)
    if settings.tavily_api_key:
        tools.append(build_search_tool(settings.tavily_api_key))

    agent = create_deep_agent(
        model=llm,
        tools=tools,
        system_prompt=prompt,
        # checkpointer=None — stateless; swap in AsyncPostgresSaver for stateful
    )

    logger.debug(
        "agent_built",
        tenant_id=str(tenant_id),
        model=settings.agent_llm_model,
        tool_count=len(tools),
    )
    return agent
