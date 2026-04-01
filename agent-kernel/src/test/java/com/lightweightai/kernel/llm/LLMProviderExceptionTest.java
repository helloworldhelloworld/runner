package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LLMProviderException - LLM 提供者异常")
class LLMProviderExceptionTest {

    @Test
    @DisplayName("包含所有上下文信息")
    void fullContext() {
        RuntimeException cause = new RuntimeException("timeout");
        LLMProviderException ex = new LLMProviderException(
                "API call failed", cause, "claude", 3);

        assertEquals("API call failed", ex.getMessage());
        assertEquals(cause, ex.getCause());
        assertEquals("claude", ex.getProviderName());
        assertEquals(3, ex.getAttempts());
    }

    @Test
    @DisplayName("null cause 也可以")
    void nullCause() {
        LLMProviderException ex = new LLMProviderException(
                "failed", null, "openai", 1);
        assertNull(ex.getCause());
        assertEquals("openai", ex.getProviderName());
    }
}
