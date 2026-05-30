package com.lightweightai.mcp.transport;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * MCP WebSocket 客户端 Transport（自定义 Custom Transport）
 *
 * <p>实现 MCP SDK 的 {@link McpClientTransport}，底层使用 JDK 标准
 * {@link java.net.http.WebSocket}（Java 11+），不引入额外依赖。相比 Streamable HTTP
 * 的无状态短连接，WebSocket 提供长连接复用，避免每次 {@code tools/call} 重建连接。
 *
 * <p>消息严格遵循 MCP 协议的 JSON-RPC 2.0 格式，与 Streamable HTTP 完全一致。
 *
 * <p>内置心跳保活（{@code sendPing}）与断连重连（指数退避）。
 *
 * <h2>限制</h2>
 * 断连重连仅在 transport 层重建 WebSocket 连接；MCP 会话级别的重新 {@code initialize}
 * 不在本实现范围内（v1）。
 */
public class WebSocketMcpClientTransport implements McpClientTransport {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketMcpClientTransport.class);

    private static final long MAX_RECONNECT_DELAY_MS = 60_000L;

    private final URI uri;
    private final Map<String, String> headers;
    private final Duration connectTimeout;
    private final Duration pingInterval;
    private final int maxReconnectAttempts;
    private final Duration reconnectBaseDelay;
    private final McpJsonMapper jsonMapper;

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mcp-ws-" );
            t.setDaemon(true);
            return t;
        });
    private final AtomicInteger reconnectCount = new AtomicInteger(0);

    private volatile Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler;
    private volatile WebSocket webSocket;
    private volatile boolean connected;
    private volatile boolean closed;
    private volatile ScheduledFuture<?> pingTask;
    /**
     * 仅在 {@code onOpen} 真正触发后才完成，用于把 {@link #connect} 返回的 Mono 与握手完成对齐。
     * 若不等待该 future，{@code buildAsync} 完成（仅代表 WebSocket 对象已构建）即让 connect 完成，
     * 此时 {@link #connected} 仍为 false，紧接着 MCP SDK 链式发送 initialize 会被
     * {@link #sendMessage} 以 "WebSocket not connected" 拒绝。
     */
    private volatile java.util.concurrent.CompletableFuture<Void> openFuture;

    private WebSocketMcpClientTransport(Builder builder) {
        this.uri = URI.create(builder.url);
        this.headers = Map.copyOf(builder.headers);
        this.connectTimeout = builder.connectTimeout;
        this.pingInterval = builder.pingInterval;
        this.maxReconnectAttempts = builder.maxReconnectAttempts;
        this.reconnectBaseDelay = builder.reconnectBaseDelay;
        this.jsonMapper = builder.jsonMapper != null ? builder.jsonMapper : McpJsonMapper.getDefault();
    }

    public static Builder builder(String url) {
        return new Builder(url);
    }

    // ==================== McpClientTransport ====================

    @Override
    public Mono<Void> connect(
            Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
        this.handler = handler;
        java.util.concurrent.CompletableFuture<Void> open = new java.util.concurrent.CompletableFuture<>();
        this.openFuture = open;
        return Mono.fromFuture(() -> {
            HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();

            WebSocket.Builder wsBuilder = httpClient.newWebSocketBuilder();
            headers.forEach(wsBuilder::header);

            return wsBuilder.buildAsync(uri, new InboundListener())
                .thenAccept(ws -> this.webSocket = ws);
        })
        // buildAsync 完成仅代表 WebSocket 对象已构建，onOpen 可能尚未触发 → 必须再等握手完成，
        // 否则 connect 完成时 connected 仍为 false，SDK 紧接着的 sendMessage(initialize) 会失败。
        .then(Mono.fromFuture(open))
        .timeout(connectTimeout);
    }

    @Override
    public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
        // 守卫必须放在 defer 内 → 在订阅期（而非装配期）求值。否则 SDK 以
        // connect().then(sendMessage(initialize)) 组合时，sendMessage 在 connect 完成前就被构造，
        // 此刻 connected=false 会把 Mono.error 锁死，即便订阅时早已连接。
        return Mono.defer(() -> {
            WebSocket ws = this.webSocket;
            if (!connected || ws == null) {
                return Mono.error(new IllegalStateException("WebSocket not connected: " + uri));
            }
            try {
                String json = jsonMapper.writeValueAsString(message);
                return Mono.fromFuture(ws.sendText(json, true).thenApply(w -> null));
            } catch (Exception e) {
                return Mono.error(e);
            }
        });
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.fromRunnable(() -> {
            closed = true;
            connected = false;
            cancelPing();
            scheduler.shutdownNow();
            WebSocket ws = this.webSocket;
            if (ws != null) {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "");
            }
        });
    }

    @Override
    public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
        return jsonMapper.convertValue(data, typeRef);
    }

    public boolean isConnected() {
        return connected;
    }

    // ==================== 内部机制 ====================

    private class InboundListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket ws) {
            connected = true;
            reconnectCount.set(0);
            ws.request(1);
            startPingScheduler();
            // 握手真正完成，解锁 connect() 返回的 Mono（此刻 connected 已为 true）
            java.util.concurrent.CompletableFuture<Void> open = openFuture;
            if (open != null) {
                open.complete(null);
            }
            logger.info("MCP WebSocket connected: {}", uri);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String raw = buffer.toString();
                buffer.setLength(0);
                try {
                    McpSchema.JSONRPCMessage msg =
                        McpSchema.deserializeJsonRpcMessage(jsonMapper, raw);
                    Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> h = handler;
                    if (h != null) {
                        h.apply(Mono.just(msg)).subscribe();
                    }
                } catch (Exception e) {
                    logger.error("Failed to handle inbound MCP message from {}: {}", uri, e.getMessage());
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            connected = false;
            failOpenFuture(new IllegalStateException(
                "WebSocket closed before open (" + statusCode + " " + reason + "): " + uri));
            logger.warn("MCP WebSocket closed ({} {}): {}", statusCode, reason, uri);
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            connected = false;
            failOpenFuture(error);
            logger.warn("MCP WebSocket error on {}: {}", uri, error.getMessage());
            scheduleReconnect();
        }

        /** 握手前失败时让 connect() 立即以异常结束，避免 Mono 永久挂起到 timeout。 */
        private void failOpenFuture(Throwable cause) {
            java.util.concurrent.CompletableFuture<Void> open = openFuture;
            if (open != null) {
                open.completeExceptionally(cause);
            }
        }
    }

    private void startPingScheduler() {
        if (pingInterval == null || pingInterval.isZero() || pingInterval.isNegative()) {
            return;
        }
        cancelPing();
        long period = pingInterval.toMillis();
        pingTask = scheduler.scheduleAtFixedRate(() -> {
            WebSocket ws = this.webSocket;
            if (connected && ws != null) {
                ws.sendPing(ByteBuffer.wrap("ping".getBytes(StandardCharsets.UTF_8)))
                    .exceptionally(ex -> {
                        connected = false;
                        scheduleReconnect();
                        return null;
                    });
            }
        }, period, period, TimeUnit.MILLISECONDS);
    }

    private void cancelPing() {
        ScheduledFuture<?> task = this.pingTask;
        if (task != null) {
            task.cancel(false);
            this.pingTask = null;
        }
    }

    private void scheduleReconnect() {
        if (closed) {
            return;
        }
        cancelPing();
        int attempt = reconnectCount.incrementAndGet();
        if (attempt > maxReconnectAttempts) {
            logger.error("MCP WebSocket exceeded max reconnect attempts ({}) for {}",
                maxReconnectAttempts, uri);
            return;
        }
        long delay = computeReconnectDelayMillis(attempt);
        logger.info("MCP WebSocket reconnect attempt {}/{} in {}ms: {}",
            attempt, maxReconnectAttempts, delay, uri);
        try {
            scheduler.schedule(() -> {
                if (!closed) {
                    connect(this.handler).subscribe();
                }
            }, delay, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // scheduler already shut down via closeGracefully()
        }
    }

    long computeReconnectDelayMillis(int attempt) {
        long base = reconnectBaseDelay.toMillis();
        int shift = Math.min(attempt - 1, 20);
        long delay = base * (1L << shift);
        if (delay <= 0 || delay > MAX_RECONNECT_DELAY_MS) {
            return MAX_RECONNECT_DELAY_MS;
        }
        return delay;
    }

    // ==================== Builder ====================

    public static class Builder {
        private final String url;
        private Map<String, String> headers = Map.of();
        private Duration connectTimeout = Duration.ofSeconds(30);
        private Duration pingInterval = Duration.ofSeconds(30);
        private int maxReconnectAttempts = 10;
        private Duration reconnectBaseDelay = Duration.ofMillis(1000);
        private McpJsonMapper jsonMapper;

        private Builder(String url) {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("WebSocket url is required");
            }
            this.url = url;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers != null ? headers : Map.of();
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder pingInterval(Duration pingInterval) {
            this.pingInterval = pingInterval;
            return this;
        }

        public Builder maxReconnectAttempts(int maxReconnectAttempts) {
            this.maxReconnectAttempts = maxReconnectAttempts;
            return this;
        }

        public Builder reconnectBaseDelay(Duration reconnectBaseDelay) {
            this.reconnectBaseDelay = reconnectBaseDelay;
            return this;
        }

        public Builder jsonMapper(McpJsonMapper jsonMapper) {
            this.jsonMapper = jsonMapper;
            return this;
        }

        public WebSocketMcpClientTransport build() {
            return new WebSocketMcpClientTransport(this);
        }
    }
}
