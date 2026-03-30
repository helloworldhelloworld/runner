package com.lightweightai.kernel.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务注册表
 *
 * 管理 Task 实例的注册与查找。YAML 加载和注解扫描都将 Task 注册到这里。
 */
public class TaskRegistry {

    private static final Logger logger = LoggerFactory.getLogger(TaskRegistry.class);

    private final Map<String, Task> tasks = new ConcurrentHashMap<>();

    public void register(Task task) {
        Task prev = tasks.put(task.getName(), task);
        if (prev != null) {
            logger.info("Task '{}' 已被覆盖注册", task.getName());
        } else {
            logger.debug("注册 Task: {} - {}", task.getName(), task.getDescription());
        }
    }

    public Optional<Task> get(String name) {
        return Optional.ofNullable(tasks.get(name));
    }

    public List<Task> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(tasks.values()));
    }

    public boolean contains(String name) {
        return tasks.containsKey(name);
    }

    public int size() {
        return tasks.size();
    }
}
