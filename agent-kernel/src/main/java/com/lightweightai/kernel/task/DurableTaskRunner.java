package com.lightweightai.kernel.task;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.BiFunction;

/** Executes persisted tasks with bounded workers; a recovered attempt is never replayed implicitly. */
public final class DurableTaskRunner implements AutoCloseable {
    private final DurableTaskService service;
    private final ExecutorService executor;
    private final ConcurrentMap<String, Future<?>> active = new ConcurrentHashMap<>();

    public DurableTaskRunner(DurableTaskService service, int workers) {
        this.service = Objects.requireNonNull(service, "service");
        if (workers < 1) throw new IllegalArgumentException("workers must be positive");
        this.executor = Executors.newFixedThreadPool(workers, r -> {
            Thread thread = new Thread(r, "durable-task-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized boolean submit(String taskId, BiFunction<DurableTask, TaskCheckpoint, Map<String, Object>> work) {
        Objects.requireNonNull(work, "work");
        if (active.containsKey(taskId)) return false;
        DurableTask started = service.start(taskId);
        Future<?> future = executor.submit(() -> {
            try {
                DurableTask current = service.get(taskId).orElseThrow();
                service.succeed(taskId, work.apply(current, current.checkpoint()));
            } catch (CancellationException e) {
                service.cancel(taskId, "worker cancelled");
            } catch (Exception e) {
                service.fail(taskId, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            } finally {
                active.remove(taskId);
            }
        });
        active.put(taskId, future);
        return started != null;
    }

    public boolean cancel(String taskId) {
        Future<?> future = active.remove(taskId);
        if (future == null) return false;
        future.cancel(true);
        service.cancel(taskId, "cancelled by caller");
        return true;
    }

    @Override public void close() { executor.shutdownNow(); active.clear(); }
}
