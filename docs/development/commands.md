# 常用命令

```bash
# Docker（在仓库根目录）
docker compose up --build

# 本地服务端（在 server/ 下）
# 使用 --extra dev 以包含测试/ lint 与两套 STT；或仅 --extra whisper / --extra fun-asr
cd server && uv sync --extra dev
cp .env.example .env   # 首次：复制后编辑 POSTGRES_PASSWORD、JWT_SECRET_KEY 等
uv run alembic upgrade head
uv run uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

# 可选：无 Alembic 时仅用 SQL 引导库表（见 scripts/init_local_db.py）
cd server && uv run python scripts/init_local_db.py

# 测试
cd server && uv run pytest

# Lint
cd server && uv run ruff check . && uv run ruff format --check .

# Git 钩子（仓库根目录，首次克隆后）
uv tool install pre-commit
pre-commit install --install-hooks -t pre-commit -t commit-msg -t pre-push

# Android（dev 调试包，HTTP 连局域网 API）
cd android-app && ./gradlew assembleDevDebug
```

OpenAPI：`http://127.0.0.1:8000/docs`。环境变量说明见 [environment.md](./environment.md)。
