package com.contentflow.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.contentflow.agent.client.AgentGenerationClient;
import com.contentflow.agent.dto.AgentConversationDtos;
import com.contentflow.agent.entity.AgentConversationEntity;
import com.contentflow.agent.entity.AgentMessageEntity;
import com.contentflow.agent.mapper.AgentConversationMapper;
import com.contentflow.agent.mapper.AgentMessageMapper;
import com.contentflow.common.exception.AppException;
import com.contentflow.content.dto.ContentDtos;
import com.contentflow.project.service.ProjectService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class AgentConversationService {
    private final AgentConversationMapper conversationMapper;
    private final AgentMessageMapper messageMapper;
    private final ProjectService projectService;
    private final AgentGenerationClient agentClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentConversationService(AgentConversationMapper conversationMapper,
                                    AgentMessageMapper messageMapper,
                                    ProjectService projectService,
                                    AgentGenerationClient agentClient) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.projectService = projectService;
        this.agentClient = agentClient;
    }

    public AgentConversationDtos.ConversationResponse create(
            Long userId, AgentConversationDtos.CreateConversationRequest request) {
        if (request == null || request.projectId() == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "project id is required");
        }
        String title = validateTitle(request.title());
        projectService.requireOwned(userId, request.projectId());
        OffsetDateTime now = OffsetDateTime.now();
        AgentConversationEntity conversation = new AgentConversationEntity();
        conversation.setUserId(userId);
        conversation.setProjectId(request.projectId());
        conversation.setThreadId(UUID.randomUUID());
        conversation.setTitle(title);
        conversation.setStatus("ACTIVE");
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.insert(conversation);
        return toConversationResponse(conversation);
    }

    public List<AgentConversationDtos.ConversationResponse> list(Long userId, Long projectId) {
        projectService.requireOwned(userId, projectId);
        return conversationMapper.selectList(new LambdaQueryWrapper<AgentConversationEntity>()
                        .eq(AgentConversationEntity::getUserId, userId)
                        .eq(AgentConversationEntity::getProjectId, projectId)
                        .eq(AgentConversationEntity::getStatus, "ACTIVE")
                        .orderByDesc(AgentConversationEntity::getUpdatedAt)
                        .orderByDesc(AgentConversationEntity::getId))
                .stream().map(this::toConversationResponse).toList();
    }

    public List<AgentConversationDtos.MessageResponse> messages(Long userId, Long conversationId) {
        requireOwned(userId, conversationId);
        return messageMapper.selectList(new LambdaQueryWrapper<AgentMessageEntity>()
                        .eq(AgentMessageEntity::getConversationId, conversationId)
                        .orderByAsc(AgentMessageEntity::getCreatedAt)
                        .orderByAsc(AgentMessageEntity::getId))
                .stream().map(this::toMessageResponse).toList();
    }

    public AgentConversationEntity requireOwned(Long userId, Long conversationId) {
        AgentConversationEntity conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "conversation not found");
        }
        if (!conversation.getUserId().equals(userId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "conversation access denied");
        }
        return conversation;
    }

    public AgentConversationDtos.SendMessageResponse sendMessage(
            Long userId, Long conversationId, AgentConversationDtos.SendMessageRequest request) {
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "message content is required");
        }
        String requestId = normalizeRequestId(request.requestId());
        AgentConversationEntity conversation = requireOwned(userId, conversationId);
        AgentMessageEntity existingUser = findRequestMessage(conversationId, requestId, "USER");
        if (existingUser != null) {
            return resumeOrReturn(userId, conversation, request.content().trim(), requestId, existingUser);
        }

        AgentMessageEntity userMessage = newMessage(
                conversationId, requestId, "USER", request.content().trim(), null);
        userMessage.setProcessingStatus("PROCESSING");
        try {
            messageMapper.insert(userMessage);
        } catch (DataIntegrityViolationException exception) {
            existingUser = findRequestMessage(conversationId, requestId, "USER");
            if (existingUser == null) throw exception;
            return resumeOrReturn(userId, conversation, request.content().trim(), requestId, existingUser);
        }

        return generateAndPersist(userId, conversation, request.content().trim(), requestId, userMessage);
    }

    private AgentConversationDtos.SendMessageResponse resumeOrReturn(
            Long userId,
            AgentConversationEntity conversation,
            String content,
            String requestId,
            AgentMessageEntity userMessage) {
        AgentMessageEntity assistantMessage = findRequestMessage(conversation.getId(), requestId, "ASSISTANT");
        if (assistantMessage != null) {
            return persistedResponse(userMessage, assistantMessage);
        }
        if (!"FAILED".equals(userMessage.getProcessingStatus())
                || messageMapper.claimFailedRequest(userMessage.getId()) != 1) {
            throw new AppException(HttpStatus.CONFLICT, "message request is still processing");
        }
        userMessage.setProcessingStatus("PROCESSING");
        return generateAndPersist(userId, conversation, content, requestId, userMessage);
    }

    private AgentConversationDtos.SendMessageResponse generateAndPersist(
            Long userId,
            AgentConversationEntity conversation,
            String content,
            String requestId,
            AgentMessageEntity userMessage) {
        try {
            ContentDtos.AgentGenerateResponse generated = agentClient.generate(
                    userId, conversation.getProjectId(), conversation.getId(), requestId, content);
            String assistantContent = generated.markdown() == null || generated.markdown().isBlank()
                    ? generated.content() : generated.markdown();
            AgentMessageEntity assistantMessage = newMessage(
                    conversation.getId(), requestId, "ASSISTANT", assistantContent, responseMetadata(generated));
            messageMapper.insert(assistantMessage);

            userMessage.setProcessingStatus("COMPLETED");
            messageMapper.updateById(userMessage);
            conversation.setUpdatedAt(OffsetDateTime.now());
            conversationMapper.updateById(conversation);
            return new AgentConversationDtos.SendMessageResponse(
                    toMessageResponse(userMessage),
                    toMessageResponse(assistantMessage),
                    generated.savedDraftId());
        } catch (RuntimeException exception) {
            userMessage.setProcessingStatus("FAILED");
            messageMapper.updateById(userMessage);
            throw exception;
        }
    }

    private AgentConversationDtos.SendMessageResponse persistedResponse(
            AgentMessageEntity userMessage, AgentMessageEntity assistantMessage) {
        return new AgentConversationDtos.SendMessageResponse(
                toMessageResponse(userMessage),
                toMessageResponse(assistantMessage),
                savedDraftId(assistantMessage.getMetadata()));
    }

    private AgentMessageEntity findRequestMessage(Long conversationId, String requestId, String role) {
        return messageMapper.selectOne(new LambdaQueryWrapper<AgentMessageEntity>()
                .eq(AgentMessageEntity::getConversationId, conversationId)
                .eq(AgentMessageEntity::getRequestId, requestId)
                .eq(AgentMessageEntity::getRole, role));
    }

    private AgentMessageEntity newMessage(
            Long conversationId, String requestId, String role, String content, String metadata) {
        AgentMessageEntity message = new AgentMessageEntity();
        message.setConversationId(conversationId);
        message.setRequestId(requestId);
        message.setRole(role);
        message.setContent(content);
        message.setMetadata(metadata);
        message.setCreatedAt(OffsetDateTime.now());
        return message;
    }

    private Long savedDraftId(String metadata) {
        if (metadata == null || metadata.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(metadata).get("savedDraftId");
            return node == null || node.isNull() ? null : node.longValue();
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String responseMetadata(ContentDtos.AgentGenerateResponse response) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("savedDraftId", response.savedDraftId());
        metadata.put("title", response.title());
        metadata.put("summary", response.summary());
        metadata.put("tags", response.tags());
        metadata.put("references", response.references());
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize agent response metadata", exception);
        }
    }

    private String validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "conversation title is required");
        }
        String normalized = title.trim();
        if (normalized.length() > 255) {
            throw new AppException(HttpStatus.BAD_REQUEST, "conversation title is too long");
        }
        return normalized;
    }

    private String normalizeRequestId(String requestId) {
        String normalized = requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString() : requestId.trim();
        if (normalized.length() > 64) {
            throw new AppException(HttpStatus.BAD_REQUEST, "message request id is too long");
        }
        return normalized;
    }

    private AgentConversationDtos.ConversationResponse toConversationResponse(AgentConversationEntity conversation) {
        return new AgentConversationDtos.ConversationResponse(
                conversation.getId(),
                conversation.getProjectId(),
                conversation.getThreadId(),
                conversation.getTitle(),
                conversation.getStatus(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt());
    }

    private AgentConversationDtos.MessageResponse toMessageResponse(AgentMessageEntity message) {
        return new AgentConversationDtos.MessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getRequestId(),
                message.getRole(),
                message.getContent(),
                message.getMetadata(),
                message.getCreatedAt());
    }
}
