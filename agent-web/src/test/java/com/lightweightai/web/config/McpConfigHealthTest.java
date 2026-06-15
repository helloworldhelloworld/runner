package com.lightweightai.web.config;

import com.lightweightai.mcp.McpToolClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * ADR-011：MCP 连接保活——{@link McpConfig#healthTick} 统一处理"启动没起→连""运行掉线→重连"，
 * 不依赖重启 runner。
 */
@DisplayName("ADR-011: MCP healthTick 连接/探活/重连")
class McpConfigHealthTest {

    @Test
    @DisplayName("未连接(current=null) → 连接，不探活")
    void connectsWhenNotConnected() {
        McpToolClient fresh = mock(McpToolClient.class);
        AtomicInteger connects = new AtomicInteger();
        Supplier<McpToolClient> connect = () -> { connects.incrementAndGet(); return fresh; };

        McpToolClient r = McpConfig.healthTick(null, () -> fail("current=null 不该探活"), connect);

        assertSame(fresh, r);
        assertEquals(1, connects.get());
    }

    @Test
    @DisplayName("已连接 + 探活成功 → 保持原 client，不重连")
    void keepsWhenProbeOk() {
        McpToolClient cur = mock(McpToolClient.class);
        AtomicInteger connects = new AtomicInteger();

        McpToolClient r = McpConfig.healthTick(cur, () -> { /* 探活成功 */ },
            () -> { connects.incrementAndGet(); return mock(McpToolClient.class); });

        assertSame(cur, r);
        assertEquals(0, connects.get(), "探活成功不该重连");
    }

    @Test
    @DisplayName("已连接 + 探活抛异常(连接死) → 重连返回新 client")
    void reconnectsWhenProbeThrows() {
        McpToolClient cur = mock(McpToolClient.class);
        McpToolClient fresh = mock(McpToolClient.class);

        McpToolClient r = McpConfig.healthTick(cur,
            () -> { throw new RuntimeException("connection dead"); },
            () -> fresh);

        assertSame(fresh, r, "探活失败应重连成新 client");
    }
}
