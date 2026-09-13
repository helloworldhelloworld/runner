package com.lightweightai.mcp;

import com.lightweightai.mcp.transport.McpHttpClients;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertSame;

class SharedHttpClientTransportFactoryTest {

    @Test
    void legacyFactoryUsesOneSharedClientForHttpSseAndWebSocket() throws Exception {
        HttpClient shared = McpHttpClients.shared();

        McpClientTransport http = ToolClient.Builder.createTransport(
            "http", McpConfiguration.ServerConfig.streamableHttp("http://localhost:8080/mcp"), null);
        McpClientTransport sse = ToolClient.Builder.createTransport(
            "sse", McpConfiguration.ServerConfig.sse("http://localhost:8080/sse"), null);
        McpClientTransport websocket = ToolClient.Builder.createTransport(
            "ws", McpConfiguration.ServerConfig.ws("ws://localhost:8080/mcp"), null);

        assertSame(shared, field(http, "httpClient"));
        assertSame(shared, field(sse, "httpClient"));
        assertSame(shared, field(websocket, "httpClient"));

        websocket.closeGracefully().block();
    }

    @Test
    void fourArgumentFactoryUsesCallerSuppliedClient() throws Exception {
        HttpClient supplied = HttpClient.newBuilder().build();

        McpClientTransport http = ToolClient.Builder.createTransport(
            "http", McpConfiguration.ServerConfig.streamableHttp("http://localhost:8080/mcp"),
            null, supplied);
        McpClientTransport websocket = ToolClient.Builder.createTransport(
            "ws", McpConfiguration.ServerConfig.ws("ws://localhost:8080/mcp"),
            null, supplied);

        assertSame(supplied, field(http, "httpClient"));
        assertSame(supplied, field(websocket, "httpClient"));
        websocket.closeGracefully().block();
        supplied.close();
    }

    private static HttpClient field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (HttpClient) field.get(target);
    }
}
