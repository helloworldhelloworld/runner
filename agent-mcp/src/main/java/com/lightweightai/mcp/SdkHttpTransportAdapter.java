package com.lightweightai.mcp;

import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;

import java.lang.reflect.Field;
import java.net.http.HttpClient;

/** Compatibility bridge for caller-built HTTP transports from the pinned MCP SDK 0.17.0. */
final class SdkHttpTransportAdapter {
    private SdkHttpTransportAdapter() {
    }

    static McpClientTransport manage(McpClientTransport transport) {
        Class<?> sdkType;
        if (transport instanceof HttpClientStreamableHttpTransport) {
            sdkType = HttpClientStreamableHttpTransport.class;
        } else if (transport instanceof HttpClientSseClientTransport) {
            sdkType = HttpClientSseClientTransport.class;
        } else {
            // Includes already-managed, WebSocket, stdio and custom transports.
            return transport;
        }

        // The SDK has no public HttpClient accessor/setter. Install the request
        // scope before McpClient connects, keeping the caller's transport and all
        // of its endpoint, header, mapper and HTTP-client settings intact.
        // This is deliberately limited to the two known SDK implementations.
        synchronized (transport) {
            try {
                Field field = sdkType.getDeclaredField("httpClient");
                if (field.getType() != HttpClient.class || !field.trySetAccessible()) {
                    throw new IllegalStateException("SDK HTTP client field is inaccessible or incompatible");
                }
                HttpClient original = (HttpClient) field.get(transport);
                RequestTrackingHttpClient scoped = original instanceof RequestTrackingHttpClient tracked
                    ? tracked : new RequestTrackingHttpClient(original);
                field.set(transport, scoped);
                return new ManagedHttpClientTransport(scoped, transport);
            } catch (ReflectiveOperationException | RuntimeException error) {
                // Fail at construction rather than silently reintroducing delayed
                // disconnects if a future SDK release changes its internals.
                throw new IllegalStateException(
                    "Cannot manage " + sdkType.getSimpleName() + "; check MCP SDK compatibility", error);
            }
        }
    }
}
