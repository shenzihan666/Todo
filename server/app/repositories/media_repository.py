import uuid

from sqlalchemy.ext.asyncio import AsyncSession

from app.models.media_upload import MediaUpload
from app.repositories.base import BaseRepository


class MediaRepository(BaseRepository):
    def __init__(self, session: AsyncSession, tenant_id: uuid.UUID) -> None:
        super().__init__(session)
        self._tenant_id = tenant_id

    @property
    def tenant_id(self) -> uuid.UUID:
        return self._tenant_id

    async def get_by_id(self, media_id: uuid.UUID) -> MediaUpload | None:
        row = await self.session.get(MediaUpload, media_id)
        if row is None or row.tenant_id != self._tenant_id:
            return None
        return row

    async def create(
        self,
        *,
        media_id: uuid.UUID,
        content_type: str,
        original_filename: str,
        stored_path: str,
        size_bytes: int,
    ) -> MediaUpload:
        row = MediaUpload(
            id=media_id,
            tenant_id=self._tenant_id,
            content_type=content_type,
            original_filename=original_filename,
            stored_path=stored_path,
            size_bytes=size_bytes,
        )
        self.session.add(row)
        await self.session.flush()
        await self.session.refresh(row)
        return row
