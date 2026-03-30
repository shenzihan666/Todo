"""Resolve tenant media uploads to base64 payloads for multimodal LLM messages."""

from __future__ import annotations

import asyncio
import base64
import uuid
from dataclasses import dataclass

import structlog
from fastapi import HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.media_path import resolve_upload_file_path
from app.repositories.media_repository import MediaRepository

logger = structlog.get_logger(__name__)


@dataclass(frozen=True)
class ResolvedMedia:
    """One image ready for OpenAI-compatible vision API (data URL)."""

    media_id: uuid.UUID
    content_type: str
    base64_data: str


def _read_file_sync(stored_path: str) -> bytes:
    try:
        full = resolve_upload_file_path(stored_path)
    except ValueError:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="media_not_found",
        ) from None
    if not full.is_file():
        logger.error("media_file_missing_on_disk", path=str(full))
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="media_file_missing",
        )
    return full.read_bytes()


async def resolve_media_for_agent(
    session: AsyncSession,
    tenant_id: uuid.UUID,
    media_ids: list[uuid.UUID],
) -> list[ResolvedMedia]:
    """Load uploaded images for the tenant and return base64-encoded payloads.

    Order matches ``media_ids``. Raises 400/404 if any id is missing or not owned.
    """
    if not media_ids:
        return []

    repo = MediaRepository(session, tenant_id)
    out: list[ResolvedMedia] = []

    for mid in media_ids:
        row = await repo.get_by_id(mid)
        if row is None:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"unknown_or_forbidden_media_id:{mid}",
            )
        try:
            raw = await asyncio.to_thread(_read_file_sync, row.stored_path)
        except HTTPException:
            raise
        b64 = base64.b64encode(raw).decode("ascii")
        out.append(
            ResolvedMedia(
                media_id=row.id,
                content_type=row.content_type,
                base64_data=b64,
            ),
        )

    return out
