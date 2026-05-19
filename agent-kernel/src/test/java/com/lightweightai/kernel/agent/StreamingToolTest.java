package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.FluxSink;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamingTool — 流式工具基类")
class StreamingToolTest {

    @Test
    @DisplayName("execute 收集 COMPLETE chunk 并返回其 ToolResult")
    void executeReturnsCompleteChunkResult() {
        StreamingTool tool = new StreamingTool() {
            @Override public String getName() { return "test_stream"; }
            @Override public String getDescription() { return "test"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.progress("test_stream", "working", 50, 100));
                emitter.next(ToolResultChunk.complete("test_stream", ToolResult.success("done")));
                emitter.complete();
            }
        };

        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        assertEquals("done", result.getContent());
    }

    @Test
    @DisplayName("execute 在 ERROR chunk 时返回错误 ToolResult")
    void executeReturnsErrorOnErrorChunk() {
        StreamingTool tool = new StreamingTool() {
            @Override public String getName() { return "err_stream"; }
            @Override public String getDescription() { return "test"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.error("err_stream", "something broke"));
                emitter.complete();
            }
        };

        ToolResult result = tool.execute(Map.of());
        assertTrue(result.isError());
        assertEquals("something broke", result.getContent());
    }

    @Test
    @DisplayName("execute 在无 chunk 时返回 error")
    void executeReturnsErrorWhenNoChunks() {
        StreamingTool tool = new StreamingTool() {
            @Override public String getName() { return "empty_stream"; }
            @Override public String getDescription() { return "test"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.complete();
            }
        };

        ToolResult result = tool.execute(Map.of());
        assertTrue(result.isError());
    }

    @Test
    @DisplayName("executeReactive 将异常包装为 error chunk 而非传播异常")
    void executeReactiveWrapsException() {
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
    @DisplayName("executeReactive 按顺序发出 progress + complete chunks")
    void executeReactiveStreamsFull() {
        StreamingTool tool = new StreamingTool() {
            @Override public String getName() { return "full_stream"; }
            @Override public String getDescription() { return "test"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.progress("full_stream", "step1", 25, 100));
                emitter.next(ToolResultChunk.progress("full_stream", "step2", 50, 100));
                emitter.next(ToolResultChunk.progress("full_stream", "step3", 75, 100));
                emitter.next(ToolResultChunk.complete("full_stream", ToolResult.success("final")));
                emitter.complete();
            }
        };

        List<ToolResultChunk> chunks = tool.executeReactive(Map.of()).collectList().block();
        assertNotNull(chunks);
        assertEquals(4, chunks.size());
        assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunks.get(0).getType());
        assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunks.get(1).getType());
        assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunks.get(2).getType());
        assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunks.get(3).getType());
        assertEquals("final", chunks.get(3).getResult().getContent());
    }

    @Test
    @DisplayName("execute 透传 args 到 executeStreaming")
    void executePassesArgsThroughToStreaming() {
        var capturedArgs = new java.util.concurrent.atomic.AtomicReference<Map<String, Object>>();

        StreamingTool tool = new StreamingTool() {
            @Override public String getName() { return "args_stream"; }
            @Override public String getDescription() { return "test"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                capturedArgs.set(args);
                emitter.next(ToolResultChunk.complete("args_stream", ToolResult.success("ok")));
                emitter.complete();
            }
        };

        Map<String, Object> input = Map.of("key", "value", "count", 42);
        tool.execute(input);

        assertNotNull(capturedArgs.get());
        assertEquals("value", capturedArgs.get().get("key"));
        assertEquals(42, capturedArgs.get().get("count"));
    }
}
