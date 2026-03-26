# Frontend

Build React UIs that display real-time subagent streams from deep agents.

The `useStream` React hook provides built-in support for deep agent streaming. It automatically tracks subagent lifecycles, separates subagent messages from the main conversation, and exposes a rich API for building multi-agent UIs.

Key features for deep agents:

- **Subagent tracking** — Automatic lifecycle management (pending, running, complete, error)
- **Message filtering** — Separate subagent messages from the main conversation stream
- **Tool call visibility** — Access tool calls and results from within subagent execution
- **State reconstruction** — Restore subagent state from thread history on page reload

## Installation

Install the LangGraph SDK to use the `useStream` hook:

```bash
npm install @langchain/langgraph-sdk
```

## Basic usage

```typescript
import { useStream } from "@langchain/langgraph-sdk/react";
import type { agent } from "./agent";

function DeepAgentChat() {
  const stream = useStream<typeof agent>({
    assistantId: "deep-agent",
    apiUrl: "http://localhost:2024",
    filterSubagentMessages: true,  // Keep subagent messages separate
  });

  const handleSubmit = (message: string) => {
    stream.submit(
      { messages: [{ content: message, type: "human" }] },
      { streamSubgraphs: true }  // Enable subagent streaming
    );
  };

  return (
    <div>
      {/* Main conversation messages */}
      {stream.messages.map((message, idx) => (
        <div key={message.id ?? idx}>
          {message.type}: {message.content}
        </div>
      ))}

      {/* Subagent progress */}
      {stream.activeSubagents.length > 0 && (
        <div>
          <h3>Active subagents:</h3>
          {stream.activeSubagents.map((subagent) => (
            <SubagentCard key={subagent.id} subagent={subagent} />
          ))}
        </div>
      )}

      {stream.isLoading && <div>Loading...</div>}
    </div>
  );
}
```

## Deep agent `useStream` parameters

| Parameter | Type | Default | Description |
| --- | --- | --- | --- |
| `filterSubagentMessages` | `boolean` | `false` | When true, subagent messages are excluded from main `stream.messages` |
| `subagentToolNames` | `string[]` | `["task"]` | Tool names that spawn subagents |

## Deep agent `useStream` return values

| Property | Type | Description |
| --- | --- | --- |
| `subagents` | `Map<string, SubagentStream>` | All subagents keyed by tool call ID |
| `activeSubagents` | `SubagentStream[]` | Currently running subagents |
| `getSubagent` | `(toolCallId: string) => SubagentStream` | Get a specific subagent |
| `getSubagentsByMessage` | `(messageId: string) => SubagentStream[]` | Get subagents triggered by a message |
| `getSubagentsByType` | `(type: string) => SubagentStream[]` | Filter subagents by type |

## Subagent stream interface

```typescript
interface SubagentStream {
  // Identity
  id: string;                    // Tool call ID
  toolCall: {                    // Original task tool call
    subagent_type: string;
    description: string;
  };

  // Lifecycle
  status: "pending" | "running" | "complete" | "error";
  startedAt: Date | null;
  completedAt: Date | null;
  isLoading: boolean;

  // Content
  messages: Message[];           // Subagent's messages
  values: Record<string, any>;   // Subagent's state
  result: string | null;         // Final result
  error: string | null;          // Error message

  // Tool calls
  toolCalls: ToolCallWithResult[];
  getToolCalls: (message: Message) => ToolCallWithResult[];

  // Hierarchy
  depth: number;                 // Nesting depth
  parentId: string | null;       // Parent subagent ID
}
```

## Rendering subagent streams

### Subagent cards

```typescript
import { useStream, type SubagentStream } from "@langchain/langgraph-sdk/react";

function SubagentCard({ subagent }: { subagent: SubagentStream }) {
  const content = getStreamingContent(subagent.messages);

  return (
    <div className="border rounded-lg p-4">
      <div className="flex items-center gap-2 mb-2">
        <StatusIcon status={subagent.status} />
        <span className="font-medium">{subagent.toolCall.subagent_type}</span>
        <span className="text-sm text-gray-500">
          {subagent.toolCall.description}
        </span>
      </div>

      {content && (
        <div className="prose text-sm mt-2">
          {content}
        </div>
      )}

      {subagent.status === "complete" && subagent.result && (
        <div className="mt-2 p-2 bg-green-50 rounded text-sm">
          {subagent.result}
        </div>
      )}

      {subagent.status === "error" && subagent.error && (
        <div className="mt-2 p-2 bg-red-50 rounded text-sm text-red-700">
          {subagent.error}
        </div>
      )}
    </div>
  );
}
```

### Map subagents to messages

```typescript
import { useMemo } from "react";
import { useStream } from "@langchain/langgraph-sdk/react";

function DeepAgentChat() {
  const stream = useStream({
    assistantId: "deep-agent",
    apiUrl: "http://localhost:2024",
    filterSubagentMessages: true,
  });

  const subagentsByMessage = useMemo(() => {
    const result = new Map();
    const messages = stream.messages;

    for (let i = 0; i < messages.length; i++) {
      if (messages[i].type !== "human") continue;

      const next = messages[i + 1];
      if (!next || next.type !== "ai" || !next.id) continue;

      const subagents = stream.getSubagentsByMessage(next.id);
      if (subagents.length > 0) {
        result.set(messages[i].id, subagents);
      }
    }
    return result;
  }, [stream.messages, stream.subagents]);

  return (
    <div>
      {stream.messages.map((message, idx) => (
        <div key={message.id ?? idx}>
          <MessageBubble message={message} />
          {message.type === "human" && subagentsByMessage.has(message.id) && (
            <SubagentPipeline
              subagents={subagentsByMessage.get(message.id)!}
              isLoading={stream.isLoading}
            />
          )}
        </div>
      ))}
    </div>
  );
}
```

### Subagent pipeline with progress

```typescript
function SubagentPipeline({
  subagents,
  isLoading,
}: {
  subagents: SubagentStream[];
  isLoading: boolean;
}) {
  const completed = subagents.filter(
    (s) => s.status === "complete" || s.status === "error"
  ).length;

  const allDone = completed === subagents.length;

  return (
    <div className="my-4 space-y-3">
      <div className="flex items-center justify-between text-sm">
        <span className="font-medium">
          Subagents ({completed}/{subagents.length})
        </span>
        {allDone && isLoading && (
          <span className="text-blue-500 animate-pulse">
            Synthesizing results...
          </span>
        )}
      </div>

      <div className="h-1.5 bg-gray-200 rounded-full overflow-hidden">
        <div
          className="h-full bg-blue-500 transition-all duration-300"
          style={{ width: `${(completed / subagents.length) * 100}%` }}
        />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
        {subagents.map((subagent) => (
          <SubagentCard key={subagent.id} subagent={subagent} />
        ))}
      </div>
    </div>
  );
}
```

## Thread persistence

Persist thread IDs across page reloads:

```typescript
import { useCallback, useState } from "react";
import { useStream } from "@langchain/langgraph-sdk/react";

function useThreadIdParam() {
  const [threadId, setThreadId] = useState<string | null>(() => {
    const params = new URLSearchParams(window.location.search);
    return params.get("threadId");
  });

  const updateThreadId = useCallback((id: string) => {
    setThreadId(id);
    const url = new URL(window.location.href);
    url.searchParams.set("threadId", id);
    window.history.replaceState({}, "", url.toString());
  }, []);

  return [threadId, updateThreadId] as const;
}

function PersistentDeepAgentChat() {
  const [threadId, onThreadId] = useThreadIdParam();

  const stream = useStream({
    assistantId: "deep-agent",
    apiUrl: "http://localhost:2024",
    filterSubagentMessages: true,
    threadId,
    onThreadId,
    reconnectOnMount: true,
  });

  return (
    <div>
      {stream.messages.map((message, idx) => (
        <div key={message.id ?? idx}>
          {message.type}: {message.content}
        </div>
      ))}

      {[...stream.subagents.values()].map((subagent) => (
        <SubagentCard key={subagent.id} subagent={subagent} />
      ))}
    </div>
  );
}
```

## Related

- **Streaming overview** — Server-side streaming with deep agents
- **Subagents** — Configure and use subagents with deep agents
- **LangChain frontend streaming** — General `useStream` documentation
- **useStream API Reference** — Full API documentation

---

*For full documentation, see https://docs.langchain.com/oss/python/deepagents/streaming/frontend*
