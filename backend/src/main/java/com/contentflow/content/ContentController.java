package com.contentflow.content;

import com.contentflow.auth.CurrentUser;
import com.contentflow.common.ApiResponse;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ContentController {
    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
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

    @GetMapping("/api/contents/{id}")
    public ApiResponse<ContentDtos.ContentResponse> detail(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(contentService.detail(user.id(), id));
    }
}
