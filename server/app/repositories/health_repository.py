from app.core.database import check_database


class HealthRepository:
    """Data access for health / readiness checks."""

    async def is_database_ready(self) -> bool:
        return await check_database()
