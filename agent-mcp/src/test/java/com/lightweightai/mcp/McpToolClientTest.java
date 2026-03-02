package com.lightweightai.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpToolClient 测试
 *
 * 验证 MCP 客户端构建逻辑（不建立实际连接）
 */
@DisplayName("McpToolClient - Builder and configuration")
class McpToolClientTest {

    @Test
    @DisplayName("Builder requires transport")
    void shouldRequireTransport() {
        assertThrows(IllegalStateException.class, () ->
            McpToolClient.builder()
                .serverName("test")
                .build()
        );
    }
}
