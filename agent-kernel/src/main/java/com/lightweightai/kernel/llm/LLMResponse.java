package com.lightweightai.kernel.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * Response from LLM provider
 */
public class LLMResponse {

    private final ConversationMessage message;
    private final List<ToolCall> toolCalls;
    private final String stopReason;
    private final UsageInfo usage;

    private LLMResponse(Builder builder) {
        this.message = builder.message;
        this.toolCalls = builder.toolCalls != null ? new ArrayList<>(builder.toolCalls) : new ArrayList<>();
        this.stopReason = builder.stopReason;
        this.usage = builder.usage;
    }

    public ConversationMessage getMessage() {
        return message;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public String getStopReason() {
        return stopReason;
    }

    public UsageInfo getUsage() {
        return usage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ConversationMessage message;
        private List<ToolCall> toolCalls;
        private String stopReason;
        private UsageInfo usage;

        public Builder message(ConversationMessage message) {
            this.message = message;
            return this;
        }

        public Builder toolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public Builder stopReason(String stopReason) {
            this.stopReason = stopReason;
            return this;
        }

        public Builder usage(UsageInfo usage) {
            this.usage = usage;
            return this;
        }

        public LLMResponse build() {
            return new LLMResponse(this);
        }
    }

    /**
     * Token usage information
     */
    public static class UsageInfo {
        private final int inputTokens;
        private final int outputTokens;

        public UsageInfo(int inputTokens, int outputTokens) {
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
        }

        public int getInputTokens() {
            return inputTokens;
        }

        public int getOutputTokens() {
            return outputTokens;
        }

        public int getTotalTokens() {
            return inputTokens + outputTokens;
        }
    }
}
