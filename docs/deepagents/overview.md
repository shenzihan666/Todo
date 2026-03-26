# Deep Agents overview

Build agents that can plan, use subagents, and leverage file systems for complex tasks

The easiest way to start building agents and applications powered by LLMs—with built-in capabilities for task planning, file systems for context management, subagent-spawning, and long-term memory.

You can use deep agents for any task, including complex, multi-step tasks.

We think of `deepagents` as an "agent harness". It is the same core tool calling loop as other agent frameworks, but with built-in tools and capabilities.

`deepagents` is a standalone library built on top of LangChain's core building blocks for agents. It uses the LangGraph runtime for durable execution, streaming, human-in-the-loop, and other features.

The `deepagents` library contains:

- **Deep Agents SDK**: A package for building agents that can handle any task
- **Deep Agents CLI**: A terminal coding agent built on the Deep Agents SDK

LangChain is the framework that provides the core building blocks for your agents.

To learn more about the differences between LangChain, LangGraph, and Deep Agents, see Frameworks, runtimes, and harnesses.

## Create a deep agent

```python
# pip install -qU deepagents
from deepagents import create_deep_agent

def get_weather(city: str) -> str:
    """Get weather for a given city."""
    return f"It's always sunny in {city}!"

agent = create_deep_agent(
    tools=[get_weather],
    system_prompt="You are a helpful assistant",
)

# Run the agent
agent.invoke(
    {"messages": [{"role": "user", "content": "what is the weather in sf"}]}
)
```

See the Quickstart and Customization guide to get started building your own agents and applications with Deep Agents.

## When to use the Deep Agents

Use the **Deep Agents SDK** when you want to build agents that can:

- **Handle complex, multi-step tasks** that require planning and decomposition
- **Manage large amounts of context** through file system tools
- **Swap filesystem backends** to use in-memory state, local disk, durable stores, sandboxes, or your own custom backend
- **Delegate work** to specialized subagents for context isolation
- **Persist memory** across conversations and threads

For building simpler agents, consider using LangChain's `create_agent` or building a custom LangGraph workflow.

Use the **Deep Agents CLI** when you want a coding agent on the command line, built on the Deep Agents SDK:

- **Run interactively or non-interactively** — use the CLI as a chat-style coding agent, or pipe tasks with `-n` for scriptable, headless execution.
- **Customize** agents with skills and memory.
- **Teach** agents as you use them about your preferences, common patterns, and custom project knowledge.
- **Execute code** on your machine or in sandboxes.

## Core capabilities

- Planning capabilities
- Virtual filesystem
- Task delegation (subagents)
- Context and token management
- Code execution
- Human-in-the-loop

## Get started

- Quickstart
- Customization
- Comparison
