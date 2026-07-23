package com.contentflow.project;

public class ProjectDtos {
    public record ProjectRequest(String name, String description) {}
    public record ProjectResponse(Long id, String name, String description) {}
}
