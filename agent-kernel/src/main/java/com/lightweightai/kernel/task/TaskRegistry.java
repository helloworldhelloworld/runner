package com.lightweightai.kernel.task;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务注册表 (Harness Engineering - Entropy Management)
 */
public class TaskRegistry {

    private final Map<String, TaskDefinition<?>> definitions = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> void register(TaskDefinition<T> definition) {
        definitions.put(definition.getName(), definition);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<TaskDefinition<T>> get(String name) {
        return Optional.ofNullable((TaskDefinition<T>) definitions.get(name));
    }

    public boolean has(String name) {
        return definitions.containsKey(name);
    }

    public Map<String, TaskDefinition<?>> getAll() {
        return Collections.unmodifiableMap(definitions);
    }

    public int size() { return definitions.size(); }
}
