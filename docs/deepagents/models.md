# Models

Deep Agents work with any LangChain chat model that supports tool calling.

## Pass a model string

The simplest way to specify a model is to pass a string to create_deep_agent. Use the `provider:model` format to select a specific provider:

```python
agent = create_deep_agent(model="openai:gpt-5.3-codex")
```

Under the hood, this calls init_chat_model with default parameters.

## Configure model parameters

To configure model-specific parameters, use init_chat_model or instantiate a provider model class directly:

```python
from langchain.chat_models import init_chat_model

agent = create_deep_agent(
    model=init_chat_model(model="gpt-4o", temperature=0)
)
```

## Supported models

Deep Agents work with any chat model that supports tool calling. See chat model integrations for the full list of supported providers.

### Suggested models

These models perform well on the Deep Agents eval suite, which tests basic agent operations. Passing these evals is necessary but not sufficient for strong performance on longer, more complex tasks.

| Provider | Models |
| --- | --- |
| Anthropic | `claude-opus-4-6`, `claude-opus-4-5`, `claude-sonnet-4-6`, `claude-sonnet-4`, `claude-sonnet-4-5`, `claude-haiku-4-5`, `claude-opus-4-1` |
| OpenAI | `gpt-5.4`, `gpt-4o`, `gpt-4.1`, `o4-mini`, `gpt-5.2-codex`, `gpt-4o-mini`, `o3` |
| Google | `gemini-3-flash-preview`, `gemini-3.1-pro-preview` |
| Open-weight | `GLM-5`, `Kimi-K2.5`, `MiniMax-M2.5`, `qwen3.5-397B-A17B`, `devstral-2-123B` |

Open-weight models are available through providers like Baseten, Fireworks OpenRouter, and Ollama.

## Learn more

- Models in LangChain: chat model features including tool calling, structured output, and multimodality
