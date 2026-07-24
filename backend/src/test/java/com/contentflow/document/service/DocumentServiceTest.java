package com.contentflow.document.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.contentflow.agent.client.AgentDocumentClient;
import com.contentflow.common.exception.AppException;
import com.contentflow.document.dto.DocumentDtos;
import com.contentflow.document.entity.DocumentEntity;
import com.contentflow.document.mapper.DocumentMapper;
import com.contentflow.project.service.ProjectService;
import com.contentflow.storage.MinioService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentServiceTest {
    @Test
    void rejectsUnsupportedFileTypeBeforeIndexing() {
        DocumentService service = new DocumentService(mock(DocumentMapper.class), mock(ProjectService.class), mock(AgentDocumentClient.class));
        MockMultipartFile file = new MockMultipartFile("file", "demo.exe", "application/octet-stream", "x".getBytes());

        assertThatThrownBy(() -> service.upload(1L, 2L, file))
                .isInstanceOf(AppException.class)
                .hasMessage("unsupported file");
    }

    @Test
    void opensOwnedDocumentFromMinioForDownload() throws Exception {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        ProjectService projectService = mock(ProjectService.class);
        MinioService minioService = mock(MinioService.class);
        DocumentEntity document = new DocumentEntity();
        document.setId(9L);
        document.setProjectId(2L);
        document.setFilename("guide.md");
        document.setMimeType("text/markdown");
        document.setFileUrl("user-1/project-2/document-9/guide.md");
        when(documentMapper.selectById(9L)).thenReturn(document);
        when(minioService.get(document.getFileUrl())).thenReturn(new ByteArrayInputStream("knowledge".getBytes(StandardCharsets.UTF_8)));
        DocumentService service = new DocumentService(documentMapper, projectService, mock(AgentDocumentClient.class), minioService);

        DocumentService.DownloadFile file = service.download(1L, 9L);

        assertThat(file.filename()).isEqualTo("guide.md");
        assertThat(file.mimeType()).isEqualTo("text/markdown");
        assertThat(new String(file.input().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("knowledge");
        verify(projectService).requireOwned(1L, 2L);
        verify(minioService).get(document.getFileUrl());
    }
}
