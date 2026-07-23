package com.contentflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "contentflow.agent")
public record AgentProperties(String baseUrl, String internalToken) {
}
