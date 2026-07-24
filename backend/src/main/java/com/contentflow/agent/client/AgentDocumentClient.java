package com.contentflow.agent.client;

import com.contentflow.common.config.AgentProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Map;

@Component
public class AgentDocumentClient {
    private final RestClient restClient;

    public AgentDocumentClient(AgentProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .defaultHeader("X-Internal-Token", properties.internalToken())
                .build();
    }

    public void index(Long projectId, Long documentId, Long userId, String filename) {
        restClient.post().uri("/documents/index")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("projectId", projectId, "documentId", documentId, "userId", userId, "fileName", filename))
                .retrieve()
                .toBodilessEntity();
    }

    public void delete(Long documentId) {
        restClient.delete().uri("/documents/{id}", documentId).retrieve().toBodilessEntity();
    }
}
