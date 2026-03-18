package com.lightweightai.mcp;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * McpToolServer 测试
 *
 * 验证 MCP 服务端构建逻辑（不启动实际传输）
 */
@DisplayName("McpToolServer - Builder and configuration")
class McpToolServerTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        registry.register(new Tool() {
            @Override
            public String getName() {
                return "test_tool";
            }
            @Override
            public String getDescription() {
                return "Test tool";
            }
            @Override
            public ToolSchema getSchema() {
                return ToolSchema.empty();
            }
            @Override
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        });
    }

    @Test
    @DisplayName("Builder requires ToolRegistry")
    void shouldRequireToolRegistry() {
        assertThrows(IllegalStateException.class, () ->
            McpToolServer.builder()
                .serverName("test")
                .build()
        );
    }

    @Test
    @DisplayName("Builder creates server with defaults")
    void shouldBuildWithDefaults() {
        McpToolServer server = McpToolServer.builder()
            .toolRegistry(registry)
            .build();

        assertNotNull(server);
    }

    @Test
    @DisplayName("Builder accepts custom server info")
    void shouldBuildWithCustomInfo() {
        McpToolServer server = McpToolServer.builder()
            .serverName("my-agent")
            .serverVersion("2.0.0")
            .toolRegistry(registry)
            .build();

        assertNotNull(server);
    }
}
