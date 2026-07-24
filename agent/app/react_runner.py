from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from typing import Awaitable, Callable
from uuid import uuid4

from langchain_core.messages import HumanMessage, SystemMessage
from langgraph.prebuilt import ToolNode, create_react_agent

from app.backend_client import BackendClient
from app.llm import LlmService
from app.model_router import ModelCandidate, ModelRouter
from app.react_tools import AgentRuntimeContext, AgentToolbox, ToolExecutionError, ToolSecurityError
from app.retrieval import RetrievalService
from app.schemas import GeneratedContent, ParsedContentRequest, RetrievedChunk

logger = logging.getLogger(__name__)

ModelExecutor = Callable[[ModelCandidate, AgentToolbox, str], Awaitable[GeneratedContent]]


@dataclass(frozen=True)
class ReActRunResult:
    generated_content: GeneratedContent
    parsed_request: ParsedContentRequest
    project_context: dict
    retrieved_chunks: list[RetrievedChunk]
    tool_calls: list[str]
    active_model_provider: str
    saved_draft_id: int | None


class ReActRunner:
    def __init__(
        self,
        model_router: ModelRouter,
        backend: BackendClient,
        retrieval: RetrievalService,
        llm: LlmService,
        model_executor: ModelExecutor | None = None,
    ) -> None:
        self.model_router = model_router
        self.backend = backend
        self.retrieval = retrieval
        self.llm = llm
        self.model_executor = model_executor or self._execute_model

    async def run(self, runtime: AgentRuntimeContext, user_request: str) -> ReActRunResult:
        save_request_id = uuid4().hex
        retrieval_required = self.llm.parse_request_fallback(user_request, {}).need_retrieval
        for candidate in self.model_router.candidates():
            toolbox = AgentToolbox(
                runtime,
                self.backend,
                self.retrieval,
                save_request_id=save_request_id,
                retrieval_required=retrieval_required,
            )
            try:
                generated = await self.model_executor(candidate, toolbox, user_request)
                parsed = self._validate_model_result(runtime, user_request, toolbox)
                return self._result(candidate.provider, generated, parsed, toolbox)
            except (ToolExecutionError, ToolSecurityError):
                raise
            except Exception:
                if toolbox.saved_draft_id is not None and toolbox.saved_content is not None:
                    parsed = self._validate_model_result(runtime, user_request, toolbox)
                    return self._result(candidate.provider, toolbox.saved_content, parsed, toolbox)
                logger.warning("model provider failed: %s", candidate.provider, exc_info=True)
        return await self._run_deterministic(runtime, user_request, save_request_id, retrieval_required)

    async def _execute_model(
        self,
        candidate: ModelCandidate,
        toolbox: AgentToolbox,
        user_request: str,
    ) -> GeneratedContent:
        if candidate.provider == "ollama":
            return await self._execute_ollama(candidate, toolbox, user_request)

        prompt = build_react_prompt(candidate.provider, toolbox.runtime)
        agent = create_react_agent(
            candidate.model,
            build_tool_node(toolbox),
            prompt=prompt,
            response_format=response_format_for_provider(candidate.provider),
        )
        state = await agent.ainvoke(
            {"messages": [HumanMessage(content=user_request)]},
            config={"recursion_limit": 12},
        )
        if toolbox.saved_content is not None:
            return toolbox.saved_content
        generated = state.get("structured_response")
        if not isinstance(generated, GeneratedContent):
            generated = GeneratedContent.model_validate(generated)
        return generated

    async def _execute_ollama(
        self,
        candidate: ModelCandidate,
        toolbox: AgentToolbox,
        user_request: str,
    ) -> GeneratedContent:
        project_context = await toolbox.get_project_context(
            user_id=toolbox.runtime.user_id,
            project_id=toolbox.runtime.project_id,
        )
        parsed = self.llm.parse_request_fallback(user_request, project_context)
        if parsed.need_retrieval:
            toolbox.search_knowledge(
                project_id=toolbox.runtime.project_id,
                query=parsed.topic,
                top_k=5,
            )
        response = await candidate.model.ainvoke([
            SystemMessage(content=(
                "/no_think\nGenerate the requested content as complete Markdown. "
                "Follow the project requirements and word limit. Use only the supplied knowledge; "
                "do not add explanations before or after the content."
            )),
            HumanMessage(content=json.dumps({
                "project_context": project_context,
                "user_request": user_request,
                "parsed_request": parsed.model_dump(),
                "retrieved_chunks": [
                    chunk.model_dump(by_alias=True) for chunk in toolbox.retrieved_chunks
                ],
            }, ensure_ascii=False)),
        ])
        markdown = response.content if isinstance(response.content, str) else ""
        generated = generated_from_local_markdown(markdown, toolbox, user_request)
        if toolbox.runtime.save_requested:
            await toolbox.save_content_draft(
                user_id=toolbox.runtime.user_id,
                project_id=toolbox.runtime.project_id,
                conversation_id=toolbox.runtime.conversation_id,
                title=generated.title,
                summary=generated.summary,
                content=generated.content,
                content_type=parsed.content_type,
                tags=generated.tags,
                references=generated.references,
            )
        return generated

    def _validate_model_result(
        self,
        runtime: AgentRuntimeContext,
        user_request: str,
        toolbox: AgentToolbox,
    ) -> ParsedContentRequest:
        if "get_project_context" not in toolbox.tool_calls:
            raise ValueError("model did not load project context")
        parsed = self.llm.parse_request_fallback(user_request, toolbox.project_context)
        if parsed.need_retrieval and "search_knowledge" not in toolbox.tool_calls:
            raise ValueError("model did not search project knowledge")
        if runtime.save_requested and "save_content_draft" not in toolbox.tool_calls:
            raise ValueError("model did not save the requested draft")
        return parsed

    async def _run_deterministic(
        self,
        runtime: AgentRuntimeContext,
        user_request: str,
        save_request_id: str,
        retrieval_required: bool,
    ) -> ReActRunResult:
        toolbox = AgentToolbox(
            runtime,
            self.backend,
            self.retrieval,
            save_request_id=save_request_id,
            retrieval_required=retrieval_required,
        )
        project_context = await toolbox.get_project_context(
            user_id=runtime.user_id,
            project_id=runtime.project_id,
        )
        parsed = self.llm.parse_request_fallback(user_request, project_context)
        if parsed.need_retrieval:
            toolbox.search_knowledge(
                project_id=runtime.project_id,
                query=parsed.topic,
                top_k=5,
            )
        generated = self.llm.generate_content_fallback(
            user_request,
            project_context,
            parsed,
            toolbox.retrieved_chunks,
        )
        if runtime.save_requested:
            await toolbox.save_content_draft(
                user_id=runtime.user_id,
                project_id=runtime.project_id,
                conversation_id=runtime.conversation_id,
                title=generated.title,
                summary=generated.summary,
                content=generated.content,
                content_type=parsed.content_type,
                tags=generated.tags,
                references=generated.references,
            )
        return self._result("deterministic", generated, parsed, toolbox)

    def _result(
        self,
        provider: str,
        generated: GeneratedContent,
        parsed: ParsedContentRequest,
        toolbox: AgentToolbox,
    ) -> ReActRunResult:
        content = toolbox.saved_content or self.llm.sanitize_references(generated, toolbox.retrieved_chunks)
        return ReActRunResult(
            generated_content=content,
            parsed_request=parsed,
            project_context=toolbox.project_context,
            retrieved_chunks=list(toolbox.retrieved_chunks),
            tool_calls=list(toolbox.tool_calls),
            active_model_provider=provider,
            saved_draft_id=toolbox.saved_draft_id,
        )


def build_react_prompt(provider: str, runtime: AgentRuntimeContext) -> str:
    no_think = "/no_think\n" if provider == "ollama" else ""
    conversation_id = "null" if runtime.conversation_id is None else str(runtime.conversation_id)
    return no_think + (
        "You are ContentFlow's content agent. The trusted runtime values are "
        f"user_id={runtime.user_id}, project_id={runtime.project_id}, conversation_id={conversation_id}. "
        "Use exactly these IDs in every tool call. Always call get_project_context first. "
        "Call search_knowledge when the user asks for project material or reference-based content. "
        "Only use references returned by search_knowledge. If save_content_draft is available, "
        "generate the complete content, call it exactly once, then return the same content. "
        "For Ollama, return the final complete article as Markdown without explanations."
    )


def response_format_for_provider(provider: str):
    return None if provider == "ollama" else GeneratedContent


def build_tool_node(toolbox: AgentToolbox) -> ToolNode:
    return ToolNode(toolbox.langchain_tools(), handle_tool_errors=False)


def generated_from_local_markdown(
    markdown: str,
    toolbox: AgentToolbox,
    user_request: str,
) -> GeneratedContent:
    content = markdown.strip()
    if not content:
        raise ValueError("Ollama returned empty content")
    first_line = content.splitlines()[0].strip()
    title = first_line.removeprefix("#").strip() if first_line.startswith("#") else first_line
    references = [
        {
            "documentId": chunk.document_id,
            "fileName": chunk.file_name,
            "chunkIndex": chunk.chunk_index,
        }
        for chunk in toolbox.retrieved_chunks
    ]
    tags = [
        item for item in (
            toolbox.project_context.get("platform"),
            toolbox.project_context.get("domain"),
        ) if item
    ]
    return GeneratedContent(
        title=(title or user_request.strip())[:60],
        summary=content[:160],
        content=content,
        tags=list(dict.fromkeys(tags)),
        references=references,
    )
