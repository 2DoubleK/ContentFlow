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
import java.util.UUID;
import com.contentflow.storage.MinioService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {
    private final DocumentMapper documentMapper;
    private final ProjectService projectService;
    private final AgentDocumentClient agentDocumentClient;
    private final MinioService minioService;

    public DocumentService(DocumentMapper documentMapper, ProjectService projectService, AgentDocumentClient agentDocumentClient) {
        this(documentMapper, projectService, agentDocumentClient, null);
    }
    public DocumentService(DocumentMapper documentMapper, ProjectService projectService, AgentDocumentClient agentDocumentClient, MinioService minioService) {
        this.documentMapper = documentMapper;
        this.projectService = projectService;
        this.agentDocumentClient = agentDocumentClient;
        this.minioService = minioService;
    }

    public DocumentDtos.DocumentResponse upload(Long ownerId, Long projectId, MultipartFile file) {
        projectService.requireOwned(ownerId, projectId);
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        if (!List.of("txt", "md", "markdown", "pdf").contains(extension) || file.getSize() > 20 * 1024 * 1024) {
            throw new AppException(HttpStatus.BAD_REQUEST, "unsupported file");
        }
        DocumentEntity document = new DocumentEntity();
        document.setOwnerId(ownerId);
        document.setProjectId(projectId);
        document.setFilename(filename);
        document.setStatus("INDEXING");
        document.setFileExt(extension);
        document.setMimeType(file.getContentType());
        document.setFileSize(file.getSize());
        documentMapper.insert(document);
        try {
            String objectKey = "user-" + ownerId + "/project-" + projectId + "/document-" + document.getId() + "/" + UUID.randomUUID() + "-" + filename;
            document.setFileUrl(objectKey);
            if (minioService != null) minioService.upload(file.getInputStream(), file.getSize(), file.getContentType(), objectKey);
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

    public DocumentDtos.DocumentResponse detail(Long ownerId, Long documentId) {
        return toResponse(requireOwned(ownerId, documentId));
    }

    public void delete(Long ownerId, Long documentId) {
        DocumentEntity document = requireOwned(ownerId, documentId);
        if (minioService != null && document.getFileUrl() != null) minioService.delete(document.getFileUrl());
        documentMapper.deleteById(documentId);
    }

    public DocumentDtos.DocumentResponse retry(Long ownerId, Long documentId) {
        DocumentEntity document = requireOwned(ownerId, documentId);
        document.setStatus("INDEXING");
        document.setErrorMessage(null);
        documentMapper.updateById(document);
        return toResponse(document);
    }

    private DocumentEntity requireOwned(Long ownerId, Long documentId) {
        DocumentEntity document = documentMapper.selectById(documentId);
        if (document == null) throw new AppException(HttpStatus.NOT_FOUND, "document not found");
        projectService.requireOwned(ownerId, document.getProjectId());
        return document;
    }

    DocumentDtos.DocumentResponse toResponse(DocumentEntity document) {
        return new DocumentDtos.DocumentResponse(document.getId(), document.getProjectId(), document.getFilename(),
                document.getStatus(), document.getErrorMessage());
    }
}
