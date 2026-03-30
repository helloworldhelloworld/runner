package com.lightweightai.kernel.task.annotation;

import com.lightweightai.kernel.task.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 任务注解扫描器
 *
 * 通过 Java SPI（ServiceLoader）自动发现 Task 实现，
 * 并按 &#64;TaskDef.group 自动组装 TaskGraph。
 */
public class TaskScanner {

    private static final Logger logger = LoggerFactory.getLogger(TaskScanner.class);

    /**
     * SPI 扫描所有 Task 实现
     */
    public static List<Task> scan() {
        List<Task> tasks = new ArrayList<>();
        ServiceLoader.load(Task.class).forEach(tasks::add);
        logger.info("SPI 扫描到 {} 个 Task 实现", tasks.size());
        return tasks;
    }

    /**
     * 扫描并注册到 TaskRegistry
     */
    public static void scanAndRegister(TaskRegistry registry) {
        scan().forEach(registry::register);
    }

    /**
     * 从已注册的 Task 中，按 @TaskDef.group 自动组装 TaskGraph
     */
    public static Map<String, TaskGraph> assembleGraphs(TaskRegistry registry) {
        Map<String, List<TaskMeta>> groups = new HashMap<>();

        for (Task task : registry.getAll()) {
            TaskDef def = task.getClass().getAnnotation(TaskDef.class);
            if (def == null) continue;
            groups.computeIfAbsent(def.group(), k -> new ArrayList<>())
                    .add(new TaskMeta(task, def));
        }

        Map<String, TaskGraph> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<TaskMeta>> entry : groups.entrySet()) {
            TaskGraph graph = buildGraph(entry.getKey(), entry.getValue());
            result.put(entry.getKey(), graph);
            logger.info("自动组装 TaskGraph '{}': {} 个任务",
                    entry.getKey(), entry.getValue().size());
        }
        return result;
    }

    private static TaskGraph buildGraph(String name, List<TaskMeta> metas) {
        // 按 order 排序
        metas.sort(Comparator.comparingInt(m -> m.def.order()));

        TaskGraph.Builder builder = TaskGraph.builder().name(name);
        for (TaskMeta meta : metas) {
            TaskDef def = meta.def;
            if (def.dependsOn().length > 0) {
                builder.addTask(meta.task, def.dependsOn());
            } else {
                builder.addTask(meta.task);
            }

            if (!def.condition().isEmpty()) {
                builder.withCondition(meta.task.getName(), parseCondition(def.condition()));
            }

            builder.withJoinStrategy(meta.task.getName(), def.joinStrategy());
        }
        return builder.build();
    }

    /**
     * 解析条件字符串 "taskName:STATUS" → Predicate
     */
    static java.util.function.Predicate<TaskContext> parseCondition(String expr) {
        String[] parts = expr.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid condition expression: '" + expr
                    + "', expected format: 'taskName:STATUS'");
        }
        String taskName = parts[0].trim();
        String status = parts[1].trim();
        return ctx -> ctx.getResult(taskName)
                .map(r -> r.getStatus().name().equals(status))
                .orElse(false);
    }

    private record TaskMeta(Task task, TaskDef def) {}
}
