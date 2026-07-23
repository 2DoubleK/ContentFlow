package com.contentflow.auth.dto;

public class AuthDtos {
    public record RegisterRequest(String username, String password) {}
    public record LoginRequest(String username, String password) {}
    public record AuthResponse(String token, Long userId, String username) {}
    public record CurrentUserResponse(Long id, String username, String role) {}
}
