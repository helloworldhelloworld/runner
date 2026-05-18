package com.lightweightai.kernel.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CostTracker - 并发安全性")
class CostTrackerConcurrencyTest {

    @Test
    @DisplayName("多线程并发 record 后总消耗一致")
    void concurrentRecordAccumulates() throws Exception {
        CostTracker tracker = new CostTracker(Integer.MAX_VALUE);
        int threads = 10;
        int recordsPerThread = 1000;
        int inputPerRecord = 10;
        int outputPerRecord = 5;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                for (int i = 0; i < recordsPerThread; i++) {
                    tracker.record(inputPerRecord, outputPerRecord);
                }
                latch.countDown();
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        int expectedInput = threads * recordsPerThread * inputPerRecord;
        int expectedOutput = threads * recordsPerThread * outputPerRecord;

        assertEquals(expectedInput, tracker.getConsumedInputTokens());
        assertEquals(expectedOutput, tracker.getConsumedOutputTokens());
        assertEquals(expectedInput + expectedOutput, tracker.getTotalConsumed());
    }

    @Test
    @DisplayName("并发 record 中 isOverBudget 最终状态正确")
    void concurrentOverBudgetDetection() throws Exception {
        int budget = 5000;
        CostTracker tracker = new CostTracker(budget);
        int threads = 5;
        int recordsPerThread = 500;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                for (int i = 0; i < recordsPerThread; i++) {
                    tracker.record(5, 5);
                }
                latch.countDown();
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        int totalConsumed = threads * recordsPerThread * 10;
        assertEquals(totalConsumed, tracker.getTotalConsumed());
        assertTrue(tracker.isOverBudget());
        assertEquals(budget - totalConsumed, tracker.remainingTokens());
    }

    @Test
    @DisplayName("负数预算与零预算的边界")
    void negativeBudget() {
        CostTracker negTracker = new CostTracker(-100);
        negTracker.record(1000, 1000);
        assertFalse(negTracker.isOverBudget());
        assertEquals(Integer.MAX_VALUE, negTracker.remainingTokens());
    }

    @Test
    @DisplayName("恰好等于预算不超")
    void exactlyAtBudget() {
        CostTracker tracker = new CostTracker(100);
        tracker.record(50, 50);
        assertFalse(tracker.isOverBudget());
        assertEquals(0, tracker.remainingTokens());
    }

    @Test
    @DisplayName("超预算 1 token 即检测到")
    void onePastBudget() {
        CostTracker tracker = new CostTracker(100);
        tracker.record(50, 51);
        assertTrue(tracker.isOverBudget());
        assertEquals(-1, tracker.remainingTokens());
    }
}
