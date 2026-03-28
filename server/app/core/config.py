from typing import Literal

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    postgres_host: str = "127.0.0.1"
    postgres_port: int = 5432
    postgres_user: str = "postgres"
    postgres_password: str = ""
    postgres_db: str = "todolist"

    database_url: str = ""

    jwt_secret_key: str = "CHANGE-ME-in-production"  # noqa: S105
    jwt_algorithm: str = "HS256"
    jwt_access_expire_minutes: int = 30
    jwt_refresh_expire_days: int = 30

    cors_origins: list[str] = ["*"]

    host: str = "0.0.0.0"  # noqa: S104
    port: int = 8000

    # Logging & observability (structlog + optional Sentry)
    log_level: str = "INFO"
    log_format: str = "json"  # "json" | "console"
    sentry_dsn: str | None = None
    sentry_environment: str = "development"
    sentry_traces_sample_rate: float = 0.0

    # Speech-to-text engine: local Faster-Whisper or Alibaba DashScope Fun-ASR
    speech_engine: Literal["whisper", "fun_asr"] = "whisper"
    # DashScope (Fun-ASR); Beijing default WebSocket; Singapore: wss://dashscope-intl.aliyuncs.com/api-ws/v1/inference
    dashscope_api_key: str = ""
    dashscope_base_ws_url: str = "wss://dashscope.aliyuncs.com/api-ws/v1/inference"
    fun_asr_model: str = "fun-asr-realtime"
    fun_asr_language_hints: list[str] = Field(default_factory=lambda: ["zh", "en"])

    # Faster-Whisper (speech-to-text, when speech_engine=whisper)
    whisper_model_size: str = "small"
    whisper_device: str = "cpu"
    whisper_compute_type: str = "int8"
    whisper_vad_filter: bool = True
    whisper_beam_size: int = 5
    speech_partial_interval_ms: int = 800
    speech_min_partial_bytes: int = 4800
    # Rolling window (seconds) of PCM kept for partial transcription; full buffer kept for final.
    speech_partial_window_seconds: int = 10
    # Require `access_token` query param (JWT) on speech WebSocket.
    speech_require_auth: bool = True
    # If set, POST /tenants requires header X-Tenant-Bootstrap-Key to match. Empty = disabled.
    tenant_bootstrap_api_key: str = ""

    # Image uploads (POST /api/v1/media)
    media_upload_dir: str = "data/uploads"
    media_max_bytes: int = 10 * 1024 * 1024

    # AI Agent (OpenAI-compatible endpoint)
    agent_llm_base_url: str = "https://api.openai.com/v1"
    agent_llm_model: str = "gpt-4o"
    agent_llm_api_key: str = ""

    # Agent system prompt override (empty = use built-in default from prompts.py)
    agent_system_prompt: str = ""

    # Web search (Tavily)
    tavily_api_key: str = ""

    @model_validator(mode="after")
    def _assemble_database_url(self) -> "Settings":
        if not self.database_url:
            self.database_url = (
                f"postgresql+asyncpg://{self.postgres_user}:{self.postgres_password}"
                f"@{self.postgres_host}:{self.postgres_port}/{self.postgres_db}"
            )
        return self

    @property
    def database_url_sync(self) -> str:
        """Sync driver URL for Alembic / tooling (postgresql+psycopg2)."""
        url = self.database_url
        if url.startswith("postgresql+asyncpg://"):
            return url.replace("postgresql+asyncpg://", "postgresql+psycopg2://", 1)
        if url.startswith("postgresql://"):
            return url
        return url


settings = Settings()
