"""HTTP middleware: request context (correlation) and structured access logs."""

from __future__ import annotations

import time
import uuid

import structlog
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response
from structlog.contextvars import bind_contextvars, clear_contextvars

access_logger = structlog.get_logger("access")


class ContextMiddleware(BaseHTTPMiddleware):
    """Bind request_id, tenant_id, method, path into structlog contextvars."""

    async def dispatch(self, request: Request, call_next) -> Response:
        clear_contextvars()
        request_id = request.headers.get("X-Request-ID") or str(uuid.uuid4())
        tenant_id = request.headers.get("X-Tenant-ID", "-")

        bind_contextvars(
            request_id=request_id,
            tenant_id=tenant_id,
            method=request.method,
            path=request.url.path,
        )
        request.state.request_id = request_id
        try:
            response = await call_next(request)
            response.headers["X-Request-ID"] = request_id
            return response
        finally:
            clear_contextvars()


class AccessLogMiddleware(BaseHTTPMiddleware):
    """Emit one structured log per HTTP request with duration and status."""

    async def dispatch(self, request: Request, call_next) -> Response:
        start = time.perf_counter()
        response = await call_next(request)
        duration_ms = round((time.perf_counter() - start) * 1000, 2)

        access_logger.info(
            "request_complete",
            status_code=response.status_code,
            duration_ms=duration_ms,
            client_ip=request.client.host if request.client else "-",
        )
        return response
