package com.contentflow.document.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.contentflow.agent.client.AgentDocumentClient;
import com.contentflow.common.exception.AppException;
import com.contentflow.document.dto.DocumentDtos;
import com.contentflow.document.entity.DocumentEntity;
import com.contentflow.document.mapper.DocumentMapper;
import com.contentflow.project.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentServiceTest {
    @Test
    void rejectsUnsupportedFileTypeBeforeIndexing() {
        DocumentService service = new DocumentService(mock(DocumentMapper.class), mock(ProjectService.class), mock(AgentDocumentClient.class));
        MockMultipartFile file = new MockMultipartFile("file", "demo.pdf", "application/pdf", "x".getBytes());

        assertThatThrownBy(() -> service.upload(1L, 2L, file))
                .isInstanceOf(AppException.class)
                .hasMessage("only txt and md files are supported");
    }
}
