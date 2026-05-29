package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamingTool - Base class for streaming tool execution")
class StreamingToolTest {

    static class CountingStreamTool extends StreamingTool {
        @Override
        public String getName() { return "counting_stream"; }

        @Override
        public String getDescription() { return "Counts from 1 to N with progress"; }

        @Override
        public ToolSchema getSchema() {
            return ToolSchema.withRequired(
                Map.of("count", Map.of("type", "integer")), "count");
        }

        @Override
        protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
            int count = ((Number) args.get("count")).intValue();
            for (int i = 1; i <= count; i++) {
                emitter.next(ToolResultChunk.progress(getName(),
                    "Processing " + i, i, count));
            }
            emitter.next(ToolResultChunk.complete(getName(),
                ToolResult.success("Counted to " + count)));
            emitter.complete();
        }
    }

    static class ErrorStreamTool extends StreamingTool {
        @Override
        public String getName() { return "error_stream"; }

        @Override
        public String getDescription() { return "Always fails"; }

        @Override
        public ToolSchema getSchema() { return ToolSchema.empty(); }

        @Override
        protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
            throw new RuntimeException("Stream processing failed");
        }
    }

    static class ErrorChunkStreamTool extends StreamingTool {
        @Override
        public String getName() { return "error_chunk_stream"; }

        @Override
        public String getDescription() { return "Emits error chunk"; }

        @Override
        public ToolSchema getSchema() { return ToolSchema.empty(); }

        @Override
        protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
            emitter.next(ToolResultChunk.error(getName(), "Something went wrong"));
            emitter.complete();
        }
    }

    @Nested
    @DisplayName("Reactive execution (executeReactive)")
    class ReactiveExecution {

        @Test
        @DisplayName("Emits progress chunks followed by complete chunk")
        void shouldEmitProgressThenComplete() {
            CountingStreamTool tool = new CountingStreamTool();

            List<ToolResultChunk> chunks = tool.executeReactive(Map.of("count", 3))
                .collectList().block();

            assertNotNull(chunks);
            assertEquals(4, chunks.size());

            for (int i = 0; i < 3; i++) {
                assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunks.get(i).getType());
                assertEquals("counting_stream", chunks.get(i).getToolName());
                assertEquals(i + 1, chunks.get(i).getProgress(), 0.001);
            }

            ToolResultChunk last = chunks.get(3);
            assertEquals(ToolResultChunk.ChunkType.COMPLETE, last.getType());
            assertNotNull(last.getResult());
            assertEquals("Counted to 3", last.getResult().getContent());
            assertFalse(last.getResult().isError());
        }

        @Test
        @DisplayName("Exception in executeStreaming produces error chunk")
        void shouldCatchExceptionAsErrorChunk() {
            ErrorStreamTool tool = new ErrorStreamTool();

            List<ToolResultChunk> chunks = tool.executeReactive(Map.of())
                .collectList().block();

            assertNotNull(chunks);
            assertEquals(1, chunks.size());
            assertEquals(ToolResultChunk.ChunkType.ERROR, chunks.get(0).getType());
            assertEquals("error_stream", chunks.get(0).getToolName());
            assertTrue(chunks.get(0).getMessage().contains("Stream processing failed"));
        }

        @Test
        @DisplayName("Error chunk emitted by implementation is passed through")
        void shouldPassThroughErrorChunks() {
            ErrorChunkStreamTool tool = new ErrorChunkStreamTool();

            List<ToolResultChunk> chunks = tool.executeReactive(Map.of())
                .collectList().block();

            assertNotNull(chunks);
            assertEquals(1, chunks.size());
            assertEquals(ToolResultChunk.ChunkType.ERROR, chunks.get(0).getType());
            assertTrue(chunks.get(0).getMessage().contains("Something went wrong"));
        }
    }

    @Nested
    @DisplayName("Synchronous execution (execute)")
    class SynchronousExecution {

        @Test
        @DisplayName("execute collects streaming result into single ToolResult")
        void shouldCollectStreamingResult() {
            CountingStreamTool tool = new CountingStreamTool();

            ToolResult result = tool.execute(Map.of("count", 5));

            assertFalse(result.isError());
            assertEquals("Counted to 5", result.getContent());
        }

        @Test
        @DisplayName("execute returns error ToolResult on exception")
        void shouldReturnErrorOnException() {
            ErrorStreamTool tool = new ErrorStreamTool();

            ToolResult result = tool.execute(Map.of());

            assertTrue(result.isError());
            assertTrue(result.getContent().contains("Stream processing failed"));
        }

        @Test
        @DisplayName("execute returns error ToolResult when error chunk is emitted")
        void shouldReturnErrorOnErrorChunk() {
            ErrorChunkStreamTool tool = new ErrorChunkStreamTool();

            ToolResult result = tool.execute(Map.of());

            assertTrue(result.isError());
            assertTrue(result.getContent().contains("Something went wrong"));
        }
    }

    @Nested
    @DisplayName("Tool interface compliance")
    class ToolInterface {

        @Test
        @DisplayName("getName returns tool name")
        void shouldReturnName() {
            assertEquals("counting_stream", new CountingStreamTool().getName());
        }

        @Test
        @DisplayName("getDescription returns description")
        void shouldReturnDescription() {
            assertEquals("Counts from 1 to N with progress",
                new CountingStreamTool().getDescription());
        }

        @Test
        @DisplayName("getSchema returns non-null schema")
        void shouldReturnSchema() {
            assertNotNull(new CountingStreamTool().getSchema());
        }

        @Test
        @DisplayName("isAutoExecute defaults to true")
        void shouldDefaultAutoExecuteTrue() {
            assertTrue(new CountingStreamTool().isAutoExecute());
        }
    }
}
