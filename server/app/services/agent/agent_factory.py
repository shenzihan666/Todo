from __future__ import annotations

import uuid

import structlog
from deepagents import create_deep_agent
from langchain_openai import ChatOpenAI

from app.core.config import settings
from app.services.agent.memory_backend import make_composite_backend_factory
from app.services.agent.memory_infra import get_checkpointer, get_store, memory_infra_initialized
from app.services.agent.prompts import build_agent_system_prompt
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
    if settings.agent_system_prompt:
        return settings.agent_system_prompt
    return build_agent_system_prompt(memory_enabled=settings.agent_memory_enabled)


def build_agent(tenant_id: uuid.UUID):
    """Build a DeepAgent for this tenant.

    When ``agent_memory_enabled`` and LangGraph infra are up, the graph is
    checkpointed (``thread_id`` in ``config["configurable"]``) and ``/memories/``
    is backed by the LangGraph store.
    """
    llm = _build_llm()
    prompt = _get_system_prompt()

    tools = build_db_tools(tenant_id)
    if settings.tavily_api_key:
        tools.append(build_search_tool(settings.tavily_api_key))

    use_memory = settings.agent_memory_enabled and memory_infra_initialized()
    checkpointer = get_checkpointer() if use_memory else None
    store = get_store() if use_memory else None
    backend = make_composite_backend_factory(tenant_id)

    agent = create_deep_agent(
        model=llm,
        tools=tools,
        system_prompt=prompt,
        checkpointer=checkpointer,
        store=store,
        backend=backend,
    )

    logger.debug(
        "agent_built",
        tenant_id=str(tenant_id),
        model=settings.agent_llm_model,
        tool_count=len(tools),
        memory_enabled=use_memory,
    )
    return agent
