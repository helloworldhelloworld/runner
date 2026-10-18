package com.lightweightai.kernel.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CancellationToken - 轻量级取消信号")
class CancellationTokenTest {

    @Test void lateRegistrationAndRepeatedCancellationInvokeOnce() {
        CancellationToken token = new CancellationToken();
        token.cancel();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var registration = token.onCancel(calls::incrementAndGet);
        assertEquals(1, calls.get());
        token.cancel();
        registration.close();
        assertEquals(1, calls.get());
    }

    @Test void closingRegistrationReleasesItsCallback() {
        CancellationToken token = new CancellationToken();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var registration = token.onCancel(calls::incrementAndGet);
        registration.close();
        registration.close();
        token.cancel();
        assertEquals(0, calls.get());
    }

    @Test void aFailingListenerDoesNotPreventOtherCleanup() {
        CancellationToken token = new CancellationToken();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        token.onCancel(() -> { throw new IllegalStateException("failed cleanup"); });
        token.onCancel(calls::incrementAndGet);
        assertDoesNotThrow(token::cancel);
        assertEquals(1, calls.get());
    }

    @Test void callbacksRunOutsideTheTokenLock() throws Exception {
        CancellationToken token = new CancellationToken();
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        var received = new java.util.concurrent.CompletableFuture<Boolean>();
        try {
            token.onCancel(() -> {
                try {
                    executor.submit(() -> token.onCancel(() -> received.complete(true)))
                            .get(3, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    received.completeExceptionally(e);
                }
            });
            token.cancel();
            assertTrue(received.get(3, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void registrationRacingCancellationIsNeverLost() throws Exception {
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 100; i++) {
                CancellationToken token = new CancellationToken();
                var calls = new java.util.concurrent.atomic.AtomicInteger();
                var start = new java.util.concurrent.CyclicBarrier(2);
                var registration = executor.submit(() -> {
                    start.await(3, java.util.concurrent.TimeUnit.SECONDS);
                    return token.onCancel(calls::incrementAndGet);
                });
                var cancellation = executor.submit(() -> {
                    start.await(3, java.util.concurrent.TimeUnit.SECONDS);
                    token.cancel();
                    return null;
                });
                cancellation.get(3, java.util.concurrent.TimeUnit.SECONDS);
                registration.get(3, java.util.concurrent.TimeUnit.SECONDS).close();
                assertEquals(1, calls.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("初始状态未取消")
    void initiallyNotCancelled() {
        CancellationToken token = new CancellationToken();
        assertFalse(token.isCancelled());
    }

    @Test
    @DisplayName("cancel 后 isCancelled 返回 true")
    void cancelSetsCancelledFlag() {
        CancellationToken token = new CancellationToken();
        token.cancel();
        assertTrue(token.isCancelled());
    }

    @Test
    @DisplayName("多次 cancel 幂等")
    void cancelIsIdempotent() {
        CancellationToken token = new CancellationToken();
        token.cancel();
        token.cancel();
        assertTrue(token.isCancelled());
    }

    @Test
    @DisplayName("跨线程可见性")
    void visibleAcrossThreads() throws InterruptedException {
        CancellationToken token = new CancellationToken();

        Thread canceller = new Thread(token::cancel);
        canceller.start();
        canceller.join();

        assertTrue(token.isCancelled());
    }
}
