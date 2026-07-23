package com.contentflow.document;

public class DocumentDtos {
    public record DocumentResponse(Long id, Long projectId, String filename, String status, String errorMessage) {}
}
