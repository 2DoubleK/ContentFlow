# Stage 11 LangGraph Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Stage 11 LangGraph workflow with project context loading, structured request parsing, conditional project-scoped RAG, and structured generated content.

**Architecture:** Spring Boot remains the only public API and supplies the authenticated user ID to the Agent. The Agent loads project context through the existing internal backend API, queries Chroma only when the parsed request needs retrieval, and constrains references to retrieved chunks. A deterministic local fallback implements the same typed LLM interface so acceptance does not depend on external model credentials.

**Tech Stack:** Python 3.12, FastAPI, Pydantic 2, LangGraph, ChromaDB, Spring Boot, JUnit 5, pytest.

## Global Constraints

- Vue calls Spring Boot only.
- Agent reads business data through Spring Boot internal APIs and does not access PostgreSQL or MinIO directly.
- Spring Boot does not query Chroma.
- Every Chroma query uses an integer `project_id` filter.
- Existing `/api/projects/{projectId}/generate` request shape remains unchanged.

---

### Task 1: Typed workflow and retrieval contracts

**Files:**
- Modify: `agent/app/schemas.py`
- Modify: `agent/app/retrieval.py`
- Test: `agent/tests/test_retrieval.py`

**Interfaces:**
- Produces: `ParsedContentRequest`, `RetrievedChunk`, `ReferenceItem`, and `GeneratedContent` Pydantic models.
- Produces: `RetrievalService.search_chunks(project_id: int, query: str, limit: int = 5) -> list[RetrievedChunk]`.
- Preserves: `RetrievalService.search(...) -> list[str]` for existing callers.

- [x] **Step 1: Write a failing structured retrieval test**

```python
def test_search_chunks_returns_project_scoped_content_and_metadata(tmp_path):
    service = RetrievalService(str(tmp_path))
    service.index_text(1, 10, "a.txt", "JWT project guide", owner_id=1)
    chunks = service.search_chunks(1, "JWT", limit=5)
    assert chunks[0].document_id == 10
    assert chunks[0].file_name == "a.txt"
    assert chunks[0].chunk_index == 0
    assert chunks[0].content
    assert chunks[0].distance is not None
```

- [x] **Step 2: Run the focused test and verify it fails**

Run: `agent/.venv312/Scripts/python.exe -m pytest agent/tests/test_retrieval.py -q`

Expected: FAIL because `search_chunks` is not defined.

- [x] **Step 3: Implement the models and map Chroma query arrays by index**

```python
result = self.collection.query(
    query_texts=[query], n_results=limit,
    where={"project_id": project_id},
    include=["documents", "metadatas", "distances"],
)
return [RetrievedChunk(documentId=metadata["document_id"], ...)
        for document, metadata, distance in zip(documents, metadatas, distances)]
```

- [x] **Step 4: Run retrieval tests and verify they pass**

Run: `agent/.venv312/Scripts/python.exe -m pytest agent/tests/test_retrieval.py -q`

Expected: all retrieval tests PASS.

### Task 2: Conditional LangGraph workflow

**Files:**
- Modify: `agent/app/llm.py`
- Modify: `agent/app/graph.py`
- Test: `agent/tests/test_graph.py`

**Interfaces:**
- Consumes: `BackendClient.get_project_context`, `RetrievalService.search_chunks`, and typed schema models.
- Produces: `build_graph(retrieval, llm, backend)` with async `load_project_context` and conditional routing.
- Produces: final state key `response: GeneratedContent`.

- [x] **Step 1: Write failing tests for both routes and reference integrity**

```python
state = await graph.ainvoke({"user_id": 9, "project_id": 7,
                             "user_request": "根据我上传的资料生成 JWT 登录文章。"})
assert state["parsed_request"]["need_retrieval"] is True
assert retrieval.queries == [(7, "JWT 登录文章", 5)]
assert state["response"].references[0].document_id == 3

state = await graph.ainvoke({"user_id": 9, "project_id": 7,
                             "user_request": "把这段标题改得更吸引人。"})
assert state["parsed_request"]["need_retrieval"] is False
assert retrieval.queries == []
```

- [x] **Step 2: Run graph tests and verify they fail**

Run: `agent/.venv312/Scripts/python.exe -m pytest agent/tests/test_graph.py -q`

Expected: FAIL because the new graph state and conditional route do not exist.

- [x] **Step 3: Implement parser/generator fallback and graph nodes**

```text
START -> load_project_context -> parse_request
parse_request -> retrieve_knowledge | generate_content
retrieve_knowledge -> generate_content -> format_output -> END
```

The fallback parser marks requests containing terms such as `资料`, `知识库`, `参考`, or `上传` as retrieval requests, extracts word limits, and merges missing platform/audience/style values from project context. The generator receives only typed retrieved chunks and creates references from their metadata.

- [x] **Step 4: Run graph tests and verify they pass**

Run: `agent/.venv312/Scripts/python.exe -m pytest agent/tests/test_graph.py -q`

Expected: RAG and no-RAG tests PASS with different executed node paths.

### Task 3: Agent and Spring Boot request contract

**Files:**
- Modify: `agent/app/main.py`
- Modify: `agent/app/langgraph_entry.py`
- Modify: `backend/src/main/java/com/contentflow/content/dto/ContentDtos.java`
- Modify: `backend/src/main/java/com/contentflow/agent/client/AgentGenerationClient.java`
- Modify: `backend/src/main/java/com/contentflow/content/service/ContentService.java`
- Modify: `backend/src/test/java/com/contentflow/content/service/ContentServiceTest.java`

**Interfaces:**
- Agent consumes: `{userId, projectId, conversationId?, prompt}`.
- Agent returns: `{title, summary, content, markdown, tags, references}` where references contain `documentId`, `fileName`, and `chunkIndex`.
- Spring public generation endpoint remains `{prompt}` and passes authenticated `ownerId` internally.

- [x] **Step 1: Update the backend test to require authenticated user forwarding**

```java
when(agentClient.generate(1L, 2L, "write article")).thenReturn(...);
verify(agentClient).generate(1L, 2L, "write article");
```

- [x] **Step 2: Run the focused backend test and verify it fails**

Run: `mvn -f backend/pom.xml -Dtest=ContentServiceTest test`

Expected: compilation/test failure because the client still accepts two arguments.

- [x] **Step 3: Update DTOs, client, service persistence, and FastAPI endpoint**

Spring Boot serializes the current owner ID as `userId`. `ContentService.generate` saves generated tags and serializes structured references. FastAPI calls `await graph.ainvoke(...)` and returns `GeneratedContent`.

- [x] **Step 4: Run focused Agent and backend tests**

Run: `agent/.venv312/Scripts/python.exe -m pytest agent/tests/test_graph.py agent/tests/test_backend_client.py -q`

Run: `mvn -f backend/pom.xml -Dtest=ContentServiceTest test`

Expected: all focused tests PASS.

### Task 4: Full verification and acceptance

**Files:**
- Modify: `开发步骤文档.md`

**Interfaces:**
- Verifies all Stage 11 state fields, node routing, project context loading, typed generation, and compatibility.

- [x] **Step 1: Run the complete Agent suite**

Run: `agent/.venv312/Scripts/python.exe -m pytest agent/tests -q`

Expected: all Agent tests PASS.

- [x] **Step 2: Run the complete backend suite**

Run: `mvn -f backend/pom.xml test`

Expected: all backend tests PASS.

- [x] **Step 3: Execute both Stage 11 acceptance requests**

RAG input: `根据我上传的资料生成 JWT 登录文章。`

Expected route: `load_project_context -> parse_request -> retrieve_knowledge -> generate_content -> format_output`.

No-RAG input: `把这段标题改得更吸引人。`

Expected route: `load_project_context -> parse_request -> generate_content -> format_output`.

- [x] **Step 4: Record the verified implementation status**

Add a concise completion note under Stage 11 with test commands and acceptance route results.
