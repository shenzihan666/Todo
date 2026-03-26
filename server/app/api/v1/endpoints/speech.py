"""WebSocket endpoint for streaming speech-to-text."""

import logging

from fastapi import APIRouter, WebSocket

from app.services.speech_service import SpeechSessionService

logger = logging.getLogger(__name__)

router = APIRouter(tags=["speech"])


@router.websocket("/ws")
async def speech_websocket(websocket: WebSocket) -> None:
    engine = websocket.app.state.whisper_engine
    service = SpeechSessionService(engine)
    await service.run(websocket)
