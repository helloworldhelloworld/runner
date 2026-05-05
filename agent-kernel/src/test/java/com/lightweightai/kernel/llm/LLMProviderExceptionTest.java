package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LLMProviderException — contextual exception for LLM provider failures")
class LLMProviderExceptionTest {

    @Test
    @DisplayName("preserves provider name and attempt count")
    void preservesContext() {
        RuntimeException cause = new RuntimeException("connection timeout");
        LLMProviderException ex = new LLMProviderException(
                "Failed after retries", cause, "claude", 3);

        assertEquals("claude", ex.getProviderName());
        assertEquals(3, ex.getAttempts());
        assertEquals("Failed after retries", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("is a RuntimeException — unchecked")
    void isRuntimeException() {
        LLMProviderException ex = new LLMProviderException("err", null, "openai", 1);
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    @DisplayName("zero attempts for first-try failures")
    void zeroAttempts() {
        LLMProviderException ex = new LLMProviderException("first fail", null, "websocket", 0);
        assertEquals(0, ex.getAttempts());
        assertEquals("websocket", ex.getProviderName());
    }

    @Test
    @DisplayName("null cause is acceptable")
    void nullCause() {
        LLMProviderException ex = new LLMProviderException("no cause", null, "claude", 1);
        assertNull(ex.getCause());
    }
}
