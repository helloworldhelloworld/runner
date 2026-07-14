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
 * <p>内置心跳保活（{@code sendPing}）。
 *
 * <h2>断连语义</h2>
 * 本 transport <b>不在 transport 层自动重连</b>。WebSocket 断开后 {@link #isConnected()}
 * 返回 false，后续 {@link #sendMessage} 立即以 {@code IllegalStateException} 失败上抛。
 * 这与官方 transport 一致：transport 只负责一条连接的生命周期；是否重建连接、以及
 * 重建后必须重新执行 MCP {@code initialize} 握手，都属于会话级关注点，由上层（调用方/
 * {@code McpToolClient}）显式决定。transport 层静默重连只会重建 TCP/WS 通道，却不会
 * 重新初始化 MCP 会话，对有状态 server 会造成"看似已连、实则会话已废"的假象。
 */
public class WebSocketMcpClientTransport implements McpClientTransport {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketMcpClientTransport.class);

    private final URI uri;
    private final Map<String, String> headers;
    private final Duration connectTimeout;
    private final Duration pingInterval;
    private final McpJsonMapper jsonMapper;

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mcp-ws-" );
            t.setDaemon(true);
            return t;
        });

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
        .timeout(connectTimeout)
        // WebSocket 打不开（升级被拒 / 连接被拒 / 建连超时）时，buildAsync 在任何 listener 回调
        // （onOpen/onClose/onError → failOpenFuture）之前就异常，openFuture 会一直悬着。而 MCP SDK
        // 以 fire-and-forget 订阅 connect()，这里的错误被丢弃；同时 sendMessage(initialize) 正阻塞在
        // openFuture 上 → 一直挂到 requestTimeout / SDK 的 initialize 超时，真实原因被吞成
        // "Client failed to initialize by explicit API call"。故在此显式把失败传导给 openFuture，
        // 令等待中的 sendMessage 立即以真实原因失败（issue #195）。已完成则为 no-op（onOpen 先赢无害）。
        .doOnError(open::completeExceptionally);
    }

    @Override
    public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
        // MCP SDK 的 McpClientSession 在构造函数里 connect(handler).subscribe() 是 fire-and-forget，
        // 不等 connect 的 Mono 完成就立刻 sendRequest("initialize")。因此 sendMessage 被调用时
        // WebSocket 往往仍在握手中（connected=false）。这里不能硬性拒绝，而要等 openFuture
        // （onOpen 触发后完成）再发送，否则 initialize 必失败并被 SDK 包成
        // "Client failed to initialize by explicit API call"。
        return Mono.defer(() -> {
            java.util.concurrent.CompletableFuture<Void> open = this.openFuture;
            if (open == null) {
                // 从未调用过 connect()，没有可等待的握手 → 立即报错
                return Mono.error(new IllegalStateException("WebSocket not connected: " + uri));
            }
            Mono<Void> send = Mono.defer(() -> {
                WebSocket ws = this.webSocket;
                if (!connected || ws == null) {
                    return Mono.error(new IllegalStateException("WebSocket not connected: " + uri));
                }
                try {
                    String json = jsonMapper.writeValueAsString(message);
                    return Mono.fromFuture(ws.sendText(json, true).thenApply(w -> (Void) null));
                } catch (Exception e) {
                    return Mono.error(e);
                }
            });
            return Mono.fromFuture(open).then(send).timeout(connectTimeout);
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
            // 必须在置 connected / 完成 openFuture 之前赋值 webSocket：onOpen 可能在 buildAsync 的
            // future 完成（即 thenAccept 里 this.webSocket=ws）之前就触发。openFuture.complete() 会
            // 同步唤醒正在等待的 sendMessage，若此时 this.webSocket 仍为 null，sendMessage 会误判
            // "WebSocket not connected"。用回调参数 ws 直接赋值可消除这个时间差。
            webSocket = ws;
            connected = true;
            ws.request(1);
            startPingScheduler();
            // 握手真正完成，解锁 connect() 返回的 Mono 与所有等待发送的 sendMessage
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
            cancelPing();
            failOpenFuture(new IllegalStateException(
                "WebSocket closed before open (" + statusCode + " " + reason + "): " + uri));
            // 不在 transport 层重连：断连即上抛，重建与重新 initialize 由上层决定（见类注释）。
            logger.warn("MCP WebSocket closed ({} {}): {}", statusCode, reason, uri);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            connected = false;
            cancelPing();
            failOpenFuture(error);
            logger.warn("MCP WebSocket error on {}: {}", uri, error.getMessage());
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
                        // ping 失败视为断连：标记断开并停止心跳，不重连（由上层处置）。
                        connected = false;
                        cancelPing();
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

    // ==================== Builder ====================

    public static class Builder {
        private final String url;
        private Map<String, String> headers = Map.of();
        private Duration connectTimeout = Duration.ofSeconds(30);
        private Duration pingInterval = Duration.ofSeconds(30);
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

        public Builder jsonMapper(McpJsonMapper jsonMapper) {
            this.jsonMapper = jsonMapper;
            return this;
        }

        public WebSocketMcpClientTransport build() {
            return new WebSocketMcpClientTransport(this);
        }
    }
}
