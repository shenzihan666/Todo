# 开发约定

- **提交信息**：Conventional Commits — `type(scope): description`
  - 类型：`feat`、`fix`、`docs`、`style`、`refactor`、`test`、`chore`、`perf`、`ci`、`build`、`revert`
- **Python 风格**：Ruff 约束；行宽 99；双引号；空格缩进
- **API 版本**：路由位于 `/api/v1/`；新 router 放在 `app/api/v1/`
- **数据库迁移**：一律通过 Alembic（`alembic revision --autogenerate -m "..."`）
- **环境变量**：复制 `server/.env.example` → `.env`，勿提交 `.env`；变量含义与分组见 [environment.md](./environment.md)
- **换行**：除 `.bat` / `.cmd` / `.ps1` 使用 CRLF 外，其余使用 LF
