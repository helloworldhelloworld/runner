package com.lightweightai.agent;

/**
 * 聊天选项
 *
 * 简化的配置类，用户只需要关心常用参数
 */
public class ChatOptions {

    private final Double temperature;
    private final Integer maxTokens;
    private final String systemPrompt;

    private ChatOptions(Builder builder) {
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.systemPrompt = builder.systemPrompt;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Double temperature;
        private Integer maxTokens;
        private String systemPrompt;

        public Builder temperature(double temperature) {
            if (temperature < 0 || temperature > 1) {
                throw new IllegalArgumentException("Temperature must be between 0 and 1");
            }
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            if (maxTokens <= 0) {
                throw new IllegalArgumentException("maxTokens must be positive");
            }
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public ChatOptions build() {
            return new ChatOptions(this);
        }
    }
}
