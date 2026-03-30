import asyncio
import uuid
from pathlib import Path

import structlog
from fastapi import HTTPException, UploadFile, status
from fastapi.responses import FileResponse

from app.core.config import settings
from app.core.exceptions import NotFoundError
from app.core.media_path import resolve_upload_file_path
from app.repositories.media_repository import MediaRepository
from app.schemas.media import MediaRead

logger = structlog.get_logger(__name__)

_ALLOWED_MIME_TO_EXT: dict[str, str] = {
    "image/jpeg": ".jpg",
    "image/png": ".png",
    "image/webp": ".webp",
    "image/gif": ".gif",
}

_READ_CHUNK = 64 * 1024


class MediaService:
    def __init__(self, repo: MediaRepository) -> None:
        self._repo = repo

    async def create_upload(self, file: UploadFile) -> MediaRead:
        max_bytes = settings.media_max_bytes
        chunk_size = min(_READ_CHUNK, max_bytes)
        chunks: list[bytes] = []
        total = 0
        while True:
            chunk = await file.read(chunk_size)
            if not chunk:
                break
            total += len(chunk)
            if total > max_bytes:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="file_too_large",
                )
            chunks.append(chunk)

        body = b"".join(chunks)
        if len(body) == 0:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="empty_file",
            )

        content_type = (file.content_type or "").split(";")[0].strip().lower()
        if content_type not in _ALLOWED_MIME_TO_EXT:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="unsupported_media_type",
            )

        ext = _ALLOWED_MIME_TO_EXT[content_type]
        media_id = uuid.uuid4()
        rel = f"{self._repo.tenant_id}/{media_id}{ext}"
        root = Path(settings.media_upload_dir)
        full = root / rel

        original = (file.filename or "image").strip() or "image"

        def _write() -> None:
            full.parent.mkdir(parents=True, exist_ok=True)
            full.write_bytes(body)

        await asyncio.to_thread(_write)

        try:
            row = await self._repo.create(
                media_id=media_id,
                content_type=content_type,
                original_filename=original[:512],
                stored_path=rel,
                size_bytes=len(body),
            )
        except Exception:
            await asyncio.to_thread(lambda: full.unlink(missing_ok=True))
            raise

        logger.info(
            "media_uploaded",
            media_id=str(row.id),
            tenant_id=str(row.tenant_id),
            size_bytes=row.size_bytes,
        )
        return MediaRead.model_validate(row)

    async def get_media_file(self, media_id: uuid.UUID) -> FileResponse:
        row = await self._repo.get_by_id(media_id)
        if not row:
            raise NotFoundError("Media", str(media_id))

        try:
            full = resolve_upload_file_path(row.stored_path)
        except ValueError:
            raise NotFoundError("Media", str(row.stored_path)) from None
        if not full.is_file():
            logger.error("media_file_missing_on_disk", path=str(full), media_id=str(media_id))
            raise NotFoundError("Media", str(media_id))

        return FileResponse(
            path=str(full),
            media_type=row.content_type,
            filename=row.original_filename,
        )
