package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolCall;

import java.util.Map;

/**
 * Gateway 流式响应处理器
 *
 * 用于处理流式响应的回调接口。
 * 协议适配器（REST/SSE/WebSocket）实现此接口，接收全链路事件。
 *
 * <p>工具事件回调均提供默认空实现，旧的协议适配器无需修改即可继续工作。
 * 需要展示工具调用进度的适配器覆盖对应方法即可。</p>
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

    // ==================== 工具事件回调（默认空实现，向后兼容） ====================

    /**
     * LLM 决定调用工具
     *
     * @param toolCall 工具调用信息（名称、ID、参数）
     */
    default void onToolCallStart(ToolCall toolCall) {}

    /**
     * 工具执行进度（来自 MCP ProgressNotification）
     *
     * @param chunk 进度信息（toolName、progress、total、message）
     */
    default void onToolProgress(ToolResultChunk chunk) {}

    /**
     * 工具执行日志（来自 MCP LoggingNotification）
     *
     * @param chunk 日志信息（toolName、message）
     */
    default void onToolLog(ToolResultChunk chunk) {}

    /**
     * 工具执行完成
     *
     * @param chunk 结果信息（toolName、ToolResult）
     */
    default void onToolResult(ToolResultChunk chunk) {}

    /**
     * 工具执行错误
     *
     * @param chunk 错误信息（toolName、message）
     */
    default void onToolError(ToolResultChunk chunk) {}

    /**
     * 后处理器注入数据（卡片、安全信号等）
     *
     * @param category 数据分类
     * @param data     数据内容
     */
    default void onPostProcessData(String category, Map<String, Object> data) {}

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
