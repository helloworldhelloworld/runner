package com.lightweightai.kernel.memory.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Manages Lane Queues for multiple sessions.
 * Each session gets its own queue to ensure serial execution within sessions.
 *
 * Key features:
 * - Automatic queue creation per session
 * - Idle queue cleanup
 * - Statistics tracking
 *
 * Inspired by OpenClaw's Lane Queue system.
 */
public class LaneQueueManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LaneQueueManager.class);

    private final Map<String, ManagedLane> lanes = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final ScheduledExecutorService cleanupScheduler;
    private final Duration idleTimeout;

    /**
     * Create with default settings.
     */
    public LaneQueueManager() {
        this(
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "lane-queue");
                t.setDaemon(true);
                return t;
            }),
            Duration.ofMinutes(30)
        );
    }

    /**
     * Create with custom executor and idle timeout.
     */
    public LaneQueueManager(ExecutorService executor, Duration idleTimeout) {
        this.executor = executor;
        this.idleTimeout = idleTimeout;
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lane-cleanup");
            t.setDaemon(true);
            return t;
        });

        // Schedule periodic cleanup
        cleanupScheduler.scheduleAtFixedRate(
            this::cleanupIdleLanes,
            idleTimeout.toMinutes(),
            idleTimeout.toMinutes() / 2,
            TimeUnit.MINUTES
        );

        log.info("LaneQueueManager initialized with idle timeout: {}", idleTimeout);
    }

    /**
     * Submit a task to a session's queue.
     */
    public <T> CompletableFuture<T> submit(String sessionId, Supplier<T> task) {
        ManagedLane lane = getOrCreateLane(sessionId);
        lane.updateLastAccess();
        return lane.queue.submit(task);
    }

    /**
     * Submit a void task.
     */
    public CompletableFuture<Void> submitVoid(String sessionId, Runnable task) {
        return submit(sessionId, () -> {
            task.run();
            return null;
        });
    }

    /**
     * Submit an async task.
     */
    public <T> CompletableFuture<T> submitAsync(String sessionId, Supplier<CompletableFuture<T>> asyncTask) {
        ManagedLane lane = getOrCreateLane(sessionId);
        lane.updateLastAccess();
        return lane.queue.submitAsync(asyncTask);
    }

    /**
     * Get the queue for a specific session (creates if not exists).
     */
    public LaneQueue getQueue(String sessionId) {
        return getOrCreateLane(sessionId).queue;
    }

    /**
     * Check if a session has an active queue.
     */
    public boolean hasQueue(String sessionId) {
        return lanes.containsKey(sessionId);
    }

    /**
     * Get the number of active lanes.
     */
    public int getActiveLaneCount() {
        return lanes.size();
    }

    /**
     * Get statistics for a session's queue.
     */
    public LaneStats getStats(String sessionId) {
        ManagedLane lane = lanes.get(sessionId);
        if (lane == null) {
            return null;
        }
        return new LaneStats(
            sessionId,
            lane.queue.getPendingCount(),
            lane.queue.isProcessing(),
            lane.lastAccess,
            lane.taskCount
        );
    }

    /**
     * Remove a session's queue.
     */
    public void removeLane(String sessionId) {
        ManagedLane removed = lanes.remove(sessionId);
        if (removed != null) {
            log.debug("Removed lane for session: {}", sessionId);
        }
    }

    /**
     * Clear all lanes.
     */
    public void clearAll() {
        lanes.clear();
        log.info("Cleared all lanes");
    }

    @Override
    public void close() {
        cleanupScheduler.shutdown();
        executor.shutdown();

        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        lanes.clear();
        log.info("LaneQueueManager closed");
    }

    private ManagedLane getOrCreateLane(String sessionId) {
        return lanes.computeIfAbsent(sessionId, id -> {
            log.debug("Creating new lane for session: {}", id);
            return new ManagedLane(new LaneQueue(id, executor));
        });
    }

    private void cleanupIdleLanes() {
        Instant cutoff = Instant.now().minus(idleTimeout);
        int removed = 0;

        for (Map.Entry<String, ManagedLane> entry : lanes.entrySet()) {
            ManagedLane lane = entry.getValue();
            if (lane.lastAccess.isBefore(cutoff) && !lane.queue.isProcessing()) {
                lanes.remove(entry.getKey());
                removed++;
            }
        }

        if (removed > 0) {
            log.info("Cleaned up {} idle lanes", removed);
        }
    }

    // Inner classes

    private static class ManagedLane {
        final LaneQueue queue;
        volatile Instant lastAccess;
        volatile long taskCount;

        ManagedLane(LaneQueue queue) {
            this.queue = queue;
            this.lastAccess = Instant.now();
            this.taskCount = 0;
        }

        void updateLastAccess() {
            this.lastAccess = Instant.now();
            this.taskCount++;
        }
    }

    public record LaneStats(
        String sessionId,
        int pendingTasks,
        boolean processing,
        Instant lastAccess,
        long totalTasks
    ) {}
}
