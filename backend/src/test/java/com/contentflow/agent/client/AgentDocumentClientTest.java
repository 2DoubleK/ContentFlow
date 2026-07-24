package com.contentflow.agent.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentflow.common.config.AgentProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AgentDocumentClientTest {
    @Test
    void sendsDocumentIndexRequestAsJson() throws Exception {
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> upgrade = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/documents/index", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();

        try {
            AgentDocumentClient client = new AgentDocumentClient(new AgentProperties(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "test-token", 5_000, 240_000));

            client.index(4L, 6L, 9L, "guide.md");

            assertThat(contentType.get()).startsWith("application/json");
            assertThat(upgrade.get()).isNull();
            assertThat(requestBody.get())
                    .contains("\"projectId\":4")
                    .contains("\"documentId\":6")
                    .contains("\"userId\":9")
                    .contains("\"fileName\":\"guide.md\"");
        } finally {
            server.stop(0);
        }
    }
}
