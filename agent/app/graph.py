from __future__ import annotations

from typing import Annotated, TypedDict

from langchain_core.messages import AIMessage, AnyMessage, HumanMessage
from langgraph.graph import END, START, StateGraph
from langgraph.graph.message import add_messages

from app.backend_client import BackendClient
from app.llm import LlmService
from app.retrieval import RetrievalService
from app.schemas import GeneratedContent, GenerateResponse, ParsedContentRequest, RetrievedChunk


class ContentFlowState(TypedDict, total=False):
    messages: Annotated[list[AnyMessage], add_messages]
    user_id: int
    project_id: int
    conversation_id: int
    user_request: str
    project_context: dict
    parsed_request: ParsedContentRequest
    retrieved_chunks: list[RetrievedChunk]
    generated_content: GeneratedContent
    response: GenerateResponse
    error_message: str | None


def build_graph(retrieval: RetrievalService, llm: LlmService, backend: BackendClient):
    builder = StateGraph(ContentFlowState)

    async def load_project_context(state: ContentFlowState) -> dict:
        context = await backend.get_project_context(state["project_id"], state["user_id"])
        return {
            "project_context": context,
            "retrieved_chunks": [],
            "error_message": None,
        }

    def parse_request(state: ContentFlowState) -> dict:
        parsed = llm.parse_request(state["user_request"], state["project_context"])
        return {
            "parsed_request": parsed,
            "messages": [HumanMessage(content=state["user_request"])],
        }

    def route_after_parse(state: ContentFlowState) -> str:
        return "retrieve" if state["parsed_request"].need_retrieval else "generate"

    def retrieve_knowledge(state: ContentFlowState) -> dict:
        chunks = retrieval.search_chunks(
            state["project_id"],
            state["parsed_request"].topic,
            limit=5,
        )
        return {"retrieved_chunks": chunks}

    def generate_content(state: ContentFlowState) -> dict:
        generated = llm.generate_content(
            state["user_request"],
            state["project_context"],
            state["parsed_request"],
            state.get("retrieved_chunks", []),
        )
        return {"generated_content": generated}

    def format_output(state: ContentFlowState) -> dict:
        generated = state["generated_content"]
        response = GenerateResponse(
            title=generated.title,
            summary=generated.summary,
            content=generated.content,
            tags=generated.tags,
            references=generated.references,
            markdown=generated.content,
        )
        return {
            "response": response,
            "messages": [AIMessage(content=generated.content)],
        }

    builder.add_node("load_project_context", load_project_context)
    builder.add_node("parse_request", parse_request)
    builder.add_node("retrieve_knowledge", retrieve_knowledge)
    builder.add_node("generate_content", generate_content)
    builder.add_node("format_output", format_output)
    builder.add_edge(START, "load_project_context")
    builder.add_edge("load_project_context", "parse_request")
    builder.add_conditional_edges(
        "parse_request",
        route_after_parse,
        {"retrieve": "retrieve_knowledge", "generate": "generate_content"},
    )
    builder.add_edge("retrieve_knowledge", "generate_content")
    builder.add_edge("generate_content", "format_output")
    builder.add_edge("format_output", END)
    return builder.compile()
