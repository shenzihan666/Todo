# Subagents

Deep Agents can create subagents to delegate work. You can specify custom subagents in the `subagents` parameter. Subagents are useful for context quarantine (keeping the main agent's context clean) and for providing specialized instructions.

This page covers __synchronous__ subagents, where the supervisor blocks until the subagent finishes. For long-running tasks, parallel workstreams, or cases where you need mid-flight steering and cancellation, see Async subagents.

## Why use subagents?

Subagents solve the __context bloat problem__. When agents use tools with large outputs (web search, file reads, database queries), the context window fills up quickly with intermediate results. Subagents isolate this detailed work—the main agent receives only the final result, not the dozens of tool calls that produced it.

__When to use subagents:__

- Multi-step tasks that would clutter the main agent's context
- Specialized domains that need custom instructions or tools
- Tasks requiring different model capabilities
- When you want to keep the main agent focused on high-level coordination

__When NOT to use subagents:__

- Simple, single-step tasks
- When you need to maintain intermediate context
- When the overhead outweighs benefits

## Configuration

`subagents` should be a list of dictionaries or `CompiledSubAgent` objects. There are two types:

### SubAgent (Dictionary-based)

For most use cases, define subagents as dictionaries with the following fields:

| Field | Type | Description |
| --- | --- | --- |
| `name` | `str` | Required. Unique identifier for the subagent. The main agent uses this name when calling the `task()` tool. |
| `description` | `str` | Required. Description of what this subagent does. Be specific and action-oriented. |
| `system_prompt` | `str` | Required. Instructions for the subagent. Custom subagents must define their own. |
| `tools` | `list[Callable]` | Required. Tools the subagent can use. Custom subagents specify their own. |
| `model` | `str` | `BaseChatModel` | Optional. Overrides the main agent's model. |
| `middleware` | `list[Middleware]` | Optional. Additional middleware for custom behavior. |
| `interrupt_on` | `dict[str, bool]` | Optional. Configure human-in-the-loop for specific tools. |
| `skills` | `list[str]` | Optional. Skills source paths for the subagent. |

### CompiledSubAgent

For complex workflows, use a prebuilt LangGraph graph:

| Field | Type | Description |
| --- | --- | --- |
| `name` | `str` | Required. Unique identifier for the subagent. |
| `description` | `str` | Required. What this subagent does. |
| `runnable` | `Runnable` | Required. A compiled LangGraph graph (must call `.compile()` first). |

## Using SubAgent

```python
import os
from typing import Literal
from tavily import TavilyClient
from deepagents import create_deep_agent

tavily_client = TavilyClient(api_key=os.environ["TAVILY_API_KEY"])

def internet_search(
    query: str,
    max_results: int = 5,
    topic: Literal["general", "news", "finance"] = "general",
    include_raw_content: bool = False,
):
    """Run a web search"""
    return tavily_client.search(
        query,
        max_results=max_results,
        include_raw_content=include_raw_content,
        topic=topic,
    )

research_subagent = {
    "name": "research-agent",
    "description": "Used to research more in depth questions",
    "system_prompt": "You are a great researcher",
    "tools": [internet_search],
    "model": "openai:gpt-4o",  # Optional override
}

agent = create_deep_agent(
    model="claude-sonnet-4-6",
    subagents=[research_subagent]
)
```

## The general-purpose subagent

In addition to any user-defined subagents, Deep Agents have access to a `general-purpose` subagent at all times. This subagent:

- Has the same system prompt as the main agent
- Has access to all the same tools
- Uses the same model (unless overridden)
- Inherits skills from the main agent (when skills are configured)

### Override the general-purpose subagent

Include a subagent with `name="general-purpose"` in your `subagents` list to replace the default:

```python
from deepagents import create_deep_agent

agent = create_deep_agent(
    model="claude-sonnet-4-6",
    tools=[internet_search],
    subagents=[
        {
            "name": "general-purpose",
            "description": "General-purpose agent for research and multi-step tasks",
            "system_prompt": "You are a general-purpose assistant.",
            "tools": [internet_search],
            "model": "openai:gpt-4o",
        },
    ],
)
```

## Best practices

### Write clear descriptions

The main agent uses descriptions to decide which subagent to call. Be specific:

✅ __Good__: `"Analyzes financial data and generates investment insights with confidence scores"`
❌ __Bad__: `"Does finance stuff"`

### Keep system prompts detailed

Include specific guidance on how to use tools and format outputs.

### Minimize tool sets

Only give subagents the tools they need. This improves focus and security.

### Choose models by task

Different models excel at different tasks.

### Return concise results

Instruct subagents to return summaries, not raw data.

---

*For full documentation, see https://docs.langchain.com/oss/python/deepagents/subagents*
