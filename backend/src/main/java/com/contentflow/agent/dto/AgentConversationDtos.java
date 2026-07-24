package com.contentflow.agent.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AgentConversationDtos {
    public record CreateConversationRequest(Long projectId, String title) {}

    public record ConversationResponse(
            Long id,
            Long projectId,
            UUID threadId,
            String title,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record MessageResponse(
            Long id,
            Long conversationId,
            String requestId,
            String role,
            String content,
            String metadata,
            OffsetDateTime createdAt) {}

    public record SendMessageRequest(String content, String requestId) {
        public SendMessageRequest(String content) {
            this(content, null);
        }
    }

    public record SendMessageResponse(
            MessageResponse userMessage,
            MessageResponse assistantMessage,
            Long savedDraftId) {}
}
