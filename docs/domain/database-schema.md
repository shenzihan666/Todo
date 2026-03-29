# 数据库模式概要

详见 `db/init/001_schema.sql` 与 Alembic 迁移。

| 对象 | 说明 |
|------|------|
| `app_metadata` | 键值存储（如 `schema_version`）；**非**租户范围 |
| `tenants` | `id`（UUID）、名称、时间戳 |
| `users` | 用户账号；关联 `tenants` |
| `refresh_tokens` | 刷新令牌存储 |
| `todos` | `id`、**tenant_id**（FK → `tenants`）、标题、描述、完成状态、`created_at`、`updated_at` |
| `media_uploads` | 上传文件元数据；**tenant_id** 隔离（见迁移 `004_add_media_uploads`） |
