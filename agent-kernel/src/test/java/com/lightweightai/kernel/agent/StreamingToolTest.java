package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.FluxSink;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamingTool - 真实流式工具基类")
class StreamingToolTest {

    @Test
    @DisplayName("executeReactive 通过 emitter 推送多个 chunk，最后 COMPLETE")
    void executeReactiveEmitsProgressThenComplete() {
        StreamingTool tool = new StreamingTool() {
            @Override public String getName() { return "test_stream"; }
            @Override public String getDescription() { return "test"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }

            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.progress("test_stream", "step 1", 50, 100));
                emitter.next(ToolResultChunk.progress("test_stream", "step 2", 100, 100));
                emitter.next(ToolResultChunk.complete("test_stream", ToolResult.success("done")));
                emitter.complete();
            }
        };

        List<ToolResultChunk> chunks = tool.executeReactive(Map.of()).collectList().block();
        assertNotNull(chunks);
        assertEquals(3, chunks.size());
        assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunks.get(0).getType());
        assertEquals("step 1", chunks.get(0).getMessage());
        assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunks.get(1).getType());
        assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunks.get(2).getType());
        assertEquals("done", chunks.get(2).getResult().getContent());
    }

    @Test
    @DisplayName("execute 同步方法收集流式结果的 COMPLETE chunk")
    void executeCollectsCompleteChunk() {
        StreamingTool tool = new StreamingTool() {
            @Override public String getName() { return "sync_stream"; }
            @Override public String getDescription() { return "test"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }

            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.progress("sync_stream", "working", 50, 100));
                emitter.next(ToolResultChunk.complete("sync_stream", ToolResult.success("final result")));
                emitter.complete();
            }
        };

        ToolResult result = tool.execute(Map.of());
        assertNotNull(result);
        assertFalse(result.isError());
        assertEquals("final result", result.getContent());
    }

    @Test
    @DisplayName("execute 同步方法处理 ERROR chunk")
    void executeHandlesErrorChunk() {
        StreamingTool tool = new StreamingTool() {
            @Override public String getName() { return "err_stream"; }
            @Override public String getDescription() { return "test"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }

            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.error("err_stream", "something failed"));
                emitter.complete();
            }
        };

        ToolResult result = tool.execute(Map.of());
        assertNotNull(result);
        assertTrue(result.isError());
        assertEquals("something failed", result.getContent());
    }

    @Test
    @DisplayName("executeStreaming 抛异常时转为 ERROR chunk")
    void exceptionInStreamingBecomesErrorChunk() {
        StreamingTool tool = new StreamingTool() {
            @Override public String getName() { return "throw_stream"; }
            @Override public String getDescription() { return "test"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }

            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                throw new RuntimeException("boom");
            }
        };

        List<ToolResultChunk> chunks = tool.executeReactive(Map.of()).collectList().block();
        assertNotNull(chunks);
        assertEquals(1, chunks.size());
        assertEquals(ToolResultChunk.ChunkType.ERROR, chunks.get(0).getType());
        assertEquals("boom", chunks.get(0).getMessage());
    }

    @Test
    @DisplayName("execute 无结果时返回错误")
    void executeWithNoResultReturnsError() {
        StreamingTool tool = new StreamingTool() {
            @Override public String getName() { return "empty_stream"; }
            @Override public String getDescription() { return "test"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }

            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.progress("empty_stream", "only progress", 50, 100));
                emitter.complete();
            }
        };

        ToolResult result = tool.execute(Map.of());
        assertNotNull(result);
        assertTrue(result.isError());
    }
}
