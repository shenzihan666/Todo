from typing import Annotated

from fastapi import APIRouter, Depends, Query, status

from app.api.deps import get_todo_service
from app.schemas.todo import TodoCreate, TodoRead, TodoUpdate
from app.services.todo_service import TodoService

router = APIRouter(prefix="/todos", tags=["todos"])


@router.get("", response_model=list[TodoRead])
async def list_todos(
    service: Annotated[TodoService, Depends(get_todo_service)],
    limit: Annotated[int, Query(ge=1, le=500)] = 100,
    offset: Annotated[int, Query(ge=0)] = 0,
):
    return await service.list_todos(limit=limit, offset=offset)


@router.post("", response_model=TodoRead, status_code=status.HTTP_201_CREATED)
async def create_todo(
    data: TodoCreate,
    service: Annotated[TodoService, Depends(get_todo_service)],
):
    return await service.create_todo(data)


@router.get("/{todo_id}", response_model=TodoRead)
async def get_todo(
    todo_id: int,
    service: Annotated[TodoService, Depends(get_todo_service)],
):
    return await service.get_todo(todo_id)


@router.patch("/{todo_id}", response_model=TodoRead)
async def update_todo(
    todo_id: int,
    data: TodoUpdate,
    service: Annotated[TodoService, Depends(get_todo_service)],
):
    return await service.update_todo(todo_id, data)


@router.delete("/{todo_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_todo(
    todo_id: int,
    service: Annotated[TodoService, Depends(get_todo_service)],
) -> None:
    await service.delete_todo(todo_id)
