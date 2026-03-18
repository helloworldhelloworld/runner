package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;

import java.util.Collections;
import java.util.Map;

/**
 * 全链路统一流式事件
 *
 * 合并 LLM 流式输出和工具流式输出，用于整个 Reactive 管道的统一事件类型。
 */
public class StreamEvent {

    public enum EventType {
        TEXT_DELTA,        // LLM 文本片段
        TOOL_CALL_START,   // LLM 决定调用工具
        TOOL_PROGRESS,     // 工具执行进度 (来自 MCP ProgressNotification)
        TOOL_LOG,          // 工具执行日志 (来自 MCP LoggingNotification)
        TOOL_RESULT,       // 工具执行完成
        TOOL_ERROR,        // 工具执行错误
        LLM_COMPLETE,      // LLM 最终响应(无更多工具调用)
        ERROR,             // 管道错误
        POST_PROCESS_DATA  // 后处理器注入数据 (卡片、标注、风控信号等)
    }

    private final EventType type;
    private final String textDelta;
    private final ToolCall toolCall;
    private final ToolResultChunk chunk;
    private final LLMResponse response;
    private final Throwable error;
    private final String category;
    private final Map<String, Object> data;
    private final Map<String, Object> metadata;

    private StreamEvent(EventType type, String textDelta, ToolCall toolCall,
                        ToolResultChunk chunk, LLMResponse response, Throwable error) {
        this(type, textDelta, toolCall, chunk, response, error, null, null, null);
    }

    private StreamEvent(EventType type, String textDelta, ToolCall toolCall,
                        ToolResultChunk chunk, LLMResponse response, Throwable error,
                        String category, Map<String, Object> data) {
        this(type, textDelta, toolCall, chunk, response, error, category, data, null);
    }

    private StreamEvent(EventType type, String textDelta, ToolCall toolCall,
                        ToolResultChunk chunk, LLMResponse response, Throwable error,
                        String category, Map<String, Object> data, Map<String, Object> metadata) {
        this.type = type;
        this.textDelta = textDelta;
        this.toolCall = toolCall;
        this.chunk = chunk;
        this.response = response;
        this.error = error;
        this.category = category;
        this.data = data;
        this.metadata = metadata;
    }

    public static StreamEvent textDelta(String delta) {
        return new StreamEvent(EventType.TEXT_DELTA, delta, null, null, null, null);
    }

    public static StreamEvent textDelta(String delta, Map<String, Object> metadata) {
        return new StreamEvent(EventType.TEXT_DELTA, delta, null, null, null, null,
                null, null, metadata != null ? Collections.unmodifiableMap(metadata) : null);
    }

    public static StreamEvent toolCallStart(ToolCall call) {
        return new StreamEvent(EventType.TOOL_CALL_START, null, call, null, null, null);
    }

    public static StreamEvent toolProgress(ToolResultChunk chunk) {
        return new StreamEvent(EventType.TOOL_PROGRESS, null, null, chunk, null, null);
    }

    public static StreamEvent toolLog(ToolResultChunk chunk) {
        return new StreamEvent(EventType.TOOL_LOG, null, null, chunk, null, null);
    }

    public static StreamEvent toolResult(ToolResultChunk chunk) {
        return new StreamEvent(EventType.TOOL_RESULT, null, null, chunk, null, null);
    }

    public static StreamEvent toolError(ToolResultChunk chunk) {
        return new StreamEvent(EventType.TOOL_ERROR, null, null, chunk, null, null);
    }

    public static StreamEvent llmComplete(LLMResponse response) {
        return new StreamEvent(EventType.LLM_COMPLETE, null, null, null, response, null);
    }

    public static StreamEvent error(Throwable error) {
        return new StreamEvent(EventType.ERROR, null, null, null, null, error);
    }

    public static StreamEvent postProcessData(String category, Map<String, Object> data) {
        return new StreamEvent(EventType.POST_PROCESS_DATA, null, null, null, null, null,
                category, data != null ? Collections.unmodifiableMap(data) : Collections.emptyMap());
    }

    public EventType getType() {
        return type;
    }

    public String getTextDelta() {
        return textDelta;
    }

    public ToolCall getToolCall() {
        return toolCall;
    }

    public ToolResultChunk getChunk() {
        return chunk;
    }

    public LLMResponse getResponse() {
        return response;
    }

    public Throwable getError() {
        return error;
    }

    public String getCategory() {
        return category;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public Map<String, Object> getMetadata() {
        return metadata != null ? metadata : Collections.emptyMap();
    }

    @Override
    public String toString() {
        return "StreamEvent{type=" + type +
                (textDelta != null ? ", textDelta='" + textDelta + "'" : "") +
                (toolCall != null ? ", toolCall=" + toolCall : "") +
                (chunk != null ? ", chunk=" + chunk : "") +
                (category != null ? ", category='" + category + "'" : "") +
                (data != null ? ", data=" + data : "") +
                "}";
    }
}
