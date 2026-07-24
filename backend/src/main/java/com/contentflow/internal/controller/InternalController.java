package com.contentflow.internal.controller;

import com.contentflow.common.api.ApiResponse;
import com.contentflow.common.config.AgentProperties;
import com.contentflow.common.exception.AppException;
import com.contentflow.content.dto.ContentDtos;
import com.contentflow.content.service.ContentService;
import com.contentflow.document.service.DocumentService;
import com.contentflow.internal.dto.InternalDtos;
import com.contentflow.project.entity.ProjectEntity;
import com.contentflow.project.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalController {
    private final ProjectService projectService;
    private final ContentService contentService;
    private final DocumentService documentService;
    private final AgentProperties properties;

    public InternalController(ProjectService projectService, ContentService contentService, DocumentService documentService,
                              AgentProperties properties) {
        this.projectService = projectService;
        this.contentService = contentService;
        this.documentService = documentService;
        this.properties = properties;
    }

    @GetMapping("/projects/{id}/context")
    public ApiResponse<InternalDtos.ProjectContextResponse> context(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                                                    @PathVariable Long id) {
        verify(token);
        ProjectEntity project = projectService.detailForInternal(id);
        return ApiResponse.ok(new InternalDtos.ProjectContextResponse(project.getId(), project.getOwnerId(), project.getName(),
                project.getDescription(), project.getPlatform(), project.getDomain(), project.getPositioning(),
                project.getTargetAudience(), project.getContentStyle()));
    }

    @PostMapping("/projects/{id}/contents")
    public ApiResponse<ContentDtos.ContentResponse> save(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                                         @PathVariable Long id,
                                                         @RequestBody InternalDtos.SaveContentRequest request) {
        verify(token);
        return ApiResponse.ok(contentService.save(request.ownerId(), id, request.title(), request.summary(), request.markdown()));
    }

    @PostMapping("/documents/{id}/chunks")
    public ApiResponse<Void> saveChunks(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                        @PathVariable Long id, @RequestBody InternalDtos.DocumentChunksRequest request) {
        verify(token);
        documentService.saveChunks(id, request.chunks().stream()
                .map(chunk -> new DocumentService.ChunkInput(chunk.chunkIndex(), chunk.content(), chunk.chromaId(), chunk.tokenCount()))
                .toList());
        return ApiResponse.ok(null);
    }

    @PatchMapping("/documents/{id}/status")
    public ApiResponse<Void> updateStatus(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                          @PathVariable Long id, @RequestBody InternalDtos.DocumentStatusRequest request) {
        verify(token);
        documentService.updateIndexStatus(id, request.status(), request.chunkCount() == null ? 0 : request.chunkCount(),
                request.errorMessage());
        return ApiResponse.ok(null);
    }

    private void verify(String token) {
        if (token == null || !properties.internalToken().equals(token)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "invalid internal token");
        }
    }
}
