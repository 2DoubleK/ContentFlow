# Stage 12 ReAct Agent Design

## Goal

Implement a ReAct Agent that can load project context, search project-scoped knowledge, and save a generated draft only when the user explicitly asks. The Agent uses a cloud API first, falls back to an Ollama model when the cloud is unavailable, and uses deterministic routing when neither model can run.

## Scope

Stage 12 implements exactly three tools:

1. `get_project_context`
2. `search_knowledge`
3. `save_content_draft`

Conversation creation, message history, and public conversation APIs remain in Stage 13. `conversation_id` is optional in Stage 12. When it is supplied, Spring Boot validates that the conversation belongs to the same user and project before saving.

## Model Routing

The Agent uses this priority order for reasoning and tool selection:

```text
Cloud OpenAI-compatible API
  -> Ollama OpenAI-compatible API
  -> deterministic rule fallback
```

Cloud configuration:

```env
LLM_API_KEY=
LLM_BASE_URL=
LLM_MODEL=
```

Ollama configuration:

```env
OLLAMA_BASE_URL=http://localhost:11434/v1
OLLAMA_API_KEY=ollama
OLLAMA_MODEL=qwen2.5:7b
OLLAMA_ENABLED=true
```

Both model providers use the same LangChain `ChatOpenAI` interface and the same bound tools. A cloud timeout, connection failure, invalid tool call, or invalid structured final response triggers Ollama. An Ollama failure triggers deterministic routing. Authorization errors and tool execution errors do not trigger another provider because repeating a business operation could duplicate a draft.

## Components

### ModelRouter

`ModelRouter` owns the cloud and Ollama model clients. It exposes provider candidates in priority order and applies bounded request timeouts. It does not contain business rules or tool implementations.

### AgentRuntimeContext

Each request creates an immutable runtime context containing:

```text
user_id
project_id
conversation_id
```

The runtime context is injected by Spring Boot request data. Model-supplied IDs must match this context. A mismatch is rejected before any backend or Chroma call.

### ReAct Tools

`get_project_context` accepts `user_id` and `project_id`, validates them against the runtime context, and calls Spring Boot through `BackendClient`. It never accesses PostgreSQL directly.

`search_knowledge` accepts `project_id`, `query`, and `top_k`. The implementation ignores any attempt to remove or replace the project boundary and always queries Chroma with:

```python
where={"project_id": runtime.project_id}
```

`top_k` is constrained to `1..10`. Returned chunks preserve document ID, file name, chunk index, content, and distance.

`save_content_draft` accepts generated content, tags, references, and optional `conversation_id`. It validates all IDs against the runtime context, filters references to chunks returned by the current request, and calls `POST /internal/agent/contents`. The tool is unavailable unless the original user request explicitly contains a save instruction.

### ReAct Runner

The runner binds the three tools to each configured model and executes a bounded tool-call loop. Tool calls and their outputs are retained in LangGraph state for testing and later Stage 13 message persistence. The final model response is parsed as `GeneratedContent`.

The deterministic fallback follows the same behavior:

```text
get_project_context
-> search_knowledge when the request needs project material
-> generate_content
-> save_content_draft only for an explicit save request
```

It calls the same tool implementations rather than bypassing them.

## Request Intent

Explicit save markers include:

```text
保存
保存为草稿
存为草稿
save
save as draft
```

Ordinary generation, rewriting, polishing, and title optimization do not save content. The save permission is derived from the original user request in application code and cannot be enabled by model output.

## Spring Boot Internal API

Add:

```text
POST /internal/agent/contents
```

Request:

```json
{
  "userId": 1,
  "projectId": 1,
  "conversationId": null,
  "title": "JWT 登录",
  "summary": "...",
  "content": "...",
  "contentType": "ARTICLE",
  "tags": ["Java", "JWT"],
  "references": [
    {
      "documentId": 2,
      "fileName": "jwt.md",
      "chunkIndex": 0
    }
  ]
}
```

Spring Boot performs all of these checks again:

1. Internal token is valid.
2. `userId` owns `projectId`.
3. A non-null `conversationId` belongs to the same user and project.
4. Markdown content is nonblank.
5. References are serialized as JSONB and tags are saved in `cf_content_tag`.

The saved row uses `status = DRAFT` and stores `conversation_id`, `content`, `markdown`, and `content_type`. Existing public content generation behavior remains compatible.

## Graph State And Response

Stage 11 state remains compatible and adds:

```text
tool_calls
active_model_provider
save_requested
saved_draft_id
```

`GenerateResponse` adds optional `savedDraftId`. It remains null for ordinary generation and contains the new content ID after a successful save.

## Error Handling

- Model infrastructure failures move to the next provider.
- Project or conversation authorization failures stop immediately.
- Chroma failures return a clear generation error; the Agent does not silently produce a falsely sourced article.
- Draft saves are not automatically retried.
- A failed save returns an error and never reports `savedDraftId`.
- References not present in the current request's retrieved chunks are removed before generation output and rejected before saving.

## Testing And Acceptance

Unit tests cover:

1. Tool arguments cannot escape the runtime user and project.
2. Chroma queries always contain the integer project filter.
3. Cloud success does not call Ollama.
4. Cloud infrastructure failure calls Ollama.
5. Cloud and Ollama failure uses deterministic routing.
6. Ordinary generation calls context and search tools but not save.
7. An explicit save request calls all three tools exactly once.
8. A null conversation ID can save a draft.
9. A supplied unrelated conversation ID is rejected by Spring Boot.
10. Saved tags, references, content type, and conversation ID are persisted.

Stage acceptance uses:

```text
根据项目资料生成文章
```

Expected tools: `get_project_context`, `search_knowledge`.

```text
根据资料生成文章并保存为草稿
```

Expected tools: `get_project_context`, `search_knowledge`, `save_content_draft`. The response contains `savedDraftId`, and PostgreSQL contains one matching `DRAFT` row.
