package com.contentflow.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.contentflow.agent.client.AgentGenerationClient;
import com.contentflow.common.exception.AppException;
import com.contentflow.content.dto.ContentDtos;
import com.contentflow.content.entity.ContentEntity;
import com.contentflow.content.entity.ContentTagEntity;
import com.contentflow.content.mapper.ContentMapper;
import com.contentflow.content.mapper.ContentTagMapper;
import com.contentflow.project.service.ProjectService;
import java.util.List;
import com.contentflow.common.api.PageResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ContentService {
    private final ContentMapper contentMapper;
    private final ProjectService projectService;
    private final AgentGenerationClient agentGenerationClient;
    private final ContentTagMapper contentTagMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ContentService(ContentMapper contentMapper, ProjectService projectService, AgentGenerationClient agentGenerationClient) {
        this(contentMapper, projectService, agentGenerationClient, null);
    }

    @Autowired
    public ContentService(ContentMapper contentMapper, ProjectService projectService, AgentGenerationClient agentGenerationClient,
                          ContentTagMapper contentTagMapper) {
        this.contentMapper = contentMapper;
        this.projectService = projectService;
        this.agentGenerationClient = agentGenerationClient;
        this.contentTagMapper = contentTagMapper;
    }

    public ContentDtos.ContentResponse generate(Long ownerId, Long projectId, ContentDtos.GenerateRequest request) {
        projectService.requireOwned(ownerId, projectId);
        if (request.prompt() == null || request.prompt().isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "prompt is required");
        }
        ContentDtos.AgentGenerateResponse generated = agentGenerationClient.generate(ownerId, projectId, request.prompt());
        String markdown = generated == null || generated.markdown() == null || generated.markdown().isBlank()
                ? generated == null ? null : generated.content()
                : generated.markdown();
        if (markdown == null || markdown.isBlank()) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "agent returned empty content");
        }
        ContentDtos.ContentResponse response = save(ownerId, projectId, generated.title(), generated.summary(), markdown,
                serializeReferences(generated.references()));
        saveTags(response.id(), generated.tags());
        return response;
    }

    @Transactional
    public ContentDtos.ContentResponse create(Long ownerId, ContentDtos.CreateRequest request) {
        if (request.projectId() == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "project id is required");
        }
        projectService.requireOwned(ownerId, request.projectId());
        if (request.markdown() == null || request.markdown().isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "markdown is required");
        }
        ContentDtos.ContentResponse response = save(ownerId, request.projectId(), request.title(), request.summary(), request.markdown(),
                serializeReferences(request.references()));
        saveTags(response.id(), request.tags());
        return response;
    }

    public ContentDtos.ContentResponse save(Long ownerId, Long projectId, String title, String summary, String markdown) {
        return save(ownerId, projectId, title, summary, markdown, "[]");
    }

    private ContentDtos.ContentResponse save(Long ownerId, Long projectId, String title, String summary, String markdown,
                                             String referencesJson) {
        ContentEntity content = new ContentEntity();
        content.setOwnerId(ownerId);
        content.setProjectId(projectId);
        content.setTitle(title == null || title.isBlank() ? "Untitled" : title);
        content.setSummary(summary);
        content.setMarkdown(markdown);
        content.setReferencesJson(referencesJson);
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

    public PageResult<ContentDtos.ContentResponse> page(Long ownerId, Long projectId, String status, String keyword,
                                                         long page, long size) {
        projectService.requireOwned(ownerId, projectId);
        LambdaQueryWrapper<ContentEntity> query = new LambdaQueryWrapper<ContentEntity>()
                .eq(ContentEntity::getProjectId, projectId)
                .eq(status != null && !status.isBlank(), ContentEntity::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper
                        .like(ContentEntity::getTitle, keyword).or().like(ContentEntity::getSummary, keyword))
                .orderByDesc(ContentEntity::getId);
        Page<ContentEntity> result = contentMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)), query);
        return new PageResult<>(result.getRecords().stream().map(this::toResponse).toList(), result.getTotal(),
                result.getCurrent(), result.getSize());
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
        if (request.tags() != null && contentTagMapper != null) {
            contentTagMapper.delete(new LambdaQueryWrapper<ContentTagEntity>()
                    .eq(ContentTagEntity::getContentId, contentId));
            saveTags(contentId, request.tags());
        }
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

    private void saveTags(Long contentId, List<String> tags) {
        if (contentTagMapper == null || tags == null) {
            return;
        }
        tags.stream().filter(tag -> tag != null && !tag.isBlank()).distinct().forEach(tag -> {
            ContentTagEntity entity = new ContentTagEntity();
            entity.setContentId(contentId);
            entity.setTagName(tag.trim());
            contentTagMapper.insert(entity);
        });
    }

    private String serializeReferences(List<ContentDtos.ReferenceItem> references) {
        try {
            return objectMapper.writeValueAsString(references == null ? List.of() : references);
        } catch (JsonProcessingException exception) {
            throw new AppException(HttpStatus.BAD_REQUEST, "invalid references");
        }
    }

    ContentDtos.ContentResponse toResponse(ContentEntity content) {
        return new ContentDtos.ContentResponse(content.getId(), content.getProjectId(), content.getTitle(),
                content.getSummary(), content.getMarkdown());
    }
}
