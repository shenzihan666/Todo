"""Pluggable long-term memory facade; current implementation uses LangGraph Postgres store."""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from typing import Protocol, runtime_checkable

from deepagents.backends.utils import create_file_data, file_data_to_string

from app.services.agent.memory_infra import get_store, memory_infra_initialized


def _store_namespace(tenant_id: uuid.UUID) -> tuple[str, ...]:
    return (str(tenant_id), "filesystem")


def _normalize_memory_key(key: str) -> str:
    k = key.strip()
    if not k.startswith("/"):
        k = f"/{k}"
    return k


@dataclass
class MemoryItem:
    """One retrieved memory row (RAG-ready: optional score/metadata)."""

    key: str
    content: str
    metadata: dict | None = None
    score: float | None = None


@runtime_checkable
class MemoryProvider(Protocol):
    async def store(
        self,
        tenant_id: uuid.UUID,
        key: str,
        content: str,
        *,
        metadata: dict | None = None,
    ) -> None: ...

    async def retrieve(
        self,
        tenant_id: uuid.UUID,
        query: str,
        *,
        limit: int = 10,
    ) -> list[MemoryItem]: ...

    async def list_keys(self, tenant_id: uuid.UUID, *, prefix: str = "") -> list[str]: ...

    async def delete(self, tenant_id: uuid.UUID, key: str) -> None: ...


class StoreMemoryProvider:
    """Maps tenant-scoped virtual paths to LangGraph ``PostgresStore`` keys."""

    async def store(
        self,
        tenant_id: uuid.UUID,
        key: str,
        content: str,
        *,
        metadata: dict | None = None,
    ) -> None:
        _ = metadata  # reserved for RAG / attributes
        store = get_store()
        if store is None:
            msg = "LangGraph store is not initialized"
            raise RuntimeError(msg)
        ns = _store_namespace(tenant_id)
        k = _normalize_memory_key(key)
        fd = create_file_data(content)
        value = {
            "content": fd["content"],
            "created_at": fd["created_at"],
            "modified_at": fd["modified_at"],
        }
        await store.aput(ns, k, value)

    async def retrieve(
        self,
        tenant_id: uuid.UUID,
        query: str,
        *,
        limit: int = 10,
    ) -> list[MemoryItem]:
        """Keyword scan over stored values; replace with vector search for RAG."""
        store = get_store()
        if store is None:
            return []
        ns_prefix = _store_namespace(tenant_id)
        q_lower = query.lower()
        out: list[MemoryItem] = []
        offset = 0
        page = 500
        while len(out) < limit:
            batch = await store.asearch(
                ns_prefix,
                query=None,
                limit=page,
                offset=offset,
            )
            if not batch:
                break
            for it in batch:
                text = ""
                try:
                    fd = {
                        "content": it.value["content"],
                        "created_at": it.value.get("created_at", ""),
                        "modified_at": it.value.get("modified_at", ""),
                    }
                    text = file_data_to_string(fd)
                except (KeyError, TypeError):
                    text = str(it.value)
                if not q_lower or q_lower in text.lower():
                    out.append(MemoryItem(key=it.key, content=text[:8000]))
                if len(out) >= limit:
                    break
            offset += page
            if len(batch) < page:
                break
        return out[:limit]

    async def list_keys(self, tenant_id: uuid.UUID, *, prefix: str = "") -> list[str]:
        items = await self.list_for_prefix(tenant_id, prefix=prefix or "/memories/")
        return [i.key for i in items]

    async def list_for_prefix(
        self,
        tenant_id: uuid.UUID,
        *,
        prefix: str = "/memories/",
    ) -> list[MemoryItem]:
        store = get_store()
        if store is None:
            return []
        ns_prefix = _store_namespace(tenant_id)
        pref = _normalize_memory_key(prefix)
        keys: list[MemoryItem] = []
        offset = 0
        page = 200
        while True:
            batch = await store.asearch(ns_prefix, query=None, limit=page, offset=offset)
            if not batch:
                break
            for it in batch:
                if not it.key.startswith(pref):
                    continue
                preview = ""
                try:
                    fd = {
                        "content": it.value["content"],
                        "created_at": it.value.get("created_at", ""),
                        "modified_at": it.value.get("modified_at", ""),
                    }
                    preview = file_data_to_string(fd)[:200]
                except (KeyError, TypeError):
                    preview = str(it.value)[:200]
                keys.append(MemoryItem(key=it.key, content=preview))
            offset += page
            if len(batch) < page:
                break
        return keys

    async def delete(self, tenant_id: uuid.UUID, key: str) -> None:
        store = get_store()
        if store is None:
            msg = "LangGraph store is not initialized"
            raise RuntimeError(msg)
        k = _normalize_memory_key(key)
        await store.adelete(_store_namespace(tenant_id), k)


def get_memory_provider() -> StoreMemoryProvider | None:
    if not memory_infra_initialized():
        return None
    return StoreMemoryProvider()
