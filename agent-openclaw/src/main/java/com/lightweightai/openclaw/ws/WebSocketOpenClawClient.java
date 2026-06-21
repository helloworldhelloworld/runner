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
import java.util.concurrent.atomic.AtomicLong;

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
            StringBuilder buf = new StringBuilder();   // 重组可能分片的 text 帧
            WebSocket.Listener listener = new WebSocket.Listener() {
                @Override
                public void onOpen(WebSocket ws) {
                    ws.request(1);
                }

                @Override
                public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                    buf.append(data);
                    if (last) {
                        String msg = buf.toString();
                        buf.setLength(0);
                        OpenClawProtocol.parseEvent(msg).ifPresent(ev -> {
                            if (ev instanceof OpenClawEvent.ErrorEvent e) {
                                sink.error(e.cause());
                            } else {
                                sink.next(ev);
                                if (ev instanceof OpenClawEvent.RunEnd) {
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
                    sink.complete();
                    return null;
                }

                @Override
                public void onError(WebSocket ws, Throwable error) {
                    sink.error(error);
                }
            };

            http.newWebSocketBuilder().buildAsync(uri, listener).whenComplete((ws, err) -> {
                if (err != null) {
                    sink.error(err);   // connect 失败 → 快失败带真因
                    return;
                }
                activeBySession.put(req.sessionId(), ws);
                ws.sendText(OpenClawProtocol.connect(nextId(), token, 3, 4), true)
                        .thenCompose(w -> w.sendText(OpenClawProtocol.chatSend(nextId(), req), true))
                        .exceptionally(t -> { sink.error(t); return null; });
            });

            // 下游取消 / 完成 / 出错 → 摘除并关连接（barge-in 时 cancel 已先发过 chat.abort）
            sink.onDispose(() -> {
                WebSocket ws = activeBySession.remove(req.sessionId());
                if (ws != null && !ws.isOutputClosed()) {
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
                }
            });
        });
    }

    @Override
    public void cancel(String sessionId) {
        WebSocket ws = activeBySession.get(sessionId);
        if (ws != null && !ws.isOutputClosed()) {
            ws.sendText(OpenClawProtocol.chatAbort(nextId(), sessionId), true);   // chat.abort {sessionKey}
        }
    }

    private String nextId() {
        return Long.toString(ids.incrementAndGet());
    }
}
