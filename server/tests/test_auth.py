import uuid
from unittest.mock import AsyncMock

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient

from app.api.deps import get_auth_service
from app.main import create_app
from app.schemas.auth import TokenResponse
from app.services.auth_service import AuthService

FAKE_TENANT_ID = uuid.UUID("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")


def _fake_token_response(**overrides) -> TokenResponse:
    defaults = {
        "access_token": "fake.access.token",
        "refresh_token": "fake_refresh_token",
        "tenant_id": FAKE_TENANT_ID,
        "username": "alice",
    }
    defaults.update(overrides)
    return TokenResponse(**defaults)


@pytest.fixture
def mock_auth_service():
    return AsyncMock(spec=AuthService)


@pytest_asyncio.fixture
async def auth_client(mock_auth_service):
    app = create_app()
    app.dependency_overrides[get_auth_service] = lambda: mock_auth_service
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac
    app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_register_success(auth_client, mock_auth_service) -> None:
    mock_auth_service.register.return_value = _fake_token_response()
    r = await auth_client.post(
        "/api/v1/auth/register",
        json={"username": "alice", "password": "secret123"},
    )
    assert r.status_code == 201
    body = r.json()
    assert body["access_token"] == "fake.access.token"
    assert body["refresh_token"] == "fake_refresh_token"
    assert body["tenant_id"] == str(FAKE_TENANT_ID)
    assert body["username"] == "alice"
    assert body["token_type"] == "bearer"


@pytest.mark.asyncio
async def test_register_short_password(auth_client) -> None:
    r = await auth_client.post(
        "/api/v1/auth/register",
        json={"username": "alice", "password": "abc"},
    )
    assert r.status_code == 422


@pytest.mark.asyncio
async def test_register_empty_username(auth_client) -> None:
    r = await auth_client.post(
        "/api/v1/auth/register",
        json={"username": "", "password": "secret123"},
    )
    assert r.status_code == 422


@pytest.mark.asyncio
async def test_login_success(auth_client, mock_auth_service) -> None:
    mock_auth_service.login.return_value = _fake_token_response()
    r = await auth_client.post(
        "/api/v1/auth/login",
        json={"username": "alice", "password": "secret123"},
    )
    assert r.status_code == 200
    body = r.json()
    assert body["access_token"] == "fake.access.token"
    assert body["tenant_id"] == str(FAKE_TENANT_ID)


@pytest.mark.asyncio
async def test_refresh_success(auth_client, mock_auth_service) -> None:
    mock_auth_service.refresh.return_value = _fake_token_response(
        access_token="new.access.token",
        refresh_token="new_refresh_token",
    )
    r = await auth_client.post(
        "/api/v1/auth/refresh",
        json={"refresh_token": "old_refresh_token"},
    )
    assert r.status_code == 200
    body = r.json()
    assert body["access_token"] == "new.access.token"
    assert body["refresh_token"] == "new_refresh_token"


@pytest.mark.asyncio
async def test_logout_success(auth_client, mock_auth_service) -> None:
    mock_auth_service.logout.return_value = None
    r = await auth_client.post(
        "/api/v1/auth/logout",
        json={"refresh_token": "some_refresh_token"},
    )
    assert r.status_code == 204
