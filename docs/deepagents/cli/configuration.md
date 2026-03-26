# Configuration

Configure the Deep Agents CLI with config.toml, hooks, and MCP servers.

The CLI stores its configuration in the `~/.deepagents/` directory:

| File | Format | Purpose |
| --- | --- | --- |
| `config.toml` | TOML | Model defaults, provider settings, constructor params, profile overrides, MCP trust store |
| `hooks.json` | JSON | External tool subscriptions to CLI lifecycle events |
| `.mcp.json` | JSON | MCP server definitions (also auto-discovered from project directories) |

## Config file

`~/.deepagents/config.toml` lets you customize model providers, set defaults, and pass extra parameters.

### Default and recent model

```toml
[models]
default = "ollama:qwen3:4b"             # your intentional long-term preference
recent = "anthropic:claude-sonnet-4-5"   # last /model switch (written automatically)
```

`[models].default` always takes priority over `[models].recent`.

### Provider configuration

```toml
[models.providers.<name>]
models = ["gpt-4o"]
api_key_env = "OPENAI_API_KEY"
base_url = "https://api.openai.com/v1"
class_path = "my_package.models:MyChatModel"
enabled = true

[models.providers.<name>.params]
temperature = 0
max_tokens = 4096

[models.providers.<name>.params."gpt-4o"]
temperature = 0.7
```

**Keys:**

- `models`: List of model names for the interactive `/model` switcher
- `api_key_env`: Override the environment variable for credentials
- `base_url`: Override the base URL
- `params`: Extra keyword arguments for the model constructor
- `class_path`: For arbitrary providers (fully-qualified Python class)
- `enabled`: Whether this provider appears in the `/model` selector

### Model constructor params

Pass extra arguments to the model constructor:

```toml
[models.providers.ollama.params]
temperature = 0
num_ctx = 8192

[models.providers.ollama.params."qwen3:4b"]
temperature = 0.5
num_ctx = 4000
```

### Per-model overrides

Model-specific overrides in `params` sub-tables win over provider-level params.

### CLI overrides with `--model-params`

For one-off adjustments:

```bash
deepagents --model ollama:llama3 --model-params '{"temperature": 0.9, "num_ctx": 16384}'
deepagents -n "Summarize this repo" --model ollama:llama3 --model-params '{"temperature": 0}'
/model --model-params '{"temperature": 0.9}' ollama:llama3
```

### Profile overrides

Override fields in the model's runtime profile:

```toml
# Apply to all models from this provider
[models.providers.anthropic.profile]
max_input_tokens = 4096

# Per-model override
[models.providers.anthropic.profile."claude-sonnet-4-5"]
max_input_tokens = 8192
```

### CLI profile overrides with `--profile-override`

```bash
deepagents --profile-override '{"max_input_tokens": 4096}'
deepagents --model anthropic:claude-sonnet-4-5 --profile-override '{"max_input_tokens": 4096}'
deepagents -n "Summarize this repo" --profile-override '{"max_input_tokens": 4096}'
```

### Custom base URL

Some providers accept a `base_url` to override the default endpoint:

```toml
[models.providers.ollama]
base_url = "http://your-host-here:port"
```

### Compatible APIs

For OpenAI or Anthropic compatible APIs:

```toml
[models.providers.openai]
base_url = "https://api.example.com/v1"
api_key_env = "EXAMPLE_API_KEY"
models = ["my-model"]

[models.providers.anthropic]
base_url = "https://api.example.com"
api_key_env = "EXAMPLE_API_KEY"
models = ["my-model"]
```

### Arbitrary providers

Use any LangChain `BaseChatModel` subclass:

```toml
[models.providers.my_custom]
class_path = "my_package.models:MyChatModel"
api_key_env = "MY_API_KEY"
base_url = "https://my-endpoint.example.com"

[models.providers.my_custom.params]
temperature = 0
max_tokens = 4096
```

## External editor

Press `Ctrl+X` or type `/editor` to compose prompts in an external editor. The CLI checks `$VISUAL`, then `$EDITOR`, then falls back to `vi` (macOS/Linux) or `notepad` (Windows).

```bash
# Set in your shell profile
export VISUAL="code"    # GUI editor (--wait auto-injected)
export EDITOR="nvim"    # Terminal fallback
```

## Hooks

Hooks let external programs react to CLI lifecycle events. Configure in `~/.deepagents/hooks.json`:

```json
{
  "hooks": [
    {
      "command": ["bash", "-c", "cat >> ~/deepagents-events.log"],
      "events": ["session.start", "session.end"]
    }
  ]
}
```

### Hook configuration

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `command` | `list[str]` | Yes | Command and arguments |
| `events` | `list[str]` | No | Event names to subscribe to (omit for all) |

### Events reference

| Event | Fields | Description |
| --- | --- | --- |
| `session.start` | `thread_id` | Fired when a session begins |
| `session.end` | `thread_id` | Fired when a session exits |
| `user.prompt` | (none) | Fired when user submits a chat message |
| `input.required` | (none) | Fired when agent requires human input |
| `permission.request` | `tool_names` | Fired before tool approval dialog |
| `tool.error` | `tool_names` | Fired when a tool call errors |
| `task.complete` | `thread_id` | Fired when agent finishes task |
| `context.compact` | (none) | Fired before context compaction |

### Hook examples

```json
{
  "hooks": [
    {
      "command": ["bash", "-c", "jq -c . >> ~/.deepagents/hook-events.jsonl"],
      "events": []
    },
    {
      "command": ["bash", "-c", "osascript -e 'display notification \"Agent finished\" with title \"Deep Agents\"'"],
      "events": ["task.complete"]
    },
    {
      "command": ["python3", "my_handler.py"],
      "events": ["session.start", "permission.request"]
    }
  ]
}
```

### Execution model

- Background thread via `asyncio.to_thread`
- Concurrent dispatch in thread pool
- 5-second timeout per command
- Fire-and-forget (errors are logged, don't crash CLI)
- Lazy loading (config read once on first dispatch)
- No shell expansion by default (use `["bash", "-c", "..."]` if needed)

---

*For full documentation, see https://docs.langchain.com/oss/python/deepagents/cli/configuration*
