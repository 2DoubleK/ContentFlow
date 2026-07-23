package com.contentflow.project.dto;

public class ProjectDtos {
    public record ProjectRequest(
            String name,
            String description,
            String platform,
            String domain,
            String positioning,
            String targetAudience,
            String contentStyle
    ) {}

    public record ProjectResponse(
            Long id,
            String name,
            String description,
            String platform,
            String domain,
            String positioning,
            String targetAudience,
            String contentStyle
    ) {}
}
