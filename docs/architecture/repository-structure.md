# 仓库目录结构

```
server/          Python API（FastAPI、SQLAlchemy async、Pydantic）；业务代码位于 server/app/
server/alembic/  数据库迁移
server/scripts/  本地辅助脚本（如 init_local_db.py）
server/tests/    Pytest 测试套件
db/init/         原始 SQL 引导（001_schema.sql）
android-app/     Kotlin + Compose（Retrofit、Navigation Compose、OkHttp WebSocket）
docs/            项目与 SDK 文档（入口见 docs/INDEX.md）
```
