package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamingTool — real streaming execution base class")
class StreamingToolTest {

    @Test
    @DisplayName("executeReactive() emits progress chunks then completes")
    void reactiveEmitsProgressThenComplete() {
        StreamingTool tool = new StreamingTool() {
            @Override public String getName() { return "counter"; }
            @Override public String getDescription() { return "Counts"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }

            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.progress("counter", 50, "halfway"));
                emitter.next(ToolResultChunk.complete("counter", ToolResult.success("done")));
                emitter.complete();
            }
        };

        Flux<ToolResultChunk> flux = tool.executeReactive(Map.of());

        StepVerifier.create(flux)
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunk.getType());
                    assertEquals("halfway", chunk.getMessage());
                    assertEquals(50, chunk.getProgress());
                })
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunk.getType());
                    assertEquals("done", chunk.getResult().getContent());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("execute() blocks and returns final COMPLETE result")
    void syncExecuteReturnsCompleteResult() {
        StreamingTool tool = new StreamingTool() {
            @Override public String getName() { return "adder"; }
            @Override public String getDescription() { return "Adds"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }

            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.progress("adder", 100, "computing"));
                emitter.next(ToolResultChunk.complete("adder", ToolResult.success("42")));
                emitter.complete();
            }
        };

        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        assertEquals("42", result.getContent());
    }

    @Test
    @DisplayName("execute() returns error when stream emits ERROR chunk")
    void syncExecuteReturnsErrorOnErrorChunk() {
        StreamingTool tool = new StreamingTool() {
            @Override public String getName() { return "fail"; }
            @Override public String getDescription() { return "Fails"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }

            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.error("fail", "disk full"));
                emitter.complete();
            }
        };

        ToolResult result = tool.execute(Map.of());
        assertTrue(result.isError());
        assertEquals("disk full", result.getContent());
    }

    @Test
    @DisplayName("exception in executeStreaming is caught and emitted as error chunk")
    void exceptionInStreamingIsCaught() {
        StreamingTool tool = new StreamingTool() {
            @Override public String getName() { return "boom"; }
            @Override public String getDescription() { return "Explodes"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }

            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                throw new RuntimeException("unexpected error");
            }
        };

        Flux<ToolResultChunk> flux = tool.executeReactive(Map.of());

        StepVerifier.create(flux)
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.ERROR, chunk.getType());
                    assertEquals("unexpected error", chunk.getMessage());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("execute() returns 'No result' when stream is empty")
    void syncExecuteReturnsNoResultOnEmptyStream() {
        StreamingTool tool = new StreamingTool() {
            @Override public String getName() { return "empty"; }
            @Override public String getDescription() { return "Empty"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }

            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                emitter.next(ToolResultChunk.progress("empty", 50, "progress only"));
                emitter.complete();
            }
        };

        ToolResult result = tool.execute(Map.of());
        assertTrue(result.isError());
    }
}
