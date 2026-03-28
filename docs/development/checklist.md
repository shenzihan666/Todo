# 修改代码时的检查清单

1. Python 变更后执行 `uv run ruff check` 与 `uv run ruff format`
2. 提交 server 变更前执行 `uv run pytest`（**pre-push** 也会执行）
3. 模型有变更时使用 `alembic revision --autogenerate`
4. 与同层现有模式保持一致（endpoints、services、repos）
5. 类型放在 `schemas/`，API 层不要直接暴露 ORM 模型
