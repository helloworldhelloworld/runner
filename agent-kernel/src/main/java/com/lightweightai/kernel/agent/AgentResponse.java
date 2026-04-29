package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
     *
     * 携带类型化的 args(Map&lt;String, Object&gt;)和 result(ToolResult)以保留结构;
     * 旧式 String 入口仍可用,内部以适配方式存储,用于向后兼容。
     */
    public static class ToolCallRecord {
        private final String toolName;
        private final Map<String, Object> argumentsMap;
        private final ToolResult resultObject;

        // 旧式 String 入口写入的快照,保证 getArguments()/getResult() 与构造入参逐字一致
        private final String legacyArgumentsString;
        private final String legacyResultString;

        /**
         * 类型化构造(推荐)— 直接接受 ToolCall 原 args 与 ToolResult。
         */
        public ToolCallRecord(String toolName, Map<String, Object> arguments, ToolResult result) {
            this.toolName = toolName;
            this.argumentsMap = arguments != null ? Map.copyOf(arguments) : Map.of();
            this.resultObject = result;
            this.legacyArgumentsString = null;
            this.legacyResultString = null;
        }

        /**
         * @deprecated 使用类型化构造 {@link #ToolCallRecord(String, Map, ToolResult)};
         *             该构造保留以兼容历史调用方,String 字段被原样存储但无法保留结构。
         */
        @Deprecated
        public ToolCallRecord(String toolName, String arguments, String result) {
            this.toolName = toolName;
            this.argumentsMap = Map.of();
            this.resultObject = result != null ? ToolResult.success(result) : null;
            this.legacyArgumentsString = arguments;
            this.legacyResultString = result;
        }

        public String getToolName() { return toolName; }

        /** 类型化 args:LLM 原始 ToolCall.arguments 的 Map 副本 */
        public Map<String, Object> getArgumentsMap() { return argumentsMap; }

        /** 类型化 result:工具实际返回的 ToolResult */
        public ToolResult getResultObject() { return resultObject; }

        /**
         * 旧式 String 形式 args。
         * 类型化构造下返回 {@link Map#toString()};旧式构造下逐字回放原入参。
         */
        public String getArguments() {
            if (legacyArgumentsString != null) return legacyArgumentsString;
            return argumentsMap.toString();
        }

        /**
         * 旧式 String 形式 result。
         * 类型化构造下返回 {@link ToolResult#getContent()};旧式构造下逐字回放原入参。
         */
        public String getResult() {
            if (legacyResultString != null) return legacyResultString;
            return resultObject != null ? resultObject.getContent() : null;
        }
    }
}
