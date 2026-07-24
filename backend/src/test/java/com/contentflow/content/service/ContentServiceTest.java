package com.contentflow.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

import com.contentflow.agent.client.AgentGenerationClient;
import com.contentflow.agent.mapper.AgentConversationMapper;
import com.contentflow.content.dto.ContentDtos;
import com.contentflow.content.entity.ContentEntity;
import com.contentflow.content.mapper.ContentMapper;
import com.contentflow.content.mapper.ContentTagMapper;
import com.contentflow.project.service.ProjectService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentServiceTest {
    @Test
    void savesGeneratedContentReturnedByAgent() {
        ContentMapper mapper = mock(ContentMapper.class);
        ProjectService projectService = mock(ProjectService.class);
        AgentGenerationClient agentClient = mock(AgentGenerationClient.class);
        ContentTagMapper tagMapper = mock(ContentTagMapper.class);
        ContentDtos.ReferenceItem reference = new ContentDtos.ReferenceItem(3L, "guide.md", 1);
        when(agentClient.generate(1L, 2L, "write article")).thenReturn(
                new ContentDtos.AgentGenerateResponse("Title", "Summary", "# Body", "# Body", java.util.List.of("Java"),
                        java.util.List.of(reference)));
        ContentService service = new ContentService(mapper, projectService, agentClient, tagMapper);

        ContentDtos.ContentResponse response = service.generate(1L, 2L, new ContentDtos.GenerateRequest("write article"));

        assertThat(response.title()).isEqualTo("Title");
        assertThat(response.markdown()).isEqualTo("# Body");
        ArgumentCaptor<ContentEntity> content = ArgumentCaptor.forClass(ContentEntity.class);
        verify(mapper).insert(content.capture());
        assertThat(content.getValue().getReferencesJson()).contains("guide.md");
        verify(tagMapper).insert(any(com.contentflow.content.entity.ContentTagEntity.class));
        verify(agentClient).generate(1L, 2L, "write article");
    }

    @Test
    void returnsAgentSavedDraftWithoutCreatingDuplicateContent() {
        ContentMapper mapper = mock(ContentMapper.class);
        ProjectService projectService = mock(ProjectService.class);
        AgentGenerationClient agentClient = mock(AgentGenerationClient.class);
        ContentEntity saved = new ContentEntity();
        saved.setId(44L);
        saved.setOwnerId(1L);
        saved.setProjectId(2L);
        saved.setTitle("Title");
        saved.setSummary("Summary");
        saved.setMarkdown("# Body");
        when(agentClient.generate(1L, 2L, "save article")).thenReturn(
                new ContentDtos.AgentGenerateResponse("Title", "Summary", "# Body", "# Body", java.util.List.of(),
                        java.util.List.of(), 44L));
        when(mapper.selectById(44L)).thenReturn(saved);
        ContentService service = new ContentService(mapper, projectService, agentClient);

        ContentDtos.ContentResponse response = service.generate(1L, 2L, new ContentDtos.GenerateRequest("save article"));

        assertThat(response.id()).isEqualTo(44L);
        verify(mapper, never()).insert(any(ContentEntity.class));
        verify(projectService, times(2)).requireOwned(1L, 2L);
    }

    @Test
    void updatesContentOwnedByCurrentUser() {
        ContentMapper mapper = mock(ContentMapper.class);
        ProjectService projectService = mock(ProjectService.class);
        ContentEntity content = new ContentEntity();
        content.setId(6L);
        content.setProjectId(2L);
        content.setOwnerId(1L);
        content.setTitle("Old title");
        content.setMarkdown("old");
        when(mapper.selectById(6L)).thenReturn(content);
        ContentService service = new ContentService(mapper, projectService, mock(AgentGenerationClient.class));

        ContentDtos.ContentResponse response = service.update(1L, 6L,
                new ContentDtos.UpdateRequest("New title", "New summary", "new"));

        assertThat(response.title()).isEqualTo("New title");
        assertThat(response.markdown()).isEqualTo("new");
        verify(mapper).updateById(content);
        verify(projectService).requireOwned(1L, 2L);
    }

    @Test
    void createsManualContentForOwnedProject() {
        ContentMapper mapper = mock(ContentMapper.class);
        ProjectService projectService = mock(ProjectService.class);
        ContentService service = new ContentService(mapper, projectService, mock(AgentGenerationClient.class));

        ContentDtos.ContentResponse response = service.create(1L,
                new ContentDtos.CreateRequest(2L, "Manual title", "Summary", "# Body"));

        assertThat(response.projectId()).isEqualTo(2L);
        assertThat(response.title()).isEqualTo("Manual title");
        verify(projectService).requireOwned(1L, 2L);
        verify(mapper).insert(any(ContentEntity.class));
    }

    @Test
    void savesTagsWhenCreatingContent() {
        ContentMapper contentMapper = mock(ContentMapper.class);
        ContentTagMapper tagMapper = mock(ContentTagMapper.class);
        ContentService service = new ContentService(contentMapper, mock(ProjectService.class),
                mock(AgentGenerationClient.class), tagMapper);

        service.create(1L, new ContentDtos.CreateRequest(2L, "Title", "Summary", "# Body", java.util.List.of("Java", "Spring")));

        verify(tagMapper, org.mockito.Mockito.times(2)).insert(any(com.contentflow.content.entity.ContentTagEntity.class));
    }

    @Test
    void replacesTagsWhenUpdatingContent() {
        ContentMapper contentMapper = mock(ContentMapper.class);
        ContentTagMapper tagMapper = mock(ContentTagMapper.class);
        ContentEntity content = new ContentEntity();
        content.setId(6L);
        content.setProjectId(2L);
        content.setTitle("Old");
        content.setMarkdown("old");
        when(contentMapper.selectById(6L)).thenReturn(content);
        ContentService service = new ContentService(contentMapper, mock(ProjectService.class),
                mock(AgentGenerationClient.class), tagMapper);

        service.update(1L, 6L, new ContentDtos.UpdateRequest("New", "Summary", "# Body", java.util.List.of("Java")));

        verify(tagMapper).delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verify(tagMapper).insert(any(com.contentflow.content.entity.ContentTagEntity.class));
    }

    @Test
    void storesReferencesAsJsonbMetadata() {
        ContentMapper mapper = mock(ContentMapper.class);
        ContentService service = new ContentService(mapper, mock(ProjectService.class), mock(AgentGenerationClient.class));

        service.create(1L, new ContentDtos.CreateRequest(2L, "Title", "Summary", "# Body", java.util.List.of(),
                java.util.List.of(new ContentDtos.ReferenceItem(3L, "guide.md", 1))));

        ArgumentCaptor<ContentEntity> content = ArgumentCaptor.forClass(ContentEntity.class);
        verify(mapper).insert(content.capture());
        assertThat(content.getValue().getReferencesJson()).contains("guide.md");
    }

    @Test
    void savesAgentDraftWithoutConversation() {
        ContentMapper mapper = mock(ContentMapper.class);
        ContentTagMapper tagMapper = mock(ContentTagMapper.class);
        ProjectService projectService = mock(ProjectService.class);
        AgentConversationMapper conversationMapper = mock(AgentConversationMapper.class);
        ContentService service = new ContentService(mapper, projectService, mock(AgentGenerationClient.class),
                tagMapper, conversationMapper);
        ContentService.AgentDraftInput input = new ContentService.AgentDraftInput(
                9L, 7L, null, "JWT", "Summary", "# JWT", "ARTICLE", java.util.List.of("Java"),
                java.util.List.of(new ContentDtos.ReferenceItem(3L, "jwt.md", 0)), "request-1");

        service.saveAgentDraft(input);

        ArgumentCaptor<ContentEntity> saved = ArgumentCaptor.forClass(ContentEntity.class);
        verify(projectService).requireOwned(9L, 7L);
        verify(mapper).insert(saved.capture());
        assertThat(saved.getValue().getConversationId()).isNull();
        assertThat(saved.getValue().getContent()).isEqualTo("# JWT");
        assertThat(saved.getValue().getMarkdown()).isEqualTo("# JWT");
        assertThat(saved.getValue().getContentType()).isEqualTo("ARTICLE");
        assertThat(saved.getValue().getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getValue().getAgentRequestId()).isEqualTo("request-1");
        assertThat(saved.getValue().getReferencesJson()).contains("jwt.md");
        verify(tagMapper).insert(any(com.contentflow.content.entity.ContentTagEntity.class));
    }

    @Test
    void rejectsConversationOwnedByAnotherProject() {
        AgentConversationMapper conversationMapper = mock(AgentConversationMapper.class);
        when(conversationMapper.existsOwned(4L, 9L, 7L)).thenReturn(false);
        ContentService service = new ContentService(mock(ContentMapper.class), mock(ProjectService.class),
                mock(AgentGenerationClient.class), mock(ContentTagMapper.class), conversationMapper);
        ContentService.AgentDraftInput input = new ContentService.AgentDraftInput(
                9L, 7L, 4L, "JWT", "Summary", "# JWT", "ARTICLE", java.util.List.of(), java.util.List.of(),
                "request-2");

        assertThatThrownBy(() -> service.saveAgentDraft(input))
                .hasMessageContaining("conversation does not belong");
    }

    @Test
    void returnsExistingDraftForRepeatedAgentRequestId() {
        ContentMapper mapper = mock(ContentMapper.class);
        ProjectService projectService = mock(ProjectService.class);
        ContentEntity existing = new ContentEntity();
        existing.setId(31L);
        existing.setOwnerId(9L);
        existing.setProjectId(7L);
        existing.setTitle("JWT");
        existing.setMarkdown("# JWT");
        existing.setAgentRequestId("request-3");
        when(mapper.selectOne(any())).thenReturn(existing);
        ContentService service = new ContentService(mapper, projectService, mock(AgentGenerationClient.class));
        ContentService.AgentDraftInput input = new ContentService.AgentDraftInput(
                9L, 7L, null, "JWT", "Summary", "# JWT", "ARTICLE", java.util.List.of(), java.util.List.of(),
                "request-3");

        ContentDtos.ContentResponse response = service.saveAgentDraft(input);

        assertThat(response.id()).isEqualTo(31L);
        verify(mapper).lockAgentRequestId("request-3");
        verify(mapper, never()).insert(any(ContentEntity.class));
    }

    @Test
    void acceptsTitleContentTypeForAgentDraft() {
        ContentMapper mapper = mock(ContentMapper.class);
        ContentService service = new ContentService(mapper, mock(ProjectService.class),
                mock(AgentGenerationClient.class));
        ContentService.AgentDraftInput input = new ContentService.AgentDraftInput(
                9L, 7L, null, "Title", "Summary", "Better title", "TITLE", java.util.List.of(), java.util.List.of(),
                "request-4");

        service.saveAgentDraft(input);

        ArgumentCaptor<ContentEntity> saved = ArgumentCaptor.forClass(ContentEntity.class);
        verify(mapper).insert(saved.capture());
        assertThat(saved.getValue().getContentType()).isEqualTo("TITLE");
    }
}
