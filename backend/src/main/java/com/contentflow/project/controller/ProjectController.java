package com.contentflow.project;

import com.contentflow.auth.CurrentUser;
import com.contentflow.common.ApiResponse;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ApiResponse<List<ProjectDtos.ProjectResponse>> list(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(projectService.list(user.id()));
    }

    @PostMapping
    public ApiResponse<ProjectDtos.ProjectResponse> create(@AuthenticationPrincipal CurrentUser user,
                                                           @RequestBody ProjectDtos.ProjectRequest request) {
        return ApiResponse.ok(projectService.create(user.id(), request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectDtos.ProjectResponse> detail(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(projectService.detail(user.id(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProjectDtos.ProjectResponse> update(@AuthenticationPrincipal CurrentUser user,
                                                           @PathVariable Long id,
                                                           @RequestBody ProjectDtos.ProjectRequest request) {
        return ApiResponse.ok(projectService.update(user.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        projectService.delete(user.id(), id);
        return ApiResponse.ok(null);
    }
}
