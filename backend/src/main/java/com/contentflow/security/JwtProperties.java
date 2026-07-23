package com.contentflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "contentflow.jwt")
public record JwtProperties(String secret, long ttlMinutes) {
}
