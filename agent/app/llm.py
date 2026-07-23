from app.config import settings


class LlmService:
    def generate(self, prompt: str, contexts: list[str]) -> dict[str, str]:
        joined = "\n\n".join(contexts).strip()
        title = prompt.strip().splitlines()[0][:60] or "Untitled"
        if settings.qwen_api_key:
            return {
                "title": title,
                "summary": "Generated with configured model.",
                "markdown": f"# {title}\n\n{joined}\n\n{prompt}",
            }
        source = joined or "No project reference content was available."
        return {
            "title": title,
            "summary": source[:160],
            "markdown": f"# {title}\n\n{source}\n\n## Request\n\n{prompt}",
        }
