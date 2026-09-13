package com.lightweightai.mcp.transport;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;

/** Process-scoped HTTP client resources used by MCP transports. */
public final class McpHttpClients {

    private static final HttpClient SHARED = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(30))
        .build();

    private McpHttpClients() {
    }

    /** Returns the process-scoped MCP HTTP client. */
    public static HttpClient shared() {
        return SHARED;
    }

    /** Adapts an existing client for MCP SDK HTTP/SSE builders. */
    public static HttpClient.Builder sharing(HttpClient client) {
        return new SharedHttpClientBuilder(Objects.requireNonNull(client, "client"));
    }
}
