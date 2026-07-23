package com.contentflow.project.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.contentflow.common.exception.AppException;
import com.contentflow.project.dto.ProjectDtos;
import com.contentflow.project.entity.ProjectEntity;
import com.contentflow.project.mapper.ProjectMapper;
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

    @Test
    void preservesProjectProfileFieldsOnCreate() {
        ProjectMapper mapper = org.mockito.Mockito.mock(ProjectMapper.class);
        ProjectService service = new ProjectService(mapper);
        ProjectDtos.ProjectRequest request = new ProjectDtos.ProjectRequest(
                "Demo",
                "Desc",
                "Web",
                "contentflow.com",
                "定位",
                "目标用户",
                "蓝白青"
        );

        ProjectDtos.ProjectResponse response = service.create(8L, request);

        assertThat(response.platform()).isEqualTo("Web");
        assertThat(response.domain()).isEqualTo("contentflow.com");
        verify(mapper).insert(org.mockito.ArgumentMatchers.any(ProjectEntity.class));
    }
}
