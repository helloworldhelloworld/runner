package com.lightweightai.kernel.task.graph;

import com.lightweightai.kernel.task.core.Task;

import java.util.List;

/**
 * 常见 DAG 形状的便捷构造方法。
 */
public final class Tasks {

    private Tasks() {}

    /** 顺序：t1 → t2 → ... → tN。 */
    public static TaskGraph sequential(Task... tasks) {
        if (tasks.length == 0) {
            throw new IllegalArgumentException("at least one task required");
        }
        TaskGraph.Builder b = TaskGraph.builder();
        b.addTask(tasks[0]);
        for (int i = 1; i < tasks.length; i++) {
            b.addTask(tasks[i], tasks[i - 1].getName());
        }
        return b.build();
    }

    /** 并行：所有任务同层。 */
    public static TaskGraph parallel(Task... tasks) {
        TaskGraph.Builder b = TaskGraph.builder();
        for (Task t : tasks) b.addTask(t);
        return b.build();
    }

    /** Fan-out / fan-in：source → branches → sink。 */
    public static TaskGraph fanOutFanIn(Task source, List<Task> branches, Task sink) {
        TaskGraph.Builder b = TaskGraph.builder();
        b.addTask(source);
        String[] branchNames = new String[branches.size()];
        for (int i = 0; i < branches.size(); i++) {
            Task branch = branches.get(i);
            b.addTask(branch, source.getName());
            branchNames[i] = branch.getName();
        }
        b.addTask(sink, branchNames);
        return b.build();
    }
}
