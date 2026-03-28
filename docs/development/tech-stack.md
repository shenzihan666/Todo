# 技术栈

| 层 | 技术 | 配置位置 |
|----|------|----------|
| API | FastAPI 0.115.x，Python 3.12+，**uv** | `server/pyproject.toml`，`server/uv.lock` |
| DB | PostgreSQL 16，SQLAlchemy 2 async + asyncpg | `docker-compose.yml` |
| Migrations | Alembic | `server/alembic.ini` |
| Android | Kotlin 2.2，JVM 17，Jetpack Compose，Retrofit，OkHttp | `android-app/app/build.gradle.kts` |
| Lint | Ruff（lint + format） | `server/pyproject.toml` 中 `[tool.ruff]` |
| Hooks | pre-commit（卫生、gitleaks、ruff、约定式提交）+ **pre-push** 跑 server 的 pytest | `.pre-commit-config.yaml` |
