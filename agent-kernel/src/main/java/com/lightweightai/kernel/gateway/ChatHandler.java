package com.lightweightai.kernel.gateway;

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
