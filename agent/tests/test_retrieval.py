from app.retrieval import RetrievalService


def test_retrieval_filters_by_project_id(tmp_path):
    service = RetrievalService(str(tmp_path))
    service.index_text(1, 10, "a.txt", "alpha project guide")
    service.index_text(2, 20, "b.txt", "beta project guide")

    results = service.search(1, "project guide", limit=5)

    assert any("alpha" in item for item in results)
    assert all("beta" not in item for item in results)
