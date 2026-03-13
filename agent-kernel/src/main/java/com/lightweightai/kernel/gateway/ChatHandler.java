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
            chatStream(request, new StreamCallback() {
                @Override
                public void onDelta(String delta, Map<String, Object> metadata) {
                    sink.next(StreamEvent.textDelta(delta));
                }

                @Override
                public void onComplete(GatewayResponse response) {
                    sink.next(StreamEvent.llmComplete(null));
                    sink.complete();
                }

                @Override
                public void onError(Throwable error) {
                    sink.error(error);
                }
            });
        });
    }

    /**
     * 流式回调 - 业务无关
     *
     * metadata 传递业务特有的附加信息（如 emotion、confidence 等），
     * 避免在接口中暴露领域特定字段。
     */
    interface StreamCallback {
        void onDelta(String delta, Map<String, Object> metadata);
        void onComplete(GatewayResponse response);
        void onError(Throwable error);
    }
}
