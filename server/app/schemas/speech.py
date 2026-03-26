"""WebSocket speech-to-text message schemas (stable client/server contract)."""

from typing import Literal

from pydantic import BaseModel, Field


class StreamConfig(BaseModel):
    """Audio stream configuration from client."""

    sample_rate: int = Field(default=16000, ge=8000, le=48000)
    encoding: Literal["pcm_s16le"] = "pcm_s16le"
    channels: int = Field(default=1, ge=1, le=2)
    language: str | None = Field(
        default=None,
        description="BCP-47 or Whisper language code (e.g. zh, en); None = auto-detect",
    )


class SpeechStartMessage(BaseModel):
    type: Literal["start"] = "start"
    config: StreamConfig = Field(default_factory=StreamConfig)


class SpeechStopMessage(BaseModel):
    type: Literal["stop"] = "stop"


class SegmentRead(BaseModel):
    start: float
    end: float
    text: str


class PartialResult(BaseModel):
    type: Literal["partial"] = "partial"
    text: str


class FinalResult(BaseModel):
    type: Literal["final"] = "final"
    text: str
    segments: list[SegmentRead] = Field(default_factory=list)
    language: str | None = None


class ErrorResult(BaseModel):
    type: Literal["error"] = "error"
    code: str
    message: str
