package com.lightweightai.kernel.gateway;

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
