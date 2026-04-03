package com.lightweightai.kernel.task;

/**
 * 任务处理函数接口 (Harness Engineering - Entropy Management)
 */
@FunctionalInterface
public interface TaskHandler<T> {
    TaskResult<T> handle(TaskContext context);
}
