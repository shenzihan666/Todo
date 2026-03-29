# 数据库模式概要

详见 `db/init/001_schema.sql` 与 Alembic 迁移。`001_schema.sql` 中的应用表定义与 Alembic（至 `006_add_scheduled_at_on_todos`）一致，供 `scripts/init_local_db.py` 在无单独执行 `alembic upgrade` 时仍能创建完整业务表；生产/团队仍以 Alembic 为权威迁移路径。

| 对象 | 说明 |
|------|------|
| `app_metadata` | 键值存储（如 `schema_version`）；**非**租户范围 |
| `tenants` | `id`（UUID）、名称、时间戳 |
| `users` | 用户账号；关联 `tenants` |
| `refresh_tokens` | 刷新令牌存储 |
| `todos` | `id`、**tenant_id**（FK → `tenants`）、标题、描述、完成状态、可选 **`scheduled_at`**（用户意图日程时刻，TIMESTAMPTZ）、`created_at`、`updated_at`（迁移 `006_add_scheduled_at_on_todos`） |
| `media_uploads` | 上传文件元数据；**tenant_id** 隔离（见迁移 `004_add_media_uploads`） |
| `conversations` | Agent 对话线程元数据：`id`（UUID，与 LangGraph `thread_id` 一致）、**tenant_id**、可选 `title`、`created_at` / `updated_at`（迁移 `005_add_conversations`） |

## LangGraph 托管表（非 Alembic）

以下由 **`langgraph-checkpoint-postgres`** / **`langgraph`** 在应用启动时调用 `AsyncPostgresSaver.setup()` / `AsyncPostgresStore.setup()` 自动建表/迁移，**不**出现在本仓库 Alembic 版本中：

- **Checkpoint**：如 `checkpoints`、`checkpoint_blobs`、`checkpoint_writes`、`checkpoint_migrations` 等（多轮对话状态）。
- **Store**：如 `store_migrations` 及条目表（`/memories/` 虚拟文件持久化，按 namespace 区分租户）。
