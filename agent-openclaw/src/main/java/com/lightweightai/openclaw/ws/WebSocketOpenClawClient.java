package com.lightweightai.openclaw.ws;

import com.lightweightai.openclaw.OpenClawChatRequest;
import com.lightweightai.openclaw.OpenClawClient;
import com.lightweightai.openclaw.OpenClawEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 真实 OpenClaw 客户端：WS 连 OpenClaw Gateway，讲其 JSON-RPC 协议（ADR-014 阶段3）。
 * 用 JDK {@link java.net.http.WebSocket}（无额外依赖，同 agent-mcp 的真 WS transport），帧编解码见
 * {@link OpenClawProtocol}。
 *
 * <p><b>一轮一连接</b>：{@link #chat} 每轮开一条 WS，{@code connect} → {@code chat.send} → 流式
 * {@code event:"chat"}（{@link OpenClawProtocol#parseEvent state 机}）→ {@code RunEnd} 收尾即完成关连接。
 * {@link #cancel} 按 sessionId 在该连接上发 {@code chat.abort}（barge-in）。
 *
 * <p><b>简化点（TODO 真网关）</b>：{@code connect} 省 challenge-response + 设备签名；
 * 持久连接复用 + ADR-011 重连韧性为后续优化（当前一轮一连接，验证语义为先）。本版对忠实假对端
 * （{@code FakeOpenClawServer}）打通往返/打断/失败时序；真网关 auth 待补。
 */
public final class WebSocketOpenClawClient implements OpenClawClient {

    private static final Logger log = LoggerFactory.getLogger(WebSocketOpenClawClient.class);

    private final URI uri;
    private final String token;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ConcurrentHashMap<String, WebSocket> activeBySession = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public WebSocketOpenClawClient(String url) {
        this(url, null);
    }

    public WebSocketOpenClawClient(String url, String token) {
        this.uri = URI.create(url);
        this.token = token;
    }

    @Override
    public Flux<OpenClawEvent> chat(OpenClawChatRequest req) {
        return Flux.create(sink -> {
            StringBuilder buf = new StringBuilder();          // 重组可能分片的 text 帧
            AtomicBoolean terminated = new AtomicBoolean();   // 终态一次性:防重复/post-final 信号(review #183-6/12)
            AtomicReference<WebSocket> wsHolder = new AtomicReference<>();
            WebSocket.Listener listener = new WebSocket.Listener() {
                @Override
                public void onOpen(WebSocket ws) {
                    ws.request(1);
                }

                @Override
                public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                    if (terminated.get()) {
                        return null;   // 本轮已终态,忽略残余帧,不再解析/缓冲(review #183-12)
                    }
                    buf.append(data);
                    if (last) {
                        String msg = buf.toString();
                        buf.setLength(0);
                        OpenClawProtocol.parseEvent(msg).ifPresent(ev -> {
                            if (ev instanceof OpenClawEvent.ErrorEvent e) {
                                if (terminated.compareAndSet(false, true)) {
                                    sink.error(e.cause());
                                } else {
                                    log.warn("OpenClaw post-terminal error frame dropped: {}", e.cause().getMessage());
                                }
                            } else {
                                sink.next(ev);
                                if (ev instanceof OpenClawEvent.RunEnd && terminated.compareAndSet(false, true)) {
                                    sink.complete();   // run 结束 → 流完成（下游收 LLM_COMPLETE 后停）
                                }
                            }
                        });
                    }
                    ws.request(1);
                    return null;
                }

                @Override
                public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                    if (terminated.compareAndSet(false, true)) {
                        sink.complete();
                    }
                    return null;
                }

                @Override
                public void onError(WebSocket ws, Throwable error) {
                    if (terminated.compareAndSet(false, true)) {
                        sink.error(error);   // 传输错误 → 快失败带真因
                    } else {
                        log.warn("OpenClaw WS error after terminal: {}", error.toString());
                    }
                }
            };

            http.newWebSocketBuilder().buildAsync(uri, listener).whenComplete((ws, err) -> {
                if (err != null) {
                    if (terminated.compareAndSet(false, true)) {
                        sink.error(err);   // connect 失败 → 快失败带真因
                    }
                    return;
                }
                wsHolder.set(ws);
                activeBySession.put(req.sessionId(), ws);
                ws.sendText(OpenClawProtocol.connect(nextId(), token, 3, 4), true)
                        .thenCompose(w -> w.sendText(OpenClawProtocol.chatSend(nextId(), req), true))
                        .exceptionally(t -> {
                            if (terminated.compareAndSet(false, true)) {
                                sink.error(t);
                            }
                            return null;
                        });
            });

            // 下游取消 / 完成 / 出错 → 摘除并关连接（barge-in 时 cancel 已先发过 chat.abort）
            sink.onDispose(() -> {
                WebSocket ws = wsHolder.get();
                if (ws != null) {
                    activeBySession.remove(req.sessionId(), ws);   // 2 参:仅当仍是本轮的 ws 才摘,防同 session 重入误摘(review #183-7)
                    if (!ws.isOutputClosed()) {
                        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
                    }
                }
            });
        });
    }

    @Override
    public void cancel(String sessionId) {
        WebSocket ws = activeBySession.get(sessionId);
        if (ws != null && !ws.isOutputClosed()) {
            // chat.abort {sessionKey};发送失败要暴露,否则 barge-in 静默失败、OpenClaw 续烧 token(review #183-8)
            ws.sendText(OpenClawProtocol.chatAbort(nextId(), sessionId), true)
                    .whenComplete((r, t) -> {
                        if (t != null) {
                            log.warn("OpenClaw chat.abort 发送失败 session={}: {}", sessionId, t.toString());
                        }
                    });
        }
    }

    private String nextId() {
        return Long.toString(ids.incrementAndGet());
    }
}
