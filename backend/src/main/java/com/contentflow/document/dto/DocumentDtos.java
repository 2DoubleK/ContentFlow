package com.contentflow.document.dto;

public class DocumentDtos {
    public record DocumentResponse(Long id, Long projectId, String filename, String status, String errorMessage) {}
}
