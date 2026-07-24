import httpx

from app.config import settings


class BackendClient:
    def __init__(self, base_url: str | None = None, internal_token: str | None = None,
                 transport: httpx.AsyncBaseTransport | None = None) -> None:
        self.base_url = settings.backend_base_url if base_url is None else base_url
        token = settings.internal_api_token if internal_token is None else internal_token
        if not self.base_url.strip():
            raise ValueError("BACKEND_BASE_URL must not be empty")
        if not token.strip():
            raise ValueError("INTERNAL_API_TOKEN must not be empty")
        self.headers = {"X-Internal-Token": token}
        self.transport = transport

    async def get_project_context(self, project_id: int, user_id: int) -> dict:
        context = await self._request("GET", f"/internal/projects/{project_id}/context")
        if context.get("ownerId") != user_id:
            raise ValueError("project does not belong to the requested user")
        return context

    async def save_document_chunks(self, document_id: int, chunks: list[dict]) -> None:
        await self._request("POST", f"/internal/documents/{document_id}/chunks", json={"chunks": chunks})

    async def update_document_status(self, document_id: int, status: str, chunk_count: int = 0,
                                     error_message: str | None = None) -> None:
        await self._request("PATCH", f"/internal/documents/{document_id}/status", json={
            "status": status,
            "chunkCount": chunk_count,
            "errorMessage": error_message,
        })

    async def save_content_draft(self, payload: dict) -> dict:
        project_id = payload["projectId"]
        return await self._request("POST", f"/internal/projects/{project_id}/contents", json=payload)

    async def _request(self, method: str, path: str, **kwargs) -> dict | None:
        async with httpx.AsyncClient(base_url=self.base_url, headers=self.headers, transport=self.transport) as client:
            response = await client.request(method, path, **kwargs)
            response.raise_for_status()
            payload = response.json()
            if not payload.get("success"):
                raise ValueError(payload.get("message") or "backend internal request failed")
            return payload.get("data")
