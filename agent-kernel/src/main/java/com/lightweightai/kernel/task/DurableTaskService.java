package com.lightweightai.kernel.task;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;

/** Lifecycle facade over TaskStore; recovery is conservative and never replays an unknown side effect. */
public final class DurableTaskService {
    private final TaskStore store;

    public DurableTaskService(TaskStore store) { this.store = Objects.requireNonNull(store, "store"); }

    public DurableTask create(String principalId, String idempotencyKey, String goal) {
        return store.create(principalId, idempotencyKey, goal);
    }

    public Optional<DurableTask> get(String taskId) { return store.get(taskId); }

    public DurableTask start(String taskId) {
        DurableTask current = require(taskId);
        if (current.status() == TaskStatus.RUNNING)
            throw new IllegalStateException("task already running: " + taskId);
        if (current.status() == TaskStatus.RECOVERY_REQUIRED)
            throw new IllegalStateException("task requires recovery decision: " + taskId);
        return store.compareAndSet(current, current.startAttempt(UUID.randomUUID().toString()));
    }

    public DurableTask checkpoint(String taskId, String stepId, String nextAction, Map<String, Object> data) {
        DurableTask current = require(taskId);
        if (current.status() != TaskStatus.RUNNING)
            throw new IllegalStateException("task is not running: " + taskId);
        return store.compareAndSet(current, current.checkpoint(
                new TaskCheckpoint(stepId, nextAction, data, current.version() + 1)));
    }

    public DurableTask succeed(String taskId, Map<String, Object> result) {
        DurableTask current = require(taskId);
        return store.compareAndSet(current, new DurableTask(current.taskId(), current.principalId(), current.idempotencyKey(),
                current.goal(), TaskStatus.SUCCEEDED, current.attempts(), current.runId(), current.checkpoint(), result,
                null, current.version() + 1, System.currentTimeMillis()));
    }

    public DurableTask fail(String taskId, String error) {
        DurableTask current = require(taskId);
        return store.compareAndSet(current, current.transition(TaskStatus.FAILED, current.runId(), error));
    }

    public DurableTask cancel(String taskId, String reason) {
        DurableTask current = require(taskId);
        return store.compareAndSet(current, current.transition(TaskStatus.CANCELLED, current.runId(), reason));
    }

    /** Mark in-flight work as requiring an explicit side-effect recovery decision after restart. */
    public List<DurableTask> recoverAfterRestart() {
        return store.list().stream().filter(t -> t.status() == TaskStatus.RUNNING).map(t ->
                store.compareAndSet(t, t.transition(TaskStatus.RECOVERY_REQUIRED, t.runId(),
                        "process restarted while attempt was running"))).toList();
    }

    private DurableTask require(String taskId) {
        return store.get(taskId).orElseThrow(() -> new IllegalArgumentException("task not found: " + taskId));
    }
}
