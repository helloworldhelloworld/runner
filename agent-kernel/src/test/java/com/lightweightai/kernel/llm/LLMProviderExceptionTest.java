package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LLMProviderException")
class LLMProviderExceptionTest {

    @Test
    @DisplayName("preserves provider name and attempt count")
    void preservesContext() {
        RuntimeException cause = new RuntimeException("connection refused");
        LLMProviderException ex = new LLMProviderException(
                "LLM call failed", cause, "claude", 3);

        assertEquals("LLM call failed", ex.getMessage());
        assertEquals("claude", ex.getProviderName());
        assertEquals(3, ex.getAttempts());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("is a RuntimeException so it propagates through non-checked signatures")
    void isRuntimeException() {
        LLMProviderException ex = new LLMProviderException(
                "fail", new RuntimeException(), "openrouter", 1);
        assertInstanceOf(RuntimeException.class, ex);
    }
}
