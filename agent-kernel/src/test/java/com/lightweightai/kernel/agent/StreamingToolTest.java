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

@DisplayName("StreamingTool — abstract base for true streaming tools")
class StreamingToolTest {

    @Nested
    @DisplayName("execute() — sync collection from reactive stream")
    class SyncExecute {

        @Test
        void collectsCompleteChunkAsResult() {
            StreamingTool tool = new StreamingTool() {
                @Override
                public String getName() { return "test-tool"; }
                @Override
                public String getDescription() { return "desc"; }
                @Override
                public ToolSchema getSchema() { return ToolSchema.empty(); }
                @Override
                protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                    emitter.next(ToolResultChunk.progress("test-tool", "working", 0.5, 1.0));
                    emitter.next(ToolResultChunk.complete("test-tool", ToolResult.success("done")));
                    emitter.complete();
                }
            };

            ToolResult result = tool.execute(Map.of());
            assertNotNull(result);
            assertFalse(result.isError());
            assertEquals("done", result.getContent());
        }

        @Test
        void returnsErrorWhenStreamEmitsErrorChunk() {
            StreamingTool tool = new StreamingTool() {
                @Override
                public String getName() { return "fail-tool"; }
                @Override
                public String getDescription() { return "desc"; }
                @Override
                public ToolSchema getSchema() { return ToolSchema.empty(); }
                @Override
                protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                    emitter.next(ToolResultChunk.error("fail-tool", "something broke"));
                    emitter.complete();
                }
            };

            ToolResult result = tool.execute(Map.of());
            assertNotNull(result);
            assertTrue(result.isError());
            assertEquals("something broke", result.getContent());
        }

        @Test
        void returnsErrorWhenStreamIsEmpty() {
            StreamingTool tool = new StreamingTool() {
                @Override
                public String getName() { return "empty-tool"; }
                @Override
                public String getDescription() { return "desc"; }
                @Override
                public ToolSchema getSchema() { return ToolSchema.empty(); }
                @Override
                protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                    emitter.complete();
                }
            };

            ToolResult result = tool.execute(Map.of());
            assertNotNull(result);
            assertTrue(result.isError());
            assertEquals("No result from streaming tool", result.getContent());
        }

        @Test
        void skipsProgressChunksAndCollectsComplete() {
            StreamingTool tool = new StreamingTool() {
                @Override
                public String getName() { return "progress-tool"; }
                @Override
                public String getDescription() { return "desc"; }
                @Override
                public ToolSchema getSchema() { return ToolSchema.empty(); }
                @Override
                protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                    emitter.next(ToolResultChunk.progress("progress-tool", "10%", 0.1, 1.0));
                    emitter.next(ToolResultChunk.progress("progress-tool", "50%", 0.5, 1.0));
                    emitter.next(ToolResultChunk.log("progress-tool", "INFO", "logging"));
                    emitter.next(ToolResultChunk.complete("progress-tool", ToolResult.success("final")));
                    emitter.complete();
                }
            };

            ToolResult result = tool.execute(Map.of());
            assertFalse(result.isError());
            assertEquals("final", result.getContent());
        }

        @Test
        void errorChunkWithNullMessageFallsBackToDefault() {
            StreamingTool tool = new StreamingTool() {
                @Override
                public String getName() { return "null-err"; }
                @Override
                public String getDescription() { return "desc"; }
                @Override
                public ToolSchema getSchema() { return ToolSchema.empty(); }
                @Override
                protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                    emitter.next(ToolResultChunk.error("null-err", null));
                    emitter.complete();
                }
            };

            ToolResult result = tool.execute(Map.of());
            assertTrue(result.isError());
            assertEquals("Tool execution failed", result.getContent());
        }
    }

    @Nested
    @DisplayName("executeReactive() — Flux stream from executeStreaming")
    class ReactiveExecute {

        @Test
        void emitsAllChunksInOrder() {
            StreamingTool tool = new StreamingTool() {
                @Override
                public String getName() { return "reactive-tool"; }
                @Override
                public String getDescription() { return "desc"; }
                @Override
                public ToolSchema getSchema() { return ToolSchema.empty(); }
                @Override
                protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                    emitter.next(ToolResultChunk.progress("reactive-tool", "step1", 0.5, 1.0));
                    emitter.next(ToolResultChunk.complete("reactive-tool", ToolResult.success("ok")));
                    emitter.complete();
                }
            };

            StepVerifier.create(tool.executeReactive(Map.of()))
                .assertNext(chunk -> assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunk.getType()))
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunk.getType());
                    assertEquals("ok", chunk.getResult().getContent());
                })
                .verifyComplete();
        }

        @Test
        void catchesExceptionAndEmitsErrorChunk() {
            StreamingTool tool = new StreamingTool() {
                @Override
                public String getName() { return "throw-tool"; }
                @Override
                public String getDescription() { return "desc"; }
                @Override
                public ToolSchema getSchema() { return ToolSchema.empty(); }
                @Override
                protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                    throw new RuntimeException("boom");
                }
            };

            StepVerifier.create(tool.executeReactive(Map.of()))
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.ERROR, chunk.getType());
                    assertEquals("boom", chunk.getMessage());
                })
                .verifyComplete();
        }

        @Test
        void passesArgsToExecuteStreaming() {
            Map<String, Object> capturedArgs[] = new Map[1];
            StreamingTool tool = new StreamingTool() {
                @Override
                public String getName() { return "args-tool"; }
                @Override
                public String getDescription() { return "desc"; }
                @Override
                public ToolSchema getSchema() { return ToolSchema.empty(); }
                @Override
                protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                    capturedArgs[0] = args;
                    emitter.next(ToolResultChunk.complete("args-tool", ToolResult.success("ok")));
                    emitter.complete();
                }
            };

            Map<String, Object> input = Map.of("key", "value");
            StepVerifier.create(tool.executeReactive(input))
                .assertNext(chunk -> assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunk.getType()))
                .verifyComplete();

            assertNotNull(capturedArgs[0]);
            assertEquals("value", capturedArgs[0].get("key"));
        }
    }

}
