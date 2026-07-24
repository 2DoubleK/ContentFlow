package com.contentflow.agent.client;

import com.contentflow.common.config.AgentProperties;
import com.contentflow.content.dto.ContentDtos;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AgentGenerationClient {
    private final RestClient restClient;

    public AgentGenerationClient(AgentProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMillis()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMillis()));
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("X-Internal-Token", properties.internalToken())
                .build();
    }

    public ContentDtos.AgentGenerateResponse generate(Long userId, Long projectId, String prompt) {
        return generate(userId, projectId, null, null, prompt);
    }

    public ContentDtos.AgentGenerateResponse generate(
            Long userId, Long projectId, Long conversationId, String prompt) {
        return generate(userId, projectId, conversationId, null, prompt);
    }

    public ContentDtos.AgentGenerateResponse generate(
            Long userId, Long projectId, Long conversationId, String requestId, String prompt) {
        return restClient.post().uri("/generate")
                .body(new ContentDtos.AgentGenerateRequest(userId, projectId, conversationId, requestId, prompt))
                .retrieve()
                .body(ContentDtos.AgentGenerateResponse.class);
    }
}
