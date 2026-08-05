package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.FluxSink;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests StreamingTool base class behavior:
 * - executeReactive() emits progress + complete chunks
 * - execute() (sync) collects the final COMPLETE chunk
 * - Error handling: exception in executeStreaming → ERROR chunk
 */
@DisplayName("StreamingTool base class")
class StreamingToolTest {

    @Test
    @DisplayName("executeReactive emits progress and complete chunks")
    void executeReactive_emitsProgressAndComplete() {
        StreamingTool tool = new TestStreamingTool("progress-tool",
                (args, emitter) -> {
                    emitter.next(ToolResultChunk.progress("progress-tool", "step 1", 0.5, 1.0));
                    emitter.next(ToolResultChunk.progress("progress-tool", "step 2", 1.0, 1.0));
                    emitter.next(ToolResultChunk.complete("progress-tool", ToolResult.success("done")));
                    emitter.complete();
                });

        StepVerifier.create(tool.executeReactive(Map.of()))
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunk.getType());
                    assertEquals("step 1", chunk.getMessage());
                })
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunk.getType());
                    assertEquals("step 2", chunk.getMessage());
                })
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunk.getType());
                    assertNotNull(chunk.getResult());
                    assertEquals("done", chunk.getResult().getContent());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("sync execute collects final COMPLETE result")
    void syncExecute_collectsFinalResult() {
        StreamingTool tool = new TestStreamingTool("sync-tool",
                (args, emitter) -> {
                    emitter.next(ToolResultChunk.progress("sync-tool", "working", 0.5, 1.0));
                    emitter.next(ToolResultChunk.complete("sync-tool", ToolResult.success("final result")));
                    emitter.complete();
                });

        ToolResult result = tool.execute(Map.of());
        assertNotNull(result);
        assertFalse(result.isError());
        assertEquals("final result", result.getContent());
    }

    @Test
    @DisplayName("sync execute returns error when stream has only ERROR chunk")
    void syncExecute_returnsErrorOnErrorChunk() {
        StreamingTool tool = new TestStreamingTool("error-tool",
                (args, emitter) -> {
                    emitter.next(ToolResultChunk.error("error-tool", "something broke"));
                    emitter.complete();
                });

        ToolResult result = tool.execute(Map.of());
        assertNotNull(result);
        assertTrue(result.isError());
        assertEquals("something broke", result.getContent());
    }

    @Test
    @DisplayName("exception in executeStreaming produces ERROR chunk, not Flux error")
    void exceptionInExecuteStreaming_producesErrorChunk() {
        StreamingTool tool = new TestStreamingTool("exception-tool",
                (args, emitter) -> {
                    throw new RuntimeException("unexpected failure");
                });

        StepVerifier.create(tool.executeReactive(Map.of()))
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.ERROR, chunk.getType());
                    assertTrue(chunk.getMessage().contains("unexpected failure"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("sync execute returns error message when no result chunks emitted")
    void syncExecute_noResultChunks_returnsError() {
        StreamingTool tool = new TestStreamingTool("empty-tool",
                (args, emitter) -> {
                    // Only emit progress, no complete/error
                    emitter.next(ToolResultChunk.progress("empty-tool", "working", 0.5, 1.0));
                    emitter.complete();
                });

        ToolResult result = tool.execute(Map.of());
        assertNotNull(result);
        assertTrue(result.isError());
        assertEquals("No result from streaming tool", result.getContent());
    }

    @FunctionalInterface
    interface StreamingAction {
        void run(Map<String, Object> args, FluxSink<ToolResultChunk> emitter);
    }

    private static class TestStreamingTool extends StreamingTool {
        private final String name;
        private final StreamingAction action;

        TestStreamingTool(String name, StreamingAction action) {
            this.name = name;
            this.action = action;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return name + " test tool"; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }

        @Override
        protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
            action.run(args, emitter);
        }
    }
}
