package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.directive.DirectiveDescriptor;
import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.core.ToolExecutionContext;
import com.lightweightai.kernel.core.ToolResultChunk;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 客户端工具模板：固定调度流程，子类仅处理 payload 构建与结果解析。
 */
public abstract class ClientTool<T> implements ContextAwareTool {

    private static final String HEADER_KEY = "header";
    private static final String PAYLOAD_KEY = "payload";
    private static final String NAME_KEY = "name";
    private static final String NAMESPACE_KEY = "namespace";

    private final ClientToolDispatcher dispatcher;
    private final DirectiveDescriptor descriptor;

    protected ClientTool(ClientToolDispatcher dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher cannot be null");
        com.lightweightai.kernel.agent.annotation.ClientTool directive =
            getClass().getAnnotation(com.lightweightai.kernel.agent.annotation.ClientTool.class);
        if (directive == null) {
            throw new IllegalStateException("@ClientTool is required on " + getClass().getName());
        }
        this.descriptor = DirectiveDescriptor.from(directive);
    }

    /**
     * 模板方法：构建 payload -> 远端调用 -> 校验上行 header -> 解析结果。
     */
    public final T invoke(Map<String, Object> args) {
        return invoke(args, dispatcher, descriptor.getTimeoutMs());
    }

    private Directive directive(Map<String, Object> args, long timeoutMs) {
        return new Directive(UUID.randomUUID().toString(), descriptor.getNamespace(), descriptor.getDownAction(),
                buildPayload(args != null ? args : Map.of()), timeoutMs);
    }

    private T invoke(Map<String, Object> args, ClientToolDispatcher target, long timeoutMs) {
        CompletableFuture<ToolResult> pending = null;
        try {
            Directive directive = directive(args, timeoutMs);
            pending = target.dispatch(directive.getDirectiveId(), descriptor.toDownlinkToolName(), directive);
            return parseResult(normalizeForParser(pending.get(timeoutMs, TimeUnit.MILLISECONDS)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Client tool interrupted: " + getName(), e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Client tool invoke failed: " + getName(), e);
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
        }
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        if (context == null) return execute(args);
        if (!context.hasClientDispatcher()) return ToolResult.error("No client dispatcher for this request: " + getName());
        try {
            return result(invoke(args, context.getClientDispatcher(), timeout(context)));
        } catch (Exception e) {
            return ToolResult.error(e);
        }
    }

    private long timeout(ToolExecutionContext context) {
        return Math.min(descriptor.getTimeoutMs(), context.getClientToolTimeoutMs());
    }

    final Mono<ToolResult> executeContextReactive(Map<String, Object> args, ToolExecutionContext context) {
        return Mono.defer(() -> {
            if (!context.hasClientDispatcher()) {
                return Mono.just(ToolResult.error("No client dispatcher for this request: " + getName()));
            }
            Directive directive = directive(args, timeout(context));
            return Mono.fromFuture(() -> context.getClientDispatcher().dispatch(
                            directive.getDirectiveId(), descriptor.toDownlinkToolName(), directive))
                    .timeout(Duration.ofMillis(timeout(context)))
                    .map(response -> result(parseResult(normalizeForParser(response))));
        });
    }

    @Override
    public Flux<ToolResultChunk> executeReactive(Map<String, Object> args, ToolExecutionContext context) {
        if (context == null) return executeReactive(args);
        return executeContextReactive(args, context)
                .map(result -> ToolResultChunk.complete(getName(), result)).flux();
    }

    protected abstract Map<String, Object> buildPayload(Map<String, Object> args);

    protected abstract T parseResult(ToolResult result);

    private ToolResult normalizeForParser(ToolResult result) {
        if (result == null || !result.hasStructuredContent()) {
            return result;
        }

        Map<String, Object> structured = result.getStructuredContent();
        Object headerNode = structured.get(HEADER_KEY);
        Object payloadNode = structured.get(PAYLOAD_KEY);
        if (!(headerNode instanceof Map<?, ?> headerMap) || !(payloadNode instanceof Map<?, ?> payloadMap)) {
            return result;
        }

        validateUplinkHeader(headerMap);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadMap;
        return ToolResult.success(result.getContent(), payload);
    }

    private void validateUplinkHeader(Map<?, ?> headerMap) {
        Object ns = headerMap.get(NAMESPACE_KEY);
        Object name = headerMap.get(NAME_KEY);
        if (!Objects.equals(descriptor.getNamespace(), ns) || !Objects.equals(descriptor.getUpAction(), name)) {
            throw new IllegalStateException(
                "Unexpected uplink header, expected " + descriptor.getNamespace() + "." + descriptor.getUpAction()
                    + " but got " + ns + "." + name);
        }
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            return result(invoke(args));
        } catch (Exception e) {
            return ToolResult.error(e);
        }
    }

    private ToolResult result(T value) {
        if (value == null) return ToolResult.success("OK");
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> structured = (Map<String, Object>) value;
            return ToolResult.success(value.toString(), structured);
        }
        return ToolResult.success(value.toString());
    }

    @Override
    public String getName() {
        return descriptor.toToolName();
    }

    @Override
    public String getDescription() {
        return "Client directive tool: " + descriptor.toToolName();
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.empty();
    }

    @Override
    public boolean isAutoExecute() {
        return false;
    }

    public DirectiveDescriptor getDirectiveDescriptor() {
        return descriptor;
    }

    /**
     * 返回该 ClientTool 配置的别名工具列表。
     *
     * <p>每个别名共享相同的 down/up action，但作为独立的 LLM 可见工具暴露。
     * 注册时由 {@link ToolRegistry#register(Tool)} 自动调用。</p>
     */
    public List<Tool> getAliasTools() {
        List<String> aliases = descriptor.getAliases();
        if (aliases.isEmpty()) {
            return Collections.emptyList();
        }
        List<Tool> views = new ArrayList<>(aliases.size());
        for (String alias : aliases) {
            views.add(new ClientToolAlias(alias, this));
        }
        return Collections.unmodifiableList(views);
    }
}
