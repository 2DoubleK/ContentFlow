package com.contentflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "contentflow.jwt")
public record JwtProperties(String secret, long ttlMinutes) {
}
