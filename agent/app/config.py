from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    backend_base_url: str = "http://localhost:8080"
    internal_api_token: str = "dev-internal-token"
    chroma_path: str = "./chroma"
    qwen_api_key: str | None = None
    qwen_model: str = "qwen-plus"

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")


settings = Settings()
