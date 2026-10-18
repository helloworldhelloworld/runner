package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.core.StreamEvent;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 业务无关的聊天处理接口
 *
 * Gateway 通过此接口委托业务逻辑，不直接依赖具体实现。
 * 业务模块（如 SoulComfort、RAG Pipeline 等）实现此接口即可接入 Gateway。
 */
public interface ChatHandler {

    /**
     * 同步聊天
     */
    GatewayResponse chat(GatewayRequest request);

    /**
     * 流式聊天
     */
    CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback);

    /**
     * Reactive 流式聊天
     *
     * 默认实现桥接现有的 callback-based chatStream()。
     * 实现类可 override 返回原生 Flux。
     */
    default Flux<StreamEvent> chatStreamReactive(GatewayRequest request) {
        return Flux.create(sink -> {
            CompletableFuture<GatewayResponse> future = chatStream(request, new StreamCallback() {
                @Override
                public void onDelta(String delta, Map<String, Object> metadata) {
                    sink.next(StreamEvent.textDelta(delta, metadata));
                }

                @Override
                public void onComplete(GatewayResponse response) {
                    if (response.isError()) {
                        sink.error(new IllegalStateException(response.getErrorMessage()));
                        return;
                    }
                    if ("budget_exhausted".equals(response.getMetadata().get("stopReason"))) {
                        Map<String, Object> usage = new java.util.LinkedHashMap<>();
                        if (response.getMetadata().get("usage") instanceof Map<?, ?> values) {
                            values.forEach((key, value) -> { if (key instanceof String name) usage.put(name, value); });
                        }
                        sink.next(StreamEvent.budgetStopped(usage));
                    } else {
                        sink.next(StreamEvent.llmComplete(null));
                    }
                    sink.complete();
                }

                @Override
                public void onError(Throwable error) {
                    sink.error(error);
                }
            });
            if (future != null) {
                sink.onCancel(() -> future.cancel(true));
                future.whenComplete((response, error) -> { if (error != null) sink.error(error); });
            }
        });
    }

    /**
     * 独立打断某会话的在途 run（入站 barge-in，ADR-012）。
     *
     * 区别于 {@link #chatStreamReactive} 里"新消息到达时隐式打断"：本方法供 Voice Gateway 在
     * 用户一开口（VAD 命中、STT 尚未完成）时立即停播。默认返回 {@code null}（无在途 run /
     * 非交互式 handler）；交互式实现（如 Orchestrator）打断成立时返回 {@code SPEECH_INTERRUPTED}
     * 事件，供传输层下发给设备停播。
     *
     * @param sessionId 会话标识
     * @return 打断成立时的 {@code SPEECH_INTERRUPTED} 事件，否则 {@code null}
     */
    default StreamEvent interrupt(String sessionId) {
        return null;
    }

    /**
     * 流式回调 - 业务无关
     *
     * metadata 传递业务特有的附加信息（如 emotion、confidence 等），
     * 避免在接口中暴露领域特定字段。
     */
    default StreamEvent interrupt(String principalId, String sessionId) {
        if (principalId == null) return interrupt(sessionId);
        throw new UnsupportedOperationException("Authenticated interruption is unsupported by this handler");
    }

    /** Additive lifecycle cancellation for transport replacement/disconnect. */
    default void cancel(String principalId, String sessionId, String cause) {
        interrupt(principalId, sessionId);
    }

    interface StreamCallback {
        void onDelta(String delta, Map<String, Object> metadata);
        void onComplete(GatewayResponse response);
        void onError(Throwable error);
    }
}
