package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.plugin.PluginFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Options for LLM requests
 */
public class LLMOptions {

    private Double temperature;
    private Integer maxTokens;
    private Integer maxBudgetTokens;  // 总预算（input+output），0=无限
    private List<PluginFunction> tools;
    private List<Map<String, Object>> toolDefinitions;  // Raw tool definitions for API
    private String systemPrompt;

    private LLMOptions(Builder builder) {
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.maxBudgetTokens = builder.maxBudgetTokens;
        this.tools = builder.tools != null ? new ArrayList<>(builder.tools) : new ArrayList<>();
        this.toolDefinitions = builder.toolDefinitions != null ? new ArrayList<>(builder.toolDefinitions) : new ArrayList<>();
        this.systemPrompt = builder.systemPrompt;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public List<PluginFunction> getTools() {
        return tools;
    }

    public List<Map<String, Object>> getToolDefinitions() {
        return toolDefinitions;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public Integer getMaxBudgetTokens() {
        return maxBudgetTokens;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Double temperature;
        private Integer maxTokens;
        private Integer maxBudgetTokens;
        private List<PluginFunction> tools;
        private List<Map<String, Object>> toolDefinitions;
        private String systemPrompt;

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder maxBudgetTokens(int maxBudgetTokens) {
            this.maxBudgetTokens = maxBudgetTokens;
            return this;
        }

        public Builder tools(List<PluginFunction> tools) {
            this.tools = tools;
            return this;
        }

        public Builder toolDefinitions(List<Map<String, Object>> toolDefinitions) {
            this.toolDefinitions = toolDefinitions;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        /**
         * 从已有 LLMOptions 拷贝所有字段作为起点，方便在保留其它配置的前提下覆盖个别字段。
         */
        public Builder from(LLMOptions existing) {
            if (existing == null) return this;
            this.temperature = existing.temperature;
            this.maxTokens = existing.maxTokens;
            this.maxBudgetTokens = existing.maxBudgetTokens;
            this.tools = existing.tools != null ? new ArrayList<>(existing.tools) : null;
            this.toolDefinitions = existing.toolDefinitions != null
                    ? new ArrayList<>(existing.toolDefinitions) : null;
            this.systemPrompt = existing.systemPrompt;
            return this;
        }

        public LLMOptions build() {
            return new LLMOptions(this);
        }
    }
}
