from __future__ import annotations

from io import BytesIO
from pathlib import Path

import chromadb
from chromadb.api.models.Collection import Collection
from pypdf import PdfReader


class RetrievalService:
    def __init__(self, persist_path: str | None = None, client=None) -> None:
        self.client = client or chromadb.PersistentClient(path=persist_path or "./chroma")
        self.collection = self.client.get_or_create_collection("contentflow_knowledge")

    def index_text(self, project_id: int, document_id: int, filename: str, text: str, owner_id: int) -> list[dict]:
        chunks = split_text(text)
        if not chunks:
            return []
        ids = [chunk_id(project_id, document_id, index) for index, _ in enumerate(chunks)]
        metadatas = [
            {
                "project_id": project_id,
                "document_id": document_id,
                "file_name": filename,
                "chunk_index": index,
                "owner_id": owner_id,
            }
            for index, _ in enumerate(chunks)
        ]
        self.collection.upsert(ids=ids, documents=chunks, metadatas=metadatas)
        return [
            {"chunkIndex": index, "content": chunk, "chromaId": ids[index], "tokenCount": len(chunk.split())}
            for index, chunk in enumerate(chunks)
        ]

    def index_file(self, project_id: int, document_id: int, filename: str, data: bytes, owner_id: int) -> list[dict]:
        filename = filename.strip().strip('"')
        suffix = Path(filename).suffix.lower()
        if suffix == ".pdf":
            text = "\n".join(page.extract_text() or "" for page in PdfReader(BytesIO(data)).pages)
        elif suffix in {".txt", ".md", ".markdown"}:
            text = data.decode("utf-8", errors="ignore")
        else:
            raise ValueError(f"unsupported document filename: {filename!r} (extension={suffix!r})")
        return self.index_text(project_id, document_id, filename, text, owner_id)

    def delete_document(self, document_id: int) -> None:
        self.collection.delete(where={"document_id": document_id})

    def search(self, project_id: int, query: str, limit: int = 4) -> list[str]:
        result = self.collection.query(
            query_texts=[query],
            n_results=limit,
            where={"project_id": project_id},
        )
        return result.get("documents", [[]])[0]


def split_text(text: str, chunk_size: int = 800, chunk_overlap: int = 120) -> list[str]:
    normalized = "\n".join(line.strip() for line in text.splitlines() if line.strip())
    if not normalized:
        return []
    step = chunk_size - chunk_overlap
    if step <= 0:
        raise ValueError("chunk_overlap must be smaller than chunk_size")
    return [normalized[i : i + chunk_size] for i in range(0, len(normalized), step)]


def chunk_id(project_id: int, document_id: int, index: int) -> str:
    return f"project_{project_id}_document_{document_id}_chunk_{index}"
