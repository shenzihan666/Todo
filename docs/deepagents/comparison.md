# Comparison with Claude Agent SDK and Codex

This page helps you understand how LangChain Deep Agents compare to the Claude Agent SDK and the Codex SDK.

## Overview

| Aspect | __LangChain Deep Agents__ | __Claude Agent SDK__ | __Codex SDK__ |
| --- | --- | --- | --- |
| __Use cases__ | Custom general-purpose agents (including coding) | Custom AI coding agents | Prebuilt coding agent that can execute coding tasks |
| __Model support__ | Flexible and model-agnostic (Anthropic, OpenAI, and 100s others) | Tightly integrated with Claude models | Tightly integrated with OpenAI models |
| __Architecture__ | Python SDK, TypeScript SDK, and CLI | Python SDK, TypeScript SDK | TypeScript SDK, CLI, desktop app, IDE extension |
| __Execution environment__ | Local, remote sandboxes, virtual filesystem | Local | Local, cloud |
| __Deployment__ | LangGraph Platform | Self-hosted | N/A |
| __Frontend__ | Integration with React | Server-side only | Server-side only |
| __Observability__ | LangSmith tracing & evaluations | N/A | OpenAI traces & command-line evaluations |
| __Security configurability__ | Composable, per-tool human-in-the-loop | Permission system with modes, rules and hooks | Built-in tiers using approval modes and OS-level sandboxes |
| __License__ | MIT | MIT (underlying Claude Code is proprietary) | Apache-2.0 |

### Key differences

__LangChain Deep Agents__:

- __Model flexibility__: Swap model providers (Anthropic, OpenAI, or 100+ others) at any time
- __Long-term memory__: Persist context across sessions and threads with the Memory Store
- __Sandbox-as-tool pattern__: Run individual operations in isolated sandboxes from different providers
- __Virtual filesystem__: Use pluggable backends (in-memory, disk, durable stores, sandboxes)
- __Production deployment__: Deploy via LangSmith or self-host with the Agent Server
- __Observability__: Use LangSmith for native tracing and debugging

__Claude Agent SDK__:

- __Standardize on Claude__: First-class support for Claude models across Anthropic, Azure, Vertex AI, and AWS Bedrock
- __Custom hosting__: Build your own HTTP/WebSocket layer and run the SDK in containers
- __Hooks__: Easily intercept and control agent behavior

__Codex SDK__:

- __Standardize on OpenAI__: GPT-5.3-Codex and OpenAI-specific tooling
- __OS-level sandbox modes__: Use built-in `read-only`, `workspace-write`, or `danger-full-access` modes
- __MCP server mode__: Expose your agent as an MCP server
- __Observability__: Use OpenAI Traces

## Feature comparison

| Feature | __Deep Agents__ | __Claude Agent SDK__ | __Codex SDK__ |
| --- | --- | --- | --- |

### Core tools

| Tool | Deep Agents | Claude Agent SDK | Codex SDK |
| --- | --- | --- | --- |
| File Read/Write/Edit | ✅ `read_file`, `write_file`, `edit_file` | ✅ Read, write, edit | ✅ Read, write, edit |
| Shell Execution | ✅ `execute` | ✅ bash | ✅ `exec` |
| Glob/Grep | ✅ `glob`, `grep` | ✅ glob, grep | ✅ Built-in |
| Web Search | ✅ Third-party support | ✅ WebSearch, WebFetch | ✅ `web_search` |
| Planning/Todos | ✅ `write_todos` | ✅ Todo lists | ✅ Plan before changes |
| Subagents | ✅ Subagents | ✅ Subagents | ✅ Multi-agent workflows |
| MCP Client | ✅ | ✅ MCP | ✅ MCP client |
| Human-in-the-Loop | ✅ Approve/edit/reject | ✅ Permission modes | ✅ Approval modes |
| Skills System | ✅ Skills | ✅ Skills | ✅ Skills |
| Long-term memory | ✅ Memory Store | ❌ | ❌ |
| Streaming | ✅ Streaming | ✅ Streaming | ✅ Streaming |

### Deployment

| Feature | Deep Agents | Claude Agent SDK | Codex SDK |
| --- | --- | --- | --- |
| Production hosting | ✅ LangGraph Platform, self-hosted | Build your own layer | ❌ |
| Local execution | ✅ | ✅ | ✅ |
| Cloud execution | ❌ | ❌ | ✅ |

### Supported protocols

| Protocol | Deep Agents | Claude Agent SDK | Codex SDK |
| --- | --- | --- | --- |
| ACP server | ✅ ACP server | ✅ (third-party) | ✅ (third-party) |
| MCP server | MCP endpoint via Agent Server | ❌ | ✅ `codex mcp-server` |
| A2A endpoint | ✅ A2A endpoint in Agent Server | ✅ | ❌ |

---

*For full documentation, see https://docs.langchain.com/oss/python/deepagents/comparison*
