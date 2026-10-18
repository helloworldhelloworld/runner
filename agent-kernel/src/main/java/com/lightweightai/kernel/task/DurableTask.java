package com.lightweightai.kernel.task;

import java.util.Map;

public record DurableTask(String taskId, String principalId, String idempotencyKey, String goal,
                          TaskStatus status, int attempts, String runId, TaskCheckpoint checkpoint,
                          Map<String, Object> result, String error, long version, long updatedAt) {
    public DurableTask {
        result = result == null ? Map.of() : Map.copyOf(result);
    }

    public DurableTask transition(TaskStatus next, String nextRunId, String nextError) {
        return new DurableTask(taskId, principalId, idempotencyKey, goal, next, attempts,
                nextRunId, checkpoint, result, nextError, version + 1, System.currentTimeMillis());
    }

    public DurableTask startAttempt(String nextRunId) {
        return new DurableTask(taskId, principalId, idempotencyKey, goal, TaskStatus.RUNNING,
                attempts + 1, nextRunId, checkpoint, result, null, version + 1, System.currentTimeMillis());
    }

    public DurableTask checkpoint(TaskCheckpoint next) {
        return new DurableTask(taskId, principalId, idempotencyKey, goal, status, attempts, runId,
                next, result, error, version + 1, System.currentTimeMillis());
    }
}
