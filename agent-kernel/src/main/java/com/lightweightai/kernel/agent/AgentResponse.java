package com.lightweightai.kernel.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 响应结果
 */
public class AgentResponse {

    private final String text;
    private final List<ToolCallRecord> toolCalls;
    private final String stopReason;

    public AgentResponse(String text) {
        this(text, new ArrayList<>(), "end_turn");
    }

    public AgentResponse(String text, List<ToolCallRecord> toolCalls, String stopReason) {
        this.text = text;
        this.toolCalls = toolCalls != null ? toolCalls : new ArrayList<>();
        this.stopReason = stopReason;
    }

    public String getText() {
        return text;
    }

    public List<ToolCallRecord> getToolCalls() {
        return toolCalls;
    }

    public String getStopReason() {
        return stopReason;
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public int getToolCallCount() {
        return toolCalls == null ? 0 : toolCalls.size();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String text;
        private List<ToolCallRecord> toolCalls = new ArrayList<>();
        private String stopReason = "end_turn";

        public Builder text(String text) { this.text = text; return this; }
        public Builder toolCalls(List<ToolCallRecord> toolCalls) { this.toolCalls = toolCalls; return this; }
        public Builder stopReason(String stopReason) { this.stopReason = stopReason; return this; }
        public AgentResponse build() { return new AgentResponse(text, toolCalls, stopReason); }
    }

    /**
     * 工具调用记录
     */
    public static class ToolCallRecord {
        private final String toolName;
        private final String arguments;
        private final String result;

        public ToolCallRecord(String toolName, String arguments, String result) {
            this.toolName = toolName;
            this.arguments = arguments;
            this.result = result;
        }

        public String getToolName() { return toolName; }
        public String getArguments() { return arguments; }
        public String getResult() { return result; }
    }
}
