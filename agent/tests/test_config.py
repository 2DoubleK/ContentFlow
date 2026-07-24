from app import config


def test_chroma_client_uses_http_mode_when_host_is_configured(monkeypatch, tmp_path):
    captured: dict[str, object] = {}

    def http_client(*, host: str, port: int):
        captured.update(host=host, port=port)
        return "http-client"

    monkeypatch.setattr(config.chromadb, "HttpClient", http_client)
    settings = config.Settings(chroma_host="chroma", chroma_port=8000, chroma_path=str(tmp_path))

    assert config.create_chroma_client(settings) == "http-client"
    assert captured == {"host": "chroma", "port": 8000}


def test_chroma_client_uses_persistent_mode_without_host(monkeypatch, tmp_path):
    captured: dict[str, object] = {}

    def persistent_client(*, path: str):
        captured["path"] = path
        return "persistent-client"

    monkeypatch.setattr(config.chromadb, "PersistentClient", persistent_client)
    settings = config.Settings(chroma_host=None, chroma_path=str(tmp_path))

    assert config.create_chroma_client(settings) == "persistent-client"
    assert captured == {"path": str(tmp_path)}


def test_ollama_defaults_match_verified_local_runtime():
    settings = config.Settings(_env_file=None)

    assert settings.ollama_enabled is True
    assert settings.ollama_base_url == "http://localhost:11434/v1"
    assert settings.ollama_model == "qwen3:1.7b"
    assert settings.ollama_context_length == 8192
