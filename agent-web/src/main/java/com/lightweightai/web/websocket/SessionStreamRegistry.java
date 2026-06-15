package com.lightweightai.web.websocket;

import com.lightweightai.kernel.core.StreamEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每个 sessionId 一条 {@link StreamEvent} 广播流——让**只读 viewer** 订阅某会话的实时调用链
 * （全链路 trace，ADR-010 / 全链路可观测规范）。
 *
 * 驱动方（语音/聊天的 /ws/chat 连接）照常驱动对话，{@code handleChat} 把每个 StreamEvent
 * **同时** {@link #publish} 到这里；viewer 发 {@code observe} 订阅同一 session，就能看到
 * agent_route / tool_call_start(含工具名+参数) / tool_result / token / speakable_chunk / …
 * 整条调用链——不用再 ssh grep 日志盲调。
 *
 * 热流 + best-effort：只投递给**当前订阅者**，无订阅者不缓存（避免 voice-duplex 长会话无限积压）；
 * 历史回看走 REST {@code /api/session/{id}/history}。
 */
public class SessionStreamRegistry {

    private final Map<String, Sinks.Many<StreamEvent>> sinks = new ConcurrentHashMap<>();

    private Sinks.Many<StreamEvent> sink(String sessionId) {
        return sinks.computeIfAbsent(sessionId,
            k -> Sinks.many().multicast().directBestEffort());
    }

    /** 把一个 StreamEvent 广播给该 session 的所有 viewer（无 viewer 则丢弃，不积压）。 */
    public void publish(String sessionId, StreamEvent event) {
        if (sessionId == null) {
            return;
        }
        sink(sessionId).tryEmitNext(event);
    }

    /** 订阅某 session 的实时事件流（只读 viewer）。 */
    public Flux<StreamEvent> observe(String sessionId) {
        return sink(sessionId).asFlux();
    }
}
