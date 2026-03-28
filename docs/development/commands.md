# 常用命令

```bash
# Docker（在仓库根目录）
docker compose up --build

# 本地服务端（在 server/ 下）
# 使用 --extra dev 以包含测试/ lint 与两套 STT；或仅 --extra whisper / --extra fun-asr
cd server && uv sync --extra dev
uv run alembic upgrade head
uv run uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

# 测试
cd server && uv run pytest

# Lint
cd server && uv run ruff check . && uv run ruff format --check .
```
