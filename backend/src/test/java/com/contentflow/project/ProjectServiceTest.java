package com.contentflow.project;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.contentflow.common.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {
    @Mock
    ProjectMapper projectMapper;

    @Test
    void rejectsAccessToProjectOwnedByAnotherUser() {
        ProjectEntity project = new ProjectEntity();
        project.setId(3L);
        project.setOwnerId(99L);
        when(projectMapper.selectById(3L)).thenReturn(project);

        ProjectService service = new ProjectService(projectMapper);

        assertThatThrownBy(() -> service.requireOwned(1L, 3L))
                .isInstanceOf(AppException.class)
                .hasMessage("project access denied");
    }
}
