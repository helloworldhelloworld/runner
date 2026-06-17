package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamingTool - streaming-to-sync bridge")
class StreamingToolTest {

    @Test
    @DisplayName("execute collects final COMPLETE chunk from streaming pipeline")
    void executeShouldCollectCompleteChunk() {
        StreamingTool tool = new StreamingTool() {
            @Override
            public String getName() { return "test_stream"; }
            @Override
            public String getDescription() { return "Test streaming tool"; }
            @Override
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.progress("test_stream", "Working...", 50, 100));
                emitter.next(ToolResultChunk.complete("test_stream", ToolResult.success("final result")));
                emitter.complete();
            }
        };

        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        assertEquals("final result", result.getContent());
    }

    @Test
    @DisplayName("execute returns error result when streaming emits ERROR chunk")
    void executeShouldReturnErrorOnErrorChunk() {
        StreamingTool tool = new StreamingTool() {
            @Override
            public String getName() { return "error_stream"; }
            @Override
            public String getDescription() { return "Error streaming tool"; }
            @Override
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.error("error_stream", "something broke"));
                emitter.complete();
            }
        };

        ToolResult result = tool.execute(Map.of());
        assertTrue(result.isError());
        assertEquals("something broke", result.getContent());
    }

    @Test
    @DisplayName("execute returns error when no COMPLETE or ERROR chunk emitted")
    void executeShouldReturnErrorWhenNoFinalChunk() {
        StreamingTool tool = new StreamingTool() {
            @Override
            public String getName() { return "empty_stream"; }
            @Override
            public String getDescription() { return "Empty streaming tool"; }
            @Override
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.progress("empty_stream", "progress only", 50, 100));
                emitter.complete();
            }
        };

        ToolResult result = tool.execute(Map.of());
        assertTrue(result.isError());
        assertEquals("No result from streaming tool", result.getContent());
    }

    @Test
    @DisplayName("executeReactive emits all chunks including progress")
    void executeReactiveShouldEmitAllChunks() {
        StreamingTool tool = new StreamingTool() {
            @Override
            public String getName() { return "multi_stream"; }
            @Override
            public String getDescription() { return "Multi-chunk streaming tool"; }
            @Override
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.progress("multi_stream", "step 1", 33, 100));
                emitter.next(ToolResultChunk.log("multi_stream", "INFO", "Processing..."));
                emitter.next(ToolResultChunk.progress("multi_stream", "step 2", 66, 100));
                emitter.next(ToolResultChunk.complete("multi_stream", ToolResult.success("done")));
                emitter.complete();
            }
        };

        List<ToolResultChunk> chunks = tool.executeReactive(Map.of()).collectList().block();
        assertNotNull(chunks);
        assertEquals(4, chunks.size());
        assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunks.get(0).getType());
        assertEquals(ToolResultChunk.ChunkType.LOG, chunks.get(1).getType());
        assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunks.get(2).getType());
        assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunks.get(3).getType());
        assertEquals("done", chunks.get(3).getResult().getContent());
    }

    @Test
    @DisplayName("executeStreaming exception is caught and emitted as ERROR chunk")
    void exceptionInExecuteStreamingShouldBecomeErrorChunk() {
        StreamingTool tool = new StreamingTool() {
            @Override
            public String getName() { return "crash_stream"; }
            @Override
            public String getDescription() { return "Crashing streaming tool"; }
            @Override
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                throw new RuntimeException("unexpected failure");
            }
        };

        List<ToolResultChunk> chunks = tool.executeReactive(Map.of()).collectList().block();
        assertNotNull(chunks);
        assertEquals(1, chunks.size());
        assertEquals(ToolResultChunk.ChunkType.ERROR, chunks.get(0).getType());
        assertEquals("unexpected failure", chunks.get(0).getMessage());
    }

    @Test
    @DisplayName("execute passes args through to executeStreaming")
    void executeShouldPassArgs() {
        List<Map<String, Object>> capturedArgs = new ArrayList<>();

        StreamingTool tool = new StreamingTool() {
            @Override
            public String getName() { return "arg_stream"; }
            @Override
            public String getDescription() { return "Arg capturing tool"; }
            @Override
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                capturedArgs.add(args);
                emitter.next(ToolResultChunk.complete("arg_stream", ToolResult.success("ok")));
                emitter.complete();
            }
        };

        Map<String, Object> input = Map.of("key", "value", "num", 42);
        tool.execute(input);

        assertEquals(1, capturedArgs.size());
        assertEquals("value", capturedArgs.get(0).get("key"));
        assertEquals(42, capturedArgs.get(0).get("num"));
    }
}
