from collections.abc import AsyncIterator

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase
from sqlalchemy import text

from app.config import settings


class Base(DeclarativeBase):
    pass


engine = create_async_engine(
    settings.database_url,
    pool_pre_ping=True,
    pool_size=5,
    max_overflow=10,
)

SessionLocal = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)


async def get_session() -> AsyncIterator[AsyncSession]:
    async with SessionLocal() as session:
        yield session


async def check_database() -> bool:
    """True when PostgreSQL is reachable and init schema (app_metadata) is present."""
    try:
        async with engine.connect() as conn:
            result = await conn.execute(
                text(
                    "SELECT 1 FROM app_metadata WHERE key = 'schema_version' LIMIT 1"
                )
            )
            return result.first() is not None
    except Exception:
        return False
