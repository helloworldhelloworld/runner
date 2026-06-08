package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamingTool - 流式工具基类")
class StreamingToolTest {

    @Nested
    @DisplayName("executeReactive 流式执行")
    class ReactiveExecution {

        @Test
        @DisplayName("正常流式输出 progress + complete")
        void normalStreamingOutput() {
            StreamingTool tool = new TestStreamingTool("test_tool", (args, emitter) -> {
                emitter.next(ToolResultChunk.progress("test_tool", "halfway", 50, 100));
                emitter.next(ToolResultChunk.complete("test_tool", ToolResult.success("done")));
                emitter.complete();
            });

            StepVerifier.create(tool.executeReactive(Map.of()))
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunk.getType());
                    assertEquals("halfway", chunk.getMessage());
                })
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunk.getType());
                    assertEquals("done", chunk.getResult().getContent());
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("executeStreaming 抛异常时 emits ERROR chunk")
        void exceptionEmitsErrorChunk() {
            StreamingTool tool = new TestStreamingTool("fail_tool", (args, emitter) -> {
                throw new RuntimeException("boom");
            });

            StepVerifier.create(tool.executeReactive(Map.of()))
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.ERROR, chunk.getType());
                    assertTrue(chunk.getMessage().contains("boom"));
                })
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("execute 同步执行")
    class SyncExecution {

        @Test
        @DisplayName("收集 COMPLETE chunk 作为结果")
        void collectsCompleteChunk() {
            StreamingTool tool = new TestStreamingTool("tool", (args, emitter) -> {
                emitter.next(ToolResultChunk.progress("tool", "half", 50, 100));
                emitter.next(ToolResultChunk.complete("tool", ToolResult.success("final result")));
                emitter.complete();
            });

            ToolResult result = tool.execute(Map.of());
            assertFalse(result.isError());
            assertEquals("final result", result.getContent());
        }

        @Test
        @DisplayName("只有 progress chunk 时返回 error")
        void noCompleteChunkReturnsError() {
            StreamingTool tool = new TestStreamingTool("tool", (args, emitter) -> {
                emitter.next(ToolResultChunk.progress("tool", "half", 50, 100));
                emitter.complete();
            });

            ToolResult result = tool.execute(Map.of());
            assertTrue(result.isError());
        }

        @Test
        @DisplayName("ERROR chunk 返回 error result")
        void errorChunkReturnsError() {
            StreamingTool tool = new TestStreamingTool("tool", (args, emitter) -> {
                emitter.next(ToolResultChunk.error("tool", "something failed"));
                emitter.complete();
            });

            ToolResult result = tool.execute(Map.of());
            assertTrue(result.isError());
            assertTrue(result.getContent().contains("something failed"));
        }
    }

    @FunctionalInterface
    interface StreamingAction {
        void execute(Map<String, Object> args, FluxSink<ToolResultChunk> emitter);
    }

    static class TestStreamingTool extends StreamingTool {
        private final String name;
        private final StreamingAction action;

        TestStreamingTool(String name, StreamingAction action) {
            this.name = name;
            this.action = action;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return "test"; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }

        @Override
        protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
            action.execute(args, emitter);
        }
    }
}
