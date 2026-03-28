"""Faster-Whisper transcription engine (large-v3 by default)."""

from __future__ import annotations

import asyncio
from typing import TYPE_CHECKING

import numpy as np
import structlog

from app.core.config import settings
from app.schemas.speech import StreamConfig
from app.services.transcription.audio_utils import pcm_s16le_to_float32_mono
from app.services.transcription.base import Segment, TranscriptionResult

if TYPE_CHECKING:
    from faster_whisper import WhisperModel

logger = structlog.get_logger(__name__)


class FasterWhisperEngine:
    """Loads model once; transcribe runs in a worker thread."""

    def __init__(self) -> None:
        self._model: WhisperModel | None = None

    def load(self) -> None:
        if self._model is not None:
            return
        from faster_whisper import WhisperModel

        logger.info(
            "whisper_model_loading",
            model=settings.whisper_model_size,
            device=settings.whisper_device,
            compute_type=settings.whisper_compute_type,
        )
        self._model = WhisperModel(
            settings.whisper_model_size,
            device=settings.whisper_device,
            compute_type=settings.whisper_compute_type,
        )

    def unload(self) -> None:
        self._model = None

    def _transcribe_sync(
        self,
        audio_float32: np.ndarray,
        sample_rate: int,
        config: StreamConfig,
        beam_size: int | None = None,
    ) -> TranscriptionResult:
        if self._model is None:
            msg = "Whisper model not loaded"
            raise RuntimeError(msg)
        if audio_float32.size < sample_rate // 10:
            return TranscriptionResult(text="", segments=[], language=config.language)

        language = config.language if config.language else None
        segments_out: list[Segment] = []
        text_parts: list[str] = []
        detected_language: str | None = None

        beam = beam_size if beam_size is not None else settings.whisper_beam_size
        segments_gen, info = self._model.transcribe(
            audio_float32,
            language=language,
            task="transcribe",
            vad_filter=settings.whisper_vad_filter,
            beam_size=beam,
        )
        detected_language = getattr(info, "language", None) or language

        for seg in segments_gen:
            t = seg.text.strip()
            if t:
                text_parts.append(t)
            segments_out.append(
                Segment(start=float(seg.start), end=float(seg.end), text=seg.text.strip()),
            )

        full_text = " ".join(text_parts).strip()
        return TranscriptionResult(
            text=full_text,
            segments=segments_out,
            language=detected_language,
        )

    async def transcribe_buffer(
        self,
        audio_pcm_s16le: bytes,
        config: StreamConfig,
        *,
        beam_size: int | None = None,
    ) -> TranscriptionResult:
        audio = pcm_s16le_to_float32_mono(audio_pcm_s16le)
        sr = config.sample_rate
        return await asyncio.to_thread(self._transcribe_sync, audio, sr, config, beam_size)
