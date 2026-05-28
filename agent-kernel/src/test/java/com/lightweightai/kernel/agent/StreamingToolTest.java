package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.FluxSink;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StreamingToolTest {

    @Test
    @DisplayName("sync execute returns COMPLETE result when subclass emits progress then complete")
    void syncExecuteReturnsCompleteResult() {
        StreamingTool tool = new StreamingTool() {
            @Override
            public String getName() { return "test-tool"; }

            @Override
            public String getDescription() { return "test"; }

            @Override
            public ToolSchema getSchema() { return ToolSchema.empty(); }

            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.progress("test-tool", "Working...", 50, 100));
                emitter.next(ToolResultChunk.complete("test-tool", ToolResult.success("done")));
                emitter.complete();
            }
        };

        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError());
        assertEquals("done", result.getContent());
    }

    @Test
    @DisplayName("sync execute returns error message when subclass emits error chunk")
    void syncExecuteReturnsErrorOnErrorChunk() {
        StreamingTool tool = new StreamingTool() {
            @Override
            public String getName() { return "error-tool"; }

            @Override
            public String getDescription() { return "test"; }

            @Override
            public ToolSchema getSchema() { return ToolSchema.empty(); }

            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.error("error-tool", "something went wrong"));
                emitter.complete();
            }
        };

        ToolResult result = tool.execute(Map.of());

        assertTrue(result.isError());
        assertEquals("something went wrong", result.getContent());
    }

    @Test
    @DisplayName("executeReactive catches exception from subclass and emits error chunk")
    void reactiveStreamCatchesExceptionAndEmitsError() {
        StreamingTool tool = new StreamingTool() {
            @Override
            public String getName() { return "throwing-tool"; }

            @Override
            public String getDescription() { return "test"; }

            @Override
            public ToolSchema getSchema() { return ToolSchema.empty(); }

            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                throw new RuntimeException("kaboom");
            }
        };

        List<ToolResultChunk> chunks = tool.executeReactive(Map.of()).collectList().block();

        assertNotNull(chunks);
        assertEquals(1, chunks.size());
        assertEquals(ToolResultChunk.ChunkType.ERROR, chunks.get(0).getType());
        assertEquals("kaboom", chunks.get(0).getMessage());
        assertEquals("throwing-tool", chunks.get(0).getToolName());
    }

    @Test
    @DisplayName("reactive stream emits progress chunks followed by COMPLETE in order")
    void reactiveStreamEmitsProgressThenComplete() {
        StreamingTool tool = new StreamingTool() {
            @Override
            public String getName() { return "progress-tool"; }

            @Override
            public String getDescription() { return "test"; }

            @Override
            public ToolSchema getSchema() { return ToolSchema.empty(); }

            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.progress("progress-tool", "Step 1", 25, 100));
                emitter.next(ToolResultChunk.progress("progress-tool", "Step 2", 50, 100));
                emitter.next(ToolResultChunk.progress("progress-tool", "Step 3", 75, 100));
                emitter.next(ToolResultChunk.complete("progress-tool", ToolResult.success("all steps done")));
                emitter.complete();
            }
        };

        List<ToolResultChunk> chunks = tool.executeReactive(Map.of()).collectList().block();

        assertNotNull(chunks);
        assertEquals(4, chunks.size());

        assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunks.get(0).getType());
        assertEquals("Step 1", chunks.get(0).getMessage());
        assertEquals(25, chunks.get(0).getProgress());

        assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunks.get(1).getType());
        assertEquals("Step 2", chunks.get(1).getMessage());

        assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunks.get(2).getType());
        assertEquals("Step 3", chunks.get(2).getMessage());

        assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunks.get(3).getType());
        assertEquals("all steps done", chunks.get(3).getResult().getContent());
        assertFalse(chunks.get(3).getResult().isError());
    }

    @Test
    @DisplayName("subclass emitting only COMPLETE without progress works correctly")
    void completeOnlyWithoutProgressWorks() {
        StreamingTool tool = new StreamingTool() {
            @Override
            public String getName() { return "direct-tool"; }

            @Override
            public String getDescription() { return "test"; }

            @Override
            public ToolSchema getSchema() { return ToolSchema.empty(); }

            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.complete("direct-tool", ToolResult.success("immediate result")));
                emitter.complete();
            }
        };

        List<ToolResultChunk> chunks = tool.executeReactive(Map.of()).collectList().block();

        assertNotNull(chunks);
        assertEquals(1, chunks.size());
        assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunks.get(0).getType());
        assertEquals("immediate result", chunks.get(0).getResult().getContent());

        ToolResult syncResult = tool.execute(Map.of());
        assertFalse(syncResult.isError());
        assertEquals("immediate result", syncResult.getContent());
    }
}
