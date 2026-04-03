package com.lightweightai.kernel.task;

/**
 * 任务生命周期观察者 (Harness Engineering - Entropy Management)
 */
public interface TaskObserver {

    default void onTaskSubmitted(String taskId, String taskName) {}

    default void onTaskStarted(String taskId, String taskName) {}

    default void onTaskCompleted(String taskId, String taskName, boolean success, long durationMs) {}

    default void onTaskFailed(String taskId, String taskName, String error) {}

    default void onTaskRetry(String taskId, String taskName, int attempt) {}
}
