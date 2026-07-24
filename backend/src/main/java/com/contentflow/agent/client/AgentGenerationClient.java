package com.contentflow.agent.client;

import com.contentflow.common.config.AgentProperties;
import com.contentflow.content.dto.ContentDtos;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AgentGenerationClient {
    private final RestClient restClient;

    public AgentGenerationClient(AgentProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("X-Internal-Token", properties.internalToken())
                .build();
    }

    public ContentDtos.AgentGenerateResponse generate(Long userId, Long projectId, String prompt) {
        return restClient.post().uri("/generate")
                .body(new ContentDtos.AgentGenerateRequest(userId, projectId, null, prompt))
                .retrieve()
                .body(ContentDtos.AgentGenerateResponse.class);
    }
}
