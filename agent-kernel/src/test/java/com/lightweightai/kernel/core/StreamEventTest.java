package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StreamEvent - 全链路统一流式事件测试
 *
 * 覆盖所有工厂方法、EventType、getter 和 toString。
 */
@DisplayName("StreamEvent - 全链路统一流式事件")
class StreamEventTest {

    // ==================== TEXT_DELTA ====================

    @Nested
    @DisplayName("TEXT_DELTA 事件")
    class TextDeltaTests {

        @Test
        @DisplayName("创建文本片段事件")
        void shouldCreateTextDelta() {
            StreamEvent event = StreamEvent.textDelta("Hello ");

            assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
            assertEquals("Hello ", event.getTextDelta());
            assertNull(event.getToolCall());
            assertNull(event.getChunk());
            assertNull(event.getResponse());
            assertNull(event.getError());
            assertNull(event.getCategory());
            assertNull(event.getData());
            assertTrue(event.getMetadata().isEmpty());
        }

        @Test
        @DisplayName("创建带 metadata 的文本片段事件")
        void shouldCreateTextDeltaWithMetadata() {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("emotion", "温柔");
            metadata.put("confidence", 0.95);

            StreamEvent event = StreamEvent.textDelta("你好", metadata);

            assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
            assertEquals("你好", event.getTextDelta());
            assertEquals("温柔", event.getMetadata().get("emotion"));
            assertEquals(0.95, event.getMetadata().get("confidence"));
        }

        @Test
        @DisplayName("metadata 为 null 时返回空 map")
        void shouldHandleNullMetadata() {
            StreamEvent event = StreamEvent.textDelta("text", null);

            assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
            assertTrue(event.getMetadata().isEmpty());
        }

        @Test
        @DisplayName("metadata 不可变")
        void shouldReturnUnmodifiableMetadata() {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("key", "value");

            StreamEvent event = StreamEvent.textDelta("text", metadata);

            assertThrows(UnsupportedOperationException.class,
                () -> event.getMetadata().put("new", "entry"));
        }

        @Test
        @DisplayName("null delta 允许创建")
        void shouldAllowNullDelta() {
            StreamEvent event = StreamEvent.textDelta(null);
            assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
            assertNull(event.getTextDelta());
        }
    }

    // ==================== TOOL_CALL_START ====================

    @Nested
    @DisplayName("TOOL_CALL_START 事件")
    class ToolCallStartTests {

        @Test
        @DisplayName("创建工具调用开始事件")
        void shouldCreateToolCallStart() {
            ToolCall toolCall = new ToolCall("tc-1", "weather", Map.of("city", "北京"));

            StreamEvent event = StreamEvent.toolCallStart(toolCall);

            assertEquals(StreamEvent.EventType.TOOL_CALL_START, event.getType());
            assertNotNull(event.getToolCall());
            assertEquals("tc-1", event.getToolCall().getId());
            assertEquals("weather", event.getToolCall().getName());
            assertEquals("北京", event.getToolCall().getArguments().get("city"));
            assertNull(event.getTextDelta());
            assertNull(event.getChunk());
        }
    }

    // ==================== TOOL_PROGRESS ====================

    @Nested
    @DisplayName("TOOL_PROGRESS 事件")
    class ToolProgressTests {

        @Test
        @DisplayName("创建工具进度事件")
        void shouldCreateToolProgress() {
            ToolResultChunk chunk = ToolResultChunk.progress("weather", "Fetching...", 50, 100);

            StreamEvent event = StreamEvent.toolProgress(chunk);

            assertEquals(StreamEvent.EventType.TOOL_PROGRESS, event.getType());
            assertNotNull(event.getChunk());
            assertEquals("weather", event.getChunk().getToolName());
            assertEquals(50, event.getChunk().getProgress());
            assertEquals(100, event.getChunk().getTotal());
        }
    }

    // ==================== TOOL_LOG ====================

    @Nested
    @DisplayName("TOOL_LOG 事件")
    class ToolLogTests {

        @Test
        @DisplayName("创建工具日志事件")
        void shouldCreateToolLog() {
            ToolResultChunk chunk = ToolResultChunk.log("search", "INFO", "Indexing...");

            StreamEvent event = StreamEvent.toolLog(chunk);

            assertEquals(StreamEvent.EventType.TOOL_LOG, event.getType());
            assertNotNull(event.getChunk());
            assertEquals("search", event.getChunk().getToolName());
            assertTrue(event.getChunk().getMessage().contains("INFO"));
        }
    }

    // ==================== TOOL_RESULT ====================

    @Nested
    @DisplayName("TOOL_RESULT 事件")
    class ToolResultTests {

        @Test
        @DisplayName("创建工具结果事件")
        void shouldCreateToolResult() {
            ToolResultChunk chunk = ToolResultChunk.complete("weather", ToolResult.success("Sunny, 25°C"));

            StreamEvent event = StreamEvent.toolResult(chunk);

            assertEquals(StreamEvent.EventType.TOOL_RESULT, event.getType());
            assertNotNull(event.getChunk());
            assertEquals(ToolResultChunk.ChunkType.COMPLETE, event.getChunk().getType());
            assertEquals("Sunny, 25°C", event.getChunk().getResult().getContent());
        }
    }

    // ==================== TOOL_ERROR ====================

    @Nested
    @DisplayName("TOOL_ERROR 事件")
    class ToolErrorTests {

        @Test
        @DisplayName("创建工具错误事件")
        void shouldCreateToolError() {
            ToolResultChunk chunk = ToolResultChunk.error("weather", "API timeout");

            StreamEvent event = StreamEvent.toolError(chunk);

            assertEquals(StreamEvent.EventType.TOOL_ERROR, event.getType());
            assertNotNull(event.getChunk());
            assertEquals(ToolResultChunk.ChunkType.ERROR, event.getChunk().getType());
            assertEquals("API timeout", event.getChunk().getMessage());
        }
    }

    // ==================== LLM_COMPLETE ====================

    @Nested
    @DisplayName("LLM_COMPLETE 事件")
    class LLMCompleteTests {

        @Test
        @DisplayName("创建 LLM 完成事件")
        void shouldCreateLLMComplete() {
            ConversationMessage msg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("Final answer")
                .build();
            LLMResponse response = LLMResponse.builder()
                .message(msg)
                .stopReason("end_turn")
                .build();

            StreamEvent event = StreamEvent.llmComplete(response);

            assertEquals(StreamEvent.EventType.LLM_COMPLETE, event.getType());
            assertNotNull(event.getResponse());
            assertEquals("end_turn", event.getResponse().getStopReason());
            assertEquals("Final answer", event.getResponse().getMessage().getTextContent());
        }

        @Test
        @DisplayName("LLM 完成事件允许 null response")
        void shouldAllowNullResponse() {
            StreamEvent event = StreamEvent.llmComplete(null);

            assertEquals(StreamEvent.EventType.LLM_COMPLETE, event.getType());
            assertNull(event.getResponse());
        }
    }

    // ==================== ERROR ====================

    @Nested
    @DisplayName("ERROR 事件")
    class ErrorTests {

        @Test
        @DisplayName("创建错误事件")
        void shouldCreateError() {
            RuntimeException ex = new RuntimeException("Connection lost");

            StreamEvent event = StreamEvent.error(ex);

            assertEquals(StreamEvent.EventType.ERROR, event.getType());
            assertNotNull(event.getError());
            assertEquals("Connection lost", event.getError().getMessage());
        }
    }

    // ==================== POST_PROCESS_DATA ====================

    @Nested
    @DisplayName("POST_PROCESS_DATA 事件")
    class PostProcessDataTests {

        @Test
        @DisplayName("创建后处理数据事件")
        void shouldCreatePostProcessData() {
            Map<String, Object> data = Map.of("title", "Card Info", "score", 85);

            StreamEvent event = StreamEvent.postProcessData("card", data);

            assertEquals(StreamEvent.EventType.POST_PROCESS_DATA, event.getType());
            assertEquals("card", event.getCategory());
            assertNotNull(event.getData());
            assertEquals("Card Info", event.getData().get("title"));
            assertEquals(85, event.getData().get("score"));
        }

        @Test
        @DisplayName("data 不可变")
        void shouldReturnUnmodifiableData() {
            Map<String, Object> data = new HashMap<>();
            data.put("key", "value");

            StreamEvent event = StreamEvent.postProcessData("cat", data);

            assertThrows(UnsupportedOperationException.class,
                () -> event.getData().put("new", "entry"));
        }

        @Test
        @DisplayName("null data 返回空 map")
        void shouldHandleNullData() {
            StreamEvent event = StreamEvent.postProcessData("cat", null);

            assertEquals(StreamEvent.EventType.POST_PROCESS_DATA, event.getType());
            assertNotNull(event.getData());
            assertTrue(event.getData().isEmpty());
        }
    }

    // ==================== TRACE ====================

    @Nested
    @DisplayName("TRACE 事件")
    class TraceTests {

        @Test
        @DisplayName("创建调用链追踪事件")
        void shouldCreateTrace() {
            StreamEvent event = StreamEvent.trace("gateway.enter", "Request received");

            assertEquals(StreamEvent.EventType.TRACE, event.getType());
            assertEquals("gateway.enter", event.getTracePhase());
            assertEquals("Request received", event.getTraceMessage());
            assertTrue(event.getTraceTimestamp() > 0);
        }

        @Test
        @DisplayName("创建带附加数据的追踪事件")
        void shouldCreateTraceWithData() {
            Map<String, Object> data = Map.of("sessionId", "s1", "latency", 42);

            StreamEvent event = StreamEvent.trace("llm.request", "Calling LLM", data);

            assertEquals(StreamEvent.EventType.TRACE, event.getType());
            assertEquals("llm.request", event.getTracePhase());
            assertNotNull(event.getData());
            assertEquals("s1", event.getData().get("sessionId"));
        }
    }

    // ==================== toString ====================

    @Nested
    @DisplayName("toString 输出")
    class ToStringTests {

        @Test
        @DisplayName("TEXT_DELTA toString 包含类型和文本")
        void shouldFormatTextDelta() {
            StreamEvent event = StreamEvent.textDelta("Hello");
            String str = event.toString();

            assertTrue(str.contains("TEXT_DELTA"));
            assertTrue(str.contains("Hello"));
        }

        @Test
        @DisplayName("TOOL_CALL_START toString 包含工具调用信息")
        void shouldFormatToolCallStart() {
            ToolCall toolCall = new ToolCall("tc-1", "weather", Map.of());
            StreamEvent event = StreamEvent.toolCallStart(toolCall);
            String str = event.toString();

            assertTrue(str.contains("TOOL_CALL_START"));
            assertTrue(str.contains("weather"));
        }

        @Test
        @DisplayName("POST_PROCESS_DATA toString 包含 category 和 data")
        void shouldFormatPostProcessData() {
            StreamEvent event = StreamEvent.postProcessData("risk", Map.of("level", "high"));
            String str = event.toString();

            assertTrue(str.contains("POST_PROCESS_DATA"));
            assertTrue(str.contains("risk"));
        }

        @Test
        @DisplayName("ERROR toString 显示类型")
        void shouldFormatError() {
            StreamEvent event = StreamEvent.error(new RuntimeException("fail"));
            String str = event.toString();

            assertTrue(str.contains("ERROR"));
        }
    }

    // ==================== 事件类型完整性 ====================

    @Test
    @DisplayName("所有 EventType 枚举值都存在")
    void shouldHaveAllEventTypes() {
        StreamEvent.EventType[] types = StreamEvent.EventType.values();

        assertEquals(10, types.length);
        assertNotNull(StreamEvent.EventType.valueOf("TEXT_DELTA"));
        assertNotNull(StreamEvent.EventType.valueOf("TOOL_CALL_START"));
        assertNotNull(StreamEvent.EventType.valueOf("TOOL_PROGRESS"));
        assertNotNull(StreamEvent.EventType.valueOf("TOOL_LOG"));
        assertNotNull(StreamEvent.EventType.valueOf("TOOL_RESULT"));
        assertNotNull(StreamEvent.EventType.valueOf("TOOL_ERROR"));
        assertNotNull(StreamEvent.EventType.valueOf("LLM_COMPLETE"));
        assertNotNull(StreamEvent.EventType.valueOf("ERROR"));
        assertNotNull(StreamEvent.EventType.valueOf("POST_PROCESS_DATA"));
        assertNotNull(StreamEvent.EventType.valueOf("TRACE"));
    }
}
