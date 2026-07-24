import pytest

from app.react_tools import (
    AgentRuntimeContext,
    AgentToolbox,
    ToolExecutionError,
    detect_save_intent,
)
from app.schemas import ReferenceItem, RetrievedChunk


class FakeBackend:
    def __init__(self) -> None:
        self.context_calls: list[tuple[int, int]] = []
        self.saved_payloads: list[dict] = []

    async def get_project_context(self, project_id: int, user_id: int) -> dict:
        self.context_calls.append((project_id, user_id))
        return {"projectId": project_id, "ownerId": user_id, "name": "Java guide"}

    async def save_content_draft(self, payload: dict) -> dict:
        self.saved_payloads.append(payload)
        return {"id": 31, "projectId": payload["projectId"]}


class SpyRetrieval:
    def __init__(self) -> None:
        self.calls: list[tuple[int, str, int]] = []

    def search_chunks(self, project_id: int, query: str, limit: int = 5) -> list[RetrievedChunk]:
        self.calls.append((project_id, query, limit))
        return [RetrievedChunk(
            documentId=3,
            fileName="jwt.md",
            chunkIndex=0,
            content="JWT reference",
            distance=0.1,
        )]


@pytest.mark.asyncio
async def test_context_tool_rejects_model_supplied_runtime_mismatch():
    backend = FakeBackend()
    toolbox = AgentToolbox(AgentRuntimeContext(9, 7, None, False), backend, SpyRetrieval())

    with pytest.raises(ValueError, match="runtime project"):
        await toolbox.get_project_context(user_id=9, project_id=8)

    assert backend.context_calls == []
    assert toolbox.tool_calls == []


@pytest.mark.asyncio
async def test_search_tool_forces_runtime_project_and_records_chunks():
    retrieval = SpyRetrieval()
    toolbox = AgentToolbox(AgentRuntimeContext(9, 7, None, False), FakeBackend(), retrieval)
    await toolbox.get_project_context(user_id=9, project_id=7)

    chunks = toolbox.search_knowledge(project_id=7, query="JWT", top_k=5)

    assert retrieval.calls == [(7, "JWT", 5)]
    assert chunks[0]["documentId"] == 3
    assert toolbox.retrieved_chunks[0].document_id == 3
    assert toolbox.tool_calls == ["get_project_context", "search_knowledge"]


def test_save_tool_is_not_exposed_without_explicit_save_intent():
    toolbox = AgentToolbox(AgentRuntimeContext(9, 7, None, False), FakeBackend(), SpyRetrieval())

    assert [item.name for item in toolbox.langchain_tools()] == [
        "get_project_context",
        "search_knowledge",
    ]


@pytest.mark.asyncio
async def test_save_tool_rejects_reference_not_returned_by_current_search():
    toolbox = AgentToolbox(AgentRuntimeContext(9, 7, None, True), FakeBackend(), SpyRetrieval())
    await toolbox.get_project_context(user_id=9, project_id=7)

    with pytest.raises(ValueError, match="current retrieval"):
        await toolbox.save_content_draft(
            user_id=9,
            project_id=7,
            conversation_id=None,
            title="JWT",
            summary="Summary",
            content="# JWT",
            content_type="ARTICLE",
            tags=["Java"],
            references=[ReferenceItem(documentId=99, fileName="fake.md", chunkIndex=0)],
        )


@pytest.mark.asyncio
async def test_explicit_save_calls_backend_once_with_runtime_ids():
    backend = FakeBackend()
    toolbox = AgentToolbox(AgentRuntimeContext(9, 7, None, True), backend, SpyRetrieval())
    await toolbox.get_project_context(user_id=9, project_id=7)
    toolbox.search_knowledge(project_id=7, query="JWT", top_k=5)

    result = await toolbox.save_content_draft(
        user_id=9,
        project_id=7,
        conversation_id=None,
        title="JWT",
        summary="Summary",
        content="# JWT",
        content_type="ARTICLE",
        tags=["Java"],
        references=[ReferenceItem(documentId=3, fileName="jwt.md", chunkIndex=0)],
    )

    assert result["id"] == 31
    assert toolbox.saved_draft_id == 31
    assert toolbox.tool_calls == ["get_project_context", "search_knowledge", "save_content_draft"]
    assert len(backend.saved_payloads) == 1
    saved_payload = backend.saved_payloads[0]
    assert saved_payload.pop("requestId")
    assert saved_payload == {
        "userId": 9,
        "projectId": 7,
        "conversationId": None,
        "title": "JWT",
        "summary": "Summary",
        "content": "# JWT",
        "contentType": "ARTICLE",
        "tags": ["Java"],
        "references": [{"documentId": 3, "fileName": "jwt.md", "chunkIndex": 0}],
    }


@pytest.mark.asyncio
async def test_repeated_save_tool_call_returns_existing_draft_without_duplicate_write():
    backend = FakeBackend()
    toolbox = AgentToolbox(AgentRuntimeContext(9, 7, None, True), backend, SpyRetrieval())
    await toolbox.get_project_context(user_id=9, project_id=7)
    toolbox.search_knowledge(project_id=7, query="JWT", top_k=5)
    arguments = {
        "user_id": 9,
        "project_id": 7,
        "conversation_id": None,
        "title": "JWT",
        "summary": "Summary",
        "content": "# JWT",
        "content_type": "ARTICLE",
        "tags": ["Java"],
        "references": [ReferenceItem(documentId=3, fileName="jwt.md", chunkIndex=0)],
    }

    first = await toolbox.save_content_draft(**arguments)
    second = await toolbox.save_content_draft(**arguments)

    assert first == second == {"id": 31, "projectId": 7}
    assert len(backend.saved_payloads) == 1
    assert toolbox.tool_calls == ["get_project_context", "search_knowledge", "save_content_draft"]


@pytest.mark.asyncio
async def test_save_tool_requires_context_and_required_retrieval_before_persisting():
    backend = FakeBackend()
    toolbox = AgentToolbox(
        AgentRuntimeContext(9, 7, None, True),
        backend,
        SpyRetrieval(),
        retrieval_required=True,
    )
    arguments = {
        "user_id": 9,
        "project_id": 7,
        "conversation_id": None,
        "title": "JWT",
        "summary": "Summary",
        "content": "# JWT",
    }

    with pytest.raises(ValueError, match="project context"):
        await toolbox.save_content_draft(**arguments)
    await toolbox.get_project_context(user_id=9, project_id=7)
    with pytest.raises(ValueError, match="knowledge"):
        await toolbox.save_content_draft(**arguments)

    assert backend.saved_payloads == []


@pytest.mark.asyncio
async def test_backend_failure_is_classified_as_tool_execution_error():
    class FailingBackend(FakeBackend):
        async def get_project_context(self, project_id: int, user_id: int) -> dict:
            raise PermissionError("forbidden")

    toolbox = AgentToolbox(AgentRuntimeContext(9, 7, None, False), FailingBackend(), SpyRetrieval())

    with pytest.raises(ToolExecutionError, match="get_project_context"):
        await toolbox.get_project_context(user_id=9, project_id=7)


@pytest.mark.parametrize("prompt", [
    "根据资料生成文章并保存为草稿",
    "请保存这篇内容",
    "save as draft",
])
def test_detect_save_intent_requires_explicit_marker(prompt):
    assert detect_save_intent(prompt) is True


def test_detect_save_intent_does_not_save_ordinary_generation():
    assert detect_save_intent("根据项目资料生成文章") is False


@pytest.mark.parametrize("prompt", [
    "生成文章，但不要保存",
    "生成后无需保存",
    "generate this but do not save",
    "请告诉我怎么保存文章",
    "别保存这篇文章",
    "请勿保存",
    "不要存为草稿",
    "never save this",
])
def test_detect_save_intent_respects_explicit_negative_instruction(prompt):
    assert detect_save_intent(prompt) is False
