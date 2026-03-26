from datetime import UTC, datetime

import pytest
import pytest_asyncio
from fastapi import HTTPException, status
from httpx import ASGITransport, AsyncClient

from app.api.deps import get_todo_service
from app.main import create_app
from app.models.todo import Todo
from app.schemas.todo import TodoCreate, TodoUpdate

NOW = datetime(2025, 1, 1, tzinfo=UTC)


def _make_todo(id_=1, title="Buy groceries", description=None, completed=False):
    todo = Todo(title=title, description=description, completed=completed)
    todo.id = id_
    todo.created_at = NOW
    todo.updated_at = NOW
    return todo


class FakeTodoService:
    """In-memory stand-in so endpoint tests need no database."""

    def __init__(self):
        self._store: dict[int, Todo] = {}
        self._seq = 0

    async def list_todos(self) -> list[Todo]:
        return sorted(self._store.values(), key=lambda t: t.created_at, reverse=True)

    async def get_todo(self, todo_id: int) -> Todo:
        if todo_id not in self._store:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Todo {todo_id} not found",
            )
        return self._store[todo_id]

    async def create_todo(self, data: TodoCreate) -> Todo:
        self._seq += 1
        todo = _make_todo(id_=self._seq, title=data.title, description=data.description)
        self._store[self._seq] = todo
        return todo

    async def update_todo(self, todo_id: int, data: TodoUpdate) -> Todo:
        todo = await self.get_todo(todo_id)
        for field, value in data.model_dump(exclude_unset=True).items():
            setattr(todo, field, value)
        return todo

    async def delete_todo(self, todo_id: int) -> None:
        await self.get_todo(todo_id)
        del self._store[todo_id]


@pytest.fixture
def fake_service():
    return FakeTodoService()


@pytest_asyncio.fixture
async def todo_client(fake_service):
    app = create_app()
    app.dependency_overrides[get_todo_service] = lambda: fake_service
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac
    app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_list_empty(todo_client: AsyncClient) -> None:
    r = await todo_client.get("/api/v1/todos")
    assert r.status_code == 200
    assert r.json() == []


@pytest.mark.asyncio
async def test_create_and_list(todo_client: AsyncClient) -> None:
    r = await todo_client.post("/api/v1/todos", json={"title": "Buy milk"})
    assert r.status_code == 201
    body = r.json()
    assert body["title"] == "Buy milk"
    assert body["completed"] is False
    assert "id" in body

    r = await todo_client.get("/api/v1/todos")
    assert r.status_code == 200
    assert len(r.json()) == 1


@pytest.mark.asyncio
async def test_get_by_id(todo_client: AsyncClient) -> None:
    cr = await todo_client.post("/api/v1/todos", json={"title": "Walk the dog"})
    todo_id = cr.json()["id"]

    r = await todo_client.get(f"/api/v1/todos/{todo_id}")
    assert r.status_code == 200
    assert r.json()["title"] == "Walk the dog"


@pytest.mark.asyncio
async def test_get_not_found(todo_client: AsyncClient) -> None:
    r = await todo_client.get("/api/v1/todos/999")
    assert r.status_code == 404


@pytest.mark.asyncio
async def test_update(todo_client: AsyncClient) -> None:
    cr = await todo_client.post("/api/v1/todos", json={"title": "Clean house"})
    todo_id = cr.json()["id"]

    r = await todo_client.patch(f"/api/v1/todos/{todo_id}", json={"completed": True})
    assert r.status_code == 200
    assert r.json()["completed"] is True
    assert r.json()["title"] == "Clean house"


@pytest.mark.asyncio
async def test_delete(todo_client: AsyncClient) -> None:
    cr = await todo_client.post("/api/v1/todos", json={"title": "Temp"})
    todo_id = cr.json()["id"]

    r = await todo_client.delete(f"/api/v1/todos/{todo_id}")
    assert r.status_code == 204

    r = await todo_client.get(f"/api/v1/todos/{todo_id}")
    assert r.status_code == 404


@pytest.mark.asyncio
async def test_create_validation_empty_title(todo_client: AsyncClient) -> None:
    r = await todo_client.post("/api/v1/todos", json={"title": ""})
    assert r.status_code == 422


@pytest.mark.asyncio
async def test_create_with_description(todo_client: AsyncClient) -> None:
    r = await todo_client.post(
        "/api/v1/todos",
        json={"title": "Read book", "description": "Chapter 5"},
    )
    assert r.status_code == 201
    body = r.json()
    assert body["description"] == "Chapter 5"


@pytest.mark.asyncio
async def test_update_not_found(todo_client: AsyncClient) -> None:
    r = await todo_client.patch("/api/v1/todos/999", json={"title": "Nope"})
    assert r.status_code == 404


@pytest.mark.asyncio
async def test_delete_not_found(todo_client: AsyncClient) -> None:
    r = await todo_client.delete("/api/v1/todos/999")
    assert r.status_code == 404
