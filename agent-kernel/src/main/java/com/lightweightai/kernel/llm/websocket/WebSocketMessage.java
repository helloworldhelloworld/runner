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
        TOOL_RESULT,            // 工具执行结果
        PING,                   // 心跳检测

        // Server -> Client
        CHAT_RESPONSE,          // 完整响应
        TEXT_DELTA,             // 流式文本片段
        TOOL_CALL,              // 工具调用请求
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
