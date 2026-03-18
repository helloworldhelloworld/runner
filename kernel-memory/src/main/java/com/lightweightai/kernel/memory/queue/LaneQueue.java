package com.lightweightai.kernel.memory.queue;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A serial execution queue for a single session.
 * Ensures tasks within a session are executed one at a time to prevent race conditions.
 *
 * Inspired by OpenClaw's Lane Queue system.
 */
public class LaneQueue {

    private static final Logger log = LoggerFactory.getLogger(LaneQueue.class);

    private final String sessionId;
    private final Queue<QueuedTask<?>> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor;

    public LaneQueue(String sessionId, ExecutorService executor) {
        this.sessionId = sessionId;
        this.executor = executor;
    }

    /**
     * Submit a task to the queue.
     * Returns a CompletableFuture that completes when the task finishes.
     */
    public <T> CompletableFuture<T> submit(Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        queue.offer(new QueuedTask<>(task, future));
        tryProcessNext();
        return future;
    }

    /**
     * Submit a void task.
     */
    public CompletableFuture<Void> submitVoid(Runnable task) {
        return submit(() -> {
            task.run();
            return null;
        });
    }

    /**
     * Submit an async task that itself returns a CompletableFuture.
     */
    public <T> CompletableFuture<T> submitAsync(Supplier<CompletableFuture<T>> asyncTask) {
        CompletableFuture<T> result = new CompletableFuture<>();

        submit(() -> {
            try {
                CompletableFuture<T> innerFuture = asyncTask.get();
                innerFuture.whenComplete((value, error) -> {
                    if (error != null) {
                        result.completeExceptionally(error);
                    } else {
                        result.complete(value);
                    }
                });
                // Wait for inner future to complete before processing next
                return innerFuture.join();
            } catch (Exception e) {
                result.completeExceptionally(e);
                throw e;
            }
        });

        return result;
    }

    /**
     * Get the number of pending tasks.
     */
    public int getPendingCount() {
        return queue.size();
    }

    /**
     * Check if the queue is currently processing.
     */
    public boolean isProcessing() {
        return running.get();
    }

    /**
     * Get the session ID this queue belongs to.
     */
    public String getSessionId() {
        return sessionId;
    }

    private void tryProcessNext() {
        if (running.compareAndSet(false, true)) {
            executor.submit(this::processQueue);
        }
    }

    private void processQueue() {
        try {
            QueuedTask<?> task;
            while ((task = queue.poll()) != null) {
                processTask(task);
            }
        } finally {
            running.set(false);
            // Check if new tasks were added while we were processing
            if (!queue.isEmpty()) {
                tryProcessNext();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void processTask(QueuedTask<T> task) {
        try {
            T result = task.task.get();
            task.future.complete(result);
            log.debug("Lane [{}] task completed", sessionId);
        } catch (Exception e) {
            log.error("Lane [{}] task failed", sessionId, e);
            task.future.completeExceptionally(e);
        }
    }

    private record QueuedTask<T>(Supplier<T> task, CompletableFuture<T> future) {}
}
