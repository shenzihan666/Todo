# Sandboxes

Execute code in isolated environments with sandbox backends.

Agents generate code, interact with filesystems, and run shell commands. Because we can't predict what an agent might do, it's important that its environment is isolated so it can't access credentials, files, or the network. Sandboxes provide this isolation by creating a boundary between the agent's execution environment and your host system.

In Deep Agents, __sandboxes are backends__ that define the environment where the agent operates. Unlike other backends (State, Filesystem, Store) which only expose file operations, sandbox backends also give the agent an `execute` tool for running shell commands. When you configure a sandbox backend, the agent gets:

- All standard filesystem tools (`ls`, `read_file`, `write_file`, `edit_file`, `glob`, `grep`)
- The `execute` tool for running arbitrary shell commands in the sandbox
- A secure boundary that protects your host system

## Why use sandboxes?

Sandboxes are used for security. They let agents execute arbitrary code, access files, and use the network without compromising your credentials, local files, or host system.

Sandboxes are especially useful for:

- Coding agents: Agents that run autonomously can use shell, git, clone repositories, and run Docker-in-Docker for build and test pipelines
- Data analysis agents: Load files, install data analysis libraries (pandas, numpy, etc.), run statistical calculations, and create outputs in a safe, isolated environment

## Integration patterns

There are two architecture patterns for integrating agents with sandboxes:

### Agent in sandbox pattern

The agent runs inside the sandbox and you communicate with it over the network.

Benefits:
- ✅ Mirrors local development closely
- ✅ Tight coupling between agent and environment

Trade-offs:
- 🔴 API keys must live inside the sandbox (security risk)
- 🔴 Updates require rebuilding images
- 🔴 Requires infrastructure for communication

### Sandbox as tool pattern

The agent runs on your machine or server. When it needs to execute code, it calls sandbox tools.

Benefits:
- ✅ Update agent code instantly without rebuilding images
- ✅ Cleaner separation between agent state and execution
- ✅ Pay only for execution time

Trade-offs:
- 🔴 Network latency on each execution call

## Available providers

For provider-specific setup, authentication, and lifecycle details:

- Modal
- Runloop
- Daytona
- AgentCore

## Basic usage

These examples assume you have already created a sandbox/devbox using the provider's SDK.

### Modal

```python
import modal
from langchain_anthropic import ChatAnthropic
from deepagents import create_deep_agent
from langchain_modal import ModalSandbox

app = modal.App.lookup("your-app")
modal_sandbox = modal.Sandbox.create(app=app)
backend = ModalSandbox(sandbox=modal_sandbox)

agent = create_deep_agent(
    model=ChatAnthropic(model="claude-sonnet-4-20250514"),
    system_prompt="You are a Python coding assistant with sandbox access.",
    backend=backend,
)
try:
    result = agent.invoke({
        "messages": [{
            "role": "user",
            "content": "Create a small Python package and run pytest",
        }]
    })
finally:
    modal_sandbox.terminate()
```

### Runloop

```python
import os
from runloop_api_client import RunloopSDK
from langchain_anthropic import ChatAnthropic
from deepagents import create_deep_agent
from langchain_runloop import RunloopSandbox

client = RunloopSDK(bearer_token=os.environ["RUNLOOP_API_KEY"])
devbox = client.devbox.create()
backend = RunloopSandbox(devbox=devbox)

agent = create_deep_agent(
    model=ChatAnthropic(model="claude-sonnet-4-20250514"),
    system_prompt="You are a Python coding assistant with sandbox access.",
    backend=backend,
)

try:
    result = agent.invoke({
        "messages": [{
            "role": "user",
            "content": "Create a small Python package and run pytest",
        }]
    })
finally:
    devbox.shutdown()
```

### Daytona

```python
from daytona import Daytona
from langchain_anthropic import ChatAnthropic
from deepagents import create_deep_agent
from langchain_daytona import DaytonaSandbox

sandbox = Daytona().create()
backend = DaytonaSandbox(sandbox=sandbox)

agent = create_deep_agent(
    model=ChatAnthropic(model="claude-sonnet-4-20250514"),
    system_prompt="You are a Python coding assistant with sandbox access.",
    backend=backend,
)

try:
    result = agent.invoke({
        "messages": [{
            "role": "user",
            "content": "Create a small Python package and run pytest",
        }]
    })
finally:
    sandbox.stop()
```

## Lifecycle and cleanup

Sandboxes consume resources and cost money until they're shut down. Remember to shut down sandboxes as soon as your application no longer needs them.

### Basic lifecycle

Each provider has its own lifecycle method:

- Modal: `modal_sandbox.terminate()`
- Runloop: `devbox.shutdown()`
- Daytona: `sandbox.stop()`
- AgentCore: `interpreter.stop()`

## Security considerations

Sandboxes isolate code execution from your host system, but they don't protect against __context injection__. An attacker who controls part of the agent's input can instruct it to read files, run commands, or exfiltrate data from within the sandbox.

### Handling secrets safely

If your agent needs to call authenticated APIs or access protected resources, you have two options:

1. __Keep secrets in tools outside the sandbox.__ Define tools that run in your host environment (not inside the sandbox) and handle authentication there. This is the recommended approach.
2. __Use a network proxy that injects credentials.__ Some sandbox providers support proxies that intercept outgoing HTTP requests from the sandbox and attach credentials before forwarding them.

### General best practices

- Review sandbox outputs before acting on them in your application
- Block sandbox network access when not needed
- Use middleware to filter or redact sensitive patterns in tool outputs
- Treat everything produced inside the sandbox as untrusted input

---

*For full documentation, see https://docs.langchain.com/oss/python/deepagents/sandboxes*
