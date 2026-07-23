package com.contentflow.internal.controller;

import com.contentflow.common.api.ApiResponse;
import com.contentflow.common.config.AgentProperties;
import com.contentflow.common.exception.AppException;
import com.contentflow.content.dto.ContentDtos;
import com.contentflow.content.service.ContentService;
import com.contentflow.internal.dto.InternalDtos;
import com.contentflow.project.entity.ProjectEntity;
import com.contentflow.project.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalController {
    private final ProjectService projectService;
    private final ContentService contentService;
    private final AgentProperties properties;

    public InternalController(ProjectService projectService, ContentService contentService, AgentProperties properties) {
        this.projectService = projectService;
        this.contentService = contentService;
        this.properties = properties;
    }

    @GetMapping("/projects/{id}/context")
    public ApiResponse<InternalDtos.ProjectContextResponse> context(@RequestHeader("X-Internal-Token") String token,
                                                                    @PathVariable Long id) {
        verify(token);
        ProjectEntity project = projectService.detailForInternal(id);
        return ApiResponse.ok(new InternalDtos.ProjectContextResponse(project.getId(), project.getOwnerId(),
                project.getName(), project.getDescription()));
    }

    @PostMapping("/projects/{id}/contents")
    public ApiResponse<ContentDtos.ContentResponse> save(@RequestHeader("X-Internal-Token") String token,
                                                         @PathVariable Long id,
                                                         @RequestBody InternalDtos.SaveContentRequest request) {
        verify(token);
        return ApiResponse.ok(contentService.save(request.ownerId(), id, request.title(), request.summary(), request.markdown()));
    }

    private void verify(String token) {
        if (!properties.internalToken().equals(token)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "invalid internal token");
        }
    }
}
