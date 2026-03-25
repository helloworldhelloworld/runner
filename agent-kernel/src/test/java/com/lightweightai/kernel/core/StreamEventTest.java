package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StreamEvent 单元测试 — 全链路统一流式事件
 *
 * 覆盖：所有工厂方法、字段正确性、不可变性
 */
@DisplayName("StreamEvent - 统一流式事件")
class StreamEventTest {

    @Nested
    @DisplayName("TEXT_DELTA 事件")
    class TextDelta {

        @Test
        @DisplayName("创建文本片段事件")
        void createTextDelta() {
            StreamEvent event = StreamEvent.textDelta("Hello");
            assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
            assertEquals("Hello", event.getTextDelta());
            assertNull(event.getToolCall());
            assertNull(event.getChunk());
            assertNull(event.getResponse());
            assertNull(event.getError());
        }

        @Test
        @DisplayName("创建带 metadata 的文本片段")
        void createTextDeltaWithMetadata() {
            Map<String, Object> meta = Map.of("model", "claude-3");
            StreamEvent event = StreamEvent.textDelta("Hi", meta);
            assertEquals("Hi", event.getTextDelta());
            assertEquals("claude-3", event.getMetadata().get("model"));
        }

        @Test
        @DisplayName("metadata 为 null 时返回空 Map")
        void textDeltaNullMetadataReturnsEmptyMap() {
            StreamEvent event = StreamEvent.textDelta("test", null);
            assertNotNull(event.getMetadata());
            assertTrue(event.getMetadata().isEmpty());
        }

        @Test
        @DisplayName("无 metadata 的 textDelta 返回空 Map")
        void textDeltaNoMetadataReturnsEmptyMap() {
            StreamEvent event = StreamEvent.textDelta("test");
            assertNotNull(event.getMetadata());
            assertTrue(event.getMetadata().isEmpty());
        }
    }

    @Nested
    @DisplayName("TOOL 相关事件")
    class ToolEvents {

        @Test
        @DisplayName("TOOL_CALL_START 包含正确的 ToolCall")
        void toolCallStart() {
            ToolCall call = new ToolCall("tc_1", "get_weather", Map.of("city", "Beijing"));
            StreamEvent event = StreamEvent.toolCallStart(call);
            assertEquals(StreamEvent.EventType.TOOL_CALL_START, event.getType());
            assertEquals("tc_1", event.getToolCall().getId());
            assertEquals("get_weather", event.getToolCall().getName());
            assertNull(event.getTextDelta());
        }

        @Test
        @DisplayName("TOOL_PROGRESS 包含 chunk")
        void toolProgress() {
            ToolResultChunk chunk = ToolResultChunk.progress("search", "Searching...", 0.5, 1.0);
            StreamEvent event = StreamEvent.toolProgress(chunk);
            assertEquals(StreamEvent.EventType.TOOL_PROGRESS, event.getType());
            assertEquals("search", event.getChunk().getToolName());
        }

        @Test
        @DisplayName("TOOL_LOG 包含 chunk")
        void toolLog() {
            ToolResultChunk chunk = ToolResultChunk.log("fetch", "INFO", "Fetching data");
            StreamEvent event = StreamEvent.toolLog(chunk);
            assertEquals(StreamEvent.EventType.TOOL_LOG, event.getType());
            assertNotNull(event.getChunk());
        }

        @Test
        @DisplayName("TOOL_RESULT 包含完成的 chunk")
        void toolResult() {
            ToolResultChunk chunk = ToolResultChunk.complete("calc", ToolResult.success("42"));
            StreamEvent event = StreamEvent.toolResult(chunk);
            assertEquals(StreamEvent.EventType.TOOL_RESULT, event.getType());
            assertEquals(ToolResultChunk.ChunkType.COMPLETE, event.getChunk().getType());
        }

        @Test
        @DisplayName("TOOL_ERROR 包含错误 chunk")
        void toolError() {
            ToolResultChunk chunk = ToolResultChunk.error("broken_tool", "timeout");
            StreamEvent event = StreamEvent.toolError(chunk);
            assertEquals(StreamEvent.EventType.TOOL_ERROR, event.getType());
            assertEquals(ToolResultChunk.ChunkType.ERROR, event.getChunk().getType());
        }
    }

    @Nested
    @DisplayName("LLM_COMPLETE 事件")
    class LlmComplete {

        @Test
        @DisplayName("包含完整 LLM 响应")
        void llmComplete() {
            LLMResponse response = LLMResponse.builder()
                    .stopReason("end_turn")
                    .build();
            StreamEvent event = StreamEvent.llmComplete(response);
            assertEquals(StreamEvent.EventType.LLM_COMPLETE, event.getType());
            assertEquals("end_turn", event.getResponse().getStopReason());
        }
    }

    @Nested
    @DisplayName("ERROR 事件")
    class ErrorEvent {

        @Test
        @DisplayName("包含异常信息")
        void errorEvent() {
            RuntimeException ex = new RuntimeException("connection lost");
            StreamEvent event = StreamEvent.error(ex);
            assertEquals(StreamEvent.EventType.ERROR, event.getType());
            assertEquals("connection lost", event.getError().getMessage());
        }
    }

    @Nested
    @DisplayName("POST_PROCESS_DATA 事件")
    class PostProcessData {

        @Test
        @DisplayName("包含 category 和 data")
        void postProcessData() {
            Map<String, Object> data = Map.of("risk", "high");
            StreamEvent event = StreamEvent.postProcessData("risk_control", data);
            assertEquals(StreamEvent.EventType.POST_PROCESS_DATA, event.getType());
            assertEquals("risk_control", event.getCategory());
            assertEquals("high", event.getData().get("risk"));
        }

        @Test
        @DisplayName("data 为 null 时返回空 Map")
        void postProcessDataNullData() {
            StreamEvent event = StreamEvent.postProcessData("card", null);
            assertNotNull(event.getData());
            assertTrue(event.getData().isEmpty());
        }
    }

    @Nested
    @DisplayName("TRACE 事件")
    class TraceEvent {

        @Test
        @DisplayName("创建追踪事件")
        void traceEvent() {
            StreamEvent event = StreamEvent.trace("llm.request", "Calling Claude API");
            assertEquals(StreamEvent.EventType.TRACE, event.getType());
            assertEquals("llm.request", event.getTracePhase());
            assertEquals("Calling Claude API", event.getTraceMessage());
            assertTrue(event.getTraceTimestamp() > 0);
        }

        @Test
        @DisplayName("带附加数据的追踪事件")
        void traceEventWithData() {
            Map<String, Object> data = Map.of("model", "claude-3", "tokens", 100);
            StreamEvent event = StreamEvent.trace("llm.response", "Done", data);
            assertEquals(StreamEvent.EventType.TRACE, event.getType());
            assertEquals("claude-3", event.getData().get("model"));
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("TEXT_DELTA toString 包含文本")
        void textDeltaToString() {
            StreamEvent event = StreamEvent.textDelta("hello");
            String str = event.toString();
            assertTrue(str.contains("TEXT_DELTA"));
            assertTrue(str.contains("hello"));
        }

        @Test
        @DisplayName("TOOL_CALL_START toString 包含 toolCall")
        void toolCallToString() {
            ToolCall call = new ToolCall("id1", "tool1", Map.of());
            StreamEvent event = StreamEvent.toolCallStart(call);
            String str = event.toString();
            assertTrue(str.contains("TOOL_CALL_START"));
            assertTrue(str.contains("tool1"));
        }
    }
}
