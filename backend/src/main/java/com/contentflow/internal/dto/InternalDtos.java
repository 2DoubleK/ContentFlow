package com.contentflow.internal.dto;

import com.contentflow.content.dto.ContentDtos;
import java.util.List;

public class InternalDtos {
    public record ProjectContextResponse(Long projectId, Long ownerId, String name, String description, String platform,
                                         String domain, String positioning, String targetAudience, String contentStyle) {}
    public record SaveContentRequest(Long ownerId, String title, String summary, String markdown) {}
    public record DocumentChunkRequest(Integer chunkIndex, String content, String chromaId, Integer tokenCount) {}
    public record DocumentChunksRequest(List<DocumentChunkRequest> chunks) {}
    public record DocumentStatusRequest(String status, Integer chunkCount, String errorMessage) {}
    public record SaveAgentContentRequest(Long userId, Long projectId, Long conversationId, String title, String summary,
                                          String content, String contentType, List<String> tags,
                                          List<ContentDtos.ReferenceItem> references, String requestId) {}
}
