package com.contentflow.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.contentflow.common.exception.AppException;
import com.contentflow.project.dto.ProjectDtos;
import com.contentflow.project.entity.ProjectEntity;
import com.contentflow.project.mapper.ProjectMapper;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProjectService {
    private final ProjectMapper projectMapper;

    public ProjectService(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    public ProjectDtos.ProjectResponse create(Long ownerId, ProjectDtos.ProjectRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "project name is required");
        }
        ProjectEntity project = new ProjectEntity();
        project.setOwnerId(ownerId);
        project.setName(request.name());
        project.setDescription(request.description());
        project.setPlatform(request.platform());
        project.setDomain(request.domain());
        project.setPositioning(request.positioning());
        project.setTargetAudience(request.targetAudience());
        project.setContentStyle(request.contentStyle());
        project.setStatus("ACTIVE");
        projectMapper.insert(project);
        return toResponse(project);
    }

    public List<ProjectDtos.ProjectResponse> list(Long ownerId) {
        return projectMapper.selectList(new LambdaQueryWrapper<ProjectEntity>()
                        .eq(ProjectEntity::getOwnerId, ownerId)
                        .orderByDesc(ProjectEntity::getId))
                .stream().map(this::toResponse).toList();
    }

    public ProjectEntity requireOwned(Long ownerId, Long projectId) {
        ProjectEntity project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "project not found");
        }
        if (!project.getOwnerId().equals(ownerId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "project access denied");
        }
        return project;
    }

    public ProjectDtos.ProjectResponse detail(Long ownerId, Long projectId) {
        return toResponse(requireOwned(ownerId, projectId));
    }

    public ProjectEntity detailForInternal(Long projectId) {
        ProjectEntity project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "project not found");
        }
        return project;
    }

    public ProjectDtos.ProjectResponse update(Long ownerId, Long projectId, ProjectDtos.ProjectRequest request) {
        ProjectEntity project = requireOwned(ownerId, projectId);
        if (request.name() != null && !request.name().isBlank()) {
            project.setName(request.name());
        }
        project.setDescription(request.description());
        project.setPlatform(request.platform());
        project.setDomain(request.domain());
        project.setPositioning(request.positioning());
        project.setTargetAudience(request.targetAudience());
        project.setContentStyle(request.contentStyle());
        projectMapper.updateById(project);
        return toResponse(project);
    }

    public void delete(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        projectMapper.deleteById(projectId);
    }

    ProjectDtos.ProjectResponse toResponse(ProjectEntity project) {
        return new ProjectDtos.ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getPlatform(),
                project.getDomain(),
                project.getPositioning(),
                project.getTargetAudience(),
                project.getContentStyle()
        );
    }
}
