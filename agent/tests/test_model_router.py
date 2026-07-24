from app.config import Settings
from app.model_router import ModelRouter


def test_router_orders_cloud_before_ollama():
    settings = Settings(
        _env_file=None,
        llm_api_key="cloud-key",
        llm_base_url="https://cloud.test/v1",
        llm_model="cloud-model",
        ollama_enabled=True,
        ollama_base_url="http://localhost:11434/v1",
        ollama_model="qwen3:1.7b",
    )

    candidates = ModelRouter(settings).candidates()

    assert [candidate.provider for candidate in candidates] == ["cloud", "ollama"]
    assert candidates[0].model.model_name == "cloud-model"
    assert candidates[1].model.model_name == "qwen3:1.7b"
    assert candidates[1].model.extra_body == {
        "think": False,
        "options": {"num_ctx": 8192},
    }


def test_router_uses_only_ollama_without_cloud_credentials():
    settings = Settings(_env_file=None, llm_api_key=None, qwen_api_key=None, ollama_enabled=True)

    candidates = ModelRouter(settings).candidates()

    assert [candidate.provider for candidate in candidates] == ["ollama"]


def test_router_can_disable_ollama():
    settings = Settings(_env_file=None, llm_api_key=None, qwen_api_key=None, ollama_enabled=False)

    assert ModelRouter(settings).candidates() == []
