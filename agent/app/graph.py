from typing import TypedDict

from langgraph.graph import END, START, StateGraph

from app.llm import LlmService
from app.retrieval import RetrievalService
from app.schemas import GenerateResponse


class ContentState(TypedDict):
    project_id: int
    prompt: str
    query: str
    contexts: list[str]
    generated: dict[str, str]
    response: GenerateResponse


def build_graph(retrieval: RetrievalService, llm: LlmService):
    graph = StateGraph(ContentState)

    def parse_request(state: ContentState) -> ContentState:
        state["query"] = state["prompt"].strip()
        return state

    def retrieve_context(state: ContentState) -> ContentState:
        state["contexts"] = retrieval.search(state["project_id"], state["query"])
        return state

    def generate_content(state: ContentState) -> ContentState:
        state["generated"] = llm.generate(state["prompt"], state["contexts"])
        return state

    def format_output(state: ContentState) -> ContentState:
        state["response"] = GenerateResponse(
            title=state["generated"]["title"],
            summary=state["generated"]["summary"],
            markdown=state["generated"]["markdown"],
            references=state["contexts"],
        )
        return state

    graph.add_node("parse_request", parse_request)
    graph.add_node("retrieve_context", retrieve_context)
    graph.add_node("generate_content", generate_content)
    graph.add_node("format_output", format_output)
    graph.add_edge(START, "parse_request")
    graph.add_edge("parse_request", "retrieve_context")
    graph.add_edge("retrieve_context", "generate_content")
    graph.add_edge("generate_content", "format_output")
    graph.add_edge("format_output", END)
    return graph.compile()
