package com.lightweightai.kernel.task.core;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Task 实例注册表。
 *
 * <p><b>注意</b>：与 {@link com.lightweightai.kernel.agent.ToolRegistry} 是两套并行 registry，
 * 因为两者面向不同的调用方（编排器 vs LLM）。Task 不应自动暴露为 Tool；
 * 需要让 LLM 自主调用时，通过 {@code TaskGraphTool} 显式包装。</p>
 *
 * <p>strict 模式下重复注册会抛错，避免 SPI 路径多个实现同名静默覆盖。</p>
 */
public final class TaskRegistry {

    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private final boolean strict;

    public TaskRegistry() {
        this(true);
    }

    public TaskRegistry(boolean strict) {
        this.strict = strict;
    }

    public void register(Task task) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(task.getName(), "task.name");
        Task prev = tasks.putIfAbsent(task.getName(), task);
        if (prev != null && strict && prev != task) {
            throw new IllegalStateException("Task name already registered: " + task.getName()
                + " (existing=" + prev.getClass().getName() + ", incoming=" + task.getClass().getName()
                + "). Use new TaskRegistry(false) to allow overwrite.");
        }
        if (prev != null && !strict) {
            tasks.put(task.getName(), task);
        }
    }

    public Optional<Task> get(String name) {
        return Optional.ofNullable(tasks.get(name));
    }

    public boolean has(String name) {
        return tasks.containsKey(name);
    }

    public int size() {
        return tasks.size();
    }

    public void unregister(String name) {
        tasks.remove(name);
    }

    public void clear() {
        tasks.clear();
    }
}
