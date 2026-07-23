package com.contentflow.internal.dto;

public class InternalDtos {
    public record ProjectContextResponse(Long projectId, Long ownerId, String name, String description) {}
    public record SaveContentRequest(Long ownerId, String title, String summary, String markdown) {}
}
