from dataclasses import dataclass

from langchain_core.language_models.chat_models import BaseChatModel
from langchain_openai import ChatOpenAI

from app.config import Settings


@dataclass(frozen=True)
class ModelCandidate:
    provider: str
    model: BaseChatModel


class ModelRouter:
    def __init__(self, config: Settings) -> None:
        self.config = config

    def candidates(self) -> list[ModelCandidate]:
        candidates: list[ModelCandidate] = []
        cloud_key = self.config.llm_api_key or self.config.qwen_api_key
        if cloud_key:
            uses_qwen = not self.config.llm_api_key and bool(self.config.qwen_api_key)
            candidates.append(ModelCandidate(
                provider="cloud",
                model=ChatOpenAI(
                    api_key=cloud_key,
                    base_url=self.config.llm_base_url or (
                        "https://dashscope.aliyuncs.com/compatible-mode/v1" if uses_qwen else None
                    ),
                    model=self.config.qwen_model if uses_qwen else self.config.llm_model,
                    temperature=0.2,
                    timeout=self.config.llm_timeout_seconds,
                    max_retries=0,
                ),
            ))
        if self.config.ollama_enabled:
            candidates.append(ModelCandidate(
                provider="ollama",
                model=ChatOpenAI(
                    api_key=self.config.ollama_api_key,
                    base_url=self.config.ollama_base_url,
                    model=self.config.ollama_model,
                    temperature=0.2,
                    timeout=self.config.llm_timeout_seconds,
                    max_retries=0,
                    extra_body={
                        "think": False,
                        "options": {"num_ctx": self.config.ollama_context_length},
                    },
                ),
            ))
        return candidates
