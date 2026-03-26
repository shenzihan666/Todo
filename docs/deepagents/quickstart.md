# Quickstart

Build your first deep agent in minutes

This guide walks you through creating your first deep agent with planning, file system tools, and subagent capabilities. You'll build a research agent that can conduct research and write reports.

## Prerequisites

Before you begin, make sure you have an API key from a model provider (e.g., Anthropic, OpenAI).

### Step 1: Install dependencies

```bash
pip install -qU deepagents tavily-python langchain-community
```

### Step 2: Set up your API keys

**Anthropic**
```bash
export ANTHROPIC_API_KEY="your-api-key"
export TAVILY_API_KEY="your-tavily-api-key"
```

**OpenAI**
```bash
export OPENAI_API_KEY="your-api-key"
export TAVILY_API_KEY="your-tavily-api-key"
```

**Google**
```bash
export GOOGLE_API_KEY="your-api-key"
export TAVILY_API_KEY="your-tavily-api-key"
```

**OpenRouter**
```bash
export OPENROUTER_API_KEY="your-api-key"
export TAVILY_API_KEY="your-tavily-api-key"
```

**Fireworks**
```bash
export FIREWORKS_API_KEY="your-api-key"
export TAVILY_API_KEY="your-tavily-api-key"
```

**Baseten**
```bash
export BASETEN_API_KEY="your-api-key"
export TAVILY_API_KEY="your-tavily-api-key"
```

**Ollama**
```bash
# Local: Ollama must be running (https://ollama.com)
# Cloud: Set your Ollama API key for hosted inference
export OLLAMA_API_KEY="your-api-key"
export TAVILY_API_KEY="your-tavily-api-key"
```

### Step 3: Create a search tool

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
```

### Step 4: Create a deep agent

```python
# System prompt to steer the agent to be an expert researcher
research_instructions = """You are an expert researcher. Your job is to conduct thorough research and then write a polished report.

You have access to an internet search tool as your primary means of gathering information.

## `internet_search`

Use this to run an internet search for a given query. You can specify the max number of results to return, the topic, and whether raw content should be included.
"""
```

Pick a model from your provider. By default, `create_deep_agent` uses `claude-sonnet-4-6`. Pass a `model` string to use a different provider — see Suggested models for the full list.

**Anthropic**
```python
agent = create_deep_agent(
    model="anthropic:claude-sonnet-4-6",
    tools=[internet_search],
    system_prompt=research_instructions,
)
```

**OpenAI**
```python
agent = create_deep_agent(
    model="openai:gpt-5.4",
    tools=[internet_search],
    system_prompt=research_instructions,
)
```

**Google**
```python
agent = create_deep_agent(
    model="google_genai:gemini-3.1-pro-preview",
    tools=[internet_search],
    system_prompt=research_instructions,
)
```

**OpenRouter**
```python
agent = create_deep_agent(
    model="openrouter:anthropic/claude-sonnet-4-6",
    tools=[internet_search],
    system_prompt=research_instructions,
)
```

**Fireworks**
```python
agent = create_deep_agent(
    model="fireworks:accounts/fireworks/models/qwen3p5-397b-a17b",
    tools=[internet_search],
    system_prompt=research_instructions,
)
```

**Baseten**
```python
agent = create_deep_agent(
    model="baseten:zai-org/GLM-5",
    tools=[internet_search],
    system_prompt=research_instructions,
)
```

**Ollama**
```python
agent = create_deep_agent(
    model="ollama:devstral-2",
    tools=[internet_search],
    system_prompt=research_instructions,
)
```

### Step 5: Run the agent

```python
result = agent.invoke({"messages": [{"role": "user", "content": "What is langgraph?"}]})

# Print the agent's response
print(result["messages"][-1].content)
```

## How does it work?

Your deep agent automatically:

1. **Plans its approach** using the built-in `write_todos` tool to break down the research task.
2. **Conducts research** by calling the `internet_search` tool to gather information.
3. **Manages context** by using file system tools (`write_file`, `read_file`) to offload large search results.
4. **Spawns subagents** as needed to delegate complex subtasks to specialized subagents.
5. **Synthesizes a report** to compile findings into a coherent response.

## Examples

For agents, patterns, and applications you can build with Deep Agents, see Examples.

## Streaming

Deep Agents have built-in streaming for real-time updates from agent execution using LangGraph.
This allows you to observe output progressively and review and debug agent and subagent work, such as tool calls, tool results, and LLM responses.

## Next steps

Now that you've built your first deep agent:

- **Customize your agent**: Learn about customization options, including custom system prompts, tools, and subagents.
- **Add long-term memory**: Enable persistent memory across conversations.
- **Deploy to production**: Learn about deployment options for LangGraph applications.
