import uuid
from io import BytesIO
from unittest.mock import AsyncMock

import pytest
from fastapi import HTTPException, UploadFile

from app.core.config import settings
from app.core.exceptions import NotFoundError
from app.repositories.media_repository import MediaRepository
from app.services.media_service import MediaService
from tests.test_todos import TEST_TENANT_ID


@pytest.mark.asyncio
async def test_create_upload_rejects_oversized(tmp_path, monkeypatch) -> None:
    monkeypatch.setattr(settings, "media_upload_dir", str(tmp_path))
    monkeypatch.setattr(settings, "media_max_bytes", 4)

    repo = AsyncMock(spec=MediaRepository)
    repo.tenant_id = TEST_TENANT_ID
    service = MediaService(repo)

    uf = UploadFile(
        filename="x.png",
        file=BytesIO(b"12345"),
        headers={"content-type": "image/png"},
    )
    with pytest.raises(HTTPException) as exc:
        await service.create_upload(uf)
    assert exc.value.status_code == 400
    assert exc.value.detail == "file_too_large"
    repo.create.assert_not_called()


@pytest.mark.asyncio
async def test_create_upload_rejects_unsupported_mime(tmp_path, monkeypatch) -> None:
    monkeypatch.setattr(settings, "media_upload_dir", str(tmp_path))

    repo = AsyncMock(spec=MediaRepository)
    repo.tenant_id = TEST_TENANT_ID
    service = MediaService(repo)

    uf = UploadFile(
        filename="x.pdf",
        file=BytesIO(b"%PDF"),
        headers={"content-type": "application/pdf"},
    )
    with pytest.raises(HTTPException) as exc:
        await service.create_upload(uf)
    assert exc.value.status_code == 400
    assert exc.value.detail == "unsupported_media_type"
    repo.create.assert_not_called()


@pytest.mark.asyncio
async def test_get_media_file_not_found() -> None:
    repo = AsyncMock(spec=MediaRepository)
    repo.get_by_id = AsyncMock(return_value=None)
    service = MediaService(repo)

    with pytest.raises(NotFoundError):
        await service.get_media_file(uuid.uuid4())
