package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LLMProviderException - Provider error context")
class LLMProviderExceptionTest {

    @Test
    @DisplayName("preserves provider name and attempt count")
    void preservesContext() {
        RuntimeException cause = new RuntimeException("connection refused");
        LLMProviderException ex = new LLMProviderException(
                "Call failed after 3 attempts", cause, "claude-pro", 3);

        assertEquals("claude-pro", ex.getProviderName());
        assertEquals(3, ex.getAttempts());
        assertEquals("Call failed after 3 attempts", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("is a RuntimeException")
    void isRuntimeException() {
        LLMProviderException ex = new LLMProviderException(
                "error", new RuntimeException(), "test", 1);
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    @DisplayName("null provider name is preserved")
    void nullProviderName() {
        LLMProviderException ex = new LLMProviderException(
                "msg", null, null, 0);
        assertNull(ex.getProviderName());
        assertEquals(0, ex.getAttempts());
    }
}
