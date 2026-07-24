from __future__ import annotations

from typing import Annotated, TypedDict

from langchain_core.messages import AIMessage, AnyMessage, HumanMessage
from langgraph.graph import END, START, StateGraph
from langgraph.graph.message import add_messages

from app.backend_client import BackendClient
from app.config import settings
from app.llm import LlmService
from app.model_router import ModelRouter
from app.react_runner import ReActRunner
from app.react_tools import AgentRuntimeContext, detect_save_intent
from app.retrieval import RetrievalService
from app.schemas import GeneratedContent, GenerateResponse, ParsedContentRequest, RetrievedChunk


class ContentFlowState(TypedDict, total=False):
    messages: Annotated[list[AnyMessage], add_messages]
    user_id: int
    project_id: int
    conversation_id: int | None
    request_id: str | None
    user_request: str
    project_context: dict
    parsed_request: ParsedContentRequest
    retrieved_chunks: list[RetrievedChunk]
    generated_content: GeneratedContent
    response: GenerateResponse
    tool_calls: list[str]
    active_model_provider: str
    save_requested: bool
    saved_draft_id: int | None
    error_message: str | None


def build_graph(
    retrieval: RetrievalService,
    llm: LlmService,
    backend: BackendClient,
    model_router: ModelRouter | None = None,
):
    builder = StateGraph(ContentFlowState)
    runner = ReActRunner(model_router or ModelRouter(settings), backend, retrieval, llm)

    async def run_react_agent(state: ContentFlowState) -> dict:
        save_requested = detect_save_intent(state["user_request"])
        result = await runner.run(
            AgentRuntimeContext(
                user_id=state["user_id"],
                project_id=state["project_id"],
                conversation_id=state.get("conversation_id"),
                save_requested=save_requested,
            ),
            state["user_request"],
            save_request_id=state.get("request_id"),
        )
        return {
            "project_context": result.project_context,
            "parsed_request": result.parsed_request,
            "retrieved_chunks": result.retrieved_chunks,
            "generated_content": result.generated_content,
            "tool_calls": result.tool_calls,
            "active_model_provider": result.active_model_provider,
            "save_requested": save_requested,
            "saved_draft_id": result.saved_draft_id,
            "error_message": None,
            "messages": [HumanMessage(content=state["user_request"])],
        }

    def format_output(state: ContentFlowState) -> dict:
        generated = state["generated_content"]
        response = GenerateResponse(
            title=generated.title,
            summary=generated.summary,
            content=generated.content,
            tags=generated.tags,
            references=generated.references,
            markdown=generated.content,
            savedDraftId=state.get("saved_draft_id"),
        )
        return {
            "response": response,
            "messages": [AIMessage(content=generated.content)],
        }

    builder.add_node("run_react_agent", run_react_agent)
    builder.add_node("format_output", format_output)
    builder.add_edge(START, "run_react_agent")
    builder.add_edge("run_react_agent", "format_output")
    builder.add_edge("format_output", END)
    return builder.compile()
