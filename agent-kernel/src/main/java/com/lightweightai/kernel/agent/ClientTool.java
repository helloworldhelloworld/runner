package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.directive.DirectiveDescriptor;
import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 客户端工具模板：固定调度流程，子类仅处理 payload 构建与结果解析。
 */
public abstract class ClientTool<T> implements Tool {

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
        Map<String, Object> payload = buildPayload(args != null ? args : Map.of());
        String callId = UUID.randomUUID().toString();
        try {
            Directive directive = new Directive(
                callId,
                descriptor.getNamespace(),
                descriptor.getDownAction(),
                payload,
                descriptor.getTimeoutMs()
            );
            // 下发 Directive 在外层封装，dispatcher 只负责分发。
            ToolResult result = dispatcher.dispatch(
                    callId,
                    descriptor.toDownlinkToolName(),
                    directive)
                .get(descriptor.getTimeoutMs(), TimeUnit.MILLISECONDS);
            ToolResult normalized = normalizeForParser(result);
            return parseResult(normalized);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Client tool invoke failed: " + getName(), e);
        }
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
            T value = invoke(args);
            if (value == null) {
                return ToolResult.success("OK");
            }
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> structured = (Map<String, Object>) value;
                return ToolResult.success(value.toString(), structured);
            }
            return ToolResult.success(value.toString());
        } catch (Exception e) {
            return ToolResult.error(e);
        }
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
}
