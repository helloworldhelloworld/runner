package com.lightweightai.kernel.task;

import java.time.Instant;

/**
 * 任务实例 (Harness Engineering - Entropy Management)
 */
public class TaskInstance<T> {

    private final String taskId;
    private final String taskName;
    private final String traceId;
    private volatile TaskStatus status;
    private volatile TaskResult<T> result;
    private final Instant createdAt;
    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private volatile int retryCount;

    public TaskInstance(String taskId, String taskName, String traceId) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.traceId = traceId;
        this.status = TaskStatus.PENDING;
        this.createdAt = Instant.now();
        this.retryCount = 0;
    }

    public void markRunning() {
        this.status = TaskStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void markSuccess(TaskResult<T> result) {
        this.status = TaskStatus.SUCCESS;
        this.result = result;
        this.completedAt = Instant.now();
    }

    public void markFailed(TaskResult<T> result) {
        this.status = TaskStatus.FAILED;
        this.result = result;
        this.completedAt = Instant.now();
    }

    public void markRetry() {
        this.status = TaskStatus.RETRY;
        this.retryCount++;
    }

    public void markCancelled() {
        this.status = TaskStatus.CANCELLED;
        this.completedAt = Instant.now();
    }

    public long getDurationMs() {
        if (startedAt == null) return 0;
        Instant end = completedAt != null ? completedAt : Instant.now();
        return end.toEpochMilli() - startedAt.toEpochMilli();
    }

    public String getTaskId() { return taskId; }
    public String getTaskName() { return taskName; }
    public String getTraceId() { return traceId; }
    public TaskStatus getStatus() { return status; }
    public TaskResult<T> getResult() { return result; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public int getRetryCount() { return retryCount; }
}
