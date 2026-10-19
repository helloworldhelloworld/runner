package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.FluxSink;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamingTool - 流式工具基类")
class StreamingToolTest {

    private static final String TOOL_NAME = "test-streaming-tool";

    private static StreamingTool createTool(java.util.function.BiConsumer<Map<String, Object>, FluxSink<ToolResultChunk>> behavior) {
        return new StreamingTool() {
            @Override
            public String getName() { return TOOL_NAME; }

            @Override
            public String getDescription() { return "test tool"; }

            @Override
            public ToolSchema getSchema() { return ToolSchema.empty(); }

            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                behavior.accept(args, emitter);
            }
        };
    }

    @Nested
    @DisplayName("execute() 同步执行")
    class Execute {

        @Test
        @DisplayName("返回 COMPLETE chunk 中的 ToolResult")
        void returnsCompleteResult() {
            StreamingTool tool = createTool((args, emitter) -> {
                emitter.next(ToolResultChunk.progress(TOOL_NAME, "working...", 1, 2));
                emitter.next(ToolResultChunk.complete(TOOL_NAME, ToolResult.success("done")));
                emitter.complete();
            });

            ToolResult result = tool.execute(Map.of());

            assertFalse(result.isError());
            assertEquals("done", result.getContent());
        }

        @Test
        @DisplayName("ERROR chunk 返回错误 ToolResult 并携带错误消息")
        void returnsErrorResult() {
            StreamingTool tool = createTool((args, emitter) -> {
                emitter.next(ToolResultChunk.error(TOOL_NAME, "something broke"));
                emitter.complete();
            });

            ToolResult result = tool.execute(Map.of());

            assertTrue(result.isError());
            assertEquals("something broke", result.getContent());
        }

        @Test
        @DisplayName("ERROR chunk 消息为 null 时回退为默认错误消息")
        void errorWithNullMessageFallsBack() {
            StreamingTool tool = createTool((args, emitter) -> {
                emitter.next(ToolResultChunk.error(TOOL_NAME, null));
                emitter.complete();
            });

            ToolResult result = tool.execute(Map.of());

            assertTrue(result.isError());
            assertEquals("Tool execution failed", result.getContent());
        }

        @Test
        @DisplayName("无终端 chunk 时返回 'No result from streaming tool' 错误")
        void noTerminalChunkReturnsNoResult() {
            StreamingTool tool = createTool((args, emitter) -> {
                emitter.next(ToolResultChunk.progress(TOOL_NAME, "step 1", 1, 3));
                emitter.next(ToolResultChunk.progress(TOOL_NAME, "step 2", 2, 3));
                emitter.complete();
            });

            ToolResult result = tool.execute(Map.of());

            assertTrue(result.isError());
            assertEquals("No result from streaming tool", result.getContent());
        }

        @Test
        @DisplayName("跳过 PROGRESS chunk，只取第一个终端 chunk")
        void skipsProgressChunks() {
            StreamingTool tool = createTool((args, emitter) -> {
                emitter.next(ToolResultChunk.progress(TOOL_NAME, "10%", 1, 10));
                emitter.next(ToolResultChunk.progress(TOOL_NAME, "50%", 5, 10));
                emitter.next(ToolResultChunk.progress(TOOL_NAME, "90%", 9, 10));
                emitter.next(ToolResultChunk.complete(TOOL_NAME, ToolResult.success("final")));
                emitter.complete();
            });

            ToolResult result = tool.execute(Map.of());

            assertFalse(result.isError());
            assertEquals("final", result.getContent());
        }
    }

    @Nested
    @DisplayName("executeReactive() 流式执行")
    class ExecuteReactive {

        @Test
        @DisplayName("executeStreaming 抛异常时流发出 ERROR chunk 后完成")
        void catchesExceptionFromExecuteStreaming() {
            StreamingTool tool = createTool((args, emitter) -> {
                throw new RuntimeException("boom");
            });

            List<ToolResultChunk> chunks = tool.executeReactive(Map.of()).collectList().block();

            assertNotNull(chunks);
            assertEquals(1, chunks.size());
            assertEquals(ToolResultChunk.ChunkType.ERROR, chunks.get(0).getType());
            assertEquals("boom", chunks.get(0).getMessage());
        }

        @Test
        @DisplayName("按顺序流出多个 progress chunk 和最终 complete chunk")
        void streamsProgressAndCompleteInOrder() {
            StreamingTool tool = createTool((args, emitter) -> {
                emitter.next(ToolResultChunk.progress(TOOL_NAME, "step 1", 1, 3));
                emitter.next(ToolResultChunk.progress(TOOL_NAME, "step 2", 2, 3));
                emitter.next(ToolResultChunk.complete(TOOL_NAME, ToolResult.success("all done")));
                emitter.complete();
            });

            List<ToolResultChunk> chunks = tool.executeReactive(Map.of()).collectList().block();

            assertNotNull(chunks);
            assertEquals(3, chunks.size());

            assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunks.get(0).getType());
            assertEquals("step 1", chunks.get(0).getMessage());

            assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunks.get(1).getType());
            assertEquals("step 2", chunks.get(1).getMessage());

            assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunks.get(2).getType());
            ToolResult result = chunks.get(2).getResult();
            assertNotNull(result);
            assertFalse(result.isError());
            assertEquals("all done", result.getContent());
        }
    }
}
