package com.contentflow.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JwtServiceTest {
    @Test
    void createsTokenContainingUserId() {
        JwtService jwtService = new JwtService(new JwtProperties("change-me-change-me-change-me-change-me", 60));

        String token = jwtService.createToken(12L, "demo");

        assertThat(jwtService.parseUserId(token)).isEqualTo(12L);
    }
}
