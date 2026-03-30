package com.lightweightai.kernel.task;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务编排共享执行上下文
 *
 * 线程安全。任务通过此上下文读取上游结果、写入共享属性。
 * 每个任务的执行结果按 taskName 存储。
 */
public class TaskContext {

    private final String sessionId;
    private final String requestId;
    private final String userInput;
    private final Map<String, Object> attributes;
    private final Map<String, TaskResult> results;

    private TaskContext(Builder builder) {
        this.sessionId = builder.sessionId;
        this.requestId = builder.requestId;
        this.userInput = builder.userInput;
        this.attributes = new ConcurrentHashMap<>(builder.attributes);
        this.results = new ConcurrentHashMap<>();
    }

    public void putResult(String taskName, TaskResult result) {
        results.put(taskName, result);
    }

    public Optional<TaskResult> getResult(String taskName) {
        return Optional.ofNullable(results.get(taskName));
    }

    public Map<String, TaskResult> getAllResults() {
        return Collections.unmodifiableMap(results);
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getAttribute(String key, Class<T> type) {
        Object val = attributes.get(key);
        if (type.isInstance(val)) return Optional.of((T) val);
        return Optional.empty();
    }

    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public String getSessionId() { return sessionId; }
    public String getRequestId() { return requestId; }
    public String getUserInput() { return userInput; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String sessionId = "default";
        private String requestId = UUID.randomUUID().toString();
        private String userInput;
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();

        public Builder sessionId(String s) { this.sessionId = s; return this; }
        public Builder requestId(String r) { this.requestId = r; return this; }
        public Builder userInput(String u) { this.userInput = u; return this; }
        public Builder attribute(String k, Object v) { attributes.put(k, v); return this; }
        public TaskContext build() { return new TaskContext(this); }
    }
}
