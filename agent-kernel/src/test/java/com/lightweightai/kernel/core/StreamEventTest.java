package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamEvent - unified streaming event")
class StreamEventTest {

    @Nested
    @DisplayName("Factory methods and event types")
    class FactoryMethods {

        @Test
        void textDelta_createsCorrectEvent() {
            StreamEvent event = StreamEvent.textDelta("hello");

            assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
            assertEquals("hello", event.getTextDelta());
            assertNull(event.getToolCall());
            assertNull(event.getChunk());
            assertNull(event.getResponse());
            assertNull(event.getError());
        }

        @Test
        void textDelta_withMetadata() {
            Map<String, Object> meta = Map.of("provider", "claude");
            StreamEvent event = StreamEvent.textDelta("hi", meta);

            assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
            assertEquals("hi", event.getTextDelta());
            assertEquals("claude", event.getMetadata().get("provider"));
        }

        @Test
        void textDelta_nullMetadataReturnsEmptyMap() {
            StreamEvent event = StreamEvent.textDelta("hi", null);
            assertNotNull(event.getMetadata());
            assertTrue(event.getMetadata().isEmpty());
        }

        @Test
        void toolCallStart_createsCorrectEvent() {
            ToolCall call = new ToolCall("id1", "calculator", "{\"a\":1}");
            StreamEvent event = StreamEvent.toolCallStart(call);

            assertEquals(StreamEvent.EventType.TOOL_CALL_START, event.getType());
            assertSame(call, event.getToolCall());
            assertNull(event.getTextDelta());
        }

        @Test
        void toolProgress_createsCorrectEvent() {
            ToolResultChunk chunk = ToolResultChunk.progress("calc", 50, "halfway");
            StreamEvent event = StreamEvent.toolProgress(chunk);

            assertEquals(StreamEvent.EventType.TOOL_PROGRESS, event.getType());
            assertSame(chunk, event.getChunk());
        }

        @Test
        void toolLog_createsCorrectEvent() {
            ToolResultChunk chunk = ToolResultChunk.log("calc", "info", "computing...");
            StreamEvent event = StreamEvent.toolLog(chunk);

            assertEquals(StreamEvent.EventType.TOOL_LOG, event.getType());
            assertSame(chunk, event.getChunk());
        }

        @Test
        void toolResult_createsCorrectEvent() {
            ToolResultChunk chunk = ToolResultChunk.finalResult("calc", "42");
            StreamEvent event = StreamEvent.toolResult(chunk);

            assertEquals(StreamEvent.EventType.TOOL_RESULT, event.getType());
            assertSame(chunk, event.getChunk());
        }

        @Test
        void toolError_createsCorrectEvent() {
            ToolResultChunk chunk = ToolResultChunk.error("calc", "division by zero");
            StreamEvent event = StreamEvent.toolError(chunk);

            assertEquals(StreamEvent.EventType.TOOL_ERROR, event.getType());
            assertSame(chunk, event.getChunk());
        }

        @Test
        void llmComplete_createsCorrectEvent() {
            LLMResponse response = LLMResponse.builder()
                .content("Final answer")
                .model("claude-3")
                .build();
            StreamEvent event = StreamEvent.llmComplete(response);

            assertEquals(StreamEvent.EventType.LLM_COMPLETE, event.getType());
            assertSame(response, event.getResponse());
        }

        @Test
        void error_createsCorrectEvent() {
            RuntimeException ex = new RuntimeException("oops");
            StreamEvent event = StreamEvent.error(ex);

            assertEquals(StreamEvent.EventType.ERROR, event.getType());
            assertSame(ex, event.getError());
        }

        @Test
        void postProcessData_createsCorrectEvent() {
            Map<String, Object> data = Map.of("card", "info-card");
            StreamEvent event = StreamEvent.postProcessData("card-append", data);

            assertEquals(StreamEvent.EventType.POST_PROCESS_DATA, event.getType());
            assertEquals("card-append", event.getCategory());
            assertEquals("info-card", event.getData().get("card"));
        }

        @Test
        void postProcessData_nullDataReturnsEmptyMap() {
            StreamEvent event = StreamEvent.postProcessData("risk", null);

            assertEquals(StreamEvent.EventType.POST_PROCESS_DATA, event.getType());
            assertNotNull(event.getData());
            assertTrue(event.getData().isEmpty());
        }

        @Test
        void trace_createsCorrectEvent() {
            StreamEvent event = StreamEvent.trace("llm.request", "sending request");

            assertEquals(StreamEvent.EventType.TRACE, event.getType());
            assertEquals("llm.request", event.getTracePhase());
            assertEquals("sending request", event.getTraceMessage());
            assertTrue(event.getTraceTimestamp() > 0);
        }

        @Test
        void trace_withData() {
            Map<String, Object> data = Map.of("tokens", 100);
            StreamEvent event = StreamEvent.trace("llm.response", "received", data);

            assertEquals(StreamEvent.EventType.TRACE, event.getType());
            assertEquals("llm.response", event.getTracePhase());
            assertEquals(100, event.getData().get("tokens"));
        }
    }

    @Nested
    @DisplayName("Metadata immutability")
    class Immutability {

        @Test
        void postProcessData_mapIsUnmodifiable() {
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("key", "value");
            StreamEvent event = StreamEvent.postProcessData("cat", data);

            assertThrows(UnsupportedOperationException.class,
                () -> event.getData().put("new", "entry"));
        }

        @Test
        void textDelta_metadataIsUnmodifiable() {
            Map<String, Object> meta = new java.util.HashMap<>();
            meta.put("key", "value");
            StreamEvent event = StreamEvent.textDelta("hi", meta);

            assertThrows(UnsupportedOperationException.class,
                () -> event.getMetadata().put("new", "entry"));
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        void textDelta_includesTextInToString() {
            StreamEvent event = StreamEvent.textDelta("hello world");
            String str = event.toString();
            assertTrue(str.contains("TEXT_DELTA"));
            assertTrue(str.contains("hello world"));
        }

        @Test
        void error_omitsNullFields() {
            StreamEvent event = StreamEvent.error(new RuntimeException("err"));
            String str = event.toString();
            assertTrue(str.contains("ERROR"));
            assertFalse(str.contains("textDelta"));
        }

        @Test
        void postProcessData_includesCategory() {
            StreamEvent event = StreamEvent.postProcessData("risk", Map.of("level", "high"));
            String str = event.toString();
            assertTrue(str.contains("risk"));
        }
    }
}
