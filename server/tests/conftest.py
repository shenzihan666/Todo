import os

# Smaller model for tests (faster CI; first run may download weights).
os.environ.setdefault("WHISPER_MODEL_SIZE", "tiny")

import pytest_asyncio
from httpx import ASGITransport, AsyncClient

from app.main import create_app


@pytest_asyncio.fixture
async def client():
    app = create_app()
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac
