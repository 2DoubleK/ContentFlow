import chromadb
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    backend_base_url: str = "http://localhost:8080"
    internal_api_token: str = "dev-internal-token"
    chroma_path: str = "./chroma"
    chroma_host: str | None = None
    chroma_port: int = 8000
    llm_api_key: str | None = None
    llm_base_url: str | None = None
    llm_model: str = "qwen-plus"
    embedding_api_key: str | None = None
    embedding_base_url: str | None = None
    embedding_model: str | None = None
    qwen_api_key: str | None = None
    qwen_model: str = "qwen-plus"
    ollama_enabled: bool = True
    ollama_base_url: str = "http://localhost:11434/v1"
    ollama_api_key: str = "ollama"
    ollama_model: str = "qwen3:1.7b"
    ollama_context_length: int = 8192
    llm_timeout_seconds: float = 60.0

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")


settings = Settings()


def create_chroma_client(config: Settings):
    if config.chroma_host:
        return chromadb.HttpClient(host=config.chroma_host, port=config.chroma_port)
    return chromadb.PersistentClient(path=config.chroma_path)
