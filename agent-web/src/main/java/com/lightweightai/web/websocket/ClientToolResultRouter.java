package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.directive.DirectiveDescriptor;
import com.lightweightai.kernel.agent.directive.DirectiveResult;
import com.lightweightai.kernel.llm.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 客户端工具回包路由器。
 *
 * 将 WebSocket payload 解析与 complete/completeLatest 匹配策略从 Handler 中剥离，
 * 保持 Handler 专注于连接生命周期和消息分发。
 */
public class ClientToolResultRouter {

    private static final Logger logger = LoggerFactory.getLogger(ClientToolResultRouter.class);

    private final ObjectMapper objectMapper;
    private final ToolRegistry toolRegistry;

    public ClientToolResultRouter(ObjectMapper objectMapper, ToolRegistry toolRegistry) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.toolRegistry = toolRegistry;
    }

    public boolean routeClientToolResult(ToolCallCompleter dispatcher, JsonNode payload) {
        Objects.requireNonNull(dispatcher, "dispatcher cannot be null");
        Objects.requireNonNull(payload, "payload cannot be null");

        String callId = payload.path("callId").asText("");
        String content = payload.path("content").asText("");
        boolean isError = payload.path("isError").asBoolean(false);

        ToolResult result = isError ? ToolResult.error(content) : ToolResult.success(content);
        if (callId.isEmpty()) {
            logger.error("client_tool_result missing callId — client MUST include callId for reliable matching");
            return false;
        }
        return dispatcher.completeCall(callId, result);
    }

    public boolean routeDirectiveResult(ToolCallCompleter dispatcher, JsonNode payload) {
        Objects.requireNonNull(dispatcher, "dispatcher cannot be null");
        Objects.requireNonNull(payload, "payload cannot be null");

        String directiveId = payload.path("directiveId").asText("");
        boolean success = payload.path("success").asBoolean(false);
        String content = payload.path("content").asText("");

        if (directiveId.isEmpty()) {
            logger.error("directive_result missing directiveId — client MUST include directiveId for reliable matching");
            return false;
        }

        ToolResult toolResult;
        if (!success) {
            toolResult = ToolResult.error(content);
        } else {
            // content 可能是 JSON 字符串（如端工具返回的 {header, payload} 结构），尝试解析为 structuredContent
            Map<String, Object> structured = tryParseJsonContent(content);
            toolResult = structured != null
                ? ToolResult.success(content, structured)
                : ToolResult.success(content);
        }

        return dispatcher.completeCall(directiveId, toolResult);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> tryParseJsonContent(String content) {
        if (content == null || content.isBlank() || !content.startsWith("{")) {
            return null;
        }
        try {
            return objectMapper.readValue(content, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 通过上行 header(namespace + upAction) 反查配置中的 downAction，得到期望匹配的下行 toolName。
     */
    private Optional<String> resolveExpectedDownlinkToolName(JsonNode payload) {
        if (toolRegistry == null) {
            return Optional.empty();
        }

        JsonNode header = payload.path("header");
        if (!header.isObject()) {
            return Optional.empty();
        }

        String namespace = header.path("namespace").asText("");
        String upAction = header.path("name").asText("");
        if (namespace.isEmpty() || upAction.isEmpty()) {
            return Optional.empty();
        }

        return toolRegistry.getDirectiveRegistry().getAll().stream()
            .filter(d -> namespace.equals(d.getNamespace()) && upAction.equals(d.getUpAction()))
            .map(DirectiveDescriptor::toDownlinkToolName)
            .findFirst();
    }

}
