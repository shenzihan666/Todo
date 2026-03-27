import structlog
from fastapi import Request, status
from fastapi.responses import JSONResponse

logger = structlog.get_logger("exceptions")


class DomainError(Exception):
    """Base class for domain-level errors (not HTTP-specific)."""


class NotFoundError(DomainError):
    def __init__(self, resource: str, identifier: str | int | None = None) -> None:
        self.resource = resource
        self.identifier = identifier
        msg = f"{resource} not found"
        if identifier is not None:
            msg = f"{resource} {identifier} not found"
        super().__init__(msg)


class ConflictError(DomainError):
    def __init__(self, detail: str) -> None:
        self.detail = detail
        super().__init__(detail)


class AuthenticationError(DomainError):
    def __init__(self, detail: str = "Authentication failed") -> None:
        self.detail = detail
        super().__init__(detail)


class ForbiddenError(DomainError):
    def __init__(self, detail: str = "Forbidden") -> None:
        self.detail = detail
        super().__init__(detail)


async def not_found_handler(_request: Request, exc: NotFoundError) -> JSONResponse:
    return JSONResponse(
        status_code=status.HTTP_404_NOT_FOUND,
        content={"detail": str(exc)},
    )


async def conflict_handler(_request: Request, exc: ConflictError) -> JSONResponse:
    return JSONResponse(
        status_code=status.HTTP_409_CONFLICT,
        content={"detail": exc.detail},
    )


async def authentication_error_handler(
    _request: Request, exc: AuthenticationError
) -> JSONResponse:
    return JSONResponse(
        status_code=status.HTTP_401_UNAUTHORIZED,
        content={"detail": exc.detail},
        headers={"WWW-Authenticate": "Bearer"},
    )


async def forbidden_handler(_request: Request, exc: ForbiddenError) -> JSONResponse:
    return JSONResponse(
        status_code=status.HTTP_403_FORBIDDEN,
        content={"detail": exc.detail},
    )


async def unhandled_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    """Return JSON for unexpected errors; log server-side."""
    logger.error(
        "unhandled_error",
        method=request.method,
        path=request.url.path,
        exc_info=exc,
    )
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"detail": "internal_server_error"},
    )
