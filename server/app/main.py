import asyncio
import contextlib
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import sentry_sdk
import structlog
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sentry_sdk.integrations.fastapi import FastApiIntegration
from slowapi import _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded

from app.api.v1.router import api_router
from app.core.config import settings
from app.core.exceptions import (
    AuthenticationError,
    ConflictError,
    ForbiddenError,
    NotFoundError,
    authentication_error_handler,
    conflict_handler,
    forbidden_handler,
    not_found_handler,
    unhandled_exception_handler,
)
from app.core.limiter import limiter
from app.core.logging import configure_logging
from app.core.middleware import AccessLogMiddleware, ContextMiddleware
from app.services.agent.memory_infra import init_memory_infra, shutdown_memory_infra
from app.services.transcription.faster_whisper_engine import FasterWhisperEngine
from app.services.transcription.fun_asr_engine import FunAsrEngine

logger = structlog.get_logger(__name__)


async def _purge_expired_refresh_tokens_once() -> None:
    from app.core.database import SessionLocal
    from app.repositories.refresh_token_repository import RefreshTokenRepository

    async with SessionLocal() as session:
        repo = RefreshTokenRepository(session)
        n = await repo.delete_expired()
        await session.commit()
        if n:
            logger.info("refresh_tokens_purged", count=n)


async def _refresh_token_cleanup_loop() -> None:
    from app.core.database import SessionLocal
    from app.repositories.refresh_token_repository import RefreshTokenRepository

    while True:
        await asyncio.sleep(3600 * 6)
        try:
            async with SessionLocal() as session:
                repo = RefreshTokenRepository(session)
                n = await repo.delete_expired()
                await session.commit()
                if n:
                    logger.info("refresh_tokens_purged", count=n)
        except Exception:
            logger.exception("refresh_token_cleanup_failed")


def _create_transcription_engine() -> FasterWhisperEngine | FunAsrEngine:
    if settings.speech_engine == "fun_asr":
        return FunAsrEngine()
    return FasterWhisperEngine()


def init_sentry() -> None:
    if not settings.sentry_dsn:
        return
    sentry_sdk.init(
        dsn=settings.sentry_dsn,
        environment=settings.sentry_environment,
        traces_sample_rate=settings.sentry_traces_sample_rate,
        integrations=[FastApiIntegration()],
    )


def create_app() -> FastAPI:
    configure_logging(settings)
    init_sentry()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        engine = _create_transcription_engine()
        if settings.speech_engine == "whisper":
            await asyncio.to_thread(engine.load)
        else:
            engine.load()
        app.state.transcription_engine = engine
        if settings.agent_memory_enabled:
            await init_memory_infra(settings.postgres_psycopg_conn_string)
        await _purge_expired_refresh_tokens_once()
        cleanup_task = asyncio.create_task(_refresh_token_cleanup_loop())
        try:
            yield
        finally:
            cleanup_task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await cleanup_task
            if settings.agent_memory_enabled:
                await shutdown_memory_infra()
            engine.unload()

    app = FastAPI(title="TodoList API", version="0.1.0", lifespan=lifespan)
    app.state.limiter = limiter
    app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

    # Order: last added = outermost. CORS -> Context -> Access -> routes.
    app.add_middleware(AccessLogMiddleware)
    app.add_middleware(ContextMiddleware)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=False,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    app.add_exception_handler(NotFoundError, not_found_handler)
    app.add_exception_handler(ConflictError, conflict_handler)
    app.add_exception_handler(AuthenticationError, authentication_error_handler)
    app.add_exception_handler(ForbiddenError, forbidden_handler)
    app.add_exception_handler(Exception, unhandled_exception_handler)

    @app.get("/")
    async def root() -> dict[str, str]:
        return {"service": "todolist-api"}

    app.include_router(api_router, prefix="/api/v1")

    return app


app = create_app()
