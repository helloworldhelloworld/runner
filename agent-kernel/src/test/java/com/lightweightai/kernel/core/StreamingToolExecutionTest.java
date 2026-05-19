package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.StreamingTool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transmission test: StreamingTool → ToolRegistry → ToolExecutor
 *
 * Verifies that a StreamingTool registered in ToolRegistry can be discovered
 * and executed by ToolExecutor, and that the result payload is correctly propagated.
 */
@DisplayName("Acceptance: StreamingTool → ToolRegistry → ToolExecutor 传导")
class StreamingToolExecutionTest {

    @Test
    @DisplayName("ToolExecutor 通过 ToolRegistry 执行 StreamingTool 并获得正确结果")
    void streamingToolExecutedViaRegistry() {
        ToolRegistry registry = new ToolRegistry();

        StreamingTool streamTool = new StreamingTool() {
            @Override public String getName() { return "stream_search"; }
            @Override public String getDescription() { return "Streaming search"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.progress("stream_search", "searching...", 50, 100));
                String query = (String) args.getOrDefault("query", "default");
                emitter.next(ToolResultChunk.complete("stream_search",
                        ToolResult.success("Found results for: " + query)));
                emitter.complete();
            }
        };

        registry.register(streamTool);

        ToolExecutor executor = new ToolExecutor(registry);

        ToolCall call = new ToolCall("call-1", "stream_search", Map.of("query", "hello world"));
        List<ToolResult> results = executor.executeToolCalls(List.of(call));

        assertEquals(1, results.size());
        ToolResult result = results.get(0);
        assertFalse(result.isError());
        assertEquals("Found results for: hello world", result.getContent());
        assertEquals("call-1", result.getToolUseId());
    }

    @Test
    @DisplayName("StreamingTool 抛异常时 ToolExecutor 返回 error ToolResult（不 crash）")
    void streamingToolExceptionHandledByExecutor() {
        ToolRegistry registry = new ToolRegistry();

        StreamingTool brokenTool = new StreamingTool() {
            @Override public String getName() { return "broken_stream"; }
            @Override public String getDescription() { return "broken"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                throw new RuntimeException("stream failed");
            }
        };

        registry.register(brokenTool);
        ToolExecutor executor = new ToolExecutor(registry);

        ToolCall call = new ToolCall("call-2", "broken_stream", Map.of());
        List<ToolResult> results = executor.executeToolCalls(List.of(call));

        assertEquals(1, results.size());
        assertTrue(results.get(0).isError());
    }

    @Test
    @DisplayName("StreamingTool 的 reactive 结果可通过 executeReactive 逐块获取")
    void streamingToolReactiveExecution() {
        StreamingTool tool = new StreamingTool() {
            @Override public String getName() { return "reactive_test"; }
            @Override public String getDescription() { return "test"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.progress("reactive_test", "step 1", 33, 100));
                emitter.next(ToolResultChunk.log("reactive_test", "INFO", "Processing..."));
                emitter.next(ToolResultChunk.complete("reactive_test", ToolResult.success("all done")));
                emitter.complete();
            }
        };

        List<ToolResultChunk> chunks = tool.executeReactive(Map.of()).collectList().block();
        assertNotNull(chunks);
        assertEquals(3, chunks.size());
        assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunks.get(0).getType());
        assertEquals("step 1", chunks.get(0).getMessage());
        assertEquals(ToolResultChunk.ChunkType.LOG, chunks.get(1).getType());
        assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunks.get(2).getType());
        assertEquals("all done", chunks.get(2).getResult().getContent());
    }

    @Test
    @DisplayName("ToolRegistry.getToolDefinitions 包含 StreamingTool 的定义")
    void streamingToolAppearsInDefinitions() {
        ToolRegistry registry = new ToolRegistry();

        StreamingTool tool = new StreamingTool() {
            @Override public String getName() { return "stream_file_read"; }
            @Override public String getDescription() { return "Read file in chunks"; }
            @Override public ToolSchema getSchema() {
                return ToolSchema.withRequired(Map.of(
                        "path", Map.of("type", "string", "description", "File path")
                ), "path");
            }
            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.complete("stream_file_read", ToolResult.success("content")));
                emitter.complete();
            }
        };

        registry.register(tool);

        List<Map<String, Object>> defs = registry.getToolDefinitions();
        assertEquals(1, defs.size());
        assertEquals("stream_file_read", defs.get(0).get("name"));
        assertEquals("Read file in chunks", defs.get(0).get("description"));

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) defs.get(0).get("input_schema");
        assertNotNull(inputSchema);
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) inputSchema.get("required");
        assertTrue(required.contains("path"));
    }
}
