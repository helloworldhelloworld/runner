package com.lightweightai.mcp;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Adds request ownership to SDK HTTP transports without taking ownership of the
 * shared HttpClient. SDK 0.17.0 only wires body cancellation after response headers
 * arrive, and waits for DELETE before disposing streams. Track the original HTTP
 * futures so either condition cannot keep a closed transport's requests alive.
 */
final class ManagedHttpClientTransport implements McpClientTransport {
    private static final Logger logger = LoggerFactory.getLogger(ManagedHttpClientTransport.class);
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5);

    private final RequestTrackingHttpClient httpClient;
    private final McpClientTransport delegate;
    private final Mono<Void> close;

    ManagedHttpClientTransport(HttpClient sharedClient,
                              Function<HttpClient.Builder, McpClientTransport> factory) {
        this.httpClient = new RequestTrackingHttpClient(sharedClient);
        this.delegate = factory.apply(McpHttpClients.sharing(httpClient));
        this.close = Mono.defer(() -> {
            // Cancel GET/POST even before headers arrive. Only the session DELETE
            // may start once closing begins; no shared-client shutdown is involved.
            httpClient.beginClose();
            return delegate.closeGracefully();
        }).timeout(CLOSE_TIMEOUT)
            .doOnTerminate(httpClient::finishClose)
            .doOnCancel(httpClient::finishClose)
            .doOnError(error -> logger.warn(
                "MCP HTTP session cleanup failed; local requests cancelled: {}", error.toString()))
            // Execute once, including concurrent close calls. Cleanup continues
            // if a caller stops waiting, and is bounded by CLOSE_TIMEOUT.
            .cache();
    }

    @Override
    public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
        return Mono.defer(() -> httpClient.isClosing()
            ? Mono.error(new IllegalStateException("MCP HTTP transport is closed"))
            : delegate.connect(handler));
    }

    @Override
    public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
        return Mono.defer(() -> httpClient.isClosing()
            ? Mono.error(new IllegalStateException("MCP HTTP transport is closed"))
            : delegate.sendMessage(message));
    }

    @Override
    public void close() {
        // The cached operation reports failures once, even with repeated close().
        closeGracefully().subscribe(null, ignored -> { });
    }

    @Override
    public Mono<Void> closeGracefully() {
        return close;
    }

    @Override
    public void setExceptionHandler(Consumer<Throwable> handler) {
        delegate.setExceptionHandler(error -> {
            // Cancellation caused by our own close is not a connection failure.
            if (!httpClient.isClosing() && handler != null) {
                handler.accept(error);
            }
        });
    }

    @Override
    public List<String> protocolVersions() {
        return delegate.protocolVersions();
    }

    @Override
    public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
        return delegate.unmarshalFrom(data, typeRef);
    }

    HttpClient sharedHttpClient() {
        return httpClient.sharedClient();
    }
}
