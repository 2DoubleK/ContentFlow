from pydantic import BaseModel, Field


class IndexResponse(BaseModel):
    project_id: int = Field(alias="projectId")
    document_id: int = Field(alias="documentId")
    chunks: int


class DocumentIndexRequest(BaseModel):
    project_id: int = Field(alias="projectId")
    document_id: int = Field(alias="documentId")
    user_id: int = Field(alias="userId")
    file_name: str = Field(alias="fileName")


class GenerateRequest(BaseModel):
    project_id: int = Field(alias="projectId")
    prompt: str


class GenerateResponse(BaseModel):
    title: str
    summary: str
    markdown: str
    references: list[str] = []
