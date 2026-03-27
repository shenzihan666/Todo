import uuid
from datetime import UTC, datetime
from unittest.mock import AsyncMock

import pytest

from app.models.todo import Todo
from app.schemas.todo import TodoCreate, TodoUpdate
from app.services.todo_service import TodoService

NOW = datetime(2025, 1, 1, tzinfo=UTC)
TEST_TENANT_ID = uuid.UUID("22222222-2222-2222-2222-222222222222")


def _make_todo(**overrides):
    defaults = {
        "id": 1,
        "tenant_id": TEST_TENANT_ID,
        "title": "Test",
        "description": None,
        "completed": False,
    }
    defaults.update(overrides)
    todo = Todo(
        tenant_id=defaults["tenant_id"],
        title=defaults["title"],
        description=defaults["description"],
        completed=defaults["completed"],
    )
    todo.id = defaults["id"]
    todo.created_at = NOW
    todo.updated_at = NOW
    return todo


@pytest.fixture
def mock_repo():
    return AsyncMock()


@pytest.fixture
def service(mock_repo):
    return TodoService(mock_repo)


@pytest.mark.asyncio
async def test_list_todos(service, mock_repo) -> None:
    mock_repo.list_all.return_value = [_make_todo(), _make_todo(id=2, title="Second")]
    result = await service.list_todos()
    assert len(result) == 2
    mock_repo.list_all.assert_awaited_once()


@pytest.mark.asyncio
async def test_get_todo_found(service, mock_repo) -> None:
    mock_repo.get_by_id.return_value = _make_todo()
    todo = await service.get_todo(1)
    assert todo.title == "Test"
    mock_repo.get_by_id.assert_awaited_once_with(1)


@pytest.mark.asyncio
async def test_get_todo_not_found(service, mock_repo) -> None:
    mock_repo.get_by_id.return_value = None
    with pytest.raises(Exception) as exc_info:
        await service.get_todo(999)
    assert exc_info.value.status_code == 404


@pytest.mark.asyncio
async def test_create_todo(service, mock_repo) -> None:
    expected = _make_todo(title="New")
    mock_repo.create.return_value = expected
    data = TodoCreate(title="New")
    result = await service.create_todo(data)
    assert result.title == "New"
    mock_repo.create.assert_awaited_once_with(data)


@pytest.mark.asyncio
async def test_update_todo(service, mock_repo) -> None:
    existing = _make_todo(title="Old")
    mock_repo.get_by_id.return_value = existing
    mock_repo.update.return_value = _make_todo(title="Updated")
    data = TodoUpdate(title="Updated")
    result = await service.update_todo(1, data)
    assert result.title == "Updated"
    mock_repo.update.assert_awaited_once_with(existing, data)


@pytest.mark.asyncio
async def test_delete_todo(service, mock_repo) -> None:
    existing = _make_todo()
    mock_repo.get_by_id.return_value = existing
    await service.delete_todo(1)
    mock_repo.delete.assert_awaited_once_with(existing)


@pytest.mark.asyncio
async def test_delete_todo_not_found(service, mock_repo) -> None:
    mock_repo.get_by_id.return_value = None
    with pytest.raises(Exception) as exc_info:
        await service.delete_todo(999)
    assert exc_info.value.status_code == 404
