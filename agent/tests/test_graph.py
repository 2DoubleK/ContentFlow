import pytest

from app.graph import build_graph
from app.llm import LlmService
from app.config import Settings
from app.model_router import ModelRouter
from app.schemas import RetrievedChunk


class FakeBackendClient:
    def __init__(self, owner_id: int = 9) -> None:
        self.owner_id = owner_id
        self.calls: list[tuple[int, int]] = []
        self.saved_payloads: list[dict] = []

    async def get_project_context(self, project_id: int, user_id: int) -> dict:
        self.calls.append((project_id, user_id))
        if user_id != self.owner_id:
            raise ValueError("project does not belong to the requested user")
        return {
            "projectId": project_id,
            "ownerId": self.owner_id,
            "name": "Java growth guide",
            "platform": "Blog",
            "domain": "Java learning",
            "positioning": "Practical tutorials",
            "targetAudience": "Java beginners",
            "contentStyle": "Clear and example-driven",
        }

    async def save_content_draft(self, payload: dict) -> dict:
        self.saved_payloads.append(payload)
        return {"id": 41, "projectId": payload["projectId"]}


class SpyRetrievalService:
    def __init__(self) -> None:
        self.queries: list[tuple[int, str, int]] = []

    def search_chunks(self, project_id: int, query: str, limit: int = 5) -> list[RetrievedChunk]:
        self.queries.append((project_id, query, limit))
        return [
            RetrievedChunk(
                documentId=3,
                fileName="jwt-guide.md",
                chunkIndex=2,
                content="JWT login uses a signed access token.",
                distance=0.12,
            )
        ]


def build_test_graph(retrieval, backend):
    router = ModelRouter(Settings(_env_file=None, llm_api_key=None, qwen_api_key=None, ollama_enabled=False))
    return build_graph(retrieval, LlmService(), backend, model_router=router)


@pytest.mark.asyncio
async def test_graph_uses_project_scoped_retrieval_for_knowledge_request():
    backend = FakeBackendClient()
    retrieval = SpyRetrievalService()
    graph = build_test_graph(retrieval, backend)

    state = await graph.ainvoke({
        "user_id": 9,
        "project_id": 7,
        "conversation_id": 12,
        "user_request": "根据我上传的资料生成 JWT 登录文章。",
    })

    assert backend.calls == [(7, 9)]
    assert retrieval.queries
    assert retrieval.queries[0][0] == 7
    assert "JWT" in retrieval.queries[0][1]
    assert state["parsed_request"].need_retrieval is True
    assert state["retrieved_chunks"][0].document_id == 3
    assert state["response"].references[0].document_id == 3
    assert "JWT login uses a signed access token." in state["response"].content
    assert state["response"].markdown == state["response"].content
    assert state["tool_calls"] == ["get_project_context", "search_knowledge"]
    assert state["active_model_provider"] == "deterministic"
    assert state["response"].saved_draft_id is None
    assert state["error_message"] is None


@pytest.mark.asyncio
async def test_graph_skips_retrieval_for_rewrite_request():
    backend = FakeBackendClient()
    retrieval = SpyRetrievalService()
    graph = build_test_graph(retrieval, backend)

    state = await graph.ainvoke({
        "user_id": 9,
        "project_id": 7,
        "user_request": "把这段标题改得更吸引人。",
    })

    assert retrieval.queries == []
    assert state["parsed_request"].need_retrieval is False
    assert state["retrieved_chunks"] == []
    assert state["response"].references == []


@pytest.mark.asyncio
async def test_graph_saves_only_when_request_explicitly_asks_for_draft():
    backend = FakeBackendClient()
    graph = build_test_graph(SpyRetrievalService(), backend)

    state = await graph.ainvoke({
        "user_id": 9,
        "project_id": 7,
        "user_request": "根据资料生成文章并保存为草稿",
    })

    assert state["tool_calls"] == [
        "get_project_context",
        "search_knowledge",
        "save_content_draft",
    ]
    assert state["saved_draft_id"] == 41
    assert state["response"].saved_draft_id == 41
    assert len(backend.saved_payloads) == 1


@pytest.mark.asyncio
async def test_graph_rejects_project_owned_by_another_user():
    graph = build_test_graph(SpyRetrievalService(), FakeBackendClient(owner_id=9))

    with pytest.raises(ValueError, match="does not belong"):
        await graph.ainvoke({
            "user_id": 10,
            "project_id": 7,
            "user_request": "Write an article",
        })
