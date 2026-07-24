from __future__ import annotations

import json
import logging
import re

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI

from app.config import settings
from app.schemas import GeneratedContent, ParsedContentRequest, ReferenceItem, RetrievedChunk

logger = logging.getLogger(__name__)


class LlmService:
    def __init__(self) -> None:
        self.model = self._create_model()

    def parse_request(self, user_request: str, project_context: dict) -> ParsedContentRequest:
        if self.model is not None:
            try:
                parser = self.model.with_structured_output(ParsedContentRequest)
                return parser.invoke([
                    SystemMessage(content=(
                        "Parse the content request. Set need_retrieval to false for rewriting, polishing, "
                        "translation, or title optimization that only uses text in the request. Use the "
                        "project context for unspecified platform, audience, and style."
                    )),
                    HumanMessage(content=f"Project context:\n{json.dumps(project_context, ensure_ascii=False)}"
                                         f"\n\nUser request:\n{user_request}"),
                ])
            except Exception:
                logger.exception("structured request parsing failed; using deterministic fallback")
        return self._fallback_parse(user_request, project_context)

    def generate_content(
        self,
        user_request: str,
        project_context: dict,
        parsed_request: ParsedContentRequest,
        retrieved_chunks: list[RetrievedChunk],
    ) -> GeneratedContent:
        if self.model is not None:
            try:
                generator = self.model.with_structured_output(GeneratedContent)
                result = generator.invoke([
                    SystemMessage(content=(
                        "Generate platform-appropriate Markdown content. Follow the word limit. Do not invent "
                        "references: every reference must exactly match one retrieved chunk."
                    )),
                    HumanMessage(content=self._generation_input(
                        user_request, project_context, parsed_request, retrieved_chunks
                    )),
                ])
                result.references = self._allowed_references(result.references, retrieved_chunks)
                return result
            except Exception:
                logger.exception("structured content generation failed; using deterministic fallback")
        return self._fallback_generate(user_request, project_context, parsed_request, retrieved_chunks)

    def parse_request_fallback(self, user_request: str, project_context: dict) -> ParsedContentRequest:
        return self._fallback_parse(user_request, project_context)

    def generate_content_fallback(
        self,
        user_request: str,
        project_context: dict,
        parsed_request: ParsedContentRequest,
        retrieved_chunks: list[RetrievedChunk],
    ) -> GeneratedContent:
        return self._fallback_generate(user_request, project_context, parsed_request, retrieved_chunks)

    def sanitize_references(
        self,
        generated: GeneratedContent,
        retrieved_chunks: list[RetrievedChunk],
    ) -> GeneratedContent:
        return generated.model_copy(update={
            "references": self._allowed_references(generated.references, retrieved_chunks),
        })

    @staticmethod
    def _create_model() -> ChatOpenAI | None:
        api_key = settings.llm_api_key or settings.qwen_api_key
        if not api_key:
            return None
        base_url = settings.llm_base_url
        model_name = settings.llm_model
        if settings.qwen_api_key and not settings.llm_api_key:
            base_url = base_url or "https://dashscope.aliyuncs.com/compatible-mode/v1"
            model_name = settings.qwen_model
        return ChatOpenAI(api_key=api_key, base_url=base_url, model=model_name, temperature=0.2)

    @staticmethod
    def _fallback_parse(user_request: str, project_context: dict) -> ParsedContentRequest:
        lowered = user_request.lower()
        rewrite_markers = ("改写", "重写", "润色", "优化", "改得", "翻译", "rewrite", "polish", "translate")
        knowledge_markers = (
            "项目资料",
            "知识库",
            "上传的资料",
            "我的资料",
            "根据资料",
            "结合资料",
            "project knowledge",
            "knowledge base",
        )
        need_retrieval = any(marker in lowered for marker in knowledge_markers) or not any(
            marker in lowered for marker in rewrite_markers
        )
        word_match = re.search(r"(\d+)\s*(?:字|词|words?)", lowered)
        platform = next(
            (name for name in ("小红书", "微信公众号", "抖音", "知乎", "B站") if name.lower() in lowered),
            project_context.get("platform"),
        )
        content_type = "TITLE" if "标题" in user_request and not need_retrieval else "ARTICLE"
        return ParsedContentRequest(
            platform=platform,
            content_type=content_type,
            topic=user_request.strip(),
            target_audience=project_context.get("targetAudience"),
            content_style=project_context.get("contentStyle"),
            word_limit=int(word_match.group(1)) if word_match else None,
            need_retrieval=need_retrieval,
        )

    @classmethod
    def _fallback_generate(
        cls,
        user_request: str,
        project_context: dict,
        parsed_request: ParsedContentRequest,
        retrieved_chunks: list[RetrievedChunk],
    ) -> GeneratedContent:
        title = cls._title(parsed_request.topic)
        project_lines = [
            value for value in (
                project_context.get("positioning"),
                project_context.get("targetAudience"),
                project_context.get("contentStyle"),
            ) if value
        ]
        sections = [f"# {title}"]
        if retrieved_chunks:
            sections.extend(["## 核心内容", *[chunk.content for chunk in retrieved_chunks]])
        else:
            sections.extend(["## 内容", user_request.strip()])
        if project_lines:
            sections.extend(["## 项目要求", "；".join(project_lines)])
        content = "\n\n".join(sections)
        if parsed_request.word_limit is not None:
            content = content[:parsed_request.word_limit]
        summary_source = retrieved_chunks[0].content if retrieved_chunks else user_request.strip()
        references = [
            ReferenceItem(
                documentId=chunk.document_id,
                fileName=chunk.file_name,
                chunkIndex=chunk.chunk_index,
            )
            for chunk in retrieved_chunks
        ]
        tags = [tag for tag in (parsed_request.platform, project_context.get("domain")) if tag]
        return GeneratedContent(
            title=title,
            summary=summary_source[:160],
            content=content,
            tags=list(dict.fromkeys(tags)),
            references=references,
        )

    @staticmethod
    def _title(topic: str) -> str:
        title = topic.strip().splitlines()[0]
        title = re.sub(r"^(根据我上传的资料|根据我的资料|根据资料)", "", title).strip()
        title = re.sub(r"[。.!！]+$", "", title).strip()
        return title[:60] or "Untitled"

    @staticmethod
    def _generation_input(
        user_request: str,
        project_context: dict,
        parsed_request: ParsedContentRequest,
        retrieved_chunks: list[RetrievedChunk],
    ) -> str:
        payload = {
            "project_context": project_context,
            "user_request": user_request,
            "parsed_request": parsed_request.model_dump(),
            "retrieved_chunks": [chunk.model_dump(by_alias=True) for chunk in retrieved_chunks],
        }
        return json.dumps(payload, ensure_ascii=False)

    @staticmethod
    def _allowed_references(
        references: list[ReferenceItem], retrieved_chunks: list[RetrievedChunk]
    ) -> list[ReferenceItem]:
        allowed = {
            (chunk.document_id, chunk.file_name, chunk.chunk_index)
            for chunk in retrieved_chunks
        }
        return [
            reference for reference in references
            if (reference.document_id, reference.file_name, reference.chunk_index) in allowed
        ]
