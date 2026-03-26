# MCP Tools

Load additional tools from MCP (Model Context Protocol) servers.

MCP lets you extend the Deep Agents CLI with tools from external servers—file systems, APIs, databases, and more—without modifying the agent itself.

## Quickstart

The CLI automatically searches for `.mcp.json` files in standard locations. Just place a config file and it gets picked up.

## Auto-discovery

Configs are checked in this order (lowest to highest precedence):

| Priority | Location | Scope |
| --- | --- | --- |
| 1 (lowest) | `~/.deepagents/.mcp.json` | User-level—all projects |
| 2 | `<project>/.deepagents/.mcp.json` | Project-level—`.deepagents` subdirectory |
| 3 (highest) | `<project>/.mcp.json` | Project-level—root (Claude Code compatible) |

The project root is the nearest parent directory containing a `.git` folder, falling back to the current working directory.

### Flags

| Flag | Behavior |
| --- | --- |
| `--mcp-config PATH` | Add an explicit config as highest-precedence source |
| `--no-mcp` | Disable MCP entirely |

### Claude Code compatibility

If you already have a `.mcp.json` at your project root for Claude Code, the Deep Agents CLI picks it up automatically.

## Configuration format

### stdio servers (default)

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
      "env": {}
    },
    "github": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"],
      "env": { "GITHUB_TOKEN": "your-token" }
    }
  }
}
```

### SSE and HTTP servers

```json
{
  "mcpServers": {
    "remote-api": {
      "type": "sse",
      "url": "https://api.example.com/mcp",
      "headers": { "Authorization": "Bearer your-token" }
    }
  }
}
```

### Server types summary

| Type | Required fields | Optional fields |
| --- | --- | --- |
| stdio (default) | `command` | `args`, `env` |
| sse | `type: "sse"`, `url` | `headers` |
| http | `type: "http"`, `url` | `headers` |

## Multiple servers

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/home/user/projects"]
    },
    "github": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"],
      "env": { "GITHUB_TOKEN": "ghp_..." }
    },
    "database": {
      "type": "sse",
      "url": "https://db-mcp.internal:8080/mcp",
      "headers": { "Authorization": "Bearer ..." }
    }
  }
}
```

## Project-level trust

Project-level configs can contain stdio servers that execute local commands. The CLI enforces a __default-deny__ policy for project-level stdio servers.

### How it works

- **Interactive mode**: Prompts for approval before launching project stdio servers
- **Non-interactive mode (`-n`)**: Project stdio servers are silently skipped unless `--trust-project-mcp` is passed
- **Remote servers (SSE/HTTP)**: Always allowed
- **User-level configs**: Always trusted

### Flags

| Flag | Behavior |
| --- | --- |
| `--trust-project-mcp` | Trust all project-level stdio servers without prompting |

```bash
deepagents --trust-project-mcp
deepagents -n "run tests" --trust-project-mcp
```

### Trust store

Trust decisions are stored in `~/.deepagents/config.toml`:

```toml
[mcp_trust.projects]
"/Users/you/myproject" = "sha256:abc123..."
```

## System prompt awareness

Connected MCP servers and their tools are automatically listed in the agent's system prompt, grouped by server name and transport type.

## Troubleshooting

- Ensure the MCP server command is available in your PATH
- Check that API keys are properly set in the `env` section
- Verify the server URL is correct for SSE/HTTP servers

## Further reading

- [LangChain MCP guide](https://python.langchain.com/docs/integrations/mcp/)
- [MCP specification](https://spec.modelcontextprotocol.io/)

---

*For full documentation, see https://docs.langchain.com/oss/python/deepagents/cli/mcp-tools*
