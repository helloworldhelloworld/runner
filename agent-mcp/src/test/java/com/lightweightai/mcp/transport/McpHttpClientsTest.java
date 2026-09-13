package com.lightweightai.mcp.transport;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertSame;

class McpHttpClientsTest {

    @Test
    void sharedBuilderReturnsTheSuppliedClientAfterSdkConfigurationCalls() {
        HttpClient client = HttpClient.newBuilder().build();

        HttpClient result = McpHttpClients.sharing(client)
            .connectTimeout(Duration.ofSeconds(1))
            .version(HttpClient.Version.HTTP_2)
            .build();

        assertSame(client, result);
    }

    @Test
    void sharedClientUsesHttp11() {
        assertSame(HttpClient.Version.HTTP_1_1, McpHttpClients.shared().version());
    }
}
