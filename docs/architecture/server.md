# 服务端分层架构

路径均相对于 `server/app/`。

```
app/api/v1/endpoints/ → schemas/ → services/ → repositories/ → models/
```

| 层 | 说明 |
|----|------|
| **Endpoints** | 路由处理；依赖 schemas 做入参/出参。含 **health**（`GET /api/v1/health`）、**auth**（注册/登录/刷新/登出）、**tenants**（在配置 `TENANT_BOOTSTRAP_API_KEY` 且请求头 `X-Tenant-Bootstrap-Key` 时的引导 `POST`；带 JWT 的 `GET`）、**todos** CRUD（JWT）、**media** 上传/下载（JWT，multipart）、**speech** WebSocket（`SPEECH_REQUIRE_AUTH=true` 时通过 `?access_token=<JWT>`）、**agent**（见下节，JWT）。 |
| **Schemas** | Pydantic 模型（请求/响应）。 |
| **Services** | 业务逻辑。 |
| **Repositories** | 数据访问（async SQLAlchemy）。 |
| **Models** | ORM（如 `models/todo.py`、`models/tenant.py`、`models/base.py`）。 |
| **Core** | 配置、数据库会话、日志、异常（`core/`）。 |

## 语音（STT）

- WebSocket：`WS /api/v1/speech/ws`，流式 PCM（`pcm_s16le`，16 kHz 单声道）。
- 引擎位于 `services/transcription/`，对外协议一致，可通过环境变量切换：
  - **`SPEECH_ENGINE=whisper`**：本地 Faster-Whisper；可选依赖组 `whisper`。
  - **`SPEECH_ENGINE=fun_asr`**：阿里云 DashScope Fun-ASR；可选组 `fun-asr`，需 `DASHSCOPE_API_KEY`，可选 `DASHSCOPE_BASE_WS_URL` 指定区域。
- `uv sync --extra dev` 可安装开发与两套 STT；Docker 镜像使用 `--extra whisper --extra fun-asr`。

## Agent（Deep Agents + LangGraph）

- **对话与 SSE**：`POST /api/v1/agent/chat`，请求体可含 `thread_id`（UUID）。省略则新建线程；服务端首条 SSE 事件为 `event: thread`，`data` 内含 `thread_id` 供客户端续聊。响应仍为 SSE（`token` / `tool_call` / `tool_result` / `done` 等）。
- **会话元数据**：业务表 `conversations`（`tenant_id` 隔离）登记线程；**多轮消息与图状态**由 LangGraph **`AsyncPostgresSaver`** 写入 PostgreSQL（`checkpoint_*` 等表，由 `langgraph-checkpoint-postgres` 在启动时 `setup()` 创建/迁移）。
- **长期记忆**：Deep Agents `CompositeBackend`：`StateBackend`（线程内草稿）+ `StoreBackend` 路由到路径前缀 `/memories/`；底层为 LangGraph **`AsyncPostgresStore`**（`store` 相关表，启动时 `setup()`）。租户隔离通过 `StoreBackend` 的 **namespace**（`(str(tenant_id), "filesystem")`）实现。
- **可插拔**：`MemoryProvider` / `StoreMemoryProvider`（`services/agent/memory_provider.py`）为后续 RAG（向量检索）预留 `retrieve(query)` 等接口；关闭记忆时设 `AGENT_MEMORY_ENABLED=false`，Agent 退化为无 checkpoint/store 的单轮行为。

相关实现：`services/agent/agent_factory.py`、`memory_infra.py`、`memory_backend.py`、`conversation_service.py`。
