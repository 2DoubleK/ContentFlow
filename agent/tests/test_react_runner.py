import pytest
from langchain_core.messages import AIMessage

from app import react_runner
from app.llm import LlmService
from app.model_router import ModelCandidate
from app.react_runner import ReActRunner
from app.react_tools import AgentRuntimeContext, AgentToolbox, ToolExecutionError
from app.schemas import GeneratedContent, ReferenceItem, RetrievedChunk


class StubRouter:
    def __init__(self, *providers: str) -> None:
        self.providers = providers

    def candidates(self):
        return [ModelCandidate(provider, object()) for provider in self.providers]


class CandidateRouter:
    def __init__(self, *candidates: ModelCandidate) -> None:
        self._candidates = candidates

    def candidates(self):
        return list(self._candidates)


class FakeLocalModel:
    def __init__(self, markdown: str = "# JWT guide\n\nLocal article") -> None:
        self.markdown = markdown
        self.messages = []

    async def ainvoke(self, messages):
        self.messages = messages
        return AIMessage(content=self.markdown)


class FakeBackend:
    def __init__(self) -> None:
        self.saved: list[dict] = []

    async def get_project_context(self, project_id: int, user_id: int) -> dict:
        return {
            "projectId": project_id,
            "ownerId": user_id,
            "name": "Java guide",
            "platform": "Blog",
            "targetAudience": "Java beginners",
            "contentStyle": "Practical",
        }

    async def save_content_draft(self, payload: dict) -> dict:
        self.saved.append(payload)
        return {"id": 31, "projectId": payload["projectId"]}


class SpyRetrieval:
    def __init__(self) -> None:
        self.calls: list[tuple[int, str, int]] = []

    def search_chunks(self, project_id: int, query: str, limit: int = 5):
        self.calls.append((project_id, query, limit))
        return [RetrievedChunk(
            documentId=3,
            fileName="jwt.md",
            chunkIndex=0,
            content="JWT reference",
            distance=0.1,
        )]


def generated_content() -> GeneratedContent:
    return GeneratedContent(
        title="JWT guide",
        summary="Summary",
        content="# JWT guide",
        tags=["Java"],
        references=[ReferenceItem(documentId=3, fileName="jwt.md", chunkIndex=0)],
    )


def test_react_prompt_contains_runtime_ids_and_local_no_think_instruction():
    prompt = react_runner.build_react_prompt("ollama", AgentRuntimeContext(14, 7, None, False))

    assert prompt.startswith("/no_think")
    assert "user_id=14" in prompt
    assert "project_id=7" in prompt
    assert "conversation_id=null" in prompt


def test_ollama_does_not_request_unsupported_openai_structured_output():
    assert react_runner.response_format_for_provider("cloud") is GeneratedContent
    assert react_runner.response_format_for_provider("ollama") is None


def test_cloud_tool_node_propagates_security_and_backend_errors():
    toolbox = AgentToolbox(AgentRuntimeContext(9, 7, None, False), FakeBackend(), SpyRetrieval())

    node = react_runner.build_tool_node(toolbox)

    assert node._handle_tool_errors is False


@pytest.mark.parametrize("prompt", [
    "根据项目资料润色文章并保存",
    "结合知识库优化内容并保存",
])
def test_explicit_project_knowledge_request_overrides_rewrite_shortcut(prompt):
    parsed = LlmService().parse_request_fallback(prompt, {})

    assert parsed.need_retrieval is True


@pytest.mark.asyncio
async def test_local_markdown_is_converted_with_current_retrieval_references():
    toolbox = AgentToolbox(AgentRuntimeContext(9, 7, None, False), FakeBackend(), SpyRetrieval())
    await toolbox.get_project_context(user_id=9, project_id=7)
    toolbox.search_knowledge(project_id=7, query="JWT", top_k=5)
    toolbox.project_context = {"platform": "Blog", "domain": "Java"}

    generated = react_runner.generated_from_local_markdown("# JWT guide\n\nLocal article", toolbox, "JWT")

    assert generated.title == "JWT guide"
    assert generated.content == "# JWT guide\n\nLocal article"
    assert generated.references[0].document_id == 3
    assert generated.tags == ["Blog", "Java"]


@pytest.mark.asyncio
async def test_ollama_path_orchestrates_required_tools_before_local_generation():
    model = FakeLocalModel()
    retrieval = SpyRetrieval()
    runner = ReActRunner(
        CandidateRouter(ModelCandidate("ollama", model)),
        FakeBackend(),
        retrieval,
        LlmService(),
    )

    result = await runner.run(
        AgentRuntimeContext(9, 7, None, False),
        "根据项目资料生成 JWT 文章",
    )

    assert result.active_model_provider == "ollama"
    assert result.tool_calls == ["get_project_context", "search_knowledge"]
    assert result.generated_content.content == "# JWT guide\n\nLocal article"
    assert result.generated_content.references[0].document_id == 3
    assert retrieval.calls == [(7, "根据项目资料生成 JWT 文章", 5)]
    assert "JWT reference" in model.messages[-1].content


@pytest.mark.asyncio
async def test_ollama_explicit_save_calls_all_tools_and_backend_once():
    model = FakeLocalModel()
    backend = FakeBackend()
    runner = ReActRunner(
        CandidateRouter(ModelCandidate("ollama", model)),
        backend,
        SpyRetrieval(),
        LlmService(),
    )

    result = await runner.run(
        AgentRuntimeContext(9, 7, None, True),
        "根据资料生成文章并保存为草稿",
    )

    assert result.active_model_provider == "ollama"
    assert result.tool_calls == [
        "get_project_context",
        "search_knowledge",
        "save_content_draft",
    ]
    assert result.saved_draft_id == 31
    assert len(backend.saved) == 1
    assert backend.saved[0]["content"] == "# JWT guide\n\nLocal article"


@pytest.mark.asyncio
async def test_runner_falls_back_from_cloud_to_ollama():
    providers: list[str] = []

    async def executor(candidate, toolbox, user_request):
        providers.append(candidate.provider)
        if candidate.provider == "cloud":
            raise ConnectionError("cloud unavailable")
        await toolbox.get_project_context(user_id=9, project_id=7)
        toolbox.search_knowledge(project_id=7, query="JWT", top_k=5)
        return generated_content()

    runner = ReActRunner(
        StubRouter("cloud", "ollama"), FakeBackend(), SpyRetrieval(), LlmService(), model_executor=executor
    )

    result = await runner.run(
        AgentRuntimeContext(9, 7, None, False),
        "根据项目资料生成 JWT 文章",
    )

    assert providers == ["cloud", "ollama"]
    assert result.active_model_provider == "ollama"
    assert result.tool_calls == ["get_project_context", "search_knowledge"]


@pytest.mark.asyncio
async def test_runner_uses_deterministic_fallback_when_all_models_fail():
    async def failing_executor(candidate, toolbox, user_request):
        raise ConnectionError(f"{candidate.provider} unavailable")

    runner = ReActRunner(
        StubRouter("cloud", "ollama"), FakeBackend(), SpyRetrieval(), LlmService(), model_executor=failing_executor
    )

    result = await runner.run(
        AgentRuntimeContext(9, 7, None, False),
        "根据项目资料生成 JWT 文章",
    )

    assert result.active_model_provider == "deterministic"
    assert result.tool_calls == ["get_project_context", "search_knowledge"]
    assert result.generated_content.references[0].document_id == 3


@pytest.mark.asyncio
async def test_deterministic_save_calls_all_three_tools_once():
    backend = FakeBackend()
    runner = ReActRunner(StubRouter(), backend, SpyRetrieval(), LlmService())

    result = await runner.run(
        AgentRuntimeContext(9, 7, None, True),
        "根据资料生成文章并保存为草稿",
    )

    assert result.tool_calls == [
        "get_project_context",
        "search_knowledge",
        "save_content_draft",
    ]
    assert result.saved_draft_id == 31
    assert len(backend.saved) == 1


@pytest.mark.asyncio
async def test_deterministic_ordinary_generation_does_not_save():
    backend = FakeBackend()
    runner = ReActRunner(StubRouter(), backend, SpyRetrieval(), LlmService())

    result = await runner.run(
        AgentRuntimeContext(9, 7, None, False),
        "根据项目资料生成文章",
    )

    assert "save_content_draft" not in result.tool_calls
    assert result.saved_draft_id is None
    assert backend.saved == []


@pytest.mark.asyncio
async def test_runner_does_not_retry_provider_after_draft_was_saved():
    backend = FakeBackend()
    providers: list[str] = []

    async def executor(candidate, toolbox, user_request):
        providers.append(candidate.provider)
        await toolbox.get_project_context(user_id=9, project_id=7)
        toolbox.search_knowledge(project_id=7, query="JWT", top_k=5)
        generated = generated_content()
        await toolbox.save_content_draft(
            user_id=9,
            project_id=7,
            conversation_id=None,
            title=generated.title,
            summary=generated.summary,
            content=generated.content,
            tags=generated.tags,
            references=generated.references,
        )
        raise ConnectionError("final response formatting failed after save")

    runner = ReActRunner(
        StubRouter("cloud", "ollama"), backend, SpyRetrieval(), LlmService(), model_executor=executor
    )

    result = await runner.run(
        AgentRuntimeContext(9, 7, None, True),
        "根据资料生成文章并保存为草稿",
    )

    assert providers == ["cloud"]
    assert len(backend.saved) == 1
    assert result.saved_draft_id == 31


@pytest.mark.asyncio
async def test_runner_does_not_fallback_on_tool_execution_failure():
    providers: list[str] = []

    async def executor(candidate, toolbox, user_request):
        providers.append(candidate.provider)
        raise ToolExecutionError("backend authorization failed")

    runner = ReActRunner(
        StubRouter("cloud", "ollama"), FakeBackend(), SpyRetrieval(), LlmService(), model_executor=executor
    )

    with pytest.raises(ToolExecutionError, match="authorization"):
        await runner.run(AgentRuntimeContext(9, 7, None, False), "根据资料生成文章")

    assert providers == ["cloud"]


@pytest.mark.asyncio
async def test_save_request_id_is_stable_across_provider_retry_after_lost_response():
    class IdempotentBackend(FakeBackend):
        def __init__(self) -> None:
            super().__init__()
            self.persisted: dict[str, dict] = {}
            self.response_lost = False
            self.attempts: list[str] = []

        async def save_content_draft(self, payload: dict) -> dict:
            request_id = payload["requestId"]
            self.attempts.append(request_id)
            result = self.persisted.setdefault(request_id, {"id": 31, "projectId": payload["projectId"]})
            if not self.response_lost:
                self.response_lost = True
                raise ConnectionError("response lost after commit")
            return result

    backend = IdempotentBackend()
    request_ids: list[str] = []

    async def executor(candidate, toolbox, user_request):
        request_ids.append(toolbox.save_request_id)
        await toolbox.get_project_context(user_id=9, project_id=7)
        toolbox.search_knowledge(project_id=7, query="JWT", top_k=5)
        generated = generated_content()
        await toolbox.save_content_draft(
            user_id=9,
            project_id=7,
            conversation_id=None,
            title=generated.title,
            summary=generated.summary,
            content=generated.content,
            tags=generated.tags,
            references=generated.references,
        )
        return generated

    runner = ReActRunner(
        StubRouter("cloud", "ollama"), backend, SpyRetrieval(), LlmService(), model_executor=executor
    )

    result = await runner.run(
        AgentRuntimeContext(9, 7, None, True),
        "根据资料生成文章并保存为草稿",
    )

    assert result.active_model_provider == "cloud"
    assert result.saved_draft_id == 31
    assert len(backend.persisted) == 1
    assert len(request_ids) == 1
    assert backend.attempts == [request_ids[0], request_ids[0]]


@pytest.mark.asyncio
async def test_save_request_id_uses_trusted_request_id_from_backend():
    observed: list[str] = []

    async def executor(candidate, toolbox, user_request):
        observed.append(toolbox.save_request_id)
        await toolbox.get_project_context(user_id=9, project_id=7)
        return generated_content()

    runner = ReActRunner(
        StubRouter("cloud"), FakeBackend(), SpyRetrieval(), LlmService(), model_executor=executor
    )

    await runner.run(AgentRuntimeContext(9, 7, 4, False), "generate", save_request_id="request-1")

    assert observed == ["request-1"]
