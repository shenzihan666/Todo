"""LangGraph Postgres checkpointer + store (shared psycopg pool) for agent memory."""

from __future__ import annotations

import structlog
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
from langgraph.store.postgres.aio import AsyncPostgresStore
from psycopg.rows import dict_row
from psycopg_pool import AsyncConnectionPool

logger = structlog.get_logger(__name__)

_pool: AsyncConnectionPool | None = None
_checkpointer: AsyncPostgresSaver | None = None
_store: AsyncPostgresStore | None = None


async def init_memory_infra(conn_string: str) -> None:
    """Open a shared pool, run migrations for checkpoint + store tables."""
    global _pool, _checkpointer, _store
    if _pool is not None:
        return

    _pool = AsyncConnectionPool(
        conn_string,
        min_size=1,
        max_size=10,
        kwargs={
            "autocommit": True,
            "prepare_threshold": 0,
            "row_factory": dict_row,
        },
    )
    await _pool.open()

    _checkpointer = AsyncPostgresSaver(conn=_pool)
    await _checkpointer.setup()

    _store = AsyncPostgresStore(conn=_pool)
    await _store.setup()

    logger.info("memory_infra_ready")


async def shutdown_memory_infra() -> None:
    """Close the shared pool."""
    global _pool, _checkpointer, _store
    if _pool is not None:
        await _pool.close()
        _pool = None
    _checkpointer = None
    _store = None
    logger.info("memory_infra_shutdown")


def get_checkpointer() -> AsyncPostgresSaver | None:
    return _checkpointer


def get_store() -> AsyncPostgresStore | None:
    return _store


def memory_infra_initialized() -> bool:
    return _checkpointer is not None and _store is not None
