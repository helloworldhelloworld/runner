package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 客户端工具调度器 — 将工具调用发送到客户端并等待结果
 *
 * 实现者负责将调用通过 WebSocket / SSE / 长轮询等通道推送到客户端，
 * 并在客户端回传结果后完成 CompletableFuture。
 *
 * 典型的实现在 agent-web 模块中基于 WebSocket 完成。
 */
@FunctionalInterface
public interface ClientToolDispatcher {

    /**
     * 将工具调用派发到客户端执行
     *
     * @param callId    本次调用的唯一标识（用于关联请求和响应）
     * @param toolName  工具名称
     * @param args      工具参数
     * @return 一个 CompletableFuture，在客户端回传结果后完成
     */
    CompletableFuture<ToolResult> dispatch(String callId, String toolName, Map<String, Object> args);
}
