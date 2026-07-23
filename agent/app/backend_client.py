import httpx

from app.config import settings


class BackendClient:
    def __init__(self) -> None:
        self.headers = {"X-Internal-Token": settings.internal_api_token}

    async def get_project_context(self, project_id: int) -> dict:
        async with httpx.AsyncClient(base_url=settings.backend_base_url, headers=self.headers) as client:
            response = await client.get(f"/internal/projects/{project_id}/context")
            response.raise_for_status()
            return response.json()["data"]
