"""Composite virtual filesystem: ephemeral state + persistent `/memories/` per tenant."""

from __future__ import annotations

import uuid

from deepagents.backends import CompositeBackend, StateBackend, StoreBackend
from deepagents.backends.store import BackendContext, NamespaceFactory

from app.core.config import settings


def _namespace_for_tenant(tenant_id: uuid.UUID) -> NamespaceFactory:
    """Isolate LangGraph store keys per tenant (UUID string + filesystem segment)."""

    tid = str(tenant_id)

    def factory(_ctx: BackendContext) -> tuple[str, ...]:
        return (tid, "filesystem")

    return factory


def make_composite_backend_factory(tenant_id: uuid.UUID):
    """Return a backend factory for `create_deep_agent(backend=...)`."""

    ns = _namespace_for_tenant(tenant_id)

    def factory(runtime):
        if not settings.agent_memory_enabled:
            return StateBackend(runtime)
        return CompositeBackend(
            default=StateBackend(runtime),
            routes={
                "/memories/": StoreBackend(runtime, namespace=ns),
            },
        )

    return factory
