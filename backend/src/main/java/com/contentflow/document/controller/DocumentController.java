package com.contentflow.document.controller;

import com.contentflow.common.api.ApiResponse;
import com.contentflow.document.dto.DocumentDtos;
import com.contentflow.document.service.DocumentService;
import com.contentflow.security.CurrentUser;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class DocumentController {
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/api/projects/{projectId}/documents")
    public ApiResponse<DocumentDtos.DocumentResponse> upload(@AuthenticationPrincipal CurrentUser user,
                                                             @PathVariable Long projectId,
                                                             @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(documentService.upload(user.id(), projectId, file));
    }

    @GetMapping("/api/projects/{projectId}/documents")
    public ApiResponse<List<DocumentDtos.DocumentResponse>> list(@AuthenticationPrincipal CurrentUser user,
                                                                 @PathVariable Long projectId) {
        return ApiResponse.ok(documentService.list(user.id(), projectId));
    }

    @GetMapping("/api/documents/{id}")
    public ApiResponse<DocumentDtos.DocumentResponse> detail(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(documentService.detail(user.id(), id));
    }

    @DeleteMapping("/api/documents/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        documentService.delete(user.id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/documents/{id}/retry")
    public ApiResponse<DocumentDtos.DocumentResponse> retry(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(documentService.retry(user.id(), id));
    }
}
