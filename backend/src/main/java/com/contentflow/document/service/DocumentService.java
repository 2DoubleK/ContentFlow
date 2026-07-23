package com.contentflow.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.contentflow.agent.client.AgentDocumentClient;
import com.contentflow.common.exception.AppException;
import com.contentflow.document.dto.DocumentDtos;
import com.contentflow.document.entity.DocumentEntity;
import com.contentflow.document.mapper.DocumentMapper;
import com.contentflow.project.service.ProjectService;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {
    private final DocumentMapper documentMapper;
    private final ProjectService projectService;
    private final AgentDocumentClient agentDocumentClient;

    public DocumentService(DocumentMapper documentMapper, ProjectService projectService, AgentDocumentClient agentDocumentClient) {
        this.documentMapper = documentMapper;
        this.projectService = projectService;
        this.agentDocumentClient = agentDocumentClient;
    }

    public DocumentDtos.DocumentResponse upload(Long ownerId, Long projectId, MultipartFile file) {
        projectService.requireOwned(ownerId, projectId);
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!filename.endsWith(".txt") && !filename.endsWith(".md")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "only txt and md files are supported");
        }
        DocumentEntity document = new DocumentEntity();
        document.setOwnerId(ownerId);
        document.setProjectId(projectId);
        document.setFilename(filename);
        document.setStatus("INDEXING");
        documentMapper.insert(document);
        try {
            agentDocumentClient.index(projectId, document.getId(), filename, file.getBytes());
            document.setStatus("READY");
        } catch (IOException | RuntimeException ex) {
            document.setStatus("FAILED");
            document.setErrorMessage(ex.getMessage());
        }
        documentMapper.updateById(document);
        return toResponse(document);
    }

    public List<DocumentDtos.DocumentResponse> list(Long ownerId, Long projectId) {
        projectService.requireOwned(ownerId, projectId);
        return documentMapper.selectList(new LambdaQueryWrapper<DocumentEntity>()
                        .eq(DocumentEntity::getProjectId, projectId)
                        .orderByDesc(DocumentEntity::getId))
                .stream().map(this::toResponse).toList();
    }

    DocumentDtos.DocumentResponse toResponse(DocumentEntity document) {
        return new DocumentDtos.DocumentResponse(document.getId(), document.getProjectId(), document.getFilename(),
                document.getStatus(), document.getErrorMessage());
    }
}
