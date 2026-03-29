# 环境变量（后端）

本地开发：将 `server/.env.example` 复制为 `server/.env` 并按需填写。**权威清单**以仓库内以下两处为准（二者不一致时，以运行时 `.env` 覆盖 `Settings` 默认值为准）：

- `server/.env.example` — 可复制模板与注释
- `server/app/core/config.py` — `Settings` 字段名、类型与默认值

变量名遵循 **Pydantic Settings**：与字段对应的环境变量为大写下划线形式（例如 `postgres_host` → `POSTGRES_HOST`；`database_url` → `DATABASE_URL`）。

---

## 数据库

| 变量 | 说明 |
|------|------|
| `POSTGRES_HOST` / `POSTGRES_PORT` / `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` | PostgreSQL 连接 |
| `DATABASE_URL` | 可选。若为空，则由上述字段组装为 `postgresql+asyncpg://…`（异步驱动） |

Alembic 等同步工具会通过 `Settings.database_url_sync` 自动换用 `psycopg2` URL。

---

## 安全与 HTTP

| 变量 | 说明 |
|------|------|
| `JWT_SECRET_KEY` | 签发 JWT 的密钥；生产环境必须更换 |
| `JWT_ACCESS_EXPIRE_MINUTES` / `JWT_REFRESH_EXPIRE_DAYS` | 访问令牌 / 刷新令牌有效期 |
| `CORS_ORIGINS` | JSON 数组字符串，如 `["http://localhost:3000"]` 或 `["*"]` |

Uvicorn 监听地址端口通常由启动参数或部署配置决定；`Settings` 中的 `host` / `port` 供需要时读取。

---

## 日志与可观测性

| 变量 | 说明 |
|------|------|
| `LOG_LEVEL` | 如 `INFO`、`DEBUG` |
| `LOG_FORMAT` | `json` 或 `console` |
| `SENTRY_DSN` | 可选；未设置则不启用 Sentry |
| `SENTRY_ENVIRONMENT` / `SENTRY_TRACES_SAMPLE_RATE` | Sentry 环境与 traces 采样率 |

---

## 语音（STT）

| 变量 | 说明 |
|------|------|
| `SPEECH_ENGINE` | `whisper`（本地 Faster-Whisper）或 `fun_asr`（阿里云 DashScope） |
| `SPEECH_REQUIRE_AUTH` | 语音 WebSocket 是否要求 `?access_token=<JWT>` |
| `DASHSCOPE_API_KEY` / `DASHSCOPE_BASE_WS_URL` / `FUN_ASR_MODEL` / `FUN_ASR_LANGUAGE_HINTS` | `fun_asr` 时使用 |
| `WHISPER_*`、`SPEECH_PARTIAL_*` | `whisper` 时模型与分片转写参数 |

依赖安装：`uv sync --extra whisper` / `--extra fun-asr` / `--extra dev`（见 [commands.md](./commands.md)）。

---

## 租户引导

| 变量 | 说明 |
|------|------|
| `TENANT_BOOTSTRAP_API_KEY` | 非空时，`POST /api/v1/tenants` 需请求头 `X-Tenant-Bootstrap-Key` 与之匹配；空则关闭该引导路径 |

详见 [multi-tenancy.md](../domain/multi-tenancy.md)。

---

## 媒体上传

| 变量 | 说明 |
|------|------|
| `MEDIA_UPLOAD_DIR` | 上传文件落盘目录（相对服务端工作目录） |
| `MEDIA_MAX_BYTES` | 单文件最大字节数 |

上传接口需 **Bearer JWT**（租户隔离）。

---

## AI Agent 与联网搜索

| 变量 | 说明 |
|------|------|
| `AGENT_LLM_BASE_URL` / `AGENT_LLM_MODEL` / `AGENT_LLM_API_KEY` | OpenAI 兼容 Chat Completions 端点（可指向 OpenAI、DeepSeek、本地 vLLM 等） |
| `AGENT_SYSTEM_PROMPT` | 可选；非空则覆盖内置系统提示 |
| `TAVILY_API_KEY` | 可选；Agent 工具链中的联网搜索（Tavily） |

`POST /api/v1/agent/chat` 为 **SSE** 流式响应，需 **Bearer JWT**。
