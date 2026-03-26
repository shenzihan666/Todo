# Custom model providers

Configure any LangChain-compatible model provider for the Deep Agents CLI.

The Deep Agents CLI supports any chat model provider compatible with LangChain, unlocking use for virtually any LLM that supports tool calling. Any service that exposes an OpenAI-compatible or Anthropic-compatible API also works out of the box.

## Quick start

Install provider packages and set credentials:

```bash
# Install with specific providers
uv tool install 'deepagents-cli[anthropic,groq]'

# Add more providers later
uv tool upgrade deepagents-cli --with langchain-ollama

# All providers
uv tool install 'deepagents-cli[anthropic,baseten,bedrock,cohere,deepseek,fireworks,google-genai,groq,huggingface,ibm,litellm,mistralai,nvidia,ollama,openai,openrouter,perplexity,vertexai,xai]'
```

Set the appropriate environment variable for your provider.

## Provider reference

| Provider | Package | Credential env var | Model profiles |
| --- | --- | --- | --- |
| OpenAI | `langchain-openai` | `OPENAI_API_KEY` | ✅ |
| Azure OpenAI | `langchain-openai` | `AZURE_OPENAI_API_KEY` | ✅ |
| Anthropic | `langchain-anthropic` | `ANTHROPIC_API_KEY` | ✅ |
| Google Gemini API | `langchain-google-genai` | `GOOGLE_API_KEY` | ✅ |
| Google Vertex AI | `langchain-google-vertexai` | `GOOGLE_CLOUD_PROJECT` | ✅ |
| Baseten | `langchain-baseten` | `BASETEN_API_KEY` | ✅ |
| AWS Bedrock | `langchain-aws` | `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | ✅ |
| Hugging Face | `langchain-huggingface` | `HUGGINGFACEHUB_API_TOKEN` | ✅ |
| Ollama | `langchain-ollama` | Optional | ❌ |
| Groq | `langchain-groq` | `GROQ_API_KEY` | ✅ |
| Cohere | `langchain-cohere` | `COHERE_API_KEY` | ❌ |
| Fireworks | `langchain-fireworks` | `FIREWORKS_API_KEY` | ✅ |
| Mistral AI | `langchain-mistralai` | `MISTRAL_API_KEY` | ✅ |
| DeepSeek | `langchain-deepseek` | `DEEPSEEK_API_KEY` | ✅ |
| IBM (watsonx.ai) | `langchain-ibm` | `WATSONX_APIKEY` | ❌ |
| Nvidia | `langchain-nvidia-ai-endpoints` | `NVIDIA_API_KEY` | ✅ |
| xAI | `langchain-xai` | `XAI_API_KEY` | ✅ |
| Perplexity | `langchain-perplexity` | `PPLX_API_KEY` | ✅ |
| OpenRouter | `langchain-openrouter` | `OPENROUTER_API_KEY` | ✅ |
| LiteLLM | `langchain-litellm` | Per-provider | ❌ |

## Switching models

To switch models in the CLI:

1. **Interactive model switcher**: `/model` displays available models
2. **Direct specification**: `/model openai:gpt-4o`
3. **Launch flag**: `deepagents --model openai:gpt-4o`

### Which models appear in the switcher

A model appears when:
1. The provider package is installed
2. The model has a profile with `tool_calling` enabled
3. The model accepts and produces text

### Troubleshooting missing models

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| Entire provider missing | Provider package not installed | Install the package |
| Provider shown but model missing | Model profile has `tool_calling: false` | Add the model to `config.toml` |
| Provider shows ⚠ "missing credentials" | API key env var not set | Set the credential env var |

## Setting a default model

Set a persistent default model:

- **Via selector**: `/model`, navigate to model, press `Ctrl+S`
- **Via command**: `/model --default provider:model`
- **Via config**: Set `[models].default` in `config.toml`
- **From shell**: `deepagents --default-model anthropic:claude-opus-4-6`

View current default:
```bash
deepagents --default-model
```

Clear default:
```bash
deepagents --clear-default-model
```

## Model resolution order

When the CLI launches:
1. `--model` flag (always wins)
2. `[models].default` in `config.toml`
3. `[models].recent` (last `/model` switch)
4. Environment auto-detection (OPENAI_API_KEY, ANTHROPIC_API_KEY, GOOGLE_API_KEY, GOOGLE_CLOUD_PROJECT)

## Model routers and proxies

Model routers like OpenRouter and LiteLLM provide access to models from multiple providers:

| Router | Package | Config |
| --- | --- | --- |
| OpenRouter | `langchain-openrouter` | `openrouter:<model>` |
| LiteLLM | `langchain-litellm` | `litellm:<model>` |

Install:
```bash
uv tool install 'deepagents-cli[openrouter]'
uv tool install 'deepagents-cli[litellm]'
```

---

*For full documentation, see https://docs.langchain.com/oss/python/deepagents/cli/providers*
