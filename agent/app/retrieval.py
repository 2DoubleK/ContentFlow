from __future__ import annotations

import hashlib
from io import BytesIO
from pathlib import Path

import chromadb
from chromadb.api.models.Collection import Collection
from pypdf import PdfReader


class RetrievalService:
    def __init__(self, persist_path: str | None = None, client=None) -> None:
        self.client = client or chromadb.PersistentClient(path=persist_path or "./chroma")
        self.collection = self.client.get_or_create_collection("contentflow_documents")

    def index_text(self, project_id: int, document_id: int, filename: str, text: str) -> int:
        chunks = split_text(text)
        if not chunks:
            return 0
        ids = [chunk_id(project_id, document_id, index, chunk) for index, chunk in enumerate(chunks)]
        metadatas = [
            {"projectId": project_id, "documentId": document_id, "filename": filename}
            for _ in chunks
        ]
        self.collection.upsert(ids=ids, documents=chunks, metadatas=metadatas)
        return len(chunks)

    def index_file(self, project_id: int, document_id: int, filename: str, data: bytes) -> int:
        suffix = Path(filename).suffix.lower()
        if suffix == ".pdf":
            text = "\n".join(page.extract_text() or "" for page in PdfReader(BytesIO(data)).pages)
        elif suffix in {".txt", ".md", ".markdown"}:
            text = data.decode("utf-8", errors="ignore")
        else:
            raise ValueError("only pdf, txt and md files are supported")
        return self.index_text(project_id, document_id, filename, text)

    def search(self, project_id: int, query: str, limit: int = 4) -> list[str]:
        result = self.collection.query(
            query_texts=[query],
            n_results=limit,
            where={"projectId": project_id},
        )
        return result.get("documents", [[]])[0]


def split_text(text: str, chunk_size: int = 800) -> list[str]:
    normalized = "\n".join(line.strip() for line in text.splitlines() if line.strip())
    return [normalized[i : i + chunk_size] for i in range(0, len(normalized), chunk_size)]


def chunk_id(project_id: int, document_id: int, index: int, text: str) -> str:
    digest = hashlib.sha1(text.encode("utf-8")).hexdigest()[:12]
    return f"{project_id}:{document_id}:{index}:{digest}"
