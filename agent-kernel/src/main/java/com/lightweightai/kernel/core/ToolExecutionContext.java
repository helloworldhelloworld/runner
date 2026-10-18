package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.DeviceContext;
import reactor.core.Disposable;
import java.util.function.Consumer;

/**
 * 工具执行上下文 — 携带 per-request 的客户端工具调度信息和设备上下文
 *
 * ToolExecutor 是全局单例，不能持有 session 级别状态。
 * ToolExecutionContext 通过方法参数传入，不可变副本保留同一执行的取消信号与线程安全模型作用域。
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
    private final CancellationToken cancellationToken;
    private final ExecutionModelScope modelScope;
    private final ExecutionConfigurationScope configurationScope;
    private final com.lightweightai.kernel.context.ExecutionUsageScope usageScope;
    private final String principalId;
    private final com.lightweightai.kernel.memory.MemoryProvider memoryProvider;
    private final Consumer<Disposable> resourceRegistrar;

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
        this(clientDispatcher, clientToolTimeoutMs, deviceContext, null, null, null, null, null, null, null);
    }

    private ToolExecutionContext(ClientToolDispatcher clientDispatcher, long clientToolTimeoutMs,
                                 DeviceContext deviceContext, CancellationToken cancellationToken,
                                 ExecutionModelScope modelScope, ExecutionConfigurationScope configurationScope,
                                 String principalId, com.lightweightai.kernel.memory.MemoryProvider memoryProvider,
                                 com.lightweightai.kernel.context.ExecutionUsageScope usageScope,
                                 Consumer<Disposable> resourceRegistrar) {
        this.clientDispatcher = clientDispatcher;
        this.clientToolTimeoutMs = clientToolTimeoutMs > 0 ? clientToolTimeoutMs : 60_000;
        this.deviceContext = deviceContext != null ? deviceContext : DeviceContext.DEFAULT;
        this.cancellationToken = cancellationToken;
        this.modelScope = modelScope;
        this.configurationScope = configurationScope;
        this.principalId = principalId;
        this.usageScope = usageScope;
        this.memoryProvider = memoryProvider;
        this.resourceRegistrar = resourceRegistrar;
    }

    public com.lightweightai.kernel.context.ExecutionUsageScope getUsageScope() { return usageScope; }
    public ToolExecutionContext withUsageScope(com.lightweightai.kernel.context.ExecutionUsageScope scope) {
        return new ToolExecutionContext(clientDispatcher, clientToolTimeoutMs, deviceContext, cancellationToken,
                modelScope, configurationScope, principalId, memoryProvider, java.util.Objects.requireNonNull(scope), resourceRegistrar);
    }

    public String getPrincipalId() { return principalId; }
    public com.lightweightai.kernel.memory.MemoryProvider getMemoryProvider() { return memoryProvider; }
    public ToolExecutionContext withPrincipalId(String principal) {
        if (principal != null && principal.isBlank()) throw new IllegalArgumentException("principal cannot be blank");
        return new ToolExecutionContext(clientDispatcher, clientToolTimeoutMs, deviceContext, cancellationToken,
                modelScope, configurationScope, principal, memoryProvider, usageScope, resourceRegistrar);
    }
    public ToolExecutionContext withMemoryProvider(com.lightweightai.kernel.memory.MemoryProvider memory) {
        return new ToolExecutionContext(clientDispatcher, clientToolTimeoutMs, deviceContext, cancellationToken,
                modelScope, configurationScope, principalId, java.util.Objects.requireNonNull(memory), usageScope, resourceRegistrar);
    }
    public ToolExecutionContext withDeviceContext(DeviceContext device) {
        return new ToolExecutionContext(clientDispatcher, clientToolTimeoutMs, device, cancellationToken,
                modelScope, configurationScope, principalId, memoryProvider, usageScope, resourceRegistrar);
    }

    public CancellationToken getCancellationToken() { return cancellationToken; }

    public Consumer<Disposable> getResourceRegistrar() { return resourceRegistrar; }

    public ToolExecutionContext withResourceRegistrar(Consumer<Disposable> registrar) {
        return new ToolExecutionContext(clientDispatcher, clientToolTimeoutMs, deviceContext, cancellationToken,
                modelScope, configurationScope, principalId, memoryProvider, usageScope, registrar);
    }

    /** Bind one execution without changing the caller's reusable device/dispatcher context. */
    public ToolExecutionContext withCancellationToken(CancellationToken token) {
        return new ToolExecutionContext(clientDispatcher, clientToolTimeoutMs, deviceContext, token, modelScope, configurationScope, principalId, memoryProvider, usageScope, resourceRegistrar);
    }

    public ExecutionConfigurationScope getConfigurationScope() { return configurationScope; }

    public ToolExecutionContext withConfigurationScope(ExecutionConfigurationScope scope) {
        return new ToolExecutionContext(clientDispatcher, clientToolTimeoutMs, deviceContext, cancellationToken,
                modelScope, java.util.Objects.requireNonNull(scope, "configuration scope required"), principalId, memoryProvider, usageScope, resourceRegistrar);
    }

    public ExecutionModelScope getModelScope() { return modelScope; }

    /** Share model selections with descendants without changing device or cancellation state. */
    public ToolExecutionContext withModelScope(ExecutionModelScope scope) {
        return new ToolExecutionContext(clientDispatcher, clientToolTimeoutMs, deviceContext,
                cancellationToken, java.util.Objects.requireNonNull(scope, "model scope required"), configurationScope, principalId, memoryProvider, usageScope, resourceRegistrar);
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
