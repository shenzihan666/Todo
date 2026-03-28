# 多租户（后端标准）

- **策略**：共享 PostgreSQL 库与 schema；业务表通过 **`tenant_id UUID NOT NULL`** 指向 `tenants(id)`，实现行级隔离。
- **`tenants`**：租户主表（主键 `id` UUID）。常规注册通过 `POST /api/v1/auth/register` 创建租户。可选引导 `POST /api/v1/tenants` 需要环境变量 `TENANT_BOOTSTRAP_API_KEY` 与请求头 `X-Tenant-Bootstrap-Key`。`GET /api/v1/tenants/{id}` 需要 JWT，且路径 `id` 必须与 token 中 `tenant_id` 一致。
- **系统表**（如 `app_metadata`）**不参与**租户隔离，为全局表。
- **HTTP 租户上下文**：租户相关路由使用 **`Authorization: Bearer`** JWT，载荷含 `tenant_id`（见 `app/api/deps.py`）。语音 WebSocket 将同一 access token 作为 **`?access_token=`** 查询参数传递。
- **Repositories**：必须接收 `tenant_id`，读写均按租户过滤；不得跨租户返回或修改数据。
- **新表**：增加 `tenant_id` + 外键到 `tenants`，并按 `(tenant_id, …)` 需要建立联合/二级索引。
