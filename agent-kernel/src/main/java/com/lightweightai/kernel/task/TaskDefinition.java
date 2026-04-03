package com.lightweightai.kernel.task;

import java.time.Duration;

/**
 * 任务定义 (Harness Engineering - Entropy Management)
 */
public class TaskDefinition<T> {

    private final String name;
    private final String description;
    private final TaskHandler<T> handler;
    private final RetryPolicy retryPolicy;
    private final Duration timeout;

    private TaskDefinition(Builder<T> builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.handler = builder.handler;
        this.retryPolicy = builder.retryPolicy != null ? builder.retryPolicy : RetryPolicy.none();
        this.timeout = builder.timeout != null ? builder.timeout : Duration.ofMinutes(5);
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public TaskHandler<T> getHandler() { return handler; }
    public RetryPolicy getRetryPolicy() { return retryPolicy; }
    public Duration getTimeout() { return timeout; }

    public static <T> Builder<T> builder() { return new Builder<>(); }

    public static class Builder<T> {
        private String name;
        private String description;
        private TaskHandler<T> handler;
        private RetryPolicy retryPolicy;
        private Duration timeout;

        public Builder<T> name(String name) { this.name = name; return this; }
        public Builder<T> description(String description) { this.description = description; return this; }
        public Builder<T> handler(TaskHandler<T> handler) { this.handler = handler; return this; }
        public Builder<T> retryPolicy(RetryPolicy policy) { this.retryPolicy = policy; return this; }
        public Builder<T> timeout(Duration timeout) { this.timeout = timeout; return this; }
        public TaskDefinition<T> build() { return new TaskDefinition<>(this); }
    }
}
