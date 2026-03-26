# Deep Agents CLI

Terminal coding agent built on the Deep Agents SDK.

The Deep Agents CLI is an open source terminal coding agent built on the Deep Agents SDK. It retains persistent memory, maintains context across sessions, learns project conventions, uses customizable skills, and executes code with approval controls.

## Built-in capabilities

The Deep Agents CLI has the following built-in capabilities:

- __File operations__ - read, write, and edit files in your project
- __Shell command execution__ - execute shell commands to run tests, build projects, manage dependencies
- __Web search__ - search the web for up-to-date information (requires Tavily API key)
- __HTTP requests__ - make HTTP requests to APIs and external services
- __Task planning and tracking__ - break down complex tasks into discrete steps
- __Memory storage and retrieval__ - store and retrieve information across sessions
- __Context compaction & offloading__ - summarize older messages to free context window space
- __Human-in-the-loop__ - require human approval for sensitive tool operations
- __Skills__ - extend agent capabilities with custom expertise and instructions
- __MCP tools__ - load external tools from Model Context Protocol servers
- __Tracing__ - trace agent operations in LangSmith

## Quickstart

Install the CLI:

```bash
pip install deepagents-cli
```

Run the agent:

```bash
deepagents
```

## Providers

The CLI ships with OpenAI, Anthropic, and Google support out of the box. Each additional model provider is a separate dependency:

```bash
# Add extra providers
uv tool install deepagents-cli --with langchain-xai
```

To use a specific provider:

```bash
deepagents --model anthropic:claude-opus-4-5
```

## Interactive mode

Type naturally as you would in a chat interface. The agent will use its built-in tools, skills, and memory to help you with tasks.

## Non-interactive mode and piping

Use `-n` to run a single task without launching the interactive UI:

```bash
deepagents -n "Write a Python script that prints hello world"
```

You can also pipe input via stdin:

```bash
echo "Explain this code" | deepagents
cat error.log | deepagents -n "What's causing this error?"
git diff | deepagents -n "Review these changes"
```

## Switch models

You can switch models during a session using the `/model` command, or at launch with the `--model` flag:

```bash
> /model anthropic:claude-opus-4-5
> /model openai:gpt-4o
```

```bash
deepagents --model openai:gpt-4o
```

Run `/model` with no arguments to open an interactive model selector.

## Configuration

The CLI stores all configuration under `~/.deepagents/`. Each agent gets its own subdirectory:

| Path | Purpose |
| --- | --- |
| `~/.deepagents/config.toml` | Model defaults, provider settings, MCP trust store |
| `~/.deepagents/hooks.json` | Lifecycle event hooks |
| `~/.deepagents/<agent_name>/` | Per-agent memory, skills, and threads |
| `.deepagents/` (project root) | Project-specific memory and skills |

List all configured agents:

```bash
deepagents list
```

## Teach your agent project conventions

The agent automatically stores information in `~/.deepagents/<agent_name>/memories/` as markdown files:

```
~/.deepagents/backend-dev/memories/
├── api-conventions.md
├── database-schema.md
└── deployment-process.md
```

When you teach the agent conventions:

```
> Our API uses snake_case and includes created_at/updated_at timestamps
```

It remembers for future sessions.

## Customize your deep agent

There are two primary ways to customize any agent:

### Memory

`AGENTS.md` files provide persistent memory that is always loaded at session start:

- __Global__: `~/.deepagents/<agent_name>/AGENTS.md`
- __Project__: `.deepagents/AGENTS.md` in any git project root

### Skills

Skills are reusable agent capabilities that provide specialized workflows and domain knowledge. Deep agent skills follow the Agent Skills standard.

## Custom subagents

Define custom subagents as markdown files:

```bash
.deepagents/agents/{subagent-name}/AGENTS.md   # Project-level
~/.deepagents/{agent}/agents/{subagent-name}/AGENTS.md  # User-level
```

## MCP tools

Extend the CLI with tools from external MCP servers. Place a `.mcp.json` at your project root:

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path/to/allowed/files"]
    }
  }
}
```

## Use remote sandboxes

The CLI uses the sandbox as tool pattern: the CLI process runs on your machine, but agent tool calls target the remote sandbox.

## Tracing with LangSmith

Enable LangSmith tracing:

```bash
export LANGCHAIN_TRACING=true
export LANGCHAIN_API_KEY="your-api-key"
export DEEPAGENTS_LANGSMITH_PROJECT="my-deep-agent-execution"
```

## Command reference

```bash
# Use a specific agent configuration
deepagents --agent mybot

# Use a specific model
deepagents --model anthropic:claude-sonnet-4-5
deepagents --model gpt-4o

# Auto-approve tool usage
deepagents -y
```

---

*For full documentation, see https://docs.langchain.com/oss/python/deepagents/cli/overview*
