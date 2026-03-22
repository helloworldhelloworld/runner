package com.lightweightai.mcp;

import com.lightweightai.kernel.agent.ToolRegistry;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpSourceProvider 测试
 *
 * 验证 Provider 包装逻辑（不建立实际 MCP 连接）
 */
@DisplayName("McpSourceProvider - MCP 工具来源提供者")
class McpSourceProviderTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    @DisplayName("构造函数不应接受 null client")
    void shouldRejectNullClient() {
        assertThrows(NullPointerException.class, () -> new McpSourceProvider(null));
    }

    @Test
    @DisplayName("getName 应包含 server 名称前缀 mcp:")
    void shouldNameIncludeServerNamePrefix() {
        McpToolClient client = buildTestClient("my-server");
        McpSourceProvider provider = new McpSourceProvider(client);
        assertEquals("mcp:my-server", provider.getName());
    }

    @Test
    @DisplayName("初始状态 isRunning 应为 false")
    void shouldNotBeRunningInitially() {
        McpToolClient client = buildTestClient("test");
        McpSourceProvider provider = new McpSourceProvider(client);
        assertFalse(provider.isRunning());
    }

    @Test
    @DisplayName("getClient 应返回底层客户端")
    void shouldExposeClient() {
        McpToolClient client = buildTestClient("test");
        McpSourceProvider provider = new McpSourceProvider(client);
        assertSame(client, provider.getClient());
    }

    @Test
    @DisplayName("stop 在未启动时应为 no-op")
    void shouldNoOpStopWhenNotRunning() {
        McpToolClient client = buildTestClient("test");
        McpSourceProvider provider = new McpSourceProvider(client);
        assertDoesNotThrow(() -> provider.stop(registry));
        assertFalse(provider.isRunning());
    }

    // ==================== 辅助方法 ====================

    private McpToolClient buildTestClient(String serverName) {
        ServerParameters params = ServerParameters.builder("echo").args("test").build();
        StdioClientTransport transport = new StdioClientTransport(
            params, new JacksonMcpJsonMapper(new ObjectMapper()));
        return McpToolClient.builder()
            .serverName(serverName)
            .transport(transport)
            .build();
    }
}
