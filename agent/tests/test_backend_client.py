import httpx
import pytest

from app.backend_client import BackendClient


@pytest.mark.asyncio
async def test_backend_client_uses_internal_token_and_expected_routes():
    requests: list[httpx.Request] = []

    async def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        if request.url.path == "/internal/projects/4/context":
            return httpx.Response(200, json={"success": True, "data": {"projectId": 4, "ownerId": 9}})
        if request.url.path == "/internal/projects/4/contents":
            return httpx.Response(200, json={"success": True, "data": {"id": 21}})
        if request.url.path == "/internal/documents/6/download":
            return httpx.Response(200, content=b"document bytes")
        return httpx.Response(200, json={"success": True, "data": None})

    client = BackendClient(
        base_url="http://backend.test",
        internal_token="internal-token",
        transport=httpx.MockTransport(handler),
    )

    assert await client.get_project_context(4, 9) == {"projectId": 4, "ownerId": 9}
    await client.save_document_chunks(6, [{"chunkIndex": 0, "content": "knowledge", "tokenCount": 2, "chromaId": "chunk-0"}])
    await client.update_document_status(6, "READY", chunk_count=1)
    assert await client.download_document(6) == b"document bytes"
    assert await client.save_content_draft({"projectId": 4, "ownerId": 9, "title": "Draft", "summary": "s", "markdown": "# Draft"}) == {"id": 21}

    assert [(request.method, request.url.path) for request in requests] == [
        ("GET", "/internal/projects/4/context"),
        ("POST", "/internal/documents/6/chunks"),
        ("PATCH", "/internal/documents/6/status"),
        ("GET", "/internal/documents/6/download"),
        ("POST", "/internal/projects/4/contents"),
    ]
    assert all(request.headers["X-Internal-Token"] == "internal-token" for request in requests)


@pytest.mark.asyncio
async def test_project_context_rejects_unrelated_user():
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"success": True, "data": {"projectId": 4, "ownerId": 10}})

    client = BackendClient(
        base_url="http://backend.test",
        internal_token="internal-token",
        transport=httpx.MockTransport(handler),
    )

    with pytest.raises(ValueError, match="does not belong"):
        await client.get_project_context(4, 9)


def test_backend_client_rejects_empty_required_configuration():
    with pytest.raises(ValueError, match="BACKEND_BASE_URL"):
        BackendClient(base_url="", internal_token="internal-token")

    with pytest.raises(ValueError, match="INTERNAL_API_TOKEN"):
        BackendClient(base_url="http://backend.test", internal_token="")
