package com.lightweightai.kernel.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CostTracker - Token 预算追踪")
class CostTrackerTest {

    @Test
    @DisplayName("初始状态未超预算")
    void initiallyUnderBudget() {
        CostTracker tracker = new CostTracker(10000);
        assertFalse(tracker.isOverBudget());
        assertEquals(10000, tracker.remainingTokens());
    }

    @Test
    @DisplayName("记录消耗后更新余额")
    void recordReducesRemaining() {
        CostTracker tracker = new CostTracker(10000);
        tracker.record(500, 200);
        assertEquals(9300, tracker.remainingTokens());
        assertFalse(tracker.isOverBudget());
    }

    @Test
    @DisplayName("超预算后 isOverBudget 返回 true")
    void overBudgetDetected() {
        CostTracker tracker = new CostTracker(1000);
        tracker.record(800, 300); // 1100 > 1000
        assertTrue(tracker.isOverBudget());
        assertTrue(tracker.remainingTokens() < 0);
    }

    @Test
    @DisplayName("多次记录累积")
    void multipleRecordsAccumulate() {
        CostTracker tracker = new CostTracker(5000);
        tracker.record(1000, 500);
        tracker.record(1000, 500);
        tracker.record(1000, 500);
        assertEquals(500, tracker.remainingTokens());
        assertFalse(tracker.isOverBudget());

        tracker.record(500, 100);
        assertTrue(tracker.isOverBudget());
    }

    @Test
    @DisplayName("总消耗统计正确")
    void totalConsumedCorrect() {
        CostTracker tracker = new CostTracker(10000);
        tracker.record(300, 100);
        tracker.record(400, 200);
        assertEquals(700, tracker.getConsumedInputTokens());
        assertEquals(300, tracker.getConsumedOutputTokens());
        assertEquals(1000, tracker.getTotalConsumed());
    }

    @Test
    @DisplayName("无限预算（0）永远不超")
    void unlimitedBudget() {
        CostTracker tracker = new CostTracker(0); // 0 = unlimited
        tracker.record(999999, 999999);
        assertFalse(tracker.isOverBudget());
    }

    @Test
    @DisplayName("无限预算 remainingTokens 返回 Integer.MAX_VALUE")
    void unlimitedBudgetRemainingTokens() {
        CostTracker tracker = new CostTracker(0);
        tracker.record(100, 100);
        assertEquals(Integer.MAX_VALUE, tracker.remainingTokens());
    }

    @Test
    @DisplayName("负预算值也被视为无限")
    void negativeBudgetTreatedAsUnlimited() {
        CostTracker tracker = new CostTracker(-1);
        tracker.record(999999, 999999);
        assertFalse(tracker.isOverBudget());
    }

    @Test
    @DisplayName("恰好等于预算时不超（差一语义）")
    void exactlyAtBudgetNotOver() {
        CostTracker tracker = new CostTracker(1000);
        tracker.record(500, 500);
        assertFalse(tracker.isOverBudget());
        assertEquals(0, tracker.remainingTokens());
    }

    @Test
    @DisplayName("并发 record 线程安全 — 总数不丢")
    void concurrentRecordSafety() throws InterruptedException {
        CostTracker tracker = new CostTracker(0);
        int threads = 10;
        int recordsPerThread = 1000;

        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < recordsPerThread; j++) {
                    tracker.record(1, 1);
                }
            });
            workers[i].start();
        }

        for (Thread w : workers) {
            w.join();
        }

        int expected = threads * recordsPerThread;
        assertEquals(expected, tracker.getConsumedInputTokens());
        assertEquals(expected, tracker.getConsumedOutputTokens());
        assertEquals(expected * 2, tracker.getTotalConsumed());
    }

    @Test
    @DisplayName("getMaxBudgetTokens 返回构造时的值")
    void maxBudgetTokensAccessor() {
        CostTracker tracker = new CostTracker(42);
        assertEquals(42, tracker.getMaxBudgetTokens());
    }
}
