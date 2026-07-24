package com.contentflow.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.contentflow.agent.client.AgentGenerationClient;
import com.contentflow.agent.dto.AgentConversationDtos;
import com.contentflow.agent.entity.AgentConversationEntity;
import com.contentflow.agent.entity.AgentMessageEntity;
import com.contentflow.agent.mapper.AgentConversationMapper;
import com.contentflow.agent.mapper.AgentMessageMapper;
import com.contentflow.content.dto.ContentDtos;
import com.contentflow.project.service.ProjectService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class AgentConversationServiceTest {
    @Test
    void createsActiveConversationForOwnedProject() {
        AgentConversationMapper conversationMapper = mock(AgentConversationMapper.class);
        ProjectService projectService = mock(ProjectService.class);
        AgentConversationService service = new AgentConversationService(
                conversationMapper, mock(AgentMessageMapper.class), projectService, mock(AgentGenerationClient.class));

        AgentConversationDtos.ConversationResponse response = service.create(
                9L, new AgentConversationDtos.CreateConversationRequest(7L, "JWT内容创作"));

        ArgumentCaptor<AgentConversationEntity> saved = ArgumentCaptor.forClass(AgentConversationEntity.class);
        verify(projectService).requireOwned(9L, 7L);
        verify(conversationMapper).insert(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(9L);
        assertThat(saved.getValue().getProjectId()).isEqualTo(7L);
        assertThat(saved.getValue().getThreadId()).isNotNull();
        assertThat(saved.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(response.title()).isEqualTo("JWT内容创作");
    }

    @Test
    void rejectsBlankOrOversizedConversationTitle() {
        AgentConversationService service = new AgentConversationService(
                mock(AgentConversationMapper.class), mock(AgentMessageMapper.class),
                mock(ProjectService.class), mock(AgentGenerationClient.class));

        assertThatThrownBy(() -> service.create(
                9L, new AgentConversationDtos.CreateConversationRequest(7L, " ")))
                .hasMessageContaining("conversation title is required");
        assertThatThrownBy(() -> service.create(
                9L, new AgentConversationDtos.CreateConversationRequest(7L, "x".repeat(256))))
                .hasMessageContaining("conversation title is too long");
    }

    @Test
    void rejectsConversationOwnedByAnotherUser() {
        AgentConversationMapper conversationMapper = mock(AgentConversationMapper.class);
        AgentConversationEntity conversation = conversation(4L, 8L, 7L);
        when(conversationMapper.selectById(4L)).thenReturn(conversation);
        AgentConversationService service = new AgentConversationService(
                conversationMapper, mock(AgentMessageMapper.class), mock(ProjectService.class),
                mock(AgentGenerationClient.class));

        assertThatThrownBy(() -> service.messages(9L, 4L))
                .hasMessageContaining("conversation access denied");
    }

    @Test
    void listsMessagesInMapperOrderForOwnedConversation() {
        AgentConversationMapper conversationMapper = mock(AgentConversationMapper.class);
        AgentMessageMapper messageMapper = mock(AgentMessageMapper.class);
        when(conversationMapper.selectById(4L)).thenReturn(conversation(4L, 9L, 7L));
        AgentMessageEntity first = message(11L, 4L, "USER", "first");
        AgentMessageEntity second = message(12L, 4L, "ASSISTANT", "second");
        when(messageMapper.selectList(any())).thenReturn(List.of(first, second));
        AgentConversationService service = new AgentConversationService(
                conversationMapper, messageMapper, mock(ProjectService.class), mock(AgentGenerationClient.class));

        List<AgentConversationDtos.MessageResponse> messages = service.messages(9L, 4L);

        assertThat(messages).extracting(AgentConversationDtos.MessageResponse::content)
                .containsExactly("first", "second");
    }

    @Test
    void savesUserThenAssistantAndForwardsConversationId() {
        AgentConversationMapper conversationMapper = mock(AgentConversationMapper.class);
        AgentMessageMapper messageMapper = mock(AgentMessageMapper.class);
        AgentGenerationClient agentClient = mock(AgentGenerationClient.class);
        AgentConversationEntity conversation = conversation(4L, 9L, 7L);
        when(conversationMapper.selectById(4L)).thenReturn(conversation);
        when(agentClient.generate(9L, 7L, 4L, "request-1", "根据资料生成文章")).thenReturn(
                new ContentDtos.AgentGenerateResponse(
                        "JWT", "Summary", "# JWT", "# JWT", List.of("Java"),
                        List.of(new ContentDtos.ReferenceItem(3L, "jwt.md", 0)), 31L));
        AgentConversationService service = new AgentConversationService(
                conversationMapper, messageMapper, mock(ProjectService.class), agentClient);

        AgentConversationDtos.SendMessageResponse response = service.sendMessage(
                9L, 4L, new AgentConversationDtos.SendMessageRequest("根据资料生成文章", "request-1"));

        ArgumentCaptor<AgentMessageEntity> messages = ArgumentCaptor.forClass(AgentMessageEntity.class);
        verify(messageMapper, times(2)).insert(messages.capture());
        assertThat(messages.getAllValues()).extracting(AgentMessageEntity::getRole)
                .containsExactly("USER", "ASSISTANT");
        assertThat(messages.getAllValues().get(1).getContent()).isEqualTo("# JWT");
        assertThat(messages.getAllValues().get(1).getMetadata()).contains("savedDraftId").contains("jwt.md");
        InOrder order = inOrder(messageMapper, agentClient);
        order.verify(messageMapper).insert(messages.getAllValues().get(0));
        order.verify(agentClient).generate(9L, 7L, 4L, "request-1", "根据资料生成文章");
        order.verify(messageMapper).insert(messages.getAllValues().get(1));
        verify(conversationMapper).updateById(conversation);
        assertThat(response.savedDraftId()).isEqualTo(31L);
        assertThat(response.assistantMessage().content()).isEqualTo("# JWT");
    }

    @Test
    void returnsPersistedMessagesForRepeatedRequestWithoutCallingAgentAgain() {
        AgentConversationMapper conversationMapper = mock(AgentConversationMapper.class);
        AgentMessageMapper messageMapper = mock(AgentMessageMapper.class);
        AgentGenerationClient agentClient = mock(AgentGenerationClient.class);
        when(conversationMapper.selectById(4L)).thenReturn(conversation(4L, 9L, 7L));
        AgentMessageEntity user = message(11L, 4L, "USER", "generate");
        user.setRequestId("request-1");
        AgentMessageEntity assistant = message(12L, 4L, "ASSISTANT", "# Result");
        assistant.setRequestId("request-1");
        assistant.setMetadata("{\"savedDraftId\":31}");
        when(messageMapper.selectOne(any())).thenReturn(user, assistant);
        AgentConversationService service = new AgentConversationService(
                conversationMapper, messageMapper, mock(ProjectService.class), agentClient);

        AgentConversationDtos.SendMessageResponse response = service.sendMessage(
                9L, 4L, new AgentConversationDtos.SendMessageRequest("generate", "request-1"));

        assertThat(response.savedDraftId()).isEqualTo(31L);
        assertThat(response.assistantMessage().content()).isEqualTo("# Result");
        verify(messageMapper, never()).insert(any(AgentMessageEntity.class));
        verify(agentClient, never()).generate(any(), any(), any(), any(), any());
    }

    @Test
    void retriesFailedRequestWithTheSameRequestId() {
        AgentConversationMapper conversationMapper = mock(AgentConversationMapper.class);
        AgentMessageMapper messageMapper = mock(AgentMessageMapper.class);
        AgentGenerationClient agentClient = mock(AgentGenerationClient.class);
        when(conversationMapper.selectById(4L)).thenReturn(conversation(4L, 9L, 7L));
        AgentMessageEntity failedUser = message(11L, 4L, "USER", "generate");
        failedUser.setRequestId("request-1");
        failedUser.setProcessingStatus("FAILED");
        when(messageMapper.selectOne(any())).thenReturn(failedUser, null);
        when(messageMapper.claimFailedRequest(11L)).thenReturn(1);
        when(agentClient.generate(9L, 7L, 4L, "request-1", "generate")).thenReturn(
                new ContentDtos.AgentGenerateResponse(
                        "Title", "Summary", "# Result", "# Result", List.of(), List.of(), 31L));
        AgentConversationService service = new AgentConversationService(
                conversationMapper, messageMapper, mock(ProjectService.class), agentClient);

        AgentConversationDtos.SendMessageResponse response = service.sendMessage(
                9L, 4L, new AgentConversationDtos.SendMessageRequest("generate", "request-1"));

        assertThat(response.assistantMessage().content()).isEqualTo("# Result");
        verify(messageMapper).claimFailedRequest(11L);
        verify(messageMapper, times(1)).insert(any(AgentMessageEntity.class));
    }

    @Test
    void keepsUserMessageWhenAgentGenerationFails() {
        AgentConversationMapper conversationMapper = mock(AgentConversationMapper.class);
        AgentMessageMapper messageMapper = mock(AgentMessageMapper.class);
        AgentGenerationClient agentClient = mock(AgentGenerationClient.class);
        when(conversationMapper.selectById(4L)).thenReturn(conversation(4L, 9L, 7L));
        when(agentClient.generate(9L, 7L, 4L, "request-2", "generate"))
                .thenThrow(new RuntimeException("agent unavailable"));
        AgentConversationService service = new AgentConversationService(
                conversationMapper, messageMapper, mock(ProjectService.class), agentClient);

        assertThatThrownBy(() -> service.sendMessage(
                9L, 4L, new AgentConversationDtos.SendMessageRequest("generate", "request-2")))
                .hasMessageContaining("agent unavailable");

        verify(messageMapper, times(1)).insert(any(AgentMessageEntity.class));
        verify(conversationMapper, never()).updateById(any(AgentConversationEntity.class));
    }

    private static AgentConversationEntity conversation(Long id, Long userId, Long projectId) {
        AgentConversationEntity entity = new AgentConversationEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setProjectId(projectId);
        entity.setTitle("Conversation");
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        return entity;
    }

    private static AgentMessageEntity message(Long id, Long conversationId, String role, String content) {
        AgentMessageEntity entity = new AgentMessageEntity();
        entity.setId(id);
        entity.setConversationId(conversationId);
        entity.setRole(role);
        entity.setContent(content);
        entity.setCreatedAt(OffsetDateTime.now());
        return entity;
    }
}
