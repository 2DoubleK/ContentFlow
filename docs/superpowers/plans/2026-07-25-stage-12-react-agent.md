# Stage 12 ReAct Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement three secure ReAct tools with cloud API, Ollama `qwen3:1.7b`, and deterministic fallback, including explicit draft saving through Spring Boot.

**Architecture:** Each Agent request creates a runtime-bound toolbox that enforces the authenticated user/project boundary. A model router attempts the cloud OpenAI-compatible API and then Ollama; a bounded LangGraph ReAct runner executes tools and parses `GeneratedContent`, while deterministic routing uses the same tools if both providers fail. Spring Boot owns PostgreSQL writes and repeats project/conversation authorization checks.

**Tech Stack:** Python 3.12, LangGraph, LangChain, FastAPI, Pydantic 2, Ollama 0.31.1, ChromaDB, Java 17, Spring Boot, MyBatis-Plus, PostgreSQL, pytest, JUnit 5.

## Global Constraints

- Implement exactly `get_project_context`, `search_knowledge`, and `save_content_draft`.
- Vue calls Spring Boot only; Agent never accesses PostgreSQL or MinIO directly.
- Chroma search always filters by runtime integer `project_id`.
- A model cannot override runtime `user_id`, `project_id`, or `conversation_id`.
- Draft saving is available only when the original request explicitly asks to save.
- `conversation_id` may be null; when present, Spring Boot validates user and project ownership.
- Cloud failure falls back to Ollama `qwen3:1.7b`; Ollama failure falls back to deterministic routing.
- Ollama uses `think=false` and an 8192-token context.
- Ollama system prompts include `/no_think` because its OpenAI-compatible endpoint may otherwise place reasoning text in normal content.
- Existing public `/api/projects/{projectId}/generate` behavior remains compatible.

---

### Task 1: Model provider configuration and routing

**Files:**
- Modify: `agent/app/config.py`
- Create: `agent/app/model_router.py`
- Modify: `agent/.env.example`
- Test: `agent/tests/test_config.py`
- Create: `agent/tests/test_model_router.py`

**Interfaces:**
- Produces: `ModelCandidate(provider: str, model: BaseChatModel)`.
- Produces: `ModelRouter.candidates() -> list[ModelCandidate]` in cloud then Ollama order.
- Consumes settings `ollama_enabled`, `ollama_base_url`, `ollama_api_key`, `ollama_model`, `ollama_context_length`, and `llm_timeout_seconds`.

- [ ] **Step 1: Write failing provider-order and Ollama-option tests**

```python
def test_router_orders_cloud_before_ollama():
    config = Settings(
        llm_api_key="cloud-key", llm_base_url="https://cloud.test/v1", llm_model="cloud-model",
        ollama_enabled=True, ollama_base_url="http://localhost:11434/v1", ollama_model="qwen3:1.7b",
    )
    candidates = ModelRouter(config).candidates()
    assert [item.provider for item in candidates] == ["cloud", "ollama"]
    assert candidates[1].model.extra_body == {"think": False, "options": {"num_ctx": 8192}}
```

- [ ] **Step 2: Run tests and verify missing settings/router failure**

Run: `agent/.venv312/Scripts/python.exe -m pytest agent/tests/test_config.py agent/tests/test_model_router.py -q`

Expected: FAIL because `ModelRouter` and Ollama settings do not exist.

- [ ] **Step 3: Implement candidate construction**

```python
@dataclass(frozen=True)
class ModelCandidate:
    provider: str
    model: BaseChatModel

class ModelRouter:
    def candidates(self) -> list[ModelCandidate]:
        result = []
        if self.settings.llm_api_key:
            result.append(ModelCandidate("cloud", ChatOpenAI(..., max_retries=0)))
        if self.settings.ollama_enabled:
            result.append(ModelCandidate("ollama", ChatOpenAI(
                base_url=self.settings.ollama_base_url,
                api_key=self.settings.ollama_api_key,
                model=self.settings.ollama_model,
                extra_body={"think": False, "options": {"num_ctx": self.settings.ollama_context_length}},
                max_retries=0,
            )))
        return result
```

- [ ] **Step 4: Run focused tests**

Run: `agent/.venv312/Scripts/python.exe -m pytest agent/tests/test_config.py agent/tests/test_model_router.py -q`

Expected: all focused tests PASS.

### Task 2: Runtime-bound ReAct tools

**Files:**
- Create: `agent/app/react_tools.py`
- Modify: `agent/app/backend_client.py`
- Create: `agent/tests/test_react_tools.py`
- Modify: `agent/tests/test_backend_client.py`

**Interfaces:**
- Produces: immutable `AgentRuntimeContext(user_id, project_id, conversation_id, save_requested)`.
- Produces: request-scoped `AgentToolbox` methods and `langchain_tools()`.
- Produces: `BackendClient.save_content_draft(payload) -> dict` using `/internal/agent/contents`.
- Tracks: `tool_calls`, `retrieved_chunks`, `saved_draft_id`, and `saved_content`.

- [ ] **Step 1: Write failing security and call-recording tests**

```python
@pytest.mark.asyncio
async def test_toolbox_enforces_runtime_project_and_explicit_save():
    toolbox = AgentToolbox(runtime=AgentRuntimeContext(9, 7, None, False), ...)
    with pytest.raises(ValueError, match="runtime project"):
        await toolbox.get_project_context(user_id=9, project_id=8)
    with pytest.raises(ValueError, match="not requested"):
        await toolbox.save_content_draft(project_id=7, user_id=9, title="x", summary="s", content="# x")
```

```python
def test_search_tool_forces_runtime_project():
    chunks = toolbox.search_knowledge(project_id=7, query="JWT", top_k=5)
    assert retrieval.calls == [(7, "JWT", 5)]
    assert toolbox.tool_calls == ["search_knowledge"]
```

- [ ] **Step 2: Run tests and verify missing toolbox failure**

Run: `agent/.venv312/Scripts/python.exe -m pytest agent/tests/test_react_tools.py agent/tests/test_backend_client.py -q`

Expected: FAIL because runtime tools and the new backend route do not exist.

- [ ] **Step 3: Implement tools and reference validation**

```python
def _validate_reference(self, reference: ReferenceItem) -> bool:
    allowed = {(chunk.document_id, chunk.file_name, chunk.chunk_index) for chunk in self.retrieved_chunks}
    return (reference.document_id, reference.file_name, reference.chunk_index) in allowed
```

`langchain_tools()` returns structured async/sync tools whose schemas expose documented arguments, while each implementation verifies them against the runtime context before acting.

- [ ] **Step 4: Run focused tests**

Run: `agent/.venv312/Scripts/python.exe -m pytest agent/tests/test_react_tools.py agent/tests/test_backend_client.py -q`

Expected: tool security, route, metadata, and save-intent tests PASS.

### Task 3: ReAct runner, failover, and graph integration

**Files:**
- Create: `agent/app/react_runner.py`
- Modify: `agent/app/llm.py`
- Modify: `agent/app/graph.py`
- Modify: `agent/app/schemas.py`
- Modify: `agent/app/main.py`
- Modify: `agent/app/langgraph_entry.py`
- Create: `agent/tests/test_react_runner.py`
- Modify: `agent/tests/test_graph.py`

**Interfaces:**
- Produces: `ReActRunResult(generated_content, parsed_request, retrieved_chunks, tool_calls, active_model_provider, saved_draft_id)`.
- Produces: `ReActRunner.run(runtime, user_request) -> ReActRunResult`.
- Extends: `GenerateResponse.saved_draft_id` serialized as `savedDraftId`.
- Preserves: Stage 11 generation fields and `/generate` request compatibility.

- [ ] **Step 1: Write failing provider failover and save-route tests**

```python
@pytest.mark.asyncio
async def test_runner_falls_back_from_cloud_to_ollama():
    executor = StubExecutor(outcomes={"cloud": ConnectionError(), "ollama": generated})
    result = await runner.run(runtime, "根据项目资料生成文章")
    assert executor.providers == ["cloud", "ollama"]
    assert result.active_model_provider == "ollama"
```

```python
@pytest.mark.asyncio
async def test_deterministic_save_calls_all_three_tools_once():
    result = await runner_without_models.run(runtime_with_save, "根据资料生成文章并保存为草稿")
    assert result.tool_calls == ["get_project_context", "search_knowledge", "save_content_draft"]
    assert result.saved_draft_id == 31
```

- [ ] **Step 2: Run tests and verify missing runner failure**

Run: `agent/.venv312/Scripts/python.exe -m pytest agent/tests/test_react_runner.py agent/tests/test_graph.py -q`

Expected: FAIL because `ReActRunner` and Stage 12 state fields do not exist.

- [ ] **Step 3: Implement bounded provider attempts and deterministic fallback**

```python
for candidate in self.model_router.candidates():
    toolbox = self.toolbox_factory(runtime)
    try:
        return await self.model_executor(candidate, toolbox, user_request)
    except MODEL_INFRASTRUCTURE_ERRORS:
        logger.warning("model provider failed: %s", candidate.provider, exc_info=True)
return await self._run_deterministic(runtime, user_request)
```

The model executor uses `create_react_agent(..., response_format=GeneratedContent)` with a bounded recursion limit. Ollama prompts begin with `/no_think`. The save tool is bound only when `save_requested` is true. A provider result that omits required context/search/save calls is invalid and moves to the next provider before deterministic fallback.

- [ ] **Step 4: Integrate the runner into LangGraph**

```text
START -> run_react_agent -> format_output -> END
```

`run_react_agent` writes all Stage 11 compatibility fields plus `tool_calls`, `active_model_provider`, `save_requested`, and `saved_draft_id`.

- [ ] **Step 5: Run focused tests**

Run: `agent/.venv312/Scripts/python.exe -m pytest agent/tests/test_react_runner.py agent/tests/test_graph.py -q`

Expected: cloud, Ollama, deterministic, ordinary generation, and explicit-save paths PASS.

### Task 4: Spring Boot internal draft persistence and authorization

**Files:**
- Modify: `backend/src/main/java/com/contentflow/internal/dto/InternalDtos.java`
- Modify: `backend/src/main/java/com/contentflow/internal/controller/InternalController.java`
- Create: `backend/src/main/java/com/contentflow/agent/mapper/AgentConversationMapper.java`
- Modify: `backend/src/main/java/com/contentflow/content/entity/ContentEntity.java`
- Modify: `backend/src/main/java/com/contentflow/content/service/ContentService.java`
- Modify: `backend/src/main/java/com/contentflow/content/dto/ContentDtos.java`
- Modify: `backend/src/test/java/com/contentflow/internal/controller/InternalControllerTest.java`
- Modify: `backend/src/test/java/com/contentflow/content/service/ContentServiceTest.java`

**Interfaces:**
- Adds: `POST /internal/agent/contents`.
- Adds: `ContentService.saveAgentDraft(Long userId, SaveAgentContentRequest request)`.
- Adds: `AgentConversationMapper.existsOwned(id, userId, projectId)`.
- Persists: `conversation_id`, `content`, `markdown`, `content_type`, `DRAFT`, tags, and references.

- [ ] **Step 1: Write failing controller/service tests**

```java
@Test
void savesAgentDraftWithoutConversation() {
    var request = new InternalDtos.SaveAgentContentRequest(
        9L, 7L, null, "JWT", "summary", "# JWT", "ARTICLE",
        List.of("Java"), List.of(new ContentDtos.ReferenceItem(3L, "jwt.md", 0)));
    service.saveAgentDraft(request);
    verify(projectService).requireOwned(9L, 7L);
    assertThat(saved.getConversationId()).isNull();
    assertThat(saved.getStatus()).isEqualTo("DRAFT");
}
```

```java
@Test
void rejectsConversationOwnedByAnotherProject() {
    when(conversationMapper.existsOwned(4L, 9L, 7L)).thenReturn(false);
    assertThatThrownBy(() -> service.saveAgentDraft(requestWithConversation(4L)))
        .isInstanceOf(AppException.class);
}
```

- [ ] **Step 2: Run focused backend tests and verify failure**

Run: `mvn -f backend/pom.xml -Dtest=InternalControllerTest,ContentServiceTest test`

Expected: compilation/test failure because the endpoint, DTO, mapper, and entity fields do not exist.

- [ ] **Step 3: Implement DTO, controller, ownership query, and persistence**

```java
@Select("""
    SELECT COUNT(*) > 0 FROM cf_agent_conversation
    WHERE id = #{id} AND user_id = #{userId} AND project_id = #{projectId}
    """)
boolean existsOwned(Long id, Long userId, Long projectId);
```

The controller verifies `X-Internal-Token` and delegates to `ContentService`. The service always calls `projectService.requireOwned`, checks a non-null conversation, inserts content, serializes references, and saves distinct nonblank tags in one transaction.

- [ ] **Step 4: Run focused backend tests**

Run: `mvn -f backend/pom.xml -Dtest=InternalControllerTest,ContentServiceTest test`

Expected: all focused backend tests PASS.

### Task 5: Full verification, local configuration, and acceptance

**Files:**
- Modify: `agent/.env` (ignored local configuration, never committed)
- Modify: `开发步骤文档.md`
- Modify: `docs/superpowers/plans/2026-07-25-stage-12-react-agent.md`

**Interfaces:**
- Verifies cloud configuration, Ollama fallback, deterministic fallback, tool routing, real draft persistence, and service health.

- [ ] **Step 1: Configure local cloud and Ollama providers**

Set ignored `agent/.env` values from the existing local credentials file without printing secrets. Configure `OLLAMA_MODEL=qwen3:1.7b`, `OLLAMA_CONTEXT_LENGTH=8192`, and `OLLAMA_ENABLED=true`.

- [ ] **Step 2: Run all Agent tests**

Run: `agent/.venv312/Scripts/python.exe -m pytest agent/tests -q`

Expected: all Agent tests PASS.

- [ ] **Step 3: Run all backend tests**

Run: `mvn -f backend/pom.xml test`

Expected: all backend tests PASS.

- [ ] **Step 4: Restart Agent and backend and verify health**

Expected: `http://localhost:8000/health` returns `ok`; `http://localhost:8080/actuator/health` returns `UP`.

- [ ] **Step 5: Execute both Stage 12 acceptance requests**

Ordinary request expected tools:

```text
get_project_context, search_knowledge
```

Explicit save request expected tools:

```text
get_project_context, search_knowledge, save_content_draft
```

The second response must contain `savedDraftId`, and PostgreSQL must contain exactly one matching `DRAFT` row.

- [ ] **Step 6: Record completion and verification evidence**

Update Stage 12 in `开发步骤文档.md` with provider used, tool-call sequences, test counts, saved draft ID, and database verification.
