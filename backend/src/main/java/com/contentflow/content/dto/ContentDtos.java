package com.contentflow.content.dto;

import java.util.List;

public class ContentDtos {
    public record GenerateRequest(String prompt) {}
    public record ReferenceItem(Long documentId, String fileName, Integer chunkIndex) {}
    public record CreateRequest(Long projectId, String title, String summary, String markdown, List<String> tags,
                                List<ReferenceItem> references) {
        public CreateRequest(Long projectId, String title, String summary, String markdown, List<String> tags) {
            this(projectId, title, summary, markdown, tags, List.of());
        }
        public CreateRequest(Long projectId, String title, String summary, String markdown) {
            this(projectId, title, summary, markdown, List.of());
        }
    }
    public record UpdateRequest(String title, String summary, String markdown, List<String> tags) {
        public UpdateRequest(String title, String summary, String markdown) {
            this(title, summary, markdown, null);
        }
    }
    public record AgentGenerateRequest(Long userId, Long projectId, Long conversationId, String prompt) {}
    public record AgentGenerateResponse(String title, String summary, String content, String markdown, List<String> tags,
                                        List<ReferenceItem> references, Long savedDraftId) {
        public AgentGenerateResponse(String title, String summary, String content, String markdown, List<String> tags,
                                     List<ReferenceItem> references) {
            this(title, summary, content, markdown, tags, references, null);
        }
    }
    public record ContentResponse(Long id, Long projectId, String title, String summary, String markdown) {}
}
