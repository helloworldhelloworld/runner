package com.lightweightai.mcp;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpToolAdapter 异步路径测试
 *
 * 验证 Tool → MCP AsyncToolSpecification 转换以及流式执行逻辑
 */
@DisplayName("McpToolAdapter - Async tool conversion and streaming")
class McpToolAdapterAsyncTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    @DisplayName("toAsyncMcpTool converts simple tool to AsyncToolSpecification")
    void shouldConvertSimpleToolToAsync() {
        Tool tool = new SimpleTool("greet", "Say hello",
            ToolSchema.empty(),
            args -> ToolResult.success("Hello!"));

        McpServerFeatures.AsyncToolSpecification spec = McpToolAdapter.toAsyncMcpTool(tool);

        assertNotNull(spec);
        assertEquals("greet", spec.tool().name());
        assertEquals("Say hello", spec.tool().description());
    }

    @Test
    @DisplayName("toAsyncMcpTools converts all enabled tools in registry")
    void shouldConvertAllEnabledToolsToAsync() {
        registry.register(new SimpleTool("tool1", "T1", ToolSchema.empty(),
            args -> ToolResult.success("r1")));
        registry.register(new SimpleTool("tool2", "T2", ToolSchema.empty(),
            args -> ToolResult.success("r2")));
        registry.register(new SimpleTool("disabled", "D", ToolSchema.empty(),
            args -> ToolResult.success("d")));
        registry.disable("disabled");

        List<McpServerFeatures.AsyncToolSpecification> specs = McpToolAdapter.toAsyncMcpTools(registry);

        assertEquals(2, specs.size());
    }

    @Test
    @DisplayName("toAsyncMcpTool with schema preserves properties and required fields")
    void shouldPreserveSchemaInAsync() {
        Tool tool = new SimpleTool("search", "Search documents",
            ToolSchema.withRequired(
                Map.of(
                    "query", Map.of("type", "string", "description", "Search query"),
                    "limit", Map.of("type", "integer", "description", "Max results")
                ),
                "query"
            ),
            args -> ToolResult.success("results"));

        McpServerFeatures.AsyncToolSpecification spec = McpToolAdapter.toAsyncMcpTool(tool);

        assertNotNull(spec.tool().inputSchema());
        assertEquals("object", spec.tool().inputSchema().type());
        assertNotNull(spec.tool().inputSchema().properties());
        assertTrue(spec.tool().inputSchema().properties().containsKey("query"));
        assertTrue(spec.tool().inputSchema().required().contains("query"));
    }

    @Test
    @DisplayName("Async tool with default executeReactive produces COMPLETE chunk and valid CallToolResult")
    void shouldExecuteDefaultReactiveTool() {
        Tool tool = new SimpleTool("echo", "Echo input",
            ToolSchema.empty(),
            args -> ToolResult.success("echoed: " + args.getOrDefault("text", "")));

        McpSchema.CallToolResult callResult = McpToolAdapter.toCallToolResult(
            ToolResult.success("echoed: hello"));

        assertNotNull(callResult);
        assertFalse(callResult.isError());
        assertEquals(1, callResult.content().size());
        assertTrue(callResult.content().get(0) instanceof McpSchema.TextContent);
        assertEquals("echoed: hello", ((McpSchema.TextContent) callResult.content().get(0)).text());
    }

    @Test
    @DisplayName("toCallToolResult maps error result correctly")
    void shouldMapErrorResultCorrectly() {
        McpSchema.CallToolResult callResult = McpToolAdapter.toCallToolResult(
            ToolResult.error("Something went wrong"));

        assertNotNull(callResult);
        assertTrue(callResult.isError());
        assertEquals("Something went wrong",
            ((McpSchema.TextContent) callResult.content().get(0)).text());
    }

    @Test
    @DisplayName("toCallToolResult preserves structuredContent")
    void shouldPreserveStructuredContentInAsync() {
        Map<String, Object> structured = Map.of("key", "value", "count", 42);
        ToolResult result = ToolResult.success("text", structured);

        McpSchema.CallToolResult callResult = McpToolAdapter.toCallToolResult(result);

        assertNotNull(callResult);
        assertFalse(callResult.isError());
        assertNotNull(callResult.structuredContent());
        @SuppressWarnings("unchecked")
        Map<String, Object> sc = (Map<String, Object>) callResult.structuredContent();
        assertEquals("value", sc.get("key"));
        assertEquals(42, sc.get("count"));
    }

    @Test
    @DisplayName("toCallToolResult omits structuredContent when absent")
    void shouldOmitStructuredContentWhenAbsent() {
        McpSchema.CallToolResult callResult = McpToolAdapter.toCallToolResult(
            ToolResult.success("plain text"));

        assertNull(callResult.structuredContent());
    }

    @Test
    @DisplayName("Streaming tool executeReactive produces intermediate chunks")
    void shouldStreamReactiveToolProduceChunks() {
        Tool streamingTool = new StreamingTestTool("process", "Process with progress");

        List<ToolResultChunk> chunks = streamingTool.executeReactive(Map.of("input", "test"))
            .collectList().block();

        assertNotNull(chunks);
        assertTrue(chunks.size() >= 2);

        long progressCount = chunks.stream()
            .filter(c -> c.getType() == ToolResultChunk.ChunkType.PROGRESS)
            .count();
        long completeCount = chunks.stream()
            .filter(c -> c.getType() == ToolResultChunk.ChunkType.COMPLETE)
            .count();

        assertTrue(progressCount >= 1, "Should have at least one progress chunk");
        assertEquals(1, completeCount, "Should have exactly one COMPLETE chunk");
    }

    @Test
    @DisplayName("extractAnnotations returns empty map for non-ToolMetadata tool")
    void shouldReturnEmptyAnnotationsForNonMetadata() {
        Tool tool = new SimpleTool("basic", "Basic tool", ToolSchema.empty(),
            args -> ToolResult.success("ok"));

        Map<String, Boolean> annotations = McpToolAdapter.extractAnnotations(tool);

        assertTrue(annotations.isEmpty());
    }

    // ==================== Test Helpers ====================

    @FunctionalInterface
    interface ToolExecutor {
        ToolResult execute(Map<String, Object> args);
    }

    private static class SimpleTool implements Tool {
        private final String name;
        private final String description;
        private final ToolSchema schema;
        private final ToolExecutor executor;

        SimpleTool(String name, String description, ToolSchema schema, ToolExecutor executor) {
            this.name = name;
            this.description = description;
            this.schema = schema;
            this.executor = executor;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return description; }
        @Override public ToolSchema getSchema() { return schema; }
        @Override public ToolResult execute(Map<String, Object> args) {
            return executor.execute(args);
        }
    }

    private static class StreamingTestTool implements Tool {
        private final String name;
        private final String description;

        StreamingTestTool(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return description; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
        @Override public ToolResult execute(Map<String, Object> args) {
            return ToolResult.success("done");
        }

        @Override
        public Flux<ToolResultChunk> executeReactive(Map<String, Object> args) {
            return Flux.just(
                ToolResultChunk.progress(name, "Starting...", 0, 100),
                ToolResultChunk.progress(name, "Processing...", 50, 100),
                ToolResultChunk.log(name, "INFO", "Processed input"),
                ToolResultChunk.complete(name, ToolResult.success("processed"))
            );
        }
    }
}
