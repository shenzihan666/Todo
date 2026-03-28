"""Orchestrates WebSocket speech sessions: buffer PCM, partial/final transcription."""

from __future__ import annotations

import contextlib
import json
import time
from typing import Any

import structlog
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
from app.services.transcription.base import TranscriptionEngine

logger = structlog.get_logger(__name__)


class SpeechSessionService:
    def __init__(self, engine: TranscriptionEngine) -> None:
        self._engine = engine

    async def run(self, websocket: WebSocket) -> None:
        """Handle messages after the route has accepted the WebSocket (and optional auth)."""
        full_buffer = bytearray()
        partial_buffer = bytearray()
        stream_config: StreamConfig | None = None
        last_partial_time = 0.0
        max_partial_bytes = 0

        try:
            while True:
                message = await websocket.receive()
                if message["type"] == "websocket.disconnect":
                    break

                if message.get("text") is not None:
                    text = message["text"]
                    if not isinstance(text, str):
                        await websocket.send_json(
                            ErrorResult(
                                code="invalid_frame",
                                message="Expected text frame with JSON.",
                            ).model_dump(),
                        )
                        continue
                    done = await self._handle_text_frame(
                        websocket,
                        text,
                        full_buffer,
                        partial_buffer,
                        stream_config,
                        last_partial_time,
                    )
                    if done is not None:
                        new_cfg, new_last_partial, should_exit = done
                        if new_cfg is not None:
                            stream_config = new_cfg
                            max_partial_bytes = int(
                                stream_config.sample_rate
                                * 2
                                * settings.speech_partial_window_seconds,
                            )
                        last_partial_time = new_last_partial
                        if should_exit:
                            break
                elif message.get("bytes") is not None:
                    data = message["bytes"]
                    if not isinstance(data, bytes | bytearray):
                        await websocket.send_json(
                            ErrorResult(
                                code="invalid_frame",
                                message="Expected binary PCM frame.",
                            ).model_dump(),
                        )
                        continue
                    if stream_config is None:
                        await websocket.send_json(
                            ErrorResult(
                                code="not_started",
                                message="Send a start message before binary audio frames.",
                            ).model_dump(),
                        )
                        continue
                    full_buffer.extend(data)
                    partial_buffer.extend(data)
                    if max_partial_bytes > 0 and len(partial_buffer) > max_partial_bytes:
                        overflow = len(partial_buffer) - max_partial_bytes
                        del partial_buffer[:overflow]

                    now = time.monotonic()
                    interval_ok = (
                        now - last_partial_time
                    ) * 1000 >= settings.speech_partial_interval_ms
                    size_ok = len(partial_buffer) >= settings.speech_min_partial_bytes
                    if interval_ok and size_ok:
                        partial = await self._engine.transcribe_buffer(
                            bytes(partial_buffer),
                            stream_config,
                            beam_size=1,
                        )
                        last_partial_time = now
                        if partial.text:
                            await websocket.send_json(
                                PartialResult(text=partial.text).model_dump()
                            )
        except WebSocketDisconnect:
            logger.debug("speech_websocket_disconnected")
        except Exception:
            logger.exception("speech_session_error")
            with contextlib.suppress(Exception):
                await websocket.send_json(
                    ErrorResult(
                        code="internal_error",
                        message="An internal error occurred.",
                    ).model_dump(),
                )

    async def _handle_text_frame(
        self,
        websocket: WebSocket,
        text: str,
        full_buffer: bytearray,
        partial_buffer: bytearray,
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
            except ValidationError:
                await websocket.send_json(
                    ErrorResult(
                        code="invalid_start",
                        message="Invalid start message.",
                    ).model_dump(),
                )
                return None
            full_buffer.clear()
            partial_buffer.clear()
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
            except ValidationError:
                await websocket.send_json(
                    ErrorResult(
                        code="invalid_stop",
                        message="Invalid stop message.",
                    ).model_dump(),
                )
                return None
            if stream_config is None:
                await websocket.send_json(
                    ErrorResult(
                        code="not_started", message="No active session to stop."
                    ).model_dump(),
                )
                return None
            final = await self._engine.transcribe_buffer(bytes(full_buffer), stream_config)
            segs = [SegmentRead(start=s.start, end=s.end, text=s.text) for s in final.segments]
            await websocket.send_json(
                FinalResult(
                    text=final.text,
                    segments=segs,
                    language=final.language,
                ).model_dump(),
            )
            full_buffer.clear()
            partial_buffer.clear()
            return None, last_partial_time, True

        await websocket.send_json(
            ErrorResult(code="unknown_message", message=f"Unknown type: {msg_type}").model_dump(),
        )
        return None
