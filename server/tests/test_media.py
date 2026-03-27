import uuid
from datetime import UTC, datetime
from io import BytesIO

import pytest
import pytest_asyncio
from fastapi import UploadFile
from httpx import ASGITransport, AsyncClient

from app.api.deps import get_media_service
from app.core.exceptions import NotFoundError
from app.main import create_app
from app.schemas.media import MediaRead
from tests.test_todos import TEST_TENANT_ID, _auth_headers

# Minimal valid PNG (1x1)
_TINY_PNG = (
    b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01"
    b"\x08\x02\x00\x00\x00\x90wS\xde\x00\x00\x00\x0cIDATx\x9cc\xf8\x0f\x00"
    b"\x01\x01\x01\x00\x18\xdd\x8d\xb4\x00\x00\x00\x00IEND\xaeB`\x82"
)


class FakeMediaService:
    async def create_upload(self, file: UploadFile) -> MediaRead:
        body = await file.read()
        return MediaRead(
            id=uuid.uuid4(),
            tenant_id=TEST_TENANT_ID,
            content_type="image/png",
            original_filename=file.filename or "image.png",
            size_bytes=len(body),
            created_at=datetime.now(UTC),
        )

    async def get_media_file(self, media_id: uuid.UUID):
        raise NotFoundError("Media", str(media_id))


@pytest_asyncio.fixture
async def media_client():
    app = create_app()
    app.dependency_overrides[get_media_service] = lambda: FakeMediaService()
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac
    app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_media_post_requires_auth() -> None:
    app = create_app()
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        r = await ac.post(
            "/api/v1/media",
            files={"file": ("x.png", BytesIO(_TINY_PNG), "image/png")},
        )
    assert r.status_code == 403


@pytest.mark.asyncio
async def test_media_post_success(media_client: AsyncClient) -> None:
    r = await media_client.post(
        "/api/v1/media",
        headers=_auth_headers(),
        files={"file": ("x.png", BytesIO(_TINY_PNG), "image/png")},
    )
    assert r.status_code == 201
    body = r.json()
    assert body["tenant_id"] == str(TEST_TENANT_ID)
    assert body["content_type"] == "image/png"
    assert body["size_bytes"] == len(_TINY_PNG)
    assert "id" in body


@pytest.mark.asyncio
async def test_media_get_not_found(media_client: AsyncClient) -> None:
    mid = uuid.uuid4()
    r = await media_client.get(f"/api/v1/media/{mid}", headers=_auth_headers())
    assert r.status_code == 404
