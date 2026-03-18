package com.lightweightai.kernel.memory.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LaneQueueTest {

    private ExecutorService executor;
    private LaneQueue queue;

    @BeforeEach
    void setUp() {
        executor = Executors.newCachedThreadPool();
        queue = new LaneQueue("test-session", executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void testSubmitAndComplete() throws Exception {
        CompletableFuture<String> future = queue.submit(() -> "Hello");

        String result = future.get(5, TimeUnit.SECONDS);

        assertEquals("Hello", result);
    }

    @Test
    void testSubmitVoid() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);

        CompletableFuture<Void> future = queue.submitVoid(() -> counter.incrementAndGet());

        future.get(5, TimeUnit.SECONDS);
        assertEquals(1, counter.get());
    }

    @Test
    void testSerialExecution() throws Exception {
        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        // Submit multiple tasks
        CompletableFuture<Void> f1 = queue.submitVoid(() -> {
            sleep(50);
            results.add(1);
        });
        CompletableFuture<Void> f2 = queue.submitVoid(() -> {
            sleep(50);
            results.add(2);
        });
        CompletableFuture<Void> f3 = queue.submitVoid(() -> {
            sleep(50);
            results.add(3);
        });

        // Wait for all
        CompletableFuture.allOf(f1, f2, f3).get(5, TimeUnit.SECONDS);

        // Should be in order (serial execution)
        assertEquals(List.of(1, 2, 3), results);
    }

    @Test
    void testExceptionHandling() {
        CompletableFuture<String> future = queue.submit(() -> {
            throw new RuntimeException("Test error");
        });

        ExecutionException ex = assertThrows(ExecutionException.class, () ->
            future.get(5, TimeUnit.SECONDS)
        );

        assertTrue(ex.getCause() instanceof RuntimeException);
        assertEquals("Test error", ex.getCause().getMessage());
    }

    @Test
    void testTaskAfterException() throws Exception {
        // First task throws
        queue.submit(() -> {
            throw new RuntimeException("Error");
        });

        // Second task should still execute
        CompletableFuture<String> future = queue.submit(() -> "Success");

        String result = future.get(5, TimeUnit.SECONDS);
        assertEquals("Success", result);
    }

    @Test
    void testPendingCount() throws Exception {
        CountDownLatch blockLatch = new CountDownLatch(1);
        CountDownLatch startedLatch = new CountDownLatch(1);

        // Submit a blocking task
        queue.submit(() -> {
            startedLatch.countDown();
            try {
                blockLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        });

        startedLatch.await(1, TimeUnit.SECONDS);

        // Submit more tasks while first is blocked
        queue.submit(() -> "A");
        queue.submit(() -> "B");

        assertTrue(queue.getPendingCount() >= 2);

        // Unblock
        blockLatch.countDown();
    }

    @Test
    void testIsProcessing() throws Exception {
        CountDownLatch blockLatch = new CountDownLatch(1);
        CountDownLatch startedLatch = new CountDownLatch(1);

        queue.submit(() -> {
            startedLatch.countDown();
            try {
                blockLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        });

        startedLatch.await(1, TimeUnit.SECONDS);

        assertTrue(queue.isProcessing());

        blockLatch.countDown();
    }

    @Test
    void testSessionId() {
        assertEquals("test-session", queue.getSessionId());
    }

    @Test
    void testSubmitAsync() throws Exception {
        CompletableFuture<String> future = queue.submitAsync(() ->
            CompletableFuture.supplyAsync(() -> {
                sleep(50);
                return "Async result";
            })
        );

        String result = future.get(5, TimeUnit.SECONDS);
        assertEquals("Async result", result);
    }

    @Test
    void testSubmitAsyncPreservesOrder() throws Exception {
        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        CompletableFuture<Void> f1 = queue.submitAsync(() ->
            CompletableFuture.runAsync(() -> {
                sleep(100);
                results.add(1);
            })
        );

        CompletableFuture<Void> f2 = queue.submitAsync(() ->
            CompletableFuture.runAsync(() -> {
                sleep(50);
                results.add(2);
            })
        );

        CompletableFuture.allOf(f1, f2).get(5, TimeUnit.SECONDS);

        // Even though f2 is faster, it should wait for f1
        assertEquals(List.of(1, 2), results);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
