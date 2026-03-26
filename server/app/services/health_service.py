from app.repositories.health_repository import HealthRepository


class HealthService:
    def __init__(self, health_repo: HealthRepository) -> None:
        self._health_repo = health_repo

    async def get_health_status(self) -> tuple[bool, str]:
        ok = await self._health_repo.is_database_ready()
        if ok:
            return True, "connected"
        return False, "offline"
