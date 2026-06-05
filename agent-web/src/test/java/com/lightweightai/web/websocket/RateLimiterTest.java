package com.lightweightai.web.websocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VertxChatWebSocketHandler.RateLimiter — sliding window rate limiting")
class RateLimiterTest {

    @Test
    @DisplayName("allows requests within limit")
    void allowsWithinLimit() {
        VertxChatWebSocketHandler.RateLimiter limiter = new VertxChatWebSocketHandler.RateLimiter(5);
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(), "Request " + i + " should be allowed");
        }
    }

    @Test
    @DisplayName("rejects requests exceeding limit within same second")
    void rejectsOverLimit() {
        VertxChatWebSocketHandler.RateLimiter limiter = new VertxChatWebSocketHandler.RateLimiter(3);
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire(), "4th request should be rejected");
    }

    @Test
    @DisplayName("resets counter in new time window")
    void resetsInNewWindow() throws InterruptedException {
        VertxChatWebSocketHandler.RateLimiter limiter = new VertxChatWebSocketHandler.RateLimiter(2);
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());

        Thread.sleep(1100);
        assertTrue(limiter.tryAcquire(), "Should allow after window reset");
    }

    @Test
    @DisplayName("single request always succeeds with limit >= 1")
    void singleRequestSucceeds() {
        VertxChatWebSocketHandler.RateLimiter limiter = new VertxChatWebSocketHandler.RateLimiter(1);
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }
}
