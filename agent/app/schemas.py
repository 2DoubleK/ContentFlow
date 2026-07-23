from pydantic import BaseModel, Field


class IndexResponse(BaseModel):
    project_id: int = Field(alias="projectId")
    document_id: int = Field(alias="documentId")
    chunks: int


class GenerateRequest(BaseModel):
    project_id: int = Field(alias="projectId")
    prompt: str


class GenerateResponse(BaseModel):
    title: str
    summary: str
    markdown: str
    references: list[str] = []
