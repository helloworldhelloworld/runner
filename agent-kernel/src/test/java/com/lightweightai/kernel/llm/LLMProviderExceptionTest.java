package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LLMProviderException - LLM 调用异常上下文")
class LLMProviderExceptionTest {

    @Test
    @DisplayName("携带 provider 名称和重试次数")
    void capturesContext() {
        RuntimeException cause = new RuntimeException("timeout");
        LLMProviderException ex = new LLMProviderException(
            "Claude API failed", cause, "claude", 3);

        assertEquals("Claude API failed", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertEquals("claude", ex.getProviderName());
        assertEquals(3, ex.getAttempts());
    }

    @Test
    @DisplayName("是 RuntimeException 子类")
    void isRuntimeException() {
        LLMProviderException ex = new LLMProviderException(
            "error", null, "openrouter", 1);
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    @DisplayName("cause 为 null 时正常工作")
    void nullCauseWorks() {
        LLMProviderException ex = new LLMProviderException(
            "error", null, "provider", 0);
        assertNull(ex.getCause());
        assertEquals("provider", ex.getProviderName());
        assertEquals(0, ex.getAttempts());
    }
}
