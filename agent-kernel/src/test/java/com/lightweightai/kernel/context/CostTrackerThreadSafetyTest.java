package com.lightweightai.kernel.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CostTracker 并发安全与边界测试。
 *
 * 已有 CostTrackerTest 覆盖基本功能。
 * 此测试补充：并发写入不丢数据、负预算行为、remainingTokens 无限模式。
 */
@DisplayName("CostTracker - 并发安全与边界条件")
class CostTrackerThreadSafetyTest {

    @Test
    @DisplayName("多线程并发 record() 不丢失 token 计数")
    void concurrentRecordDoesNotLoseTokens() throws Exception {
        CostTracker tracker = new CostTracker(0);
        int threads = 10;
        int recordsPerThread = 1000;
        int inputPerRecord = 10;
        int outputPerRecord = 5;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                for (int i = 0; i < recordsPerThread; i++) {
                    tracker.record(inputPerRecord, outputPerRecord);
                }
                latch.countDown();
            });
        }

        latch.await();
        pool.shutdown();

        int expectedInput = threads * recordsPerThread * inputPerRecord;
        int expectedOutput = threads * recordsPerThread * outputPerRecord;

        assertEquals(expectedInput, tracker.getConsumedInputTokens(),
                "Concurrent record() must not lose input tokens");
        assertEquals(expectedOutput, tracker.getConsumedOutputTokens(),
                "Concurrent record() must not lose output tokens");
        assertEquals(expectedInput + expectedOutput, tracker.getTotalConsumed());
    }

    @Test
    @DisplayName("无限预算 remainingTokens 返回 Integer.MAX_VALUE")
    void unlimitedBudgetRemainingIsMaxValue() {
        CostTracker tracker = new CostTracker(0);
        tracker.record(100000, 100000);
        assertEquals(Integer.MAX_VALUE, tracker.remainingTokens(),
                "Unlimited budget (0) must return Integer.MAX_VALUE regardless of consumption");
    }

    @Test
    @DisplayName("负预算模式（maxBudgetTokens < 0）视为无限")
    void negativeBudgetTreatedAsUnlimited() {
        CostTracker tracker = new CostTracker(-1);
        tracker.record(999999, 999999);
        assertFalse(tracker.isOverBudget());
        assertEquals(Integer.MAX_VALUE, tracker.remainingTokens());
    }

    @Test
    @DisplayName("恰好等于预算时不算超预算")
    void exactBudgetIsNotOver() {
        CostTracker tracker = new CostTracker(1000);
        tracker.record(500, 500);
        assertFalse(tracker.isOverBudget(), "Exactly at budget should not be over");
        assertEquals(0, tracker.remainingTokens());
    }

    @Test
    @DisplayName("超预算 1 token 即检测到")
    void oneTokenOverBudget() {
        CostTracker tracker = new CostTracker(1000);
        tracker.record(500, 501);
        assertTrue(tracker.isOverBudget(), "1 token over budget must be detected");
        assertEquals(-1, tracker.remainingTokens());
    }

    @Test
    @DisplayName("getMaxBudgetTokens 返回构造时的值")
    void maxBudgetReturnsConstructorValue() {
        assertEquals(5000, new CostTracker(5000).getMaxBudgetTokens());
        assertEquals(0, new CostTracker(0).getMaxBudgetTokens());
    }
}
