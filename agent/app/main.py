from fastapi import FastAPI, File, Form, HTTPException, UploadFile

from app.config import create_chroma_client, settings
from app.graph import build_graph
from app.llm import LlmService
from app.retrieval import RetrievalService
from app.schemas import GenerateRequest, GenerateResponse, IndexResponse

app = FastAPI(title="ContentFlow Agent")
retrieval = RetrievalService(client=create_chroma_client(settings))
graph = build_graph(retrieval, LlmService())


@app.get("/health")
def health() -> dict[str, str]:
    try:
        retrieval.collection.count()
    except Exception as exception:
        raise HTTPException(status_code=503, detail="Chroma connection failed") from exception
    return {"status": "ok"}


@app.post("/documents/index", response_model=IndexResponse)
async def index_document(
    projectId: int = Form(...),
    documentId: int = Form(...),
    file: UploadFile = File(...),
) -> IndexResponse:
    chunks = retrieval.index_file(projectId, documentId, file.filename or "document.txt", await file.read())
    return IndexResponse(projectId=projectId, documentId=documentId, chunks=chunks)


@app.delete("/documents/{document_id}")
def delete_document(document_id: int) -> dict[str, str]:
    retrieval.collection.delete(where={"documentId": document_id})
    return {"status": "ok"}


@app.post("/generate", response_model=GenerateResponse)
def generate(request: GenerateRequest) -> GenerateResponse:
    state = graph.invoke({"project_id": request.project_id, "prompt": request.prompt})
    return state["response"]
