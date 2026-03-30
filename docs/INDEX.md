# 文档索引（地图）

检索时优先查阅本表：按**主题关键词** → **路径**定位具体文档。

---

## 项目文档（本仓库）

### 项目与目标

| 路径 | 主题 | 关键词 |
|------|------|--------|
| [project/goals.md](./project/goals.md) | 项目目标与范围 | 目标、愿景、范围、monorepo |

### 架构

| 路径 | 主题 | 关键词 |
|------|------|--------|
| [architecture/repository-structure.md](./architecture/repository-structure.md) | 仓库目录结构 | 目录、结构、server、android-app、db |
| [architecture/server.md](./architecture/server.md) | 服务端分层、语音 STT、Agent 记忆与线程、确认后写入、多模态图片 | FastAPI、分层、WebSocket、whisper、fun_asr、speech、agent、thread、LangGraph、checkpoint、memory、list_todos、list_bills、bills、delete_todo、require_confirmation、proposed_actions、display_scheduled_at、display_amount、target、clarification、ask_user_questions、execute-actions、multimodal、media_ids、vision、image_url |

### 研发流程

| 路径 | 主题 | 关键词 |
|------|------|--------|
| [development/tech-stack.md](./development/tech-stack.md) | 技术栈与配置文件 | uv、PostgreSQL、Ruff、Compose、Coil、Gradle |
| [development/commands.md](./development/commands.md) | 常用命令 | docker、uvicorn、pytest、ruff、alembic |
| [development/environment.md](./development/environment.md) | 后端环境变量 | .env、JWT、STT、Agent、Sentry、租户引导 |
| [development/conventions.md](./development/conventions.md) | 约定与规范 | commit、API 版本、迁移、.env、换行 |
| [development/checklist.md](./development/checklist.md) | 提交前检查清单 | ruff、pytest、schemas |

### 业务与数据

| 路径 | 主题 | 关键词 |
|------|------|--------|
| [domain/multi-tenancy.md](./domain/multi-tenancy.md) | 多租户模型与 API 约定 | tenant_id、JWT、租户、隔离 |
| [domain/database-schema.md](./domain/database-schema.md) | 数据库表概要 | tenants、todos、scheduled_at、bills、billed_at、app_metadata、conversations、checkpoint、store、media_uploads、media_ids |

### 客户端平台

| 路径 | 主题 | 关键词 |
|------|------|--------|
| [platform/android.md](./platform/android.md) | Android 架构与语音、日程时间、账单、Agent 气泡与确认清单、聊天图片 | MVVM、Compose、WebSocket、Speech、上划取消、cancelSession、clearTranscriptIfIdle、PendingImagesBar、Schedule、scheduled_at、ScheduleMonthCalendar、月历、HorizontalPager、Bills、billed_at、Agent SSE、流式、ConfirmActionsSheet、clarification、execute-actions、thread_id、history、AttachmentImageSheet、ModalBottomSheet、MediaStore、FileProvider、READ_MEDIA_IMAGES、CAMERA、PickMultipleVisualMedia、media_ids、Coil、ImageLoader、ChatBubble、imageUris、mediaUrls、flavors、strings.xml、i18n |

---

## Deep Agents SDK（`docs/deepagents/`）

| 文件 | 主题 | 关键词 |
|------|------|--------|
| `deepagents/overview.md` | SDK 简介、架构、概念 | deepagents、langgraph、langchain |
| `deepagents/quickstart.md` | 入门与第一个 Agent | install、setup、tutorial |
| `deepagents/models.md` | 模型配置与选择 | model、llm、provider |
| `deepagents/backends.md` | 执行后端 | backend、runtime、deployment |
| `deepagents/skills.md` | Skill 与扩展 | skill、tool、capability |
| `deepagents/subagents.md` | 子 Agent | subagent、delegation、orchestration |
| `deepagents/harness.md` | Harness 与定制 | harness、loop、tool-calling |
| `deepagents/customization.md` | 行为扩展 | config、prompt、system-message |
| `deepagents/human-in-the-loop.md` | 人机协同 | hitl、approval、confirm |
| `deepagents/sandboxes.md` | 沙箱与隔离 | sandbox、docker、security |
| `deepagents/long-term-memory.md` | 长期记忆 | memory、storage、recall |
| `deepagents/data-analysis.md` | 数据分析任务 | data、analysis、pandas |
| `deepagents/comparison.md` | 框架对比 | compare、LangChain、LangGraph |
| `deepagents/acp.md` | Agent 通信协议 | acp、protocol、messaging |

### CLI（`docs/deepagents/cli/`）

| 文件 | 主题 | 关键词 |
|------|------|--------|
| `cli/overview.md` | CLI 简介 | cli、terminal |
| `cli/configuration.md` | CLI 配置 | config、env、dotfile |
| `cli/providers.md` | 模型提供商 | provider、api-key |
| `cli/mcp-tools.md` | MCP 工具 | mcp、tool-server |

### Streaming（`docs/deepagents/streaming/`）

| 文件 | 主题 | 关键词 |
|------|------|--------|
| `streaming/overview.md` | 流式架构 | stream、sse、websocket |
| `streaming/frontend.md` | 前端对接 | frontend、react、ui |
