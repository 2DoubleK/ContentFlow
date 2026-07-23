package com.contentflow.document;

import com.contentflow.config.AgentProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class AgentDocumentClient {
    private final RestClient restClient;

    public AgentDocumentClient(AgentProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("X-Internal-Token", properties.internalToken())
                .build();
    }

    public void index(Long projectId, Long documentId, String filename, byte[] bytes) {
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("projectId", projectId.toString());
        body.add("documentId", documentId.toString());
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        restClient.post().uri("/documents/index")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
