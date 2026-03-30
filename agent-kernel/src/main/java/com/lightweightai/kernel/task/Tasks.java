package com.lightweightai.kernel.task;

/**
 * 便捷编排方法
 */
public final class Tasks {

    private Tasks() {}

    /**
     * 顺序管道：每个任务依赖前一个
     */
    public static TaskGraph sequential(String name, Task... tasks) {
        TaskGraph.Builder builder = TaskGraph.builder().name(name);
        String previous = null;
        for (Task task : tasks) {
            if (previous == null) {
                builder.addTask(task);
            } else {
                builder.addTask(task, previous);
            }
            previous = task.getName();
        }
        return builder.build();
    }

    /**
     * 并行组：所有任务同时执行，无依赖
     */
    public static TaskGraph parallel(String name, Task... tasks) {
        TaskGraph.Builder builder = TaskGraph.builder().name(name);
        for (Task task : tasks) {
            builder.addTask(task);
        }
        return builder.build();
    }

    /**
     * 扇出-汇聚：并行执行后由 mergeTask 汇总
     */
    public static TaskGraph fanOutFanIn(String name, Task mergeTask, Task... parallelTasks) {
        TaskGraph.Builder builder = TaskGraph.builder().name(name);
        String[] depNames = new String[parallelTasks.length];
        for (int i = 0; i < parallelTasks.length; i++) {
            builder.addTask(parallelTasks[i]);
            depNames[i] = parallelTasks[i].getName();
        }
        builder.addTask(mergeTask, depNames);
        return builder.build();
    }
}
