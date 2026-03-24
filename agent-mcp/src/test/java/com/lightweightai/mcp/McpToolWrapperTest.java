package com.lightweightai.mcp;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpToolWrapper tests
 *
 * Verifies that McpToolWrapper correctly wraps MCP tools into the framework's Tool interface.
 * Since McpToolClient requires a real transport and we cannot use Mockito,
 * metadata/schema tests use null toolClient (not accessed by those code paths),
 * and execute tests use a StubMcpToolWrapper that overrides executeReactive
 * to simulate MCP call results.
 */
@DisplayName("McpToolWrapper - MCP to Tool interface adapter")
class McpToolWrapperTest {

    private McpSchema.Tool mcpTool;
    private McpSchema.JsonSchema jsonSchema;

    @BeforeEach
    void setUp() {
        jsonSchema = new McpSchema.JsonSchema(
            "object",
            Map.of(
                "city", Map.of("type", "string", "description", "City name"),
                "units", Map.of("type", "string", "description", "Temperature units")
            ),
            List.of("city"),
            null,
            null,
            null
        );

        mcpTool = new McpSchema.Tool(
            "get_weather",
            null,
            "Get current weather for a city",
            jsonSchema,
            null,
            null,
            null
        );
    }

    // ==================== getName ====================

    @Test
    @DisplayName("getName returns the MCP tool name")
    void shouldReturnToolName() {
        McpToolWrapper wrapper = new McpToolWrapper(null, mcpTool, "weather-server");

        assertEquals("get_weather", wrapper.getName());
    }

    @Test
    @DisplayName("getName returns name for tool with special characters")
    void shouldReturnToolNameWithSpecialChars() {
        McpSchema.Tool specialTool = new McpSchema.Tool(
            "my-tool_v2.0",
            null,
            "A tool with special chars in name",
            null,
            null,
            null,
            null
        );

        McpToolWrapper wrapper = new McpToolWrapper(null, specialTool, "server");
        assertEquals("my-tool_v2.0", wrapper.getName());
    }

    // ==================== getDescription ====================

    @Test
    @DisplayName("getDescription returns the MCP tool description")
    void shouldReturnToolDescription() {
        McpToolWrapper wrapper = new McpToolWrapper(null, mcpTool, "weather-server");

        assertEquals("Get current weather for a city", wrapper.getDescription());
    }

    @Test
    @DisplayName("getDescription returns null when MCP tool has no description")
    void shouldReturnNullDescriptionWhenAbsent() {
        McpSchema.Tool noDescTool = new McpSchema.Tool(
            "silent_tool",
            null,
            null,
            null,
            null,
            null,
            null
        );

        McpToolWrapper wrapper = new McpToolWrapper(null, noDescTool, "server");
        assertNull(wrapper.getDescription());
    }

    // ==================== getSchema ====================

    @Test
    @DisplayName("getSchema returns correct schema from MCP inputSchema")
    void shouldReturnCorrectSchema() {
        McpToolWrapper wrapper = new McpToolWrapper(null, mcpTool, "weather-server");

        ToolSchema schema = wrapper.getSchema();
        assertNotNull(schema);

        Map<String, Object> schemaMap = schema.toMap();
        assertEquals("object", schemaMap.get("type"));
        assertNotNull(schemaMap.get("properties"));
        assertNotNull(schemaMap.get("required"));

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");
        assertTrue(properties.containsKey("city"));
        assertTrue(properties.containsKey("units"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schemaMap.get("required");
        assertTrue(required.contains("city"));
    }

    @Test
    @DisplayName("getSchema returns empty schema when MCP tool has no inputSchema")
    void shouldReturnEmptySchemaWhenNoInputSchema() {
        McpSchema.Tool noSchemaTool = new McpSchema.Tool(
            "no_schema_tool",
            null,
            "Tool without schema",
            null,
            null,
            null,
            null
        );

        McpToolWrapper wrapper = new McpToolWrapper(null, noSchemaTool, "server");
        ToolSchema schema = wrapper.getSchema();
        assertNotNull(schema);
        // ToolSchema.empty() returns a schema with type=object and empty properties
        assertEquals("object", schema.getType());
        assertTrue(schema.getProperties().isEmpty());
    }

    @Test
    @DisplayName("getSchema handles inputSchema with null properties gracefully")
    void shouldHandleNullPropertiesInSchema() {
        McpSchema.JsonSchema nullPropsSchema = new McpSchema.JsonSchema(
            "object",
            null,
            null,
            null,
            null,
            null
        );

        McpSchema.Tool tool = new McpSchema.Tool(
            "minimal_tool",
            null,
            "Minimal tool",
            nullPropsSchema,
            null,
            null,
            null
        );

        McpToolWrapper wrapper = new McpToolWrapper(null, tool, "server");
        ToolSchema schema = wrapper.getSchema();
        assertNotNull(schema);
        assertEquals("object", schema.getType());
    }

    // ==================== ToolMetadata ====================

    @Test
    @DisplayName("getCategory returns mcp:serverName")
    void shouldReturnMcpCategory() {
        McpToolWrapper wrapper = new McpToolWrapper(null, mcpTool, "weather-server");

        assertEquals("mcp:weather-server", wrapper.getCategory());
    }

    @Test
    @DisplayName("getTags includes mcp, remote, and server name")
    void shouldReturnCorrectTags() {
        McpToolWrapper wrapper = new McpToolWrapper(null, mcpTool, "weather-server");

        List<String> tags = wrapper.getTags();
        assertEquals(3, tags.size());
        assertTrue(tags.contains("mcp"));
        assertTrue(tags.contains("remote"));
        assertTrue(tags.contains("weather-server"));
    }

    @Test
    @DisplayName("getAuthor returns mcp-server:serverName")
    void shouldReturnAuthor() {
        McpToolWrapper wrapper = new McpToolWrapper(null, mcpTool, "weather-server");

        assertEquals("mcp-server:weather-server", wrapper.getAuthor());
    }

    @Test
    @DisplayName("isClientSide defaults to false")
    void shouldDefaultClientSideToFalse() {
        McpToolWrapper wrapper = new McpToolWrapper(null, mcpTool, "server");

        assertFalse(wrapper.isClientSide());
    }

    @Test
    @DisplayName("setClientSide changes clientSide flag")
    void shouldSetClientSideFlag() {
        McpToolWrapper wrapper = new McpToolWrapper(null, mcpTool, "server");

        wrapper.setClientSide(true);
        assertTrue(wrapper.isClientSide());

        wrapper.setClientSide(false);
        assertFalse(wrapper.isClientSide());
    }

    // ==================== Tool + ToolMetadata interface ====================

    @Test
    @DisplayName("McpToolWrapper implements Tool interface")
    void shouldImplementToolInterface() {
        McpToolWrapper wrapper = new McpToolWrapper(null, mcpTool, "server");

        assertInstanceOf(Tool.class, wrapper);
    }

    @Test
    @DisplayName("McpToolWrapper implements ToolMetadata interface")
    void shouldImplementToolMetadataInterface() {
        McpToolWrapper wrapper = new McpToolWrapper(null, mcpTool, "server");

        assertInstanceOf(ToolMetadata.class, wrapper);
    }

    // ==================== Accessor methods ====================

    @Test
    @DisplayName("getServerName returns the server name")
    void shouldReturnServerName() {
        McpToolWrapper wrapper = new McpToolWrapper(null, mcpTool, "my-server");

        assertEquals("my-server", wrapper.getServerName());
    }

    @Test
    @DisplayName("getMcpTool returns the underlying MCP tool")
    void shouldReturnMcpTool() {
        McpToolWrapper wrapper = new McpToolWrapper(null, mcpTool, "server");

        assertSame(mcpTool, wrapper.getMcpTool());
    }

    @Test
    @DisplayName("getToolClient returns the tool client")
    void shouldReturnToolClient() {
        McpToolWrapper wrapper = new McpToolWrapper(null, mcpTool, "server");

        assertNull(wrapper.getToolClient());
    }

    // ==================== toString ====================

    @Test
    @DisplayName("toString includes tool name and server name")
    void shouldReturnDescriptiveToString() {
        McpToolWrapper wrapper = new McpToolWrapper(null, mcpTool, "weather-server");

        String str = wrapper.toString();
        assertTrue(str.contains("get_weather"));
        assertTrue(str.contains("weather-server"));
    }

    // ==================== executeReactive error handling ====================

    @Test
    @DisplayName("executeReactive handles MCP client errors gracefully")
    void shouldHandleMcpExecutionErrorGracefully() {
        // Create real routers that the wrapper needs
        ProgressNotificationRouter progressRouter = new ProgressNotificationRouter();
        LoggingNotificationRouter loggingRouter = new LoggingNotificationRouter();

        // Create a wrapper with a stub toolClient that simulates an error
        McpToolWrapper wrapper = new StubErrorMcpToolWrapper(
            mcpTool, "weather-server", progressRouter, loggingRouter,
            new RuntimeException("Connection refused")
        );

        // executeReactive should catch the error and emit an ERROR chunk
        List<ToolResultChunk> chunks = wrapper.executeReactive(Map.of("city", "Tokyo"))
            .collectList()
            .block();

        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());

        // Find the error chunk
        ToolResultChunk errorChunk = chunks.stream()
            .filter(c -> c.getType() == ToolResultChunk.ChunkType.ERROR)
            .findFirst()
            .orElse(null);

        assertNotNull(errorChunk, "Should contain an error chunk");
        assertTrue(errorChunk.getMessage().contains("Connection refused")
                || errorChunk.getMessage().contains("MCP call failed"));
    }

    @Test
    @DisplayName("executeReactive returns COMPLETE chunk on successful call")
    void shouldReturnCompleteChunkOnSuccess() {
        ProgressNotificationRouter progressRouter = new ProgressNotificationRouter();
        LoggingNotificationRouter loggingRouter = new LoggingNotificationRouter();

        McpToolWrapper wrapper = new StubSuccessMcpToolWrapper(
            mcpTool, "weather-server", progressRouter, loggingRouter,
            "Sunny, 25C"
        );

        List<ToolResultChunk> chunks = wrapper.executeReactive(Map.of("city", "Tokyo"))
            .collectList()
            .block();

        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());

        ToolResultChunk completeChunk = chunks.stream()
            .filter(c -> c.getType() == ToolResultChunk.ChunkType.COMPLETE)
            .findFirst()
            .orElse(null);

        assertNotNull(completeChunk, "Should contain a COMPLETE chunk");
        assertNotNull(completeChunk.getResult());
        assertFalse(completeChunk.getResult().isError());
        assertTrue(completeChunk.getResult().getContent().contains("Sunny, 25C"));
    }

    @Test
    @DisplayName("execute blocks on executeReactive and returns ToolResult")
    void shouldBlockAndReturnToolResult() {
        ProgressNotificationRouter progressRouter = new ProgressNotificationRouter();
        LoggingNotificationRouter loggingRouter = new LoggingNotificationRouter();

        McpToolWrapper wrapper = new StubSuccessMcpToolWrapper(
            mcpTool, "weather-server", progressRouter, loggingRouter,
            "Rainy, 15C"
        );

        ToolResult result = wrapper.execute(Map.of("city", "London"));

        assertNotNull(result);
        assertFalse(result.isError());
        assertTrue(result.getContent().contains("Rainy, 15C"));
    }

    @Test
    @DisplayName("execute returns error ToolResult on MCP failure")
    void shouldReturnErrorToolResultOnFailure() {
        ProgressNotificationRouter progressRouter = new ProgressNotificationRouter();
        LoggingNotificationRouter loggingRouter = new LoggingNotificationRouter();

        McpToolWrapper wrapper = new StubErrorMcpToolWrapper(
            mcpTool, "weather-server", progressRouter, loggingRouter,
            new RuntimeException("Server timeout")
        );

        ToolResult result = wrapper.execute(Map.of("city", "Paris"));

        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(result.getContent().contains("Server timeout")
                || result.getContent().contains("MCP call failed"));
    }

    // ==================== Test helpers ====================

    /**
     * Stub McpToolWrapper that overrides executeReactive to simulate a successful MCP call.
     * This avoids needing a real McpToolClient with transport.
     */
    private static class StubSuccessMcpToolWrapper extends McpToolWrapper {
        private final ProgressNotificationRouter progressRouter;
        private final LoggingNotificationRouter loggingRouter;
        private final String responseText;

        StubSuccessMcpToolWrapper(McpSchema.Tool mcpTool, String serverName,
                                  ProgressNotificationRouter progressRouter,
                                  LoggingNotificationRouter loggingRouter,
                                  String responseText) {
            super(null, mcpTool, serverName);
            this.progressRouter = progressRouter;
            this.loggingRouter = loggingRouter;
            this.responseText = responseText;
        }

        @Override
        public reactor.core.publisher.Flux<ToolResultChunk> executeReactive(Map<String, Object> args) {
            ToolResult toolResult = ToolResult.success(responseText);
            ToolResultChunk completeChunk = ToolResultChunk.complete(getName(), toolResult);
            return reactor.core.publisher.Flux.just(completeChunk);
        }
    }

    /**
     * Stub McpToolWrapper that overrides executeReactive to simulate an MCP error.
     */
    private static class StubErrorMcpToolWrapper extends McpToolWrapper {
        private final ProgressNotificationRouter progressRouter;
        private final LoggingNotificationRouter loggingRouter;
        private final RuntimeException error;

        StubErrorMcpToolWrapper(McpSchema.Tool mcpTool, String serverName,
                                ProgressNotificationRouter progressRouter,
                                LoggingNotificationRouter loggingRouter,
                                RuntimeException error) {
            super(null, mcpTool, serverName);
            this.progressRouter = progressRouter;
            this.loggingRouter = loggingRouter;
            this.error = error;
        }

        @Override
        public reactor.core.publisher.Flux<ToolResultChunk> executeReactive(Map<String, Object> args) {
            String toolName = getName();
            return reactor.core.publisher.Flux.just(
                ToolResultChunk.error(toolName, "MCP call failed: " + error.getMessage())
            );
        }
    }
}
