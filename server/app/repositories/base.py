from sqlalchemy.ext.asyncio import AsyncSession


class BaseRepository:
    """Common repository base for future CRUD patterns."""

    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    @property
    def session(self) -> AsyncSession:
        return self._session
