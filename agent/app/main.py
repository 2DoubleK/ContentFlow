from fastapi import FastAPI, File, Form, UploadFile

from app.config import settings
from app.graph import build_graph
from app.llm import LlmService
from app.retrieval import RetrievalService
from app.schemas import GenerateRequest, GenerateResponse, IndexResponse

app = FastAPI(title="ContentFlow Agent")
retrieval = RetrievalService(settings.chroma_path)
graph = build_graph(retrieval, LlmService())


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/documents/index", response_model=IndexResponse)
async def index_document(
    projectId: int = Form(...),
    documentId: int = Form(...),
    file: UploadFile = File(...),
) -> IndexResponse:
    chunks = retrieval.index_file(projectId, documentId, file.filename or "document.txt", await file.read())
    return IndexResponse(projectId=projectId, documentId=documentId, chunks=chunks)


@app.post("/generate", response_model=GenerateResponse)
def generate(request: GenerateRequest) -> GenerateResponse:
    state = graph.invoke({"project_id": request.project_id, "prompt": request.prompt})
    return state["response"]
