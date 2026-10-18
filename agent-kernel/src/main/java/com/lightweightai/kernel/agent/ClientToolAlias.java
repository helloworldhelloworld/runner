package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.directive.DirectiveDescriptor;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.core.ToolExecutionContext;
import com.lightweightai.kernel.core.ToolResultChunk;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Objects;

/**
 * {@link ClientTool} 的别名视图：使用不同的 LLM 可见名，但所有调用都委托给主工具。
 *
 * <p>用于支持单个 ClientTool 在 LLM 端暴露多个名字（共享同一组 down/up action）。
 * 由 {@link ClientTool#getAliasTools()} 构建，注册由 {@link ToolRegistry#register(Tool)} 自动完成。</p>
 */
public final class ClientToolAlias implements ContextAwareTool {

    private final String aliasName;
    private final ClientTool<?> primary;

    public ClientToolAlias(String aliasName, ClientTool<?> primary) {
        this.aliasName = Objects.requireNonNull(aliasName, "aliasName cannot be null");
        this.primary = Objects.requireNonNull(primary, "primary cannot be null");
        if (aliasName.trim().isEmpty()) {
            throw new IllegalArgumentException("aliasName cannot be blank");
        }
    }

    @Override
    public String getName() {
        return aliasName;
    }

    @Override
    public String getDescription() {
        return primary.getDescription();
    }

    @Override
    public ToolSchema getSchema() {
        return primary.getSchema();
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        return primary.execute(args);
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        return primary.execute(args, context);
    }

    @Override
    public Flux<ToolResultChunk> executeReactive(Map<String, Object> args, ToolExecutionContext context) {
        if (context == null) return executeReactive(args);
        return primary.executeContextReactive(args, context)
                .map(result -> ToolResultChunk.complete(aliasName, result)).flux();
    }

    @Override
    public boolean isAutoExecute() {
        return primary.isAutoExecute();
    }

    public ClientTool<?> getPrimary() {
        return primary;
    }

    public DirectiveDescriptor getDirectiveDescriptor() {
        return primary.getDirectiveDescriptor();
    }
}
