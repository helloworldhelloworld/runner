package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LLMProviderExceptionTest {

    @Test
    void containsAllContext() {
        RuntimeException cause = new RuntimeException("connection refused");
        LLMProviderException ex = new LLMProviderException("Failed", cause, "claude", 3);
        assertEquals("Failed", ex.getMessage());
        assertEquals(cause, ex.getCause());
        assertEquals("claude", ex.getProviderName());
        assertEquals(3, ex.getAttempts());
    }

    @Test
    void isRuntimeException() {
        assertInstanceOf(RuntimeException.class, new LLMProviderException("err", null, "test", 1));
    }

    @Test
    void nullCauseWorks() {
        LLMProviderException ex = new LLMProviderException("timeout", null, "openrouter", 5);
        assertNull(ex.getCause());
        assertEquals("openrouter", ex.getProviderName());
        assertEquals(5, ex.getAttempts());
    }
}
