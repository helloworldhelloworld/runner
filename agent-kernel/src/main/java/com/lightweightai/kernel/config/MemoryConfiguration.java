package com.lightweightai.kernel.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for conversation memory and context management
 */
public class MemoryConfiguration {

    private StorageConfig storage = new StorageConfig();
    private StrategyConfig strategy = new StrategyConfig();
    private Map<String, StrategyConfig> modelStrategies = new HashMap<>();

    public StorageConfig getStorage() {
        return storage;
    }

    public void setStorage(StorageConfig storage) {
        this.storage = storage;
    }

    public StrategyConfig getStrategy() {
        return strategy;
    }

    public void setStrategy(StrategyConfig strategy) {
        this.strategy = strategy;
    }

    public Map<String, StrategyConfig> getModelStrategies() {
        return modelStrategies;
    }

    public void setModelStrategies(Map<String, StrategyConfig> modelStrategies) {
        this.modelStrategies = modelStrategies;
    }

    /**
     * Storage backend configuration
     */
    public static class StorageConfig {
        private String type = "inmemory"; // inmemory, redis, postgres, file
        private Map<String, Object> config = new HashMap<>();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Map<String, Object> getConfig() {
            return config;
        }

        public void setConfig(Map<String, Object> config) {
            this.config = config;
        }
    }

    /**
     * Context optimization strategy configuration
     */
    public static class StrategyConfig {
        private String type = "sliding"; // sliding, summarization, semantic, hybrid
        private Map<String, Object> config = new HashMap<>();

        public StrategyConfig() {
            // Default config for sliding window
            config.put("maxMessages", 50);
            config.put("keepSystemMessage", true);
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Map<String, Object> getConfig() {
            return config;
        }

        public void setConfig(Map<String, Object> config) {
            this.config = config;
        }
    }
}
