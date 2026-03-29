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

- **对话与 SSE**：`POST /api/v1/agent/chat`，请求体可含 `thread_id`（UUID）、`require_confirmation`（默认 `false`）、**`media_ids`**（`POST /api/v1/media` 返回的图片 id，租户内有效）。服务端将对应文件读入并编码为 **data URL**，与带 **Reference UTC** 前缀的正文一起组成 **多模态** `HumanMessage`（OpenAI 兼容 `text` + `image_url`）送入模型；`additional_kwargs.media_ids` 供历史接口还原附件列表。省略 `thread_id` 则新建线程；服务端首条 SSE 事件为 `event: thread`，`data` 内含 `thread_id` 供客户端续聊。响应为 SSE（`thread` / `token` / `tool_call` / `tool_result` / `proposed_actions` / `done` / `error` 等）。**历史回放**（需 `AGENT_MEMORY_ENABLED` 与 LangGraph checkpoint）：`GET /api/v1/agent/threads/{thread_id}/history`（JWT），返回 `role`（`user` | `assistant`）、`content`、以及用户轮次可选的 **`media`**（`id`、`content_type`）数组，供客户端冷启动后恢复对话列表与缩略图 URL（`GET /api/v1/media/{id}`）。
- **确认后再写入**：`require_confirmation=true` 时，`create_todo` / `update_todo` / `delete_todo` 不提交数据库，仅在流结束前追加 `event: proposed_actions`，`data` 为 `{"actions":[...]}`（每项含 `action`、`args`、`display_title`、`display_scheduled_at`）。客户端确认后调用 **`POST /api/v1/agent/execute-actions`**（JWT），请求体 `{"actions":[...]}`，与上述项同形，服务端按序执行并返回 `executed` 与 `results`。`list_todos` 在确认模式下仍真实查询，便于 Agent 推理。
- **会话元数据**：业务表 `conversations`（`tenant_id` 隔离）登记线程；**多轮消息与图状态**由 LangGraph **`AsyncPostgresSaver`** 写入 PostgreSQL（`checkpoint_*` 等表，由 `langgraph-checkpoint-postgres` 在启动时 `setup()` 创建/迁移）。
- **长期记忆**：Deep Agents `CompositeBackend`：`StateBackend`（线程内草稿）+ `StoreBackend` 路由到路径前缀 `/memories/`；底层为 LangGraph **`AsyncPostgresStore`**（`store` 相关表，启动时 `setup()`）。租户隔离通过 `StoreBackend` 的 **namespace**（`(str(tenant_id), "filesystem")`）实现。
- **可插拔**：`MemoryProvider` / `StoreMemoryProvider`（`services/agent/memory_provider.py`）为后续 RAG（向量检索）预留 `retrieve(query)` 等接口；关闭记忆时设 `AGENT_MEMORY_ENABLED=false`，Agent 退化为无 checkpoint/store 的单轮行为。
- **Todo 工具**（租户隔离）：`services/agent/tools/db_tools.py` 暴露 `list_todos`（可选 `scheduled_at` 区间）、`create_todo`、`update_todo`、`delete_todo`；模型宜先 `list_todos` 再按 id 更新/删除。批量删除为多次 `delete_todo`（后续可再加批量 tool）。传入可变的 `proposed_actions` 列表时进入干跑模式，写操作只追加待确认项、不 `commit`。

相关实现：`services/agent/agent_factory.py`、`memory_infra.py`、`memory_backend.py`、`conversation_service.py`。
