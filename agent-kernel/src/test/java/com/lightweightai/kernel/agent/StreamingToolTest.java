package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.FluxSink;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamingTool — streaming tool execution contract")
class StreamingToolTest {

    @Test
    @DisplayName("executeReactive emits progress chunks then COMPLETE")
    void streamingToolEmitsProgressAndComplete() {
        StreamingTool tool = new StreamingTool() {
            @Override
            public String getName() { return "test_stream"; }
            @Override
            public String getDescription() { return "test"; }
            @Override
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.progress("test_stream", "step 1", 50, 100));
                emitter.next(ToolResultChunk.progress("test_stream", "step 2", 100, 100));
                emitter.next(ToolResultChunk.complete("test_stream", ToolResult.success("all done")));
                emitter.complete();
            }
        };

        StepVerifier.create(tool.executeReactive(Map.of()))
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunk.getType());
                    assertEquals("step 1", chunk.getMessage());
                    assertEquals(50.0, chunk.getProgress());
                })
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunk.getType());
                    assertEquals("step 2", chunk.getMessage());
                })
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunk.getType());
                    assertNotNull(chunk.getResult());
                    assertEquals("all done", chunk.getResult().getContent());
                    assertFalse(chunk.getResult().isError());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("execute() synchronously collects the streaming COMPLETE result")
    void syncExecuteCollectsStreamingResult() {
        StreamingTool tool = new StreamingTool() {
            @Override
            public String getName() { return "sync_collect"; }
            @Override
            public String getDescription() { return "test"; }
            @Override
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.progress("sync_collect", "working", 50, 100));
                emitter.next(ToolResultChunk.complete("sync_collect", ToolResult.success("final result")));
                emitter.complete();
            }
        };

        ToolResult result = tool.execute(Map.of());
        assertNotNull(result);
        assertFalse(result.isError());
        assertEquals("final result", result.getContent());
    }

    @Test
    @DisplayName("execute() returns error result when streaming throws exception")
    void syncExecuteHandlesException() {
        StreamingTool tool = new StreamingTool() {
            @Override
            public String getName() { return "error_tool"; }
            @Override
            public String getDescription() { return "test"; }
            @Override
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                throw new RuntimeException("stream failed");
            }
        };

        ToolResult result = tool.execute(Map.of());
        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(result.getContent().contains("stream failed"));
    }

    @Test
    @DisplayName("executeReactive wraps exception as ERROR chunk")
    void reactiveWrapsException() {
        StreamingTool tool = new StreamingTool() {
            @Override
            public String getName() { return "err_reactive"; }
            @Override
            public String getDescription() { return "test"; }
            @Override
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                throw new IllegalStateException("boom");
            }
        };

        StepVerifier.create(tool.executeReactive(Map.of()))
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.ERROR, chunk.getType());
                    assertTrue(chunk.getMessage().contains("boom"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("args are correctly passed to executeStreaming")
    void argsPassedToStreaming() {
        StreamingTool tool = new StreamingTool() {
            @Override
            public String getName() { return "args_tool"; }
            @Override
            public String getDescription() { return "test"; }
            @Override
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                String val = (String) args.get("key");
                emitter.next(ToolResultChunk.complete("args_tool", ToolResult.success("got:" + val)));
                emitter.complete();
            }
        };

        ToolResult result = tool.execute(Map.of("key", "hello"));
        assertEquals("got:hello", result.getContent());
    }
}
