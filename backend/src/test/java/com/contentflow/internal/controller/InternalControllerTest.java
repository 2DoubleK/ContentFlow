package com.contentflow.internal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.contentflow.common.config.AgentProperties;
import com.contentflow.document.service.DocumentService;
import com.contentflow.internal.dto.InternalDtos;
import com.contentflow.content.service.ContentService;
import com.contentflow.project.service.ProjectService;
import java.util.List;
import org.junit.jupiter.api.Test;

class InternalControllerTest {
    @Test
    void acceptsChunkAndStatusUpdatesFromTrustedAgent() {
        DocumentService documentService = mock(DocumentService.class);
        InternalController controller = new InternalController(
                mock(ProjectService.class), mock(ContentService.class), documentService,
                new AgentProperties("http://agent", "internal-token"));
        List<InternalDtos.DocumentChunkRequest> chunks = List.of(
                new InternalDtos.DocumentChunkRequest(0, "knowledge", "project_4_document_6_chunk_0", 2));
        List<DocumentService.ChunkInput> expectedChunks = List.of(
                new DocumentService.ChunkInput(0, "knowledge", "project_4_document_6_chunk_0", 2));

        assertThat(controller.saveChunks("internal-token", 6L, new InternalDtos.DocumentChunksRequest(chunks)).success()).isTrue();
        assertThat(controller.updateStatus("internal-token", 6L,
                new InternalDtos.DocumentStatusRequest("READY", 1, null)).success()).isTrue();

        verify(documentService).saveChunks(6L, expectedChunks);
        verify(documentService).updateIndexStatus(6L, "READY", 1, null);
    }
}
