package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LLMProviderException - LLM 提供者异常")
class LLMProviderExceptionTest {

    @Test
    @DisplayName("携带 provider 名称和重试次数")
    void carriesProviderContext() {
        RuntimeException cause = new RuntimeException("connection refused");
        LLMProviderException ex = new LLMProviderException(
                "Failed to call LLM", cause, "claude", 3);

        assertEquals("Failed to call LLM", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertEquals("claude", ex.getProviderName());
        assertEquals(3, ex.getAttempts());
    }

    @Test
    @DisplayName("继承自 RuntimeException")
    void isRuntimeException() {
        LLMProviderException ex = new LLMProviderException("err", null, "openrouter", 1);
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    @DisplayName("null cause 不抛异常")
    void nullCauseAllowed() {
        LLMProviderException ex = new LLMProviderException("timeout", null, "mock", 0);
        assertNull(ex.getCause());
        assertEquals("mock", ex.getProviderName());
        assertEquals(0, ex.getAttempts());
    }
}
