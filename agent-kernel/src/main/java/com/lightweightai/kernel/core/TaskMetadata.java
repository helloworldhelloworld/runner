package com.lightweightai.kernel.core;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Metadata about a task
 */
public class TaskMetadata {

    private final String name;
    private final String description;
    private final Duration timeout;
    private final boolean cancellable;
    private final Map<String, Object> properties;

    private TaskMetadata(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.timeout = builder.timeout;
        this.cancellable = builder.cancellable;
        this.properties = new HashMap<>(builder.properties);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public boolean isCancellable() {
        return cancellable;
    }

    public Map<String, Object> getProperties() {
        return new HashMap<>(properties);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private String description = "";
        private Duration timeout = Duration.ofMinutes(5);
        private boolean cancellable = true;
        private Map<String, Object> properties = new HashMap<>();

        public Builder(String name) {
            this.name = name;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder cancellable(boolean cancellable) {
            this.cancellable = cancellable;
            return this;
        }

        public Builder property(String key, Object value) {
            this.properties.put(key, value);
            return this;
        }

        public TaskMetadata build() {
            return new TaskMetadata(this);
        }
    }
}
