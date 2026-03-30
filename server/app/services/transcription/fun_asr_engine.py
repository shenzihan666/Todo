"""Alibaba DashScope FunASR cloud transcription (streaming Recognition API)."""

from __future__ import annotations

import asyncio
from typing import Any

import structlog

from app.core.config import settings
from app.schemas.speech import StreamConfig
from app.services.transcription.base import Segment, TranscriptionResult

logger = structlog.get_logger(__name__)

# ~100 ms at 16 kHz mono s16le
_CHUNK_BYTES = 3200


def _language_to_hints(language: str | None) -> list[str] | None:
    """Map StreamConfig.language (BCP-47 or short code) to DashScope language_hints."""
    if not language:
        return None
    lang = language.lower().strip()
    # Strip region subtag: zh-CN -> zh
    primary = lang.split("-")[0].split("_")[0]
    # Fun-ASR supports zh, en, ja (model-dependent)
    if primary in ("zh", "en", "ja"):
        return [primary]
    return [primary]


class FunAsrEngine:
    """DashScope Fun-ASR realtime; uses bidirectional streaming over WebSocket."""

    def __init__(self) -> None:
        self._api_key: str | None = None
        self._ws_url: str | None = None

    def load(self) -> None:
        """Persist API key and WebSocket URL on this engine instance."""
        if not settings.dashscope_api_key:
            msg = "DASHSCOPE_API_KEY is required when SPEECH_ENGINE=fun_asr"
            raise RuntimeError(msg)
        self._api_key = settings.dashscope_api_key
        self._ws_url = settings.dashscope_base_ws_url
        logger.info(
            "fun_asr_configured",
            model=settings.fun_asr_model,
            ws_url=settings.dashscope_base_ws_url,
        )

    def unload(self) -> None:
        self._api_key = None
        self._ws_url = None

    def _transcribe_sync(
        self,
        audio_pcm_s16le: bytes,
        config: StreamConfig,
        beam_size: int | None = None,
    ) -> TranscriptionResult:
        del beam_size  # Fun-ASR does not use beam search; kept for protocol compatibility.
        if not self._api_key or not self._ws_url:
            msg = "FunAsrEngine not loaded; call load() first"
            raise RuntimeError(msg)
        import dashscope

        dashscope.api_key = self._api_key
        dashscope.base_websocket_api_url = self._ws_url
        sr = config.sample_rate
        if sr != 16000:
            logger.warning("fun_asr_sample_rate", expected=16000, got=sr)
        if len(audio_pcm_s16le) < sr // 10 * 2:  # < ~100 ms of s16le
            return TranscriptionResult(text="", segments=[], language=config.language)

        from dashscope.audio.asr import Recognition, RecognitionCallback, RecognitionResult

        hints = _language_to_hints(config.language)
        if hints is None:
            hints = list(settings.fun_asr_language_hints)

        collected_segments: list[Segment] = []
        latest_text = ""
        error_holder: list[str] = []

        class _Callback(RecognitionCallback):
            def on_event(self, result: RecognitionResult) -> None:
                nonlocal latest_text
                sentence = result.get_sentence()
                if isinstance(sentence, dict):
                    sentences: list[dict[str, Any]] = [sentence]
                elif isinstance(sentence, list):
                    sentences = [s for s in sentence if isinstance(s, dict)]
                else:
                    sentences = []
                for s in sentences:
                    if "text" not in s:
                        continue
                    latest_text = str(s["text"]).strip()
                    if RecognitionResult.is_sentence_end(s):
                        begin_ms = float(s.get("begin_time", 0))
                        end_ms = float(s.get("end_time", 0))
                        collected_segments.append(
                            Segment(
                                start=begin_ms / 1000.0,
                                end=end_ms / 1000.0,
                                text=str(s.get("text", "")).strip(),
                            ),
                        )

            def on_error(self, result: object) -> None:
                error_holder.append(str(getattr(result, "message", result)))

        callback = _Callback()
        recognition = Recognition(
            model=settings.fun_asr_model,
            format="pcm",
            sample_rate=sr,
            language_hints=hints,
            semantic_punctuation_enabled=False,
            callback=callback,
        )
        recognition.start()
        try:
            offset = 0
            buf_len = len(audio_pcm_s16le)
            while offset < buf_len:
                chunk = audio_pcm_s16le[offset : offset + _CHUNK_BYTES]
                offset += len(chunk)
                recognition.send_audio_frame(chunk)
        finally:
            recognition.stop()

        if error_holder:
            msg = error_holder[0]
            logger.error("fun_asr_error", message=msg)
            raise RuntimeError(msg)

        if collected_segments:
            full_text = " ".join(s.text for s in collected_segments if s.text).strip()
            lang_out = config.language or (hints[0] if hints else None)
            return TranscriptionResult(
                text=full_text,
                segments=collected_segments,
                language=lang_out,
            )
        text_out = latest_text.strip()
        lang_out = config.language or (hints[0] if hints else None)
        return TranscriptionResult(text=text_out, segments=[], language=lang_out)

    async def transcribe_buffer(
        self,
        audio_pcm_s16le: bytes,
        config: StreamConfig,
        *,
        beam_size: int | None = None,
    ) -> TranscriptionResult:
        return await asyncio.to_thread(
            self._transcribe_sync,
            audio_pcm_s16le,
            config,
            beam_size,
        )
