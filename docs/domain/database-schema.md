# 数据库模式概要

详见 `db/init/001_schema.sql` 与 Alembic 迁移。

| 对象 | 说明 |
|------|------|
| `app_metadata` | 键值存储（如 `schema_version`）；**非**租户范围 |
| `tenants` | `id`（UUID）、名称、时间戳 |
| `todos` | `id`、**tenant_id**（FK → `tenants`）、标题、描述、完成状态、`created_at`、`updated_at` |
