"""WebSocket endpoint for streaming speech-to-text."""

import uuid

import jwt
import structlog
from fastapi import APIRouter, WebSocket
from structlog.contextvars import bind_contextvars, clear_contextvars

from app.core.config import settings
from app.core.security import decode_access_token
from app.services.speech_service import SpeechSessionService

logger = structlog.get_logger(__name__)

router = APIRouter(tags=["speech"])


@router.websocket("/ws")
async def speech_websocket(websocket: WebSocket) -> None:
    request_id = websocket.headers.get("X-Request-ID") or str(uuid.uuid4())
    tenant_id = "-"

    if settings.speech_require_auth:
        token = websocket.query_params.get("access_token")
        if not token:
            await websocket.close(code=1008)
            return
        try:
            payload = decode_access_token(token)
            tenant_id = str(payload.get("tenant_id", "-"))
        except jwt.PyJWTError:
            await websocket.close(code=1008)
            return

    bind_contextvars(
        request_id=request_id,
        tenant_id=tenant_id,
        method="WEBSOCKET",
        path=str(websocket.url.path),
    )
    logger.info("ws_connected")
    try:
        await websocket.accept()
        engine = websocket.app.state.transcription_engine
        service = SpeechSessionService(engine)
        await service.run(websocket)
    finally:
        logger.info("ws_disconnected")
        clear_contextvars()
