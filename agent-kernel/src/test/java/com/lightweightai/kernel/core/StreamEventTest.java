package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamEvent - 全链路统一流式事件")
class StreamEventTest {

    @Test
    @DisplayName("textDelta 创建与属性")
    void textDelta() {
        StreamEvent event = StreamEvent.textDelta("hello");

        assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
        assertEquals("hello", event.getTextDelta());
        assertNull(event.getToolCall());
        assertNull(event.getChunk());
        assertNull(event.getResponse());
        assertNull(event.getError());
        assertNull(event.getCategory());
        assertNull(event.getData());
        assertTrue(event.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("textDelta 带 metadata")
    void textDeltaWithMetadata() {
        Map<String, Object> meta = Map.of("confidence", 0.9);
        StreamEvent event = StreamEvent.textDelta("world", meta);

        assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
        assertEquals("world", event.getTextDelta());
        assertEquals(0.9, event.getMetadata().get("confidence"));
    }

    @Test
    @DisplayName("textDelta 带 null metadata 返回空 map")
    void textDeltaNullMetadata() {
        StreamEvent event = StreamEvent.textDelta("test", null);
        assertNotNull(event.getMetadata());
        assertTrue(event.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("toolCallStart 事件")
    void toolCallStart() {
        ToolCall call = new ToolCall("call-1", "weather", Map.of("city", "Beijing"));
        StreamEvent event = StreamEvent.toolCallStart(call);

        assertEquals(StreamEvent.EventType.TOOL_CALL_START, event.getType());
        assertEquals(call, event.getToolCall());
        assertNull(event.getTextDelta());
    }

    @Test
    @DisplayName("toolProgress 事件")
    void toolProgress() {
        ToolResultChunk chunk = ToolResultChunk.progress("tool1", "50%", 50, 100);
        StreamEvent event = StreamEvent.toolProgress(chunk);

        assertEquals(StreamEvent.EventType.TOOL_PROGRESS, event.getType());
        assertEquals(chunk, event.getChunk());
    }

    @Test
    @DisplayName("toolLog 事件")
    void toolLog() {
        ToolResultChunk chunk = ToolResultChunk.log("tool1", "INFO", "log msg");
        StreamEvent event = StreamEvent.toolLog(chunk);

        assertEquals(StreamEvent.EventType.TOOL_LOG, event.getType());
        assertEquals(chunk, event.getChunk());
    }

    @Test
    @DisplayName("toolResult 事件")
    void toolResult() {
        ToolResult result = ToolResult.success("done");
        ToolResultChunk chunk = ToolResultChunk.complete("tool1", result);
        StreamEvent event = StreamEvent.toolResult(chunk);

        assertEquals(StreamEvent.EventType.TOOL_RESULT, event.getType());
        assertEquals(chunk, event.getChunk());
    }

    @Test
    @DisplayName("toolError 事件")
    void toolError() {
        ToolResultChunk chunk = ToolResultChunk.error("tool1", "failed");
        StreamEvent event = StreamEvent.toolError(chunk);

        assertEquals(StreamEvent.EventType.TOOL_ERROR, event.getType());
        assertEquals(chunk, event.getChunk());
    }

    @Test
    @DisplayName("llmComplete 事件")
    void llmComplete() {
        LLMResponse response = LLMResponse.builder()
                .content("final answer")
                .build();
        StreamEvent event = StreamEvent.llmComplete(response);

        assertEquals(StreamEvent.EventType.LLM_COMPLETE, event.getType());
        assertEquals(response, event.getResponse());
        assertNull(event.getTextDelta());
    }

    @Test
    @DisplayName("error 事件")
    void errorEvent() {
        RuntimeException ex = new RuntimeException("boom");
        StreamEvent event = StreamEvent.error(ex);

        assertEquals(StreamEvent.EventType.ERROR, event.getType());
        assertEquals(ex, event.getError());
    }

    @Test
    @DisplayName("postProcessData 事件")
    void postProcessData() {
        Map<String, Object> data = Map.of("card", "weather-card");
        StreamEvent event = StreamEvent.postProcessData("card", data);

        assertEquals(StreamEvent.EventType.POST_PROCESS_DATA, event.getType());
        assertEquals("card", event.getCategory());
        assertEquals("weather-card", event.getData().get("card"));
    }

    @Test
    @DisplayName("postProcessData null data 返回空 map")
    void postProcessDataNullData() {
        StreamEvent event = StreamEvent.postProcessData("empty", null);
        assertNotNull(event.getData());
        assertTrue(event.getData().isEmpty());
    }

    @Test
    @DisplayName("trace 事件")
    void traceEvent() {
        StreamEvent event = StreamEvent.trace("llm.request", "calling claude");

        assertEquals(StreamEvent.EventType.TRACE, event.getType());
        assertEquals("llm.request", event.getTracePhase());
        assertEquals("calling claude", event.getTraceMessage());
        assertTrue(event.getTraceTimestamp() > 0);
    }

    @Test
    @DisplayName("trace 带数据事件")
    void traceWithData() {
        Map<String, Object> data = Map.of("tokens", 100);
        StreamEvent event = StreamEvent.trace("llm.response", "done", data);

        assertEquals(StreamEvent.EventType.TRACE, event.getType());
        assertEquals("llm.response", event.getTracePhase());
        assertEquals(100, event.getData().get("tokens"));
    }

    @Test
    @DisplayName("toString 包含关键信息")
    void toStringOutput() {
        StreamEvent event = StreamEvent.textDelta("hello");
        String str = event.toString();
        assertTrue(str.contains("TEXT_DELTA"));
        assertTrue(str.contains("hello"));
    }

    @Test
    @DisplayName("postProcessData data 不可修改")
    void postProcessDataImmutable() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("key", "value");
        StreamEvent event = StreamEvent.postProcessData("cat", data);

        assertThrows(UnsupportedOperationException.class, () ->
                event.getData().put("new", "entry"));
    }

    @Test
    @DisplayName("textDelta metadata 不可修改")
    void textDeltaMetadataImmutable() {
        Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("key", "value");
        StreamEvent event = StreamEvent.textDelta("x", meta);

        assertThrows(UnsupportedOperationException.class, () ->
                event.getMetadata().put("new", "entry"));
    }
}
