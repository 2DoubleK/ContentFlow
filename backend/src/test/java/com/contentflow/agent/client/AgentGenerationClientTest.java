package com.contentflow.agent.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentflow.common.config.AgentProperties;
import com.contentflow.content.dto.ContentDtos;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AgentGenerationClientTest {
    @Test
    void sendsHttp11JsonRequestWithInternalToken() throws Exception {
        AtomicReference<String> upgrade = new AtomicReference<>();
        AtomicReference<String> token = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/generate", exchange -> {
            upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            token.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ("{\"title\":\"Title\",\"summary\":\"Summary\","
                    + "\"content\":\"# Body\",\"markdown\":\"# Body\",\"tags\":[],\"references\":[]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            AgentGenerationClient client = new AgentGenerationClient(new AgentProperties(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "test-token", 5_000, 240_000));

            ContentDtos.AgentGenerateResponse response = client.generate(9L, 7L, 4L, "request-1", "write article");

            assertThat(response.title()).isEqualTo("Title");
            assertThat(upgrade.get()).isNull();
            assertThat(token.get()).isEqualTo("test-token");
            assertThat(requestBody.get()).contains("\"userId\":9").contains("\"projectId\":7")
                    .contains("\"conversationId\":4").contains("\"requestId\":\"request-1\"");
        } finally {
            server.stop(0);
        }
    }
}
