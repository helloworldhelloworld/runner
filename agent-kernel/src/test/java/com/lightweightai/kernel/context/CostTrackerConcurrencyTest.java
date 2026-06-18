package com.lightweightai.kernel.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CostTracker - concurrency & edge cases")
class CostTrackerConcurrencyTest {

    @Test
    @DisplayName("concurrent recording from multiple threads — total is consistent")
    void concurrentRecordingIsConsistent() throws Exception {
        int threads = 10;
        int recordsPerThread = 1000;
        CostTracker tracker = new CostTracker(0);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                for (int i = 0; i < recordsPerThread; i++) {
                    tracker.record(1, 1);
                }
                latch.countDown();
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        int expectedTotal = threads * recordsPerThread * 2;
        assertEquals(expectedTotal, tracker.getTotalConsumed(),
                "All concurrent records should accumulate atomically");
        assertEquals(threads * recordsPerThread, tracker.getConsumedInputTokens());
        assertEquals(threads * recordsPerThread, tracker.getConsumedOutputTokens());
    }

    @Test
    @DisplayName("negative maxBudgetTokens behaves like unlimited (same as 0)")
    void negativeBudgetBehavesLikeUnlimited() {
        CostTracker tracker = new CostTracker(-100);
        tracker.record(999999, 999999);
        assertFalse(tracker.isOverBudget(),
                "Negative budget should behave like unlimited (condition: maxBudgetTokens <= 0)");
        assertEquals(Integer.MAX_VALUE, tracker.remainingTokens());
    }

    @Test
    @DisplayName("exact budget boundary — consumed == budget is NOT over budget")
    void exactBoundaryIsNotOverBudget() {
        CostTracker tracker = new CostTracker(1000);
        tracker.record(500, 500);
        assertFalse(tracker.isOverBudget(), "Exactly at budget should not be over");
        assertEquals(0, tracker.remainingTokens());
    }

    @Test
    @DisplayName("one token over budget — triggers over budget")
    void oneOverBudgetTriggersFlag() {
        CostTracker tracker = new CostTracker(1000);
        tracker.record(500, 501);
        assertTrue(tracker.isOverBudget());
        assertEquals(-1, tracker.remainingTokens());
    }

    @Test
    @DisplayName("getMaxBudgetTokens returns configured value")
    void getMaxBudgetReturnsConfigured() {
        assertEquals(5000, new CostTracker(5000).getMaxBudgetTokens());
        assertEquals(0, new CostTracker(0).getMaxBudgetTokens());
    }

    @Test
    @DisplayName("record with zero tokens is valid and does not change totals")
    void recordZeroTokens() {
        CostTracker tracker = new CostTracker(100);
        tracker.record(0, 0);
        assertEquals(0, tracker.getTotalConsumed());
        assertEquals(100, tracker.remainingTokens());
    }
}
