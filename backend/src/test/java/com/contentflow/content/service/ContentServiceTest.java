package com.contentflow.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.contentflow.project.ProjectService;
import org.junit.jupiter.api.Test;

class ContentServiceTest {
    @Test
    void savesGeneratedContentReturnedByAgent() {
        ContentMapper mapper = mock(ContentMapper.class);
        ProjectService projectService = mock(ProjectService.class);
        AgentGenerationClient agentClient = mock(AgentGenerationClient.class);
        when(agentClient.generate(2L, "write article")).thenReturn(
                new ContentDtos.AgentGenerateResponse("Title", "Summary", "# Body", java.util.List.of()));
        ContentService service = new ContentService(mapper, projectService, agentClient);

        ContentDtos.ContentResponse response = service.generate(1L, 2L, new ContentDtos.GenerateRequest("write article"));

        assertThat(response.title()).isEqualTo("Title");
        assertThat(response.markdown()).isEqualTo("# Body");
        verify(mapper).insert(any(ContentEntity.class));
    }
}
