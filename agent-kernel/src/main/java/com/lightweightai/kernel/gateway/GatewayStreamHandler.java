package com.lightweightai.kernel.gateway;

import java.util.Map;

/**
 * Gateway 流式响应处理器
 *
 * 用于处理流式响应的回调接口。
 */
public interface GatewayStreamHandler {

    /**
     * 收到文本片段
     *
     * @param delta 文本片段
     */
    void onDelta(String delta);

    /**
     * 收到文本片段（含元数据）
     *
     * 默认实现忽略 metadata，调用 onDelta(delta)。
     * 需要业务元数据（如 emotion）的协议适配器可覆盖此方法。
     *
     * @param delta    文本片段
     * @param metadata 业务元数据（如 emotion、confidence 等）
     */
    default void onDelta(String delta, Map<String, Object> metadata) {
        onDelta(delta);
    }

    /**
     * 流式响应完成
     *
     * @param response 完整响应
     */
    void onComplete(GatewayResponse response);

    /**
     * 发生错误
     *
     * @param error 错误信息
     */
    void onError(Throwable error);

    /**
     * 创建简单的 Lambda 友好处理器
     */
    static GatewayStreamHandler simple(
            java.util.function.Consumer<String> onDelta,
            java.util.function.Consumer<GatewayResponse> onComplete,
            java.util.function.Consumer<Throwable> onError) {

        return new GatewayStreamHandler() {
            @Override
            public void onDelta(String delta) {
                onDelta.accept(delta);
            }

            @Override
            public void onComplete(GatewayResponse response) {
                onComplete.accept(response);
            }

            @Override
            public void onError(Throwable error) {
                onError.accept(error);
            }
        };
    }
}
