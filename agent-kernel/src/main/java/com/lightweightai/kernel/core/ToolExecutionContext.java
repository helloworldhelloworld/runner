package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.DeviceContext;

/**
 * 工具执行上下文 — 携带 per-request 的客户端工具调度信息和设备上下文
 *
 * ToolExecutor 是全局单例，不能持有 session 级别状态。
 * ToolExecutionContext 通过方法参数传入，线程安全、无状态共享。
 *
 * <pre>
 * ToolExecutionContext ctx = new ToolExecutionContext(dispatcher, 60_000, deviceContext);
 * executor.executeToolCall(toolCall, ctx);
 * </pre>
 */
public class ToolExecutionContext {

    private final ClientToolDispatcher clientDispatcher;
    private final long clientToolTimeoutMs;
    private final DeviceContext deviceContext;

    /**
     * @param clientDispatcher  客户端工具调度器（绑定到 WebSocket session），可为 null
     * @param clientToolTimeoutMs 客户端工具超时毫秒数
     */
    public ToolExecutionContext(ClientToolDispatcher clientDispatcher, long clientToolTimeoutMs) {
        this(clientDispatcher, clientToolTimeoutMs, DeviceContext.DEFAULT);
    }

    /**
     * @param clientDispatcher    客户端工具调度器，可为 null
     * @param clientToolTimeoutMs 客户端工具超时毫秒数
     * @param deviceContext       设备上下文（用于工具分发）
     */
    public ToolExecutionContext(ClientToolDispatcher clientDispatcher, long clientToolTimeoutMs,
                                DeviceContext deviceContext) {
        this.clientDispatcher = clientDispatcher;
        this.clientToolTimeoutMs = clientToolTimeoutMs > 0 ? clientToolTimeoutMs : 60_000;
        this.deviceContext = deviceContext != null ? deviceContext : DeviceContext.DEFAULT;
    }

    /**
     * 获取客户端调度器
     */
    public ClientToolDispatcher getClientDispatcher() {
        return clientDispatcher;
    }

    /**
     * 获取客户端工具超时
     */
    public long getClientToolTimeoutMs() {
        return clientToolTimeoutMs;
    }

    /**
     * 是否有可用的客户端调度器
     */
    public boolean hasClientDispatcher() {
        return clientDispatcher != null;
    }

    /**
     * 获取设备上下文
     */
    public DeviceContext getDeviceContext() {
        return deviceContext;
    }

    /**
     * 无客户端工具的空上下文
     */
    public static ToolExecutionContext serverOnly() {
        return new ToolExecutionContext(null, 0);
    }
}
