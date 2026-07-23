from app.graph import build_graph
from app.llm import LlmService
from app.retrieval import RetrievalService


def test_graph_returns_structured_content(tmp_path):
    retrieval = RetrievalService(str(tmp_path))
    retrieval.index_text(7, 1, "ref.md", "brand tone is concise")
    graph = build_graph(retrieval, LlmService())

    state = graph.invoke({"project_id": 7, "prompt": "Write launch copy"})

    assert state["response"].title == "Write launch copy"
    assert "# Write launch copy" in state["response"].markdown
    assert state["response"].references
