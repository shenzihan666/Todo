# Harness capabilities

An agent harness is a combination of several different capabilities that make building long-running agents easier:

- Planning capabilities
- Virtual filesystem
- Task delegation (subagents)
- Context and token management
- Code execution
- Human-in-the-loop

Alongside these capabilities, Deep Agents use Skills and Memory for additional context and instructions.

## Planning capabilities

The harness provides a `write_todos` tool that agents can use to maintain a structured task list.
__Features:__

- Track multiple tasks with statuses (`'pending'`, `'in_progress'`, `'completed'`)
- Persisted in agent state
- Helps agent organize complex multi-step work
- Useful for long-running tasks and planning

## Virtual filesystem access

The harness provides a configurable virtual filesystem which can be backed by different pluggable backends.
The backends support the following file system operations:

| Tool | Description |
| --- | --- |
| `ls` | List files in a directory with metadata (size, modified time) |
| `read_file` | Read file contents with line numbers, supports offset/limit for large files. Also supports reading images (`.png`, `.jpg`, `.jpeg`, `.gif`, `.webp`), returning them as multimodal content blocks. |
| `write_file` | Create new files |
| `edit_file` | Perform exact string replacements in files (with global replace mode) |
| `glob` | Find files matching patterns (e.g., `**/*.py`) |
| `grep` | Search file contents with multiple output modes (files only, content with context, or counts) |
| `execute` | Run shell commands in the environment (available with sandbox backends only) |

The virtual filesystem is used by several other harness capabilities such as skills, memory, code execution, and context management.
You can also use the file system when building custom tools and middleware for Deep Agents.
For more information, see backends.

## Task delegation (subagents)

The harness allows the main agent to create ephemeral "subagents" for isolated multi-step tasks.
__Why it's useful:__

- __Context isolation__ - Subagent's work doesn't clutter main agent's context
- __Parallel execution__ - Multiple subagents can run concurrently
- __Specialization__ - Subagents can have different tools/configurations
- __Token efficiency__ - Large subtask context is compressed into a single result

__How it works:__

- Main agent has a `task` tool
- When invoked, it creates a fresh agent instance with its own context
- Subagent executes autonomously until completion
- Returns a single final report to the main agent
- Subagents are stateless (can't send multiple messages back)

__Default subagent:__

- "general-purpose" subagent automatically available
- Has filesystem tools by default
- Can be customized with additional tools/middleware

__Custom subagents:__

- Define specialized subagents with specific tools
- Example: code-reviewer, web-researcher, test-runner
- Configure via `subagents` parameter

## Context management

The harness manages context so deep agents can handle long-running tasks within token limits while retaining the information they need.
__How it works:__

- __Input context__ — System prompt, memory, skills, and tool prompts shape what the agent knows at startup
- __Compression__ — Built-in offloading and summarization keep context within window limits as tasks progress
- __Isolation__ — Subagents quarantine heavy work and return only results (see Task delegation)
- __Long-term memory__ — Persistent storage across threads via the virtual filesystem

__Why it's useful:__

- Enables multi-step tasks that exceed a single context window
- Keeps the most relevant information in scope without manual trimming
- Reduces token usage through automatic summarization and offloading

For configuration details, see Context engineering.

## Code execution

When you use a sandbox backend, the harness exposes an `execute` tool that lets the agent run shell commands in an isolated environment. This enables the agent to install dependencies, run scripts, and execute code as part of its task.
__How it works:__

- Sandbox backends implement the `SandboxBackendProtocol` — when detected, the harness adds the `execute` tool to the agent's available tools
- Without a sandbox backend, the agent only has filesystem tools (`read_file`, `write_file`, etc.) and cannot run commands
- The `execute` tool returns combined stdout/stderr, exit code, and truncates large outputs (saving to a file for the agent to read incrementally)

__Why it's useful:__

- __Security__ — Code runs in isolation, protecting your host system from the agent's operations
- __Clean environments__ — Use specific dependencies or OS configurations without local setup
- __Reproducibility__ — Consistent execution environments across teams

For setup, providers, and file transfer APIs, see Sandboxes.

## Human-in-the-loop

The harness can pause agent execution at specified tool calls to allow human approval or modification. This feature is opt-in via the `interrupt_on` parameter.
__Configuration:__

- Pass `interrupt_on` to `create_deep_agent` with a mapping of tool names to interrupt configurations
- Example: `interrupt_on={"edit_file": True}` pauses before every edit
- You can provide approval messages or modify tool inputs when prompted

__Why it's useful:__

- Safety gates for destructive operations
- User verification before expensive API calls
- Interactive debugging and guidance

## Skills

The harness supports skills that provide specialized workflows and domain knowledge to your deep agent.
__How it works:__

- Skills follow the Agent Skills standard
- Each skill is a directory containing a `SKILL.md` file with instructions and metadata
- Skills can include additional scripts, reference docs, templates, and other resources
- Skills use progressive disclosure—they are only loaded when the agent determines they're useful for the current task
- Agent reads frontmatter from each `SKILL.md` file at startup, then reviews full skill content when needed

__Why it's useful:__

- Reduces token usage by only loading relevant skills when needed
- Bundles capabilities together into larger actions with additional context
- Provides specialized expertise without cluttering the system prompt
- Enables modular, reusable agent capabilities

For more information, see Skills.

## Memory

The harness supports persistent memory files that provide extra context to your deep agent across conversations.
These files often contain general coding style, preferences, conventions, and guidelines that help the agent understand how to work with your codebase and follow your preferences.
__How it works:__

- Uses `AGENTS.md` files to provide persistent context
- Memory files are always loaded (unlike skills, which use progressive disclosure)
- Pass one or more file paths to the `memory` parameter when creating your agent
- Files are stored in the agent's backend (StateBackend, StoreBackend, or FilesystemBackend)
- The agent can update memory based on your interactions, feedback, and identified patterns

__Why it's useful:__

- Provides persistent context that doesn't need to be re-specified each conversation
- Useful for storing user preferences, project guidelines, or domain knowledge
- Always available to the agent, ensuring consistent behavior

For configuration details and examples, see Memory.

---
