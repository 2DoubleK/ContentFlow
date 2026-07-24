from app.config import create_chroma_client, settings
from app.backend_client import BackendClient
from app.graph import build_graph
from app.llm import LlmService
from app.retrieval import RetrievalService

graph = build_graph(RetrievalService(client=create_chroma_client(settings)), LlmService(), BackendClient())
