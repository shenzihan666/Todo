"""Orchestrates WebSocket speech sessions: buffer PCM, partial/final transcription."""

from __future__ import annotations

import contextlib
import json
import logging
import time
from typing import Any

from fastapi import WebSocket, WebSocketDisconnect
from pydantic import ValidationError

from app.core.config import settings
from app.schemas.speech import (
    ErrorResult,
    FinalResult,
    PartialResult,
    SegmentRead,
    SpeechStartMessage,
    SpeechStopMessage,
    StreamConfig,
)
from app.services.transcription.faster_whisper_engine import FasterWhisperEngine

logger = logging.getLogger(__name__)


class SpeechSessionService:
    def __init__(self, engine: FasterWhisperEngine) -> None:
        self._engine = engine

    async def run(self, websocket: WebSocket) -> None:
        await websocket.accept()
        buffer = bytearray()
        stream_config: StreamConfig | None = None
        last_partial_time = 0.0

        try:
            while True:
                message = await websocket.receive()
                if message["type"] == "websocket.disconnect":
                    break

                if message.get("text") is not None:
                    text = message["text"]
                    assert isinstance(text, str)
                    done = await self._handle_text_frame(
                        websocket,
                        text,
                        buffer,
                        stream_config,
                        last_partial_time,
                    )
                    if done is not None:
                        new_cfg, new_last_partial, should_exit = done
                        if new_cfg is not None:
                            stream_config = new_cfg
                        last_partial_time = new_last_partial
                        if should_exit:
                            break
                elif message.get("bytes") is not None:
                    data = message["bytes"]
                    assert isinstance(data, bytes | bytearray)
                    if stream_config is None:
                        await websocket.send_json(
                            ErrorResult(
                                code="not_started",
                                message="Send a start message before binary audio frames.",
                            ).model_dump(),
                        )
                        continue
                    buffer.extend(data)
                    now = time.monotonic()
                    interval_ok = (
                        now - last_partial_time
                    ) * 1000 >= settings.speech_partial_interval_ms
                    size_ok = len(buffer) >= settings.speech_min_partial_bytes
                    if interval_ok and size_ok:
                        partial = await self._engine.transcribe_buffer(
                            bytes(buffer), stream_config
                        )
                        last_partial_time = now
                        if partial.text:
                            await websocket.send_json(
                                PartialResult(text=partial.text).model_dump()
                            )
        except WebSocketDisconnect:
            logger.debug("Speech WebSocket disconnected")
        except Exception as e:
            logger.exception("Speech session error: %s", e)
            with contextlib.suppress(Exception):
                await websocket.send_json(
                    ErrorResult(code="internal_error", message=str(e)).model_dump(),
                )

    async def _handle_text_frame(
        self,
        websocket: WebSocket,
        text: str,
        buffer: bytearray,
        stream_config: StreamConfig | None,
        last_partial_time: float,
    ) -> tuple[StreamConfig | None, float, bool] | None:
        """Returns (new_stream_config if set, last_partial_time, should_exit) or None."""
        try:
            payload: dict[str, Any] = json.loads(text)
        except json.JSONDecodeError:
            await websocket.send_json(
                ErrorResult(code="bad_json", message="Invalid JSON.").model_dump(),
            )
            return None

        msg_type = payload.get("type")
        if msg_type == "start":
            try:
                start = SpeechStartMessage.model_validate(payload)
            except ValidationError as e:
                await websocket.send_json(
                    ErrorResult(code="invalid_start", message=str(e)).model_dump(),
                )
                return None
            buffer.clear()
            cfg = start.config
            if cfg.encoding != "pcm_s16le":
                await websocket.send_json(
                    ErrorResult(
                        code="unsupported_encoding",
                        message=f"Only pcm_s16le is supported, got {cfg.encoding}.",
                    ).model_dump(),
                )
                return None
            if cfg.channels != 1:
                await websocket.send_json(
                    ErrorResult(
                        code="unsupported_channels",
                        message="Only mono (channels=1) is supported.",
                    ).model_dump(),
                )
                return None
            return cfg, time.monotonic(), False

        if msg_type == "stop":
            try:
                SpeechStopMessage.model_validate(payload)
            except ValidationError as e:
                await websocket.send_json(
                    ErrorResult(code="invalid_stop", message=str(e)).model_dump(),
                )
                return None
            if stream_config is None:
                await websocket.send_json(
                    ErrorResult(
                        code="not_started", message="No active session to stop."
                    ).model_dump(),
                )
                return None
            final = await self._engine.transcribe_buffer(bytes(buffer), stream_config)
            segs = [SegmentRead(start=s.start, end=s.end, text=s.text) for s in final.segments]
            await websocket.send_json(
                FinalResult(
                    text=final.text,
                    segments=segs,
                    language=final.language,
                ).model_dump(),
            )
            buffer.clear()
            return None, last_partial_time, True

        await websocket.send_json(
            ErrorResult(code="unknown_message", message=f"Unknown type: {msg_type}").model_dump(),
        )
        return None
