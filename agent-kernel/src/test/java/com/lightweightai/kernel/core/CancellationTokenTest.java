package com.lightweightai.kernel.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CancellationToken - 轻量级取消信号")
class CancellationTokenTest {

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
