package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.ToolResult;

import java.util.Map;

/**
 * 工具流式结果片段
 *
 * 表示工具执行过程中的一个事件：进度通知、日志、最终结果或错误。
 * 用于 Reactive 流式管道中实时推送工具执行状态。
 */
public class ToolResultChunk {

    public enum ChunkType { PROGRESS, LOG, COMPLETE, ERROR }

    private final ChunkType type;
    private final String toolName;
    private final String toolCallId;
    private final String message;
    private final double progress;
    private final double total;
    private final ToolResult result;
    private final Throwable error;
    private final Map<String, Object> meta;

    private ToolResultChunk(ChunkType type, String toolName, String toolCallId,
                            String message, double progress, double total,
                            ToolResult result, Throwable error,
                            Map<String, Object> meta) {
        this.type = type;
        this.toolName = toolName;
        this.toolCallId = toolCallId;
        this.message = message;
        this.progress = progress;
        this.total = total;
        this.result = result;
        this.error = error;
        this.meta = meta;
    }

    public static ToolResultChunk progress(String toolName, String message,
            double progress, double total, Map<String, Object> meta) {
        return new ToolResultChunk(ChunkType.PROGRESS, toolName, null, message, progress, total, null, null, meta);
    }

    public static ToolResultChunk progress(String toolName, String message, double progress, double total) {
        return progress(toolName, message, progress, total, null);
    }

    public static ToolResultChunk log(String toolName, String level,
            String logger, String message, Map<String, Object> meta) {
        String formatted = "[" + level + "] " + (logger != null ? logger + ": " : "") + message;
        return new ToolResultChunk(ChunkType.LOG, toolName, null, formatted, 0, 0, null, null, meta);
    }

    public static ToolResultChunk log(String toolName, String level, String message) {
        return log(toolName, level, null, message, null);
    }

    public static ToolResultChunk complete(String toolName, ToolResult result) {
        return new ToolResultChunk(ChunkType.COMPLETE, toolName, null, null, 0, 0, result, null, null);
    }

    public static ToolResultChunk error(String toolName, String message) {
        return new ToolResultChunk(ChunkType.ERROR, toolName, null, message, 0, 0, null,
                new RuntimeException(message), null);
    }

    public ToolResultChunk withToolCallId(String id) {
        return new ToolResultChunk(type, toolName, id, message, progress, total, result, error, meta);
    }

    public ChunkType getType() {
        return type;
    }

    public String getToolName() {
        return toolName;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getMessage() {
        return message;
    }

    public double getProgress() {
        return progress;
    }

    public double getTotal() {
        return total;
    }

    public ToolResult getResult() {
        return result;
    }

    public Throwable getError() {
        return error;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    @Override
    public String toString() {
        return "ToolResultChunk{type=" + type + ", toolName='" + toolName + "'" +
                (toolCallId != null ? ", toolCallId='" + toolCallId + "'" : "") +
                (message != null ? ", message='" + message + "'" : "") +
                "}";
    }
}
