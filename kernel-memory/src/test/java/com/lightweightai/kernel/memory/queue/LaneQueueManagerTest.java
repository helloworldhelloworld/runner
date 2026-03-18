package com.lightweightai.kernel.memory.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LaneQueueManagerTest {

    private LaneQueueManager manager;

    @BeforeEach
    void setUp() {
        manager = new LaneQueueManager(
            Executors.newCachedThreadPool(),
            Duration.ofMinutes(5)
        );
    }

    @AfterEach
    void tearDown() {
        manager.close();
    }

    @Test
    void testSubmitToSession() throws Exception {
        CompletableFuture<String> future = manager.submit("session-1", () -> "Hello");

        String result = future.get(5, TimeUnit.SECONDS);

        assertEquals("Hello", result);
        assertTrue(manager.hasQueue("session-1"));
    }

    @Test
    void testMultipleSessions() throws Exception {
        List<Integer> session1Results = Collections.synchronizedList(new ArrayList<>());
        List<Integer> session2Results = Collections.synchronizedList(new ArrayList<>());

        // Submit to session 1
        manager.submitVoid("session-1", () -> {
            sleep(100);
            session1Results.add(1);
        });
        manager.submitVoid("session-1", () -> session1Results.add(2));

        // Submit to session 2
        manager.submitVoid("session-2", () -> session2Results.add(1));
        manager.submitVoid("session-2", () -> session2Results.add(2));

        // Wait for completion
        Thread.sleep(500);

        // Each session should have its own order
        assertEquals(List.of(1, 2), session1Results);
        assertEquals(List.of(1, 2), session2Results);

        assertEquals(2, manager.getActiveLaneCount());
    }

    @Test
    void testSessionIsolation() throws Exception {
        AtomicInteger session1Counter = new AtomicInteger(0);
        AtomicInteger session2Counter = new AtomicInteger(0);

        CountDownLatch session1Blocked = new CountDownLatch(1);
        CountDownLatch session1Started = new CountDownLatch(1);

        // Block session 1
        manager.submit("session-1", () -> {
            session1Started.countDown();
            try {
                session1Blocked.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return session1Counter.incrementAndGet();
        });

        session1Started.await(1, TimeUnit.SECONDS);

        // Session 2 should still be able to process
        CompletableFuture<Integer> future = manager.submit("session-2", session2Counter::incrementAndGet);
        Integer result = future.get(1, TimeUnit.SECONDS);

        assertEquals(1, result);
        assertEquals(0, session1Counter.get());  // Session 1 still blocked

        // Unblock session 1
        session1Blocked.countDown();
    }

    @Test
    void testGetQueue() {
        LaneQueue queue = manager.getQueue("my-session");

        assertNotNull(queue);
        assertEquals("my-session", queue.getSessionId());
        assertTrue(manager.hasQueue("my-session"));
    }

    @Test
    void testHasQueue() {
        assertFalse(manager.hasQueue("nonexistent"));

        manager.submit("existing", () -> "test");

        assertTrue(manager.hasQueue("existing"));
    }

    @Test
    void testGetStats() throws Exception {
        manager.submit("session-1", () -> "task1");
        manager.submit("session-1", () -> "task2");

        Thread.sleep(100);  // Let tasks complete

        LaneQueueManager.LaneStats stats = manager.getStats("session-1");

        assertNotNull(stats);
        assertEquals("session-1", stats.sessionId());
        assertEquals(2, stats.totalTasks());
        assertNotNull(stats.lastAccess());
    }

    @Test
    void testGetStatsNonexistent() {
        LaneQueueManager.LaneStats stats = manager.getStats("nonexistent");
        assertNull(stats);
    }

    @Test
    void testRemoveLane() {
        manager.submit("to-remove", () -> "test");

        assertTrue(manager.hasQueue("to-remove"));

        manager.removeLane("to-remove");

        assertFalse(manager.hasQueue("to-remove"));
    }

    @Test
    void testClearAll() {
        manager.submit("session-1", () -> "test");
        manager.submit("session-2", () -> "test");
        manager.submit("session-3", () -> "test");

        assertEquals(3, manager.getActiveLaneCount());

        manager.clearAll();

        assertEquals(0, manager.getActiveLaneCount());
    }

    @Test
    void testSubmitAsync() throws Exception {
        CompletableFuture<String> future = manager.submitAsync("session-1", () ->
            CompletableFuture.supplyAsync(() -> "Async")
        );

        String result = future.get(5, TimeUnit.SECONDS);
        assertEquals("Async", result);
    }

    @Test
    void testActiveLaneCount() {
        assertEquals(0, manager.getActiveLaneCount());

        manager.submit("session-1", () -> null);
        assertEquals(1, manager.getActiveLaneCount());

        manager.submit("session-2", () -> null);
        assertEquals(2, manager.getActiveLaneCount());

        manager.submit("session-1", () -> null);  // Same session
        assertEquals(2, manager.getActiveLaneCount());
    }

    @Test
    void testConcurrentAccess() throws Exception {
        int numThreads = 10;
        int tasksPerThread = 100;
        ExecutorService testExecutor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger totalCompleted = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final String sessionId = "session-" + (i % 3);  // 3 sessions
            testExecutor.submit(() -> {
                try {
                    for (int j = 0; j < tasksPerThread; j++) {
                        manager.submit(sessionId, totalCompleted::incrementAndGet).get();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        assertEquals(numThreads * tasksPerThread, totalCompleted.get());

        testExecutor.shutdown();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
