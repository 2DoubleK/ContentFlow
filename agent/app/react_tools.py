from __future__ import annotations

from dataclasses import dataclass
from uuid import uuid4

import httpx
from langchain_core.tools import BaseTool, StructuredTool

from app.backend_client import BackendClient
from app.retrieval import RetrievalService
from app.schemas import GeneratedContent, ReferenceItem, RetrievedChunk


class ToolSecurityError(ValueError):
    pass


class ToolExecutionError(ValueError):
    pass


@dataclass(frozen=True)
class AgentRuntimeContext:
    user_id: int
    project_id: int
    conversation_id: int | None
    save_requested: bool


class AgentToolbox:
    def __init__(
        self,
        runtime: AgentRuntimeContext,
        backend: BackendClient,
        retrieval: RetrievalService,
        save_request_id: str | None = None,
        retrieval_required: bool = False,
    ) -> None:
        self.runtime = runtime
        self.backend = backend
        self.retrieval = retrieval
        self.save_request_id = save_request_id or uuid4().hex
        self.retrieval_required = retrieval_required
        self.tool_calls: list[str] = []
        self.retrieved_chunks: list[RetrievedChunk] = []
        self.project_context: dict = {}
        self.saved_draft_id: int | None = None
        self.saved_content: GeneratedContent | None = None
        self.saved_result: dict | None = None

    async def get_project_context(self, user_id: int, project_id: int) -> dict:
        self._validate_ids(user_id, project_id)
        try:
            context = await self.backend.get_project_context(project_id, user_id)
        except Exception as exception:
            raise ToolExecutionError(f"get_project_context failed: {exception}") from exception
        self.project_context = context
        self.tool_calls.append("get_project_context")
        return context

    def search_knowledge(self, project_id: int, query: str, top_k: int = 5) -> list[dict]:
        if project_id != self.runtime.project_id:
            raise ToolSecurityError("model project_id does not match runtime project")
        if "get_project_context" not in self.tool_calls:
            raise ToolSecurityError("project context must be loaded before knowledge search")
        limit = max(1, min(top_k, 10))
        try:
            chunks = self.retrieval.search_chunks(self.runtime.project_id, query, limit=limit)
        except Exception as exception:
            raise ToolExecutionError(f"search_knowledge failed: {exception}") from exception
        self.retrieved_chunks = chunks
        self.tool_calls.append("search_knowledge")
        return [chunk.model_dump(by_alias=True) for chunk in chunks]

    async def save_content_draft(
        self,
        user_id: int,
        project_id: int,
        conversation_id: int | None,
        title: str,
        summary: str,
        content: str,
        content_type: str = "ARTICLE",
        tags: list[str] | None = None,
        references: list[ReferenceItem] | None = None,
    ) -> dict:
        if not self.runtime.save_requested:
            raise ToolSecurityError("draft save was not requested by the user")
        if self.saved_result is not None:
            return self.saved_result
        self._validate_ids(user_id, project_id)
        if "get_project_context" not in self.tool_calls:
            raise ToolSecurityError("project context must be loaded before saving a draft")
        if self.retrieval_required and "search_knowledge" not in self.tool_calls:
            raise ToolSecurityError("project knowledge must be searched before saving this draft")
        if conversation_id != self.runtime.conversation_id:
            raise ToolSecurityError("model conversation_id does not match runtime conversation")
        normalized_references = [
            item if isinstance(item, ReferenceItem) else ReferenceItem.model_validate(item)
            for item in (references or [])
        ]
        allowed = {
            (chunk.document_id, chunk.file_name, chunk.chunk_index)
            for chunk in self.retrieved_chunks
        }
        if any(
            (item.document_id, item.file_name, item.chunk_index) not in allowed
            for item in normalized_references
        ):
            raise ToolSecurityError("draft reference was not returned by the current retrieval")
        payload = {
            "requestId": self.save_request_id,
            "userId": self.runtime.user_id,
            "projectId": self.runtime.project_id,
            "conversationId": self.runtime.conversation_id,
            "title": title,
            "summary": summary,
            "content": content,
            "contentType": content_type,
            "tags": tags or [],
            "references": [item.model_dump(by_alias=True) for item in normalized_references],
        }
        try:
            result = await self.backend.save_content_draft(payload)
        except Exception as exception:
            if not _is_retryable_save_error(exception):
                raise ToolExecutionError(f"save_content_draft failed: {exception}") from exception
            try:
                result = await self.backend.save_content_draft(payload)
            except Exception as retry_exception:
                raise ToolExecutionError(f"save_content_draft failed after retry: {retry_exception}") from retry_exception
        self.saved_result = result
        self.saved_draft_id = int(result["id"])
        self.saved_content = GeneratedContent(
            title=title,
            summary=summary,
            content=content,
            tags=tags or [],
            references=normalized_references,
        )
        self.tool_calls.append("save_content_draft")
        return result

    def langchain_tools(self) -> list[BaseTool]:
        tools: list[BaseTool] = [
            StructuredTool.from_function(
                coroutine=self.get_project_context,
                name="get_project_context",
                description="Load the current project context after validating the user and project IDs.",
            ),
            StructuredTool.from_function(
                func=self.search_knowledge,
                name="search_knowledge",
                description="Search knowledge for the current project using a project-scoped Chroma query.",
            ),
        ]
        if self.runtime.save_requested:
            tools.append(StructuredTool.from_function(
                coroutine=self.save_content_draft,
                name="save_content_draft",
                description="Save generated content as a draft when the user explicitly requested saving.",
            ))
        return tools

    def _validate_ids(self, user_id: int, project_id: int) -> None:
        if user_id != self.runtime.user_id:
            raise ToolSecurityError("model user_id does not match runtime user")
        if project_id != self.runtime.project_id:
            raise ToolSecurityError("model project_id does not match runtime project")


def detect_save_intent(user_request: str) -> bool:
    lowered = user_request.lower()
    if any(marker in lowered for marker in (
        "不要保存",
        "无需保存",
        "不用保存",
        "不保存",
        "别保存",
        "请勿保存",
        "不要存为草稿",
        "无需存为草稿",
        "别存为草稿",
        "do not save",
        "don't save",
        "never save",
        "怎么保存",
        "如何保存",
        "怎样保存",
        "how to save",
    )):
        return False
    return any(marker in lowered for marker in (
        "保存",
        "存为草稿",
        "save as draft",
        "save this",
    ))


def _is_retryable_save_error(exception: Exception) -> bool:
    if isinstance(exception, (ConnectionError, TimeoutError, httpx.TransportError)):
        return True
    return isinstance(exception, httpx.HTTPStatusError) and exception.response.status_code >= 500
