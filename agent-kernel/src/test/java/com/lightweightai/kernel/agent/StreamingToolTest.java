package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.FluxSink;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamingTool - reactive tool execution base class")
class StreamingToolTest {

    @Nested
    @DisplayName("executeReactive - real streaming path")
    class ExecuteReactive {

        @Test
        @DisplayName("emits progress chunks then complete chunk")
        void emitsProgressThenComplete() {
            StreamingTool tool = new TestStreamingTool("test-tool") {
                @Override
                protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                    emitter.next(ToolResultChunk.progress("test-tool", "step 1", 0.5, 1.0));
                    emitter.next(ToolResultChunk.progress("test-tool", "step 2", 1.0, 1.0));
                    emitter.next(ToolResultChunk.complete("test-tool", ToolResult.success("done")));
                    emitter.complete();
                }
            };

            StepVerifier.create(tool.executeReactive(Map.of()))
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunk.getType());
                    assertEquals("step 1", chunk.getMessage());
                    assertEquals(0.5, chunk.getProgress());
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
        @DisplayName("exception in executeStreaming emits error chunk then completes")
        void exceptionEmitsErrorChunk() {
            StreamingTool tool = new TestStreamingTool("fail-tool") {
                @Override
                protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                    throw new RuntimeException("disk full");
                }
            };

            StepVerifier.create(tool.executeReactive(Map.of()))
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.ERROR, chunk.getType());
                    assertTrue(chunk.getMessage().contains("disk full"));
                })
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("execute - synchronous fallback")
    class SyncExecute {

        @Test
        @DisplayName("collects final result from streaming")
        void collectsFinalResult() {
            StreamingTool tool = new TestStreamingTool("sync-tool") {
                @Override
                protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                    emitter.next(ToolResultChunk.progress("sync-tool", "working", 0.5, 1.0));
                    emitter.next(ToolResultChunk.complete("sync-tool", ToolResult.success("result-42")));
                    emitter.complete();
                }
            };

            ToolResult result = tool.execute(Map.of());
            assertFalse(result.isError());
            assertEquals("result-42", result.getContent());
        }

        @Test
        @DisplayName("returns error when streaming produces only progress (no complete/error)")
        void noCompleteReturnsError() {
            StreamingTool tool = new TestStreamingTool("empty-tool") {
                @Override
                protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                    emitter.next(ToolResultChunk.progress("empty-tool", "working", 0.5, 1.0));
                    emitter.complete();
                }
            };

            ToolResult result = tool.execute(Map.of());
            assertTrue(result.isError());
            assertTrue(result.getContent().contains("No result"));
        }

        @Test
        @DisplayName("returns error message from error chunk")
        void errorChunkReturnsErrorResult() {
            StreamingTool tool = new TestStreamingTool("err-tool") {
                @Override
                protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                    emitter.next(ToolResultChunk.error("err-tool", "permission denied"));
                    emitter.complete();
                }
            };

            ToolResult result = tool.execute(Map.of());
            assertTrue(result.isError());
            assertEquals("permission denied", result.getContent());
        }

        @Test
        @DisplayName("exception in streaming returns error result")
        void exceptionReturnsError() {
            StreamingTool tool = new TestStreamingTool("throw-tool") {
                @Override
                protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                    throw new IllegalStateException("out of memory");
                }
            };

            ToolResult result = tool.execute(Map.of());
            assertTrue(result.isError());
        }
    }

    private static abstract class TestStreamingTool extends StreamingTool {
        private final String name;

        TestStreamingTool(String name) {
            this.name = name;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getDescription() { return "test streaming tool"; }

        @Override
        public Map<String, Object> getSchema() { return Map.of(); }
    }
}
