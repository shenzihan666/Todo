"""Placeholder for future todo CRUD endpoints."""

from fastapi import APIRouter

router = APIRouter(prefix="/todos", tags=["todos"])


@router.get("", response_model=list[dict])
async def list_todos() -> list[dict]:
    """Not implemented yet — returns empty list."""
    return []
