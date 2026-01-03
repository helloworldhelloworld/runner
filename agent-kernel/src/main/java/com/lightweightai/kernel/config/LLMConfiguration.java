package com.lightweightai.kernel.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for LLM providers
 */
public class LLMConfiguration {

    private Map<String, ProviderConfig> providers = new HashMap<>();
    private String defaultProvider;

    public Map<String, ProviderConfig> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, ProviderConfig> providers) {
        this.providers = providers;
    }

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public void addProvider(String name, ProviderConfig config) {
        this.providers.put(name, config);
    }

    /**
     * Provider-specific configuration
     */
    public static class ProviderConfig {
        private String type; // "claude", "openai"
        private String apiKey;
        private String endpoint;
        private String model;
        private Integer maxTokens;
        private RetryConfig retry = new RetryConfig();
        private TimeoutConfig timeout = new TimeoutConfig();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }

        public RetryConfig getRetry() {
            return retry;
        }

        public void setRetry(RetryConfig retry) {
            this.retry = retry;
        }

        public TimeoutConfig getTimeout() {
            return timeout;
        }

        public void setTimeout(TimeoutConfig timeout) {
            this.timeout = timeout;
        }
    }

    /**
     * Retry configuration
     */
    public static class RetryConfig {
        private int maxRetries = 3;
        private long initialDelayMs = 1000;
        private double backoffMultiplier = 2.0;

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }

        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }

        public void setBackoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
        }
    }

    /**
     * Timeout configuration
     */
    public static class TimeoutConfig {
        private long connectionTimeoutMs = 30000;
        private long requestTimeoutMs = 60000;

        public long getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(long connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }

        public long getRequestTimeoutMs() {
            return requestTimeoutMs;
        }

        public void setRequestTimeoutMs(long requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
        }
    }
}
