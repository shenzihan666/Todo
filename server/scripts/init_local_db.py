"""Create the database if it doesn't exist and apply db/init/001_schema.sql."""

import asyncio
import sys
from pathlib import Path

import asyncpg

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.core.config import settings  # noqa: E402

SCHEMA = ROOT.parent / "db" / "init" / "001_schema.sql"


async def main() -> None:
    conn = await asyncpg.connect(
        user=settings.postgres_user,
        password=settings.postgres_password,
        host=settings.postgres_host,
        port=settings.postgres_port,
        database="postgres",
    )
    try:
        row = await conn.fetchrow(
            "SELECT 1 FROM pg_database WHERE datname = $1",
            settings.postgres_db,
        )
        if not row:
            await conn.execute(f'CREATE DATABASE "{settings.postgres_db}"')
            print(f"Created database '{settings.postgres_db}'.")
        else:
            print(f"Database '{settings.postgres_db}' already exists.")
    finally:
        await conn.close()

    conn = await asyncpg.connect(
        user=settings.postgres_user,
        password=settings.postgres_password,
        host=settings.postgres_host,
        port=settings.postgres_port,
        database=settings.postgres_db,
    )
    try:
        sql = SCHEMA.read_text(encoding="utf-8")
        lines = [line for line in sql.splitlines() if not line.strip().startswith("--")]
        for stmt in "\n".join(lines).split(";"):
            stmt = stmt.strip()
            if stmt:
                await conn.execute(stmt)
        print(f"Applied schema from {SCHEMA.name}.")
    finally:
        await conn.close()


if __name__ == "__main__":
    asyncio.run(main())
