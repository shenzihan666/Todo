"""Pluggable transcription engine protocol."""

from typing import Protocol, runtime_checkable

from pydantic import BaseModel, Field

from app.schemas.speech import StreamConfig


class Segment(BaseModel):
    start: float
    end: float
    text: str


class TranscriptionResult(BaseModel):
    text: str
    segments: list[Segment] = Field(default_factory=list)
    language: str | None = None


@runtime_checkable
class TranscriptionEngine(Protocol):
    """Implementations may wrap Faster-Whisper, cloud APIs, etc."""

    async def transcribe_buffer(
        self, audio_pcm_s16le: bytes, config: StreamConfig
    ) -> TranscriptionResult:
        """Transcribe one PCM s16le buffer (mono)."""
        ...
