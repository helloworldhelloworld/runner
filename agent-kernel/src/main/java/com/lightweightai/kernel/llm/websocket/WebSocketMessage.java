package com.lightweightai.kernel.llm.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * WebSocket message protocol for LLM communication
 *
 * Supports bidirectional streaming with function calling (Claude Skills)
 */
public class WebSocketMessage {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @JsonProperty("type")
    private MessageType type;

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("data")
    private Object data;

    @JsonProperty("error")
    private String error;

    public enum MessageType {
        // Client -> Server
        CHAT_REQUEST,           // 发起对话请求
        TOOL_RESULT,            // 工具执行结果（服务端工具）
        CLIENT_TOOL_RESULT,     // 客户端工具执行结果（客户端回传）
        CLIENT_MANIFEST,        // 端侧上报能力清单（Directive 协议）
        DIRECTIVE_RESULT,       // 端侧回传 Directive 结果（Directive 协议）
        PING,                   // 心跳检测

        // Server -> Client
        CHAT_RESPONSE,          // 完整响应
        TEXT_DELTA,             // 流式文本片段
        TOOL_CALL,              // 工具调用请求（服务端工具）
        CLIENT_TOOL_CALL,       // 客户端工具调用请求（派发到客户端执行）
        DIRECTIVE,              // 云端下发 Directive 指令（Directive 协议）
        ERROR,                  // 错误消息
        PONG                    // 心跳响应
    }

    public WebSocketMessage() {
    }

    public WebSocketMessage(MessageType type, String requestId, Object data) {
        this.type = type;
        this.requestId = requestId;
        this.data = data;
    }

    // Getters and Setters
    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    // Serialization
    public String toJson() throws IOException {
        return objectMapper.writeValueAsString(this);
    }

    public static WebSocketMessage fromJson(String json) throws IOException {
        return objectMapper.readValue(json, WebSocketMessage.class);
    }

    // Factory methods for different message types

    public static WebSocketMessage chatRequest(String requestId, ChatRequestData data) {
        return new WebSocketMessage(MessageType.CHAT_REQUEST, requestId, data);
    }

    public static WebSocketMessage toolResult(String requestId, ToolResultData data) {
        return new WebSocketMessage(MessageType.TOOL_RESULT, requestId, data);
    }

    public static WebSocketMessage ping(String requestId) {
        return new WebSocketMessage(MessageType.PING, requestId, null);
    }

    public static WebSocketMessage clientToolCall(String requestId, ClientToolCallData data) {
        return new WebSocketMessage(MessageType.CLIENT_TOOL_CALL, requestId, data);
    }

    public static WebSocketMessage clientToolResult(String requestId, ClientToolResultData data) {
        return new WebSocketMessage(MessageType.CLIENT_TOOL_RESULT, requestId, data);
    }

    public static WebSocketMessage error(String requestId, String errorMessage) {
        WebSocketMessage msg = new WebSocketMessage(MessageType.ERROR, requestId, null);
        msg.setError(errorMessage);
        return msg;
    }

    /**
     * Chat request data structure
     */
    public static class ChatRequestData {
        @JsonProperty("messages")
        private List<Map<String, Object>> messages;

        @JsonProperty("tools")
        private List<Map<String, Object>> tools;

        @JsonProperty("max_tokens")
        private Integer maxTokens;

        @JsonProperty("temperature")
        private Double temperature;

        @JsonProperty("stream")
        private Boolean stream;

        // Getters and Setters
        public List<Map<String, Object>> getMessages() {
            return messages;
        }

        public void setMessages(List<Map<String, Object>> messages) {
            this.messages = messages;
        }

        public List<Map<String, Object>> getTools() {
            return tools;
        }

        public void setTools(List<Map<String, Object>> tools) {
            this.tools = tools;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Boolean getStream() {
            return stream;
        }

        public void setStream(Boolean stream) {
            this.stream = stream;
        }
    }

    /**
     * Tool result data structure
     */
    public static class ToolResultData {
        @JsonProperty("tool_use_id")
        private String toolUseId;

        @JsonProperty("content")
        private String content;

        @JsonProperty("is_error")
        private Boolean isError;

        // Getters and Setters
        public String getToolUseId() {
            return toolUseId;
        }

        public void setToolUseId(String toolUseId) {
            this.toolUseId = toolUseId;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public Boolean getIsError() {
            return isError;
        }

        public void setIsError(Boolean isError) {
            this.isError = isError;
        }
    }

    /**
     * Text delta data (streaming)
     */
    public static class TextDeltaData {
        @JsonProperty("text")
        private String text;

        @JsonProperty("index")
        private Integer index;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public Integer getIndex() {
            return index;
        }

        public void setIndex(Integer index) {
            this.index = index;
        }
    }

    /**
     * Tool call data
     */
    public static class ToolCallData {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("input")
        private Map<String, Object> input;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, Object> getInput() {
            return input;
        }

        public void setInput(Map<String, Object> input) {
            this.input = input;
        }
    }

    /**
     * Complete response data
     */
    public static class ChatResponseData {
        @JsonProperty("content")
        private String content;

        @JsonProperty("tool_calls")
        private List<ToolCallData> toolCalls;

        @JsonProperty("stop_reason")
        private String stopReason;

        @JsonProperty("usage")
        private UsageData usage;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public List<ToolCallData> getToolCalls() {
            return toolCalls;
        }

        public void setToolCalls(List<ToolCallData> toolCalls) {
            this.toolCalls = toolCalls;
        }

        public String getStopReason() {
            return stopReason;
        }

        public void setStopReason(String stopReason) {
            this.stopReason = stopReason;
        }

        public UsageData getUsage() {
            return usage;
        }

        public void setUsage(UsageData usage) {
            this.usage = usage;
        }
    }

    /**
     * Usage statistics
     */
    public static class UsageData {
        @JsonProperty("input_tokens")
        private Integer inputTokens;

        @JsonProperty("output_tokens")
        private Integer outputTokens;

        public Integer getInputTokens() {
            return inputTokens;
        }

        public void setInputTokens(Integer inputTokens) {
            this.inputTokens = inputTokens;
        }

        public Integer getOutputTokens() {
            return outputTokens;
        }

        public void setOutputTokens(Integer outputTokens) {
            this.outputTokens = outputTokens;
        }
    }

    public static WebSocketMessage directive(String requestId, DirectiveEnvelopeData data) {
        return new WebSocketMessage(MessageType.DIRECTIVE, requestId, data);
    }

    public static WebSocketMessage directiveResult(String requestId, DirectiveResultData data) {
        return new WebSocketMessage(MessageType.DIRECTIVE_RESULT, requestId, data);
    }

    public static WebSocketMessage clientManifest(String requestId, ClientManifestData data) {
        return new WebSocketMessage(MessageType.CLIENT_MANIFEST, requestId, data);
    }

    /**
     * Directive 指令数据（Server → Client）
     */
    public static class DirectiveHeader {
        @JsonProperty("namespace")
        private String namespace;

        @JsonProperty("name")
        private String name;

        public DirectiveHeader() {
        }

        public DirectiveHeader(String namespace, String name) {
            this.namespace = namespace;
            this.name = name;
        }

        public String getNamespace() { return namespace; }
        public void setNamespace(String namespace) { this.namespace = namespace; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    /**
     * Directive 指令项数据（Server → Client）
     */
    public static class DirectiveData {
        @JsonProperty("header")
        private DirectiveHeader header;

        @JsonProperty("payload")
        private Map<String, Object> payload;

        public DirectiveData() {
        }

        public DirectiveData(DirectiveHeader header, Map<String, Object> payload) {
            this.header = header;
            this.payload = payload;
        }

        public DirectiveHeader getHeader() { return header; }
        public void setHeader(DirectiveHeader header) { this.header = header; }
        public Map<String, Object> getPayload() { return payload; }
        public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    }

    /**
     * Directive 指令信封数据（Server → Client）
     */
    public static class DirectiveEnvelopeData {
        @JsonProperty("directive_id")
        private String directiveId;

        @JsonProperty("directives")
        private List<DirectiveData> directives;

        public DirectiveEnvelopeData() {
        }

        public DirectiveEnvelopeData(String directiveId, List<DirectiveData> directives) {
            this.directiveId = directiveId;
            this.directives = directives;
        }

        public String getDirectiveId() { return directiveId; }
        public void setDirectiveId(String directiveId) { this.directiveId = directiveId; }
        public List<DirectiveData> getDirectives() { return directives; }
        public void setDirectives(List<DirectiveData> directives) { this.directives = directives; }
    }

    /**
     * Directive 结果数据（Client → Server）
     */
    public static class DirectiveResultData {
        @JsonProperty("directive_id")
        private String directiveId;

        @JsonProperty("success")
        private Boolean success;

        @JsonProperty("content")
        private String content;

        @JsonProperty("metadata")
        private Map<String, Object> metadata;

        public DirectiveResultData() {
        }

        public DirectiveResultData(String directiveId, Boolean success,
                                    String content, Map<String, Object> metadata) {
            this.directiveId = directiveId;
            this.success = success;
            this.content = content;
            this.metadata = metadata;
        }

        public String getDirectiveId() { return directiveId; }
        public void setDirectiveId(String directiveId) { this.directiveId = directiveId; }
        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    /**
     * 端侧能力清单数据（Client → Server）
     */
    public static class ClientManifestData {
        @JsonProperty("client_type")
        private String clientType;

        @JsonProperty("client_version")
        private String clientVersion;

        @JsonProperty("capabilities")
        private List<CapabilityData> capabilities;

        public ClientManifestData() {
        }

        public String getClientType() { return clientType; }
        public void setClientType(String clientType) { this.clientType = clientType; }
        public String getClientVersion() { return clientVersion; }
        public void setClientVersion(String clientVersion) { this.clientVersion = clientVersion; }
        public List<CapabilityData> getCapabilities() { return capabilities; }
        public void setCapabilities(List<CapabilityData> capabilities) { this.capabilities = capabilities; }
    }

    /**
     * 端侧单个能力声明数据
     */
    public static class CapabilityData {
        @JsonProperty("namespace")
        private String namespace;

        @JsonProperty("name")
        private String name;

        @JsonProperty("description")
        private String description;

        @JsonProperty("input_schema")
        private Map<String, Object> inputSchema;

        @JsonProperty("default_timeout_ms")
        private Long defaultTimeoutMs;

        public CapabilityData() {
        }

        public String getNamespace() { return namespace; }
        public void setNamespace(String namespace) { this.namespace = namespace; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Map<String, Object> getInputSchema() { return inputSchema; }
        public void setInputSchema(Map<String, Object> inputSchema) { this.inputSchema = inputSchema; }
        public Long getDefaultTimeoutMs() { return defaultTimeoutMs; }
        public void setDefaultTimeoutMs(Long defaultTimeoutMs) { this.defaultTimeoutMs = defaultTimeoutMs; }
    }

    /**
     * 客户端工具调用数据（Server → Client）
     */
    public static class ClientToolCallData {
        @JsonProperty("call_id")
        private String callId;

        @JsonProperty("tool_name")
        private String toolName;

        @JsonProperty("arguments")
        private Map<String, Object> arguments;

        @JsonProperty("timeout_ms")
        private Long timeoutMs;

        public ClientToolCallData() {
        }

        public ClientToolCallData(String callId, String toolName,
                                  Map<String, Object> arguments, Long timeoutMs) {
            this.callId = callId;
            this.toolName = toolName;
            this.arguments = arguments;
            this.timeoutMs = timeoutMs;
        }

        public String getCallId() { return callId; }
        public void setCallId(String callId) { this.callId = callId; }
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        public Map<String, Object> getArguments() { return arguments; }
        public void setArguments(Map<String, Object> arguments) { this.arguments = arguments; }
        public Long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(Long timeoutMs) { this.timeoutMs = timeoutMs; }
    }

    /**
     * 客户端工具执行结果（Client → Server）
     */
    public static class ClientToolResultData {
        @JsonProperty("call_id")
        private String callId;

        @JsonProperty("content")
        private String content;

        @JsonProperty("is_error")
        private Boolean isError;

        public ClientToolResultData() {
        }

        public ClientToolResultData(String callId, String content, Boolean isError) {
            this.callId = callId;
            this.content = content;
            this.isError = isError;
        }

        public String getCallId() { return callId; }
        public void setCallId(String callId) { this.callId = callId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Boolean getIsError() { return isError; }
        public void setIsError(Boolean isError) { this.isError = isError; }
    }

    @Override
    public String toString() {
        return "WebSocketMessage{" +
            "type=" + type +
            ", requestId='" + requestId + '\'' +
            ", data=" + data +
            ", error='" + error + '\'' +
            '}';
    }
}
