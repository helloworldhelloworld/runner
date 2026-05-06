package com.lightweightai.kernel.task.annotation;

import com.lightweightai.kernel.task.config.GuardExpression;
import com.lightweightai.kernel.task.core.Task;
import com.lightweightai.kernel.task.core.TaskContext;
import com.lightweightai.kernel.task.core.TaskRegistry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Predicate;

/**
 * 通过 Java SPI 扫描所有 {@link Task} 实现，
 * 凡标注 {@link TaskDef} 的类即注册到 {@link TaskRegistry}。
 *
 * <p>SPI 文件：{@code META-INF/services/com.lightweightai.kernel.task.core.Task}。</p>
 */
public final class TaskScanner {

    private TaskScanner() {}

    /** 扫描并注册全部带 {@link TaskDef} 的 Task。返回注册数量。 */
    public static int scanAndRegister(TaskRegistry registry) {
        return scanAndRegister(registry, t -> true);
    }

    /** 扫描并按 group 过滤后注册。 */
    public static int scanAndRegisterByGroup(TaskRegistry registry, String group) {
        return scanAndRegister(registry, def -> group.equals(def.group()));
    }

    public static int scanAndRegister(TaskRegistry registry, Predicate<TaskDef> filter) {
        int count = 0;
        for (Task task : discover()) {
            TaskDef def = task.getClass().getAnnotation(TaskDef.class);
            if (def == null) continue;
            if (!filter.test(def)) continue;
            registry.register(task);
            count++;
        }
        return count;
    }

    public static List<Task> discover() {
        List<Task> tasks = new ArrayList<>();
        ServiceLoader<Task> loader = ServiceLoader.load(Task.class);
        Iterator<Task> it = loader.iterator();
        while (it.hasNext()) {
            try {
                tasks.add(it.next());
            } catch (Throwable t) {
                // 单个 SPI 失败不应阻断其他实现
            }
        }
        return tasks;
    }

    /**
     * 暴露 {@link TaskDef#condition()} 解析为 {@link Predicate}。
     *
     * <p><b>必须 public</b>：跨包的 SPI 适配器与单元测试都需要调用（PR #109 review B1，
     * 原 PR 把它做成 package-private 直接导致测试编译失败）。</p>
     */
    public static Predicate<TaskContext> parseCondition(String condition) {
        if (condition == null || condition.isBlank()) {
            return ctx -> true;
        }
        return GuardExpression.parse(condition);
    }
}
