package com.contentflow.content;

import java.util.List;

public class ContentDtos {
    public record GenerateRequest(String prompt) {}
    public record AgentGenerateRequest(Long projectId, String prompt) {}
    public record AgentGenerateResponse(String title, String summary, String markdown, List<String> references) {}
    public record ContentResponse(Long id, Long projectId, String title, String summary, String markdown) {}
}
