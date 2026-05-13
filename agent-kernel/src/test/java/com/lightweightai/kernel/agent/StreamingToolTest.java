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

@DisplayName("StreamingTool — streaming execution critical path")
class StreamingToolTest {

    @Test
    @DisplayName("executeReactive emits progress chunks then complete")
    void executeReactiveEmitsProgressAndComplete() {
        StreamingTool tool = new TestStreamingTool("streamer", (args, emitter) -> {
            emitter.next(ToolResultChunk.progress("streamer", 50, "halfway"));
            emitter.next(ToolResultChunk.progress("streamer", 100, "done"));
            emitter.next(ToolResultChunk.complete("streamer", ToolResult.success("final result")));
            emitter.complete();
        });

        StepVerifier.create(tool.executeReactive(Map.of()))
                .expectNextMatches(c -> c.getType() == ToolResultChunk.ChunkType.PROGRESS && c.getMessage().equals("halfway"))
                .expectNextMatches(c -> c.getType() == ToolResultChunk.ChunkType.PROGRESS && c.getMessage().equals("done"))
                .expectNextMatches(c -> c.getType() == ToolResultChunk.ChunkType.COMPLETE
                        && c.getResult().getContent().equals("final result"))
                .verifyComplete();
    }

    @Test
    @DisplayName("execute() collects the COMPLETE chunk result synchronously")
    void executeSyncCollectsCompleteResult() {
        StreamingTool tool = new TestStreamingTool("sync-test", (args, emitter) -> {
            emitter.next(ToolResultChunk.progress("sync-test", 50, "progress"));
            emitter.next(ToolResultChunk.complete("sync-test", ToolResult.success("sync result")));
            emitter.complete();
        });

        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        assertEquals("sync result", result.getContent());
    }

    @Test
    @DisplayName("execute() returns error when streaming produces ERROR chunk")
    void executeSyncReturnsErrorOnErrorChunk() {
        StreamingTool tool = new TestStreamingTool("err-test", (args, emitter) -> {
            emitter.next(ToolResultChunk.error("err-test", "something went wrong"));
            emitter.complete();
        });

        ToolResult result = tool.execute(Map.of());
        assertTrue(result.isError());
        assertTrue(result.getContent().contains("something went wrong"));
    }

    @Test
    @DisplayName("execute() returns 'No result' when streaming emits no complete/error")
    void executeSyncReturnsNoResultOnEmptyStream() {
        StreamingTool tool = new TestStreamingTool("empty", (args, emitter) -> {
            emitter.next(ToolResultChunk.progress("empty", 10, "just progress"));
            emitter.complete();
        });

        ToolResult result = tool.execute(Map.of());
        assertTrue(result.isError());
        assertTrue(result.getContent().contains("No result"));
    }

    @Test
    @DisplayName("exception in executeStreaming is caught and emitted as ERROR chunk")
    void exceptionInStreamingIsCaught() {
        StreamingTool tool = new TestStreamingTool("throw-test", (args, emitter) -> {
            throw new RuntimeException("boom");
        });

        StepVerifier.create(tool.executeReactive(Map.of()))
                .expectNextMatches(c -> c.getType() == ToolResultChunk.ChunkType.ERROR
                        && c.getMessage().contains("boom"))
                .verifyComplete();
    }

    @Test
    @DisplayName("exception in executeStreaming — sync path returns error result")
    void exceptionInStreamingSyncPath() {
        StreamingTool tool = new TestStreamingTool("throw-sync", (args, emitter) -> {
            throw new IllegalStateException("sync boom");
        });

        ToolResult result = tool.execute(Map.of());
        assertTrue(result.isError());
        assertTrue(result.getContent().contains("sync boom"));
    }

    @Test
    @DisplayName("args are passed through to executeStreaming")
    void argsPassedThrough() {
        StreamingTool tool = new TestStreamingTool("args-test", (args, emitter) -> {
            String val = (String) args.get("key");
            emitter.next(ToolResultChunk.complete("args-test", ToolResult.success("got: " + val)));
            emitter.complete();
        });

        ToolResult result = tool.execute(Map.of("key", "hello"));
        assertEquals("got: hello", result.getContent());
    }

    // ==================== Helpers ====================

    @FunctionalInterface
    interface StreamAction {
        void execute(Map<String, Object> args, FluxSink<ToolResultChunk> emitter);
    }

    private static class TestStreamingTool extends StreamingTool {
        private final String name;
        private final StreamAction action;

        TestStreamingTool(String name, StreamAction action) {
            this.name = name;
            this.action = action;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getDescription() { return "test streaming tool"; }

        @Override
        public ToolSchema getSchema() { return ToolSchema.empty(); }

        @Override
        protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
            action.execute(args, emitter);
        }
    }
}
