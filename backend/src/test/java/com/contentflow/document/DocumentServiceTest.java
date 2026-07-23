package com.contentflow.document;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.contentflow.common.AppException;
import com.contentflow.project.ProjectService;
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
