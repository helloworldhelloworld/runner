package com.lightweightai.agent;

/**
 * 流式回调接口
 *
 * 用于接收实时的LLM输出
 */
public interface StreamCallback {

    /**
     * 当收到新的token时调用
     *
     * @param token 文本片段
     */
    default void onToken(String token) {
    }

    /**
     * 当流式输出完成时调用
     *
     * @param fullText 完整的回复文本
     */
    default void onComplete(String fullText) {
    }

    /**
     * 当发生错误时调用
     *
     * @param error 错误信息
     */
    default void onError(Throwable error) {
    }
}
