package com.contentflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "contentflow.agent")
public record AgentProperties(
        String baseUrl,
        String internalToken,
        int connectTimeoutMillis,
        int readTimeoutMillis) {
    public AgentProperties {
        connectTimeoutMillis = connectTimeoutMillis > 0 ? connectTimeoutMillis : 5_000;
        readTimeoutMillis = readTimeoutMillis > 0 ? readTimeoutMillis : 240_000;
    }
}
