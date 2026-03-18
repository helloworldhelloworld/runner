package com.lightweightai.kernel.memory.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A single entry in a session transcript (JSONL format).
 * Provides an audit trail of all messages, tool calls, and results.
 */
public class TranscriptEntry {
    private final String type;
    private final Instant timestamp;
    private final String role;
    private final String content;
    private final List<ToolCallEntry> toolCalls;
    private final ToolResultEntry toolResult;
    private final Map<String, Object> metadata;

    @JsonCreator
    public TranscriptEntry(
            @JsonProperty("type") String type,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("role") String role,
            @JsonProperty("content") String content,
            @JsonProperty("toolCalls") List<ToolCallEntry> toolCalls,
            @JsonProperty("toolResult") ToolResultEntry toolResult,
            @JsonProperty("metadata") Map<String, Object> metadata) {
        this.type = type;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
        this.toolResult = toolResult;
        this.metadata = metadata;
    }

    public String getType() {
        return type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public List<ToolCallEntry> getToolCalls() {
        return toolCalls;
    }

    public ToolResultEntry getToolResult() {
        return toolResult;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    // Factory methods for common entry types
    public static TranscriptEntry userMessage(String content) {
        return new TranscriptEntry("message", Instant.now(), "user", content, null, null, null);
    }

    public static TranscriptEntry assistantMessage(String content) {
        return new TranscriptEntry("message", Instant.now(), "assistant", content, null, null, null);
    }

    public static TranscriptEntry assistantWithToolCalls(String content, List<ToolCallEntry> toolCalls) {
        return new TranscriptEntry("message", Instant.now(), "assistant", content, toolCalls, null, null);
    }

    public static TranscriptEntry toolResult(ToolResultEntry result) {
        return new TranscriptEntry("tool_result", Instant.now(), null, null, null, result, null);
    }

    public static TranscriptEntry systemEvent(String content, Map<String, Object> metadata) {
        return new TranscriptEntry("system", Instant.now(), "system", content, null, null, metadata);
    }

    public static class ToolCallEntry {
        private final String id;
        private final String name;
        private final Map<String, Object> arguments;

        @JsonCreator
        public ToolCallEntry(
                @JsonProperty("id") String id,
                @JsonProperty("name") String name,
                @JsonProperty("arguments") Map<String, Object> arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Map<String, Object> getArguments() {
            return arguments;
        }
    }

    public static class ToolResultEntry {
        private final String toolCallId;
        private final String name;
        private final Object result;
        private final boolean isError;

        @JsonCreator
        public ToolResultEntry(
                @JsonProperty("toolCallId") String toolCallId,
                @JsonProperty("name") String name,
                @JsonProperty("result") Object result,
                @JsonProperty("isError") boolean isError) {
            this.toolCallId = toolCallId;
            this.name = name;
            this.result = result;
            this.isError = isError;
        }

        public String getToolCallId() {
            return toolCallId;
        }

        public String getName() {
            return name;
        }

        public Object getResult() {
            return result;
        }

        @JsonProperty("isError")
        public boolean isError() {
            return isError;
        }
    }
}
