import logging

from fastapi import FastAPI, HTTPException, Request

from app.backend_client import BackendClient
from app.config import create_chroma_client, settings
from app.graph import build_graph
from app.llm import LlmService
from app.retrieval import RetrievalService
from app.schemas import DocumentIndexRequest, GenerateRequest, GenerateResponse, IndexResponse

app = FastAPI(title="ContentFlow Agent")
logger = logging.getLogger(__name__)
retrieval = RetrievalService(client=create_chroma_client(settings))
graph = build_graph(retrieval, LlmService(), BackendClient())


@app.get("/health")
def health() -> dict[str, str]:
    try:
        retrieval.collection.count()
    except Exception as exception:
        raise HTTPException(status_code=503, detail="Chroma connection failed") from exception
    return {"status": "ok"}


@app.post("/documents/index", response_model=IndexResponse)
async def index_document(
    request: Request,
) -> IndexResponse:
    try:
        payload = await request.json()
        index_request = DocumentIndexRequest.model_validate(payload)
    except Exception as exception:
        raise HTTPException(status_code=422, detail=f"invalid document index request: {exception}") from exception

    backend = BackendClient()
    try:
        data = await backend.download_document(index_request.document_id)
        retrieval.delete_document(index_request.document_id)
        chunks = retrieval.index_file(index_request.project_id, index_request.document_id, index_request.file_name, data, index_request.user_id)
        if not chunks:
            raise ValueError("document does not contain extractable text")
        await backend.save_document_chunks(index_request.document_id, chunks)
        await backend.update_document_status(index_request.document_id, "READY", chunk_count=len(chunks))
        return IndexResponse(projectId=index_request.project_id, documentId=index_request.document_id, chunks=len(chunks))
    except Exception as exception:
        logger.exception("document indexing failed: document_id=%s", index_request.document_id)
        await backend.update_document_status(index_request.document_id, "FAILED", error_message=str(exception))
        raise HTTPException(status_code=422, detail="document indexing failed") from exception


@app.delete("/documents/{document_id}")
def delete_document(document_id: int) -> dict[str, str]:
    retrieval.delete_document(document_id)
    return {"status": "ok"}


@app.post("/generate", response_model=GenerateResponse)
async def generate(request: GenerateRequest) -> GenerateResponse:
    state = await graph.ainvoke({
        "user_id": request.user_id,
        "project_id": request.project_id,
        "conversation_id": request.conversation_id,
        "user_request": request.prompt,
    })
    return state["response"]
