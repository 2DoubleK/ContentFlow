package com.contentflow.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.contentflow.agent.client.AgentGenerationClient;
import com.contentflow.common.exception.AppException;
import com.contentflow.content.dto.ContentDtos;
import com.contentflow.content.entity.ContentEntity;
import com.contentflow.content.mapper.ContentMapper;
import com.contentflow.project.service.ProjectService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ContentService {
    private final ContentMapper contentMapper;
    private final ProjectService projectService;
    private final AgentGenerationClient agentGenerationClient;

    public ContentService(ContentMapper contentMapper, ProjectService projectService, AgentGenerationClient agentGenerationClient) {
        this.contentMapper = contentMapper;
        this.projectService = projectService;
        this.agentGenerationClient = agentGenerationClient;
    }

    public ContentDtos.ContentResponse generate(Long ownerId, Long projectId, ContentDtos.GenerateRequest request) {
        projectService.requireOwned(ownerId, projectId);
        if (request.prompt() == null || request.prompt().isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "prompt is required");
        }
        ContentDtos.AgentGenerateResponse generated = agentGenerationClient.generate(projectId, request.prompt());
        if (generated == null || generated.markdown() == null || generated.markdown().isBlank()) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "agent returned empty content");
        }
        return save(ownerId, projectId, generated.title(), generated.summary(), generated.markdown());
    }

    public ContentDtos.ContentResponse create(Long ownerId, ContentDtos.CreateRequest request) {
        if (request.projectId() == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "project id is required");
        }
        projectService.requireOwned(ownerId, request.projectId());
        if (request.markdown() == null || request.markdown().isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "markdown is required");
        }
        return save(ownerId, request.projectId(), request.title(), request.summary(), request.markdown());
    }

    public ContentDtos.ContentResponse save(Long ownerId, Long projectId, String title, String summary, String markdown) {
        ContentEntity content = new ContentEntity();
        content.setOwnerId(ownerId);
        content.setProjectId(projectId);
        content.setTitle(title == null || title.isBlank() ? "Untitled" : title);
        content.setSummary(summary);
        content.setMarkdown(markdown);
        contentMapper.insert(content);
        return toResponse(content);
    }

    public List<ContentDtos.ContentResponse> list(Long ownerId, Long projectId) {
        projectService.requireOwned(ownerId, projectId);
        return contentMapper.selectList(new LambdaQueryWrapper<ContentEntity>()
                        .eq(ContentEntity::getProjectId, projectId)
                        .orderByDesc(ContentEntity::getId))
                .stream().map(this::toResponse).toList();
    }

    public ContentDtos.ContentResponse detail(Long ownerId, Long contentId) {
        return toResponse(requireOwned(ownerId, contentId));
    }

    public ContentDtos.ContentResponse update(Long ownerId, Long contentId, ContentDtos.UpdateRequest request) {
        ContentEntity content = requireOwned(ownerId, contentId);
        if (request.title() != null && !request.title().isBlank()) {
            content.setTitle(request.title());
        }
        content.setSummary(request.summary());
        if (request.markdown() != null && !request.markdown().isBlank()) {
            content.setMarkdown(request.markdown());
        }
        contentMapper.updateById(content);
        return toResponse(content);
    }

    public void delete(Long ownerId, Long contentId) {
        requireOwned(ownerId, contentId);
        contentMapper.deleteById(contentId);
    }

    private ContentEntity requireOwned(Long ownerId, Long contentId) {
        ContentEntity content = contentMapper.selectById(contentId);
        if (content == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "content not found");
        }
        projectService.requireOwned(ownerId, content.getProjectId());
        return content;
    }

    ContentDtos.ContentResponse toResponse(ContentEntity content) {
        return new ContentDtos.ContentResponse(content.getId(), content.getProjectId(), content.getTitle(),
                content.getSummary(), content.getMarkdown());
    }
}
