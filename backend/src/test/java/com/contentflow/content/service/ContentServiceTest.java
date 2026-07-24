package com.contentflow.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

import com.contentflow.agent.client.AgentGenerationClient;
import com.contentflow.content.dto.ContentDtos;
import com.contentflow.content.entity.ContentEntity;
import com.contentflow.content.mapper.ContentMapper;
import com.contentflow.content.mapper.ContentTagMapper;
import com.contentflow.project.service.ProjectService;
import org.junit.jupiter.api.Test;

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
}
