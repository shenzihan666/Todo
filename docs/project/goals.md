# 项目目标

TodoList 单体仓库（monorepo）用于交付一套可运行的待办应用：

- **后端**：基于 **FastAPI** 的 API 服务，使用 **PostgreSQL** 与 **Alembic** 管理数据与迁移。
- **客户端**：**Android** 应用，采用 **Jetpack Compose** 构建界面。

整体目标是在多租户隔离的前提下，提供待办 CRUD、认证与健康检查等能力，并支持可选的语音转文字（STT）流式通道，便于移动端「按住说话」等交互。
