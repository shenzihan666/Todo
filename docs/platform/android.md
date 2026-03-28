# Android 速查

- **架构**：MVVM — `ui/` → `domain/repository/` → `data/network/`（语音：`data/speech/`、`data/audio/`）
- **依赖注入**：手写 `AppContainer`（后续可换 Hilt/KSP 等）
- **构建变体**：`dev`（HTTP、本地 API）/ `prod`（HTTPS、生产 API）；`BuildConfig.API_BASE_URL`、`HEALTH_URL`
- **配置**：`local.properties` 中 `sdk.dir`；可选 `local.server.host`（Gradle 默认如 `192.168.1.1`）、`local.server.port`（默认 `8000`）
- **首页 / 语音**：`ui/home/` — 按住说话与对话转写；`domain/speech/SpeechTranscriber` + `data/speech/`（OkHttp WebSocket）；`AudioRecorder`（16 kHz PCM）→ `ws://<host>:<port>/api/v1/speech/ws?access_token=<JWT>`，URL 由 `ui/settings/SettingsViewModel.kt` 中 `buildSpeechWebSocketUrl` 构建（登录后使用缓存的 access token）
- **多租户 API**：注册/登录返回含 `tenant_id` 的 JWT；租户相关 HTTP 请求带 **`Authorization: Bearer`**（见 `di/AppContainer.kt` 中 `AuthInterceptor`）
