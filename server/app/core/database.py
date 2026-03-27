from collections.abc import AsyncIterator

import structlog
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

import app.models  # noqa: F401 — register all ORM tables (User, RefreshToken, etc.)
from app.core.config import settings
from app.models.base import Base

logger = structlog.get_logger(__name__)

engine = create_async_engine(
    settings.database_url,
    pool_pre_ping=True,
    pool_size=5,
    max_overflow=10,
)

SessionLocal = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)


async def get_session() -> AsyncIterator[AsyncSession]:
    async with SessionLocal() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise


async def check_database() -> bool:
    """True when PostgreSQL is reachable and init schema (app_metadata) is present."""
    try:
        async with engine.connect() as conn:
            result = await conn.execute(
                text(
                    "SELECT 1 FROM app_metadata WHERE key = 'schema_version' LIMIT 1",
                ),
            )
            return result.first() is not None
    except Exception:
        logger.exception("database_health_check_failed")
        return False


__all__ = ["Base", "SessionLocal", "engine", "get_session", "check_database"]
