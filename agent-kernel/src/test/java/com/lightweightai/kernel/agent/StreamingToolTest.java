package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.FluxSink;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamingTool - 流式工具基类")
class StreamingToolTest {

    private StreamingTool createTool(String name,
                                     java.util.function.BiConsumer<Map<String, Object>, FluxSink<ToolResultChunk>> logic) {
        return new StreamingTool() {
            @Override
            public String getName() { return name; }

            @Override
            public String getDescription() { return "Test streaming tool"; }

            @Override
            public ToolSchema getSchema() { return ToolSchema.empty(); }

            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                logic.accept(args, emitter);
            }
        };
    }

    @Nested
    @DisplayName("executeReactive 流式执行")
    class ReactiveTests {

        @Test
        @DisplayName("进度 + 完成的事件序列")
        void progressThenComplete() {
            StreamingTool tool = createTool("progress_tool", (args, emitter) -> {
                emitter.next(ToolResultChunk.progress("progress_tool", "Step 1", 0.5, 1.0));
                emitter.next(ToolResultChunk.complete("progress_tool", ToolResult.success("done")));
                emitter.complete();
            });

            List<ToolResultChunk> chunks = new ArrayList<>();

            StepVerifier.create(tool.executeReactive(Map.of()).doOnNext(chunks::add))
                    .expectNextCount(2)
                    .verifyComplete();

            assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunks.get(0).getType());
            assertEquals("Step 1", chunks.get(0).getMessage());
            assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunks.get(1).getType());
            assertEquals("done", chunks.get(1).getResult().getContent());
        }

        @Test
        @DisplayName("executeStreaming 抛异常时发出 ERROR chunk")
        void exceptionProducesErrorChunk() {
            StreamingTool tool = createTool("error_tool", (args, emitter) -> {
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
    @DisplayName("execute 同步执行 (收集流式结果)")
    class SyncTests {

        @Test
        @DisplayName("收集 COMPLETE chunk 的 ToolResult")
        void collectsCompleteResult() {
            StreamingTool tool = createTool("sync_tool", (args, emitter) -> {
                emitter.next(ToolResultChunk.progress("sync_tool", "working...", 0.5, 1.0));
                emitter.next(ToolResultChunk.complete("sync_tool", ToolResult.success("final result")));
                emitter.complete();
            });

            ToolResult result = tool.execute(Map.of());
            assertFalse(result.isError());
            assertEquals("final result", result.getContent());
        }

        @Test
        @DisplayName("ERROR chunk 转为 error ToolResult")
        void errorChunkBecomesErrorResult() {
            StreamingTool tool = createTool("err_tool", (args, emitter) -> {
                emitter.next(ToolResultChunk.error("err_tool", "something failed"));
                emitter.complete();
            });

            ToolResult result = tool.execute(Map.of());
            assertTrue(result.isError());
            assertTrue(result.getContent().contains("something failed"));
        }

        @Test
        @DisplayName("无 COMPLETE/ERROR chunk 返回 error")
        void noTerminalChunkReturnsError() {
            StreamingTool tool = createTool("empty_tool", (args, emitter) -> {
                emitter.next(ToolResultChunk.progress("empty_tool", "orphan", 0, 0));
                emitter.complete();
            });

            ToolResult result = tool.execute(Map.of());
            assertTrue(result.isError());
            assertTrue(result.getContent().contains("No result"));
        }
    }
}
