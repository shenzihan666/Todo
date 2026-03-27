import uuid

import pytest
from httpx import ASGITransport, AsyncClient

from app.core.config import settings
from app.core.security import create_access_token
from app.main import create_app

JWT_TENANT = uuid.UUID("11111111-1111-1111-1111-111111111111")


def _auth_headers(tenant_id: uuid.UUID | None = None) -> dict[str, str]:
    tid = tenant_id or JWT_TENANT
    token = create_access_token(
        user_id=uuid.UUID("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
        tenant_id=tid,
        username="testuser",
    )
    return {"Authorization": f"Bearer {token}"}


@pytest.mark.asyncio
async def test_get_tenant_requires_auth() -> None:
    app = create_app()
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        r = await ac.get(f"/api/v1/tenants/{JWT_TENANT}")
    assert r.status_code == 403


@pytest.mark.asyncio
async def test_get_tenant_forbidden_when_tenant_id_mismatch() -> None:
    app = create_app()
    transport = ASGITransport(app=app)
    other = uuid.UUID("22222222-2222-2222-2222-222222222222")
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        r = await ac.get(
            f"/api/v1/tenants/{other}",
            headers=_auth_headers(tenant_id=JWT_TENANT),
        )
    assert r.status_code == 403
    assert "does not match" in r.json()["detail"]


@pytest.mark.asyncio
async def test_create_tenant_disabled_no_bootstrap_key(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(settings, "tenant_bootstrap_api_key", "")
    app = create_app()
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        r = await ac.post("/api/v1/tenants", json={"name": "orphan"})
    assert r.status_code == 403
    assert "register" in r.json()["detail"].lower()
