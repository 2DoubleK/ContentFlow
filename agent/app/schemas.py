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
    user_id: int = Field(alias="userId")
    project_id: int = Field(alias="projectId")
    conversation_id: int | None = Field(default=None, alias="conversationId")
    prompt: str


class ParsedContentRequest(BaseModel):
    platform: str | None = None
    content_type: str = "ARTICLE"
    topic: str
    target_audience: str | None = None
    content_style: str | None = None
    word_limit: int | None = None
    need_retrieval: bool = True


class RetrievedChunk(BaseModel):
    document_id: int = Field(alias="documentId")
    file_name: str = Field(alias="fileName")
    chunk_index: int = Field(alias="chunkIndex")
    content: str
    distance: float | None = None


class ReferenceItem(BaseModel):
    document_id: int = Field(alias="documentId")
    file_name: str = Field(alias="fileName")
    chunk_index: int = Field(alias="chunkIndex")


class GeneratedContent(BaseModel):
    title: str
    summary: str
    content: str
    tags: list[str] = Field(default_factory=list)
    references: list[ReferenceItem] = Field(default_factory=list)


class GenerateResponse(GeneratedContent):
    markdown: str
    saved_draft_id: int | None = Field(default=None, alias="savedDraftId")
