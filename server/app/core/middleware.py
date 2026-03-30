"""HTTP middleware: request context (correlation) and structured access logs.

Implemented as ASGI callables (not ``BaseHTTPMiddleware``) so streaming responses
(SSE) are not buffered in memory.
"""

from __future__ import annotations

import time
import uuid

import structlog
from starlette.datastructures import Headers, MutableHeaders
from starlette.types import ASGIApp, Receive, Scope, Send
from structlog.contextvars import bind_contextvars, clear_contextvars

access_logger = structlog.get_logger("access")


class ContextMiddleware:
    """Bind request_id, tenant_id, method, path into structlog contextvars."""

    def __init__(self, app: ASGIApp) -> None:
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        clear_contextvars()
        headers = Headers(scope=scope)
        request_id = headers.get("x-request-id") or str(uuid.uuid4())
        tenant_id = headers.get("x-tenant-id", "-")

        bind_contextvars(
            request_id=request_id,
            tenant_id=tenant_id,
            method=scope.get("method", "-"),
            path=scope.get("path", "-"),
        )

        async def send_wrapper(message: dict) -> None:
            if message["type"] == "http.response.start":
                response_headers = MutableHeaders(scope=message)
                response_headers["X-Request-ID"] = request_id
            await send(message)

        try:
            await self.app(scope, receive, send_wrapper)
        finally:
            clear_contextvars()


class AccessLogMiddleware:
    """Emit one structured log per HTTP request with duration and status."""

    def __init__(self, app: ASGIApp) -> None:
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        start = time.perf_counter()
        status_code = 500

        async def send_wrapper(message: dict) -> None:
            nonlocal status_code
            if message["type"] == "http.response.start":
                status_code = int(message["status"])
            await send(message)

        await self.app(scope, receive, send_wrapper)
        duration_ms = round((time.perf_counter() - start) * 1000, 2)

        client = scope.get("client")
        client_ip = client[0] if client else "-"

        access_logger.info(
            "request_complete",
            status_code=status_code,
            duration_ms=duration_ms,
            client_ip=client_ip,
        )
