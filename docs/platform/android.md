# Android 速查

- **架构**：MVVM — `ui/` → `domain/repository/` → `data/network/`（语音：`data/speech/`、`data/audio/`）
- **依赖注入**：手写 `AppContainer`（后续可换 Hilt/KSP 等）
- **构建变体**：`dev`（HTTP、本地 API）/ `prod`（HTTPS、生产 API）；`BuildConfig.API_BASE_URL`、`HEALTH_URL`
- **配置**：`local.properties` 中 `sdk.dir`；可选 `local.server.host`（Gradle 默认如 `192.168.1.1`）、`local.server.port`（默认 `8000`）
- **首页 / 语音**：`ui/home/` — 按住说话与对话转写；`domain/speech/SpeechTranscriber` + `data/speech/`（OkHttp WebSocket）；`AudioRecorder`（16 kHz PCM）。`buildSpeechWebSocketUrl(host)` 只生成无凭据的 `ws://<host>:<port>/api/v1/speech/ws`；`RemoteSpeechTranscriber` 经 `SpeechTokenProvider` 在升级请求上加 **`Authorization: Bearer`**。握手 **401/403** 时调 `/auth/refresh` 后 **重连一次**；仍失败则 `clearAuth()`，`ui/main/MainRoute.kt` 根据 `isLoggedIn` 回登录。服务端 `/api/v1/speech/ws` 支持 Bearer，并兼容 `?access_token=`
- **多租户 API**：注册/登录返回含 `tenant_id` 的 JWT；租户相关 HTTP 请求带 **`Authorization: Bearer`**（见 `di/AppContainer.kt` 中 `AuthInterceptor`）
