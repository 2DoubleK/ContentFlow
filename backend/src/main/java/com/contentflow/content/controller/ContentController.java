package com.contentflow.content.controller;

import com.contentflow.common.api.ApiResponse;
import com.contentflow.content.dto.ContentDtos;
import com.contentflow.content.service.ContentService;
import com.contentflow.security.CurrentUser;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import com.contentflow.common.api.PageResult;

@RestController
public class ContentController {
    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @PostMapping("/api/contents")
    public ApiResponse<ContentDtos.ContentResponse> create(@AuthenticationPrincipal CurrentUser user,
                                                            @RequestBody ContentDtos.CreateRequest request) {
        return ApiResponse.ok(contentService.create(user.id(), request));
    }

    @PostMapping("/api/projects/{projectId}/generate")
    public ApiResponse<ContentDtos.ContentResponse> generate(@AuthenticationPrincipal CurrentUser user,
                                                             @PathVariable Long projectId,
                                                             @RequestBody ContentDtos.GenerateRequest request) {
        return ApiResponse.ok(contentService.generate(user.id(), projectId, request));
    }

    @GetMapping("/api/projects/{projectId}/contents")
    public ApiResponse<List<ContentDtos.ContentResponse>> list(@AuthenticationPrincipal CurrentUser user,
                                                               @PathVariable Long projectId) {
        return ApiResponse.ok(contentService.list(user.id(), projectId));
    }

    @GetMapping("/api/contents")
    public ApiResponse<PageResult<ContentDtos.ContentResponse>> page(@AuthenticationPrincipal CurrentUser user,
            @RequestParam Long projectId, @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(contentService.page(user.id(), projectId, status, keyword, page, size));
    }

    @GetMapping("/api/contents/{id}")
    public ApiResponse<ContentDtos.ContentResponse> detail(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(contentService.detail(user.id(), id));
    }

    @PutMapping("/api/contents/{id}")
    public ApiResponse<ContentDtos.ContentResponse> update(@AuthenticationPrincipal CurrentUser user,
                                                           @PathVariable Long id,
                                                           @RequestBody ContentDtos.UpdateRequest request) {
        return ApiResponse.ok(contentService.update(user.id(), id, request));
    }

    @DeleteMapping("/api/contents/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        contentService.delete(user.id(), id);
        return ApiResponse.ok(null);
    }
}
