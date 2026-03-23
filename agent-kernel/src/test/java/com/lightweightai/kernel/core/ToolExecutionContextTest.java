package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolExecutionContext - 工具执行上下文")
class ToolExecutionContextTest {

    @Test
    @DisplayName("serverOnly 无客户端调度器")
    void serverOnly() {
        ToolExecutionContext ctx = ToolExecutionContext.serverOnly();

        assertFalse(ctx.hasClientDispatcher());
        assertNull(ctx.getClientDispatcher());
        assertEquals(60_000, ctx.getClientToolTimeoutMs());
    }

    @Test
    @DisplayName("带客户端调度器")
    void withClientDispatcher() {
        ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
            CompletableFuture.completedFuture(null);

        ToolExecutionContext ctx = new ToolExecutionContext(dispatcher, 30_000);

        assertTrue(ctx.hasClientDispatcher());
        assertNotNull(ctx.getClientDispatcher());
        assertEquals(30_000, ctx.getClientToolTimeoutMs());
    }

    @Test
    @DisplayName("负超时时间使用默认值")
    void negativeTimeoutUsesDefault() {
        ToolExecutionContext ctx = new ToolExecutionContext(null, -1);
        assertEquals(60_000, ctx.getClientToolTimeoutMs());
    }

    @Test
    @DisplayName("零超时使用默认值")
    void zeroTimeoutUsesDefault() {
        ToolExecutionContext ctx = new ToolExecutionContext(null, 0);
        assertEquals(60_000, ctx.getClientToolTimeoutMs());
    }

    @Test
    @DisplayName("null 调度器 hasClientDispatcher 返回 false")
    void nullDispatcher() {
        ToolExecutionContext ctx = new ToolExecutionContext(null, 5000);
        assertFalse(ctx.hasClientDispatcher());
    }
}
