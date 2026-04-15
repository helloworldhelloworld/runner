package com.lightweightai.kernel.core;

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
 * StreamEvent - 不可变性 & null 安全性 补充测试
 *
 * 已有 StreamEventTest 覆盖工厂方法正确性
 * 本测试补充：
 * - metadata 不可变性验证（修改原始 Map 不影响事件）
 * - postProcessData 的 data 不可变性
 * - metadata 返回值不可变性（修改返回值不影响事件）
 * - getMetadata() 对 null 的一致处理
 * - 各种 null 字段的 getter 安全性
 * - ToolResultChunk 的 withToolCallId 不可变性
 */
@DisplayName("StreamEvent - 不可变性 & Null 安全")
class StreamEventImmutabilityTest {

    @Nested
    @DisplayName("metadata 不可变性")
    class MetadataImmutability {

        @Test
        @DisplayName("textDelta metadata 包裹后不可修改")
        void textDeltaMetadataShouldBeUnmodifiable() {
            Map<String, Object> original = new HashMap<>();
            original.put("key", "value");
            StreamEvent event = StreamEvent.textDelta("test", original);

            // 修改原始 Map 不应影响事件
            original.put("newKey", "newValue");
            assertFalse(event.getMetadata().containsKey("newKey"),
                "Modifying original map should not affect event metadata");

            // 事件的 metadata 不可修改
            assertThrows(UnsupportedOperationException.class, () ->
                event.getMetadata().put("another", "value"),
                "Event metadata should be unmodifiable");
        }

        @Test
        @DisplayName("postProcessData 的 data 包裹后不可修改")
        void postProcessDataShouldBeUnmodifiable() {
            Map<String, Object> original = new HashMap<>();
            original.put("cardType", "weather");
            StreamEvent event = StreamEvent.postProcessData("card", original);

            // 修改原始 Map 不应影响事件
            original.put("extra", "data");
            assertFalse(event.getData().containsKey("extra"),
                "Modifying original map should not affect event data");

            // 事件的 data 不可修改
            assertThrows(UnsupportedOperationException.class, () ->
                event.getData().put("another", "value"),
                "Event data should be unmodifiable");
        }
    }

    @Nested
    @DisplayName("getMetadata null 一致性")
    class MetadataNullConsistency {

        @Test
        @DisplayName("textDelta(text) 无 metadata 返回空 Map")
        void textDeltaWithoutMetadataReturnsEmptyMap() {
            StreamEvent event = StreamEvent.textDelta("test");
            assertNotNull(event.getMetadata());
            assertTrue(event.getMetadata().isEmpty());
        }

        @Test
        @DisplayName("textDelta(text, null) 返回空 Map")
        void textDeltaWithNullMetadataReturnsEmptyMap() {
            StreamEvent event = StreamEvent.textDelta("test", null);
            assertNotNull(event.getMetadata());
            assertTrue(event.getMetadata().isEmpty());
        }

        @Test
        @DisplayName("toolCallStart 的 metadata 返回空 Map")
        void toolCallStartMetadataReturnsEmptyMap() {
            ToolCall call = new ToolCall("tc1", "tool", Map.of());
            StreamEvent event = StreamEvent.toolCallStart(call);
            assertNotNull(event.getMetadata());
            assertTrue(event.getMetadata().isEmpty());
        }

        @Test
        @DisplayName("llmComplete 的 metadata 返回空 Map")
        void llmCompleteMetadataReturnsEmptyMap() {
            StreamEvent event = StreamEvent.llmComplete(null);
            assertNotNull(event.getMetadata());
            assertTrue(event.getMetadata().isEmpty());
        }

        @Test
        @DisplayName("error 的 metadata 返回空 Map")
        void errorMetadataReturnsEmptyMap() {
            StreamEvent event = StreamEvent.error(new RuntimeException("err"));
            assertNotNull(event.getMetadata());
            assertTrue(event.getMetadata().isEmpty());
        }

        @Test
        @DisplayName("trace 的 metadata 返回空 Map")
        void traceMetadataReturnsEmptyMap() {
            StreamEvent event = StreamEvent.trace("phase", "msg");
            assertNotNull(event.getMetadata());
            assertTrue(event.getMetadata().isEmpty());
        }
    }

    @Nested
    @DisplayName("Null 字段 getter 安全性")
    class NullFieldSafety {

        @Test
        @DisplayName("textDelta 事件的非相关字段为 null")
        void textDeltaNullFields() {
            StreamEvent event = StreamEvent.textDelta("test");
            assertNull(event.getToolCall());
            assertNull(event.getChunk());
            assertNull(event.getResponse());
            assertNull(event.getError());
            assertNull(event.getCategory());
            assertNull(event.getData());
            assertNull(event.getTracePhase());
            assertNull(event.getTraceMessage());
            assertEquals(0, event.getTraceTimestamp());
        }

        @Test
        @DisplayName("toolCallStart 事件的非相关字段为 null")
        void toolCallStartNullFields() {
            StreamEvent event = StreamEvent.toolCallStart(new ToolCall("tc1", "t", Map.of()));
            assertNull(event.getTextDelta());
            assertNull(event.getChunk());
            assertNull(event.getResponse());
            assertNull(event.getError());
        }

        @Test
        @DisplayName("error 事件的非相关字段为 null")
        void errorNullFields() {
            StreamEvent event = StreamEvent.error(new RuntimeException("err"));
            assertNull(event.getTextDelta());
            assertNull(event.getToolCall());
            assertNull(event.getChunk());
            assertNull(event.getResponse());
        }

        @Test
        @DisplayName("llmComplete(null) response 为 null 安全")
        void llmCompleteNullResponse() {
            StreamEvent event = StreamEvent.llmComplete(null);
            assertNull(event.getResponse());
            assertEquals(StreamEvent.EventType.LLM_COMPLETE, event.getType());
        }
    }

    @Nested
    @DisplayName("ToolResultChunk 不可变性")
    class ToolResultChunkImmutability {

        @Test
        @DisplayName("withToolCallId 返回新实例不修改原始")
        void withToolCallIdReturnsNewInstance() {
            ToolResultChunk original = ToolResultChunk.complete("tool1",
                ToolResult.success("result"));
            assertNull(original.getToolCallId());

            ToolResultChunk withId = original.withToolCallId("tc1");
            assertEquals("tc1", withId.getToolCallId());
            assertNull(original.getToolCallId(), "Original should not be modified");
        }

        @Test
        @DisplayName("progress chunk 包含正确的进度信息")
        void progressChunkFields() {
            ToolResultChunk chunk = ToolResultChunk.progress("tool1", "Loading", 50, 100);
            assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunk.getType());
            assertEquals("tool1", chunk.getToolName());
            assertEquals("Loading", chunk.getMessage());
            assertEquals(50, chunk.getProgress());
            assertEquals(100, chunk.getTotal());
        }

        @Test
        @DisplayName("log chunk 包含格式化的消息")
        void logChunkFormatting() {
            ToolResultChunk chunk = ToolResultChunk.log("tool1", "WARN", "myLogger", "message", null);
            assertEquals(ToolResultChunk.ChunkType.LOG, chunk.getType());
            assertTrue(chunk.getMessage().contains("WARN"));
            assertTrue(chunk.getMessage().contains("myLogger"));
            assertTrue(chunk.getMessage().contains("message"));
        }

        @Test
        @DisplayName("log chunk 无 logger 时格式正确")
        void logChunkWithoutLogger() {
            ToolResultChunk chunk = ToolResultChunk.log("tool1", "INFO", "simple message");
            assertEquals(ToolResultChunk.ChunkType.LOG, chunk.getType());
            assertTrue(chunk.getMessage().contains("INFO"));
            assertTrue(chunk.getMessage().contains("simple message"));
        }

        @Test
        @DisplayName("error chunk 包含异常")
        void errorChunkHasException() {
            ToolResultChunk chunk = ToolResultChunk.error("tool1", "timeout");
            assertEquals(ToolResultChunk.ChunkType.ERROR, chunk.getType());
            assertEquals("timeout", chunk.getMessage());
            assertNotNull(chunk.getError());
            assertTrue(chunk.getError() instanceof RuntimeException);
        }

        @Test
        @DisplayName("complete chunk 包含 ToolResult")
        void completeChunkHasResult() {
            ToolResult result = ToolResult.success("answer");
            ToolResultChunk chunk = ToolResultChunk.complete("tool1", result);
            assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunk.getType());
            assertNotNull(chunk.getResult());
            assertEquals("answer", chunk.getResult().getContent());
        }

        @Test
        @DisplayName("toString 包含关键信息")
        void toStringContainsInfo() {
            ToolResultChunk chunk = ToolResultChunk.progress("myTool", "Working", 30, 100)
                .withToolCallId("tc_123");
            String str = chunk.toString();
            assertTrue(str.contains("PROGRESS"));
            assertTrue(str.contains("myTool"));
            assertTrue(str.contains("tc_123"));
        }
    }
}
