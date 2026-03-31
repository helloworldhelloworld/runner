package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamEvent - 全链路统一流式事件")
class StreamEventTest {

    // ==================== Factory Methods ====================

    @Test
    @DisplayName("textDelta 创建文本片段事件")
    void shouldCreateTextDeltaEvent() {
        StreamEvent event = StreamEvent.textDelta("Hello");

        assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
        assertEquals("Hello", event.getTextDelta());
        assertNull(event.getToolCall());
        assertNull(event.getChunk());
        assertNull(event.getResponse());
        assertNull(event.getError());
        assertNull(event.getCategory());
        assertNull(event.getData());
    }

    @Test
    @DisplayName("textDelta 带 metadata 创建事件")
    void shouldCreateTextDeltaWithMetadata() {
        Map<String, Object> metadata = Map.of("emotion", "happy", "confidence", 0.95);
        StreamEvent event = StreamEvent.textDelta("World", metadata);

        assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
        assertEquals("World", event.getTextDelta());
        assertEquals("happy", event.getMetadata().get("emotion"));
        assertEquals(0.95, event.getMetadata().get("confidence"));
    }

    @Test
    @DisplayName("textDelta 空 metadata 返回空 map")
    void shouldHandleNullMetadata() {
        StreamEvent event = StreamEvent.textDelta("text", null);

        assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
        assertTrue(event.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("textDelta metadata 不可变")
    void shouldMakeMetadataUnmodifiable() {
        Map<String, Object> metadata = Map.of("key", "value");
        StreamEvent event = StreamEvent.textDelta("text", metadata);

        assertThrows(UnsupportedOperationException.class,
            () -> event.getMetadata().put("new", "entry"));
    }

    @Test
    @DisplayName("toolCallStart 创建工具调用开始事件")
    void shouldCreateToolCallStartEvent() {
        ToolCall call = new ToolCall("call-1", "add", Map.of("a", 1, "b", 2));
        StreamEvent event = StreamEvent.toolCallStart(call);

        assertEquals(StreamEvent.EventType.TOOL_CALL_START, event.getType());
        assertNotNull(event.getToolCall());
        assertEquals("add", event.getToolCall().getName());
        assertEquals("call-1", event.getToolCall().getId());
        assertNull(event.getTextDelta());
    }

    @Test
    @DisplayName("toolProgress 创建工具进度事件")
    void shouldCreateToolProgressEvent() {
        ToolResultChunk chunk = ToolResultChunk.progress("tool1", "50%", 50, 100);
        StreamEvent event = StreamEvent.toolProgress(chunk);

        assertEquals(StreamEvent.EventType.TOOL_PROGRESS, event.getType());
        assertNotNull(event.getChunk());
        assertEquals("tool1", event.getChunk().getToolName());
    }

    @Test
    @DisplayName("toolLog 创建工具日志事件")
    void shouldCreateToolLogEvent() {
        ToolResultChunk chunk = ToolResultChunk.log("tool1", "INFO", "processing");
        StreamEvent event = StreamEvent.toolLog(chunk);

        assertEquals(StreamEvent.EventType.TOOL_LOG, event.getType());
        assertNotNull(event.getChunk());
    }

    @Test
    @DisplayName("toolResult 创建工具结果事件")
    void shouldCreateToolResultEvent() {
        ToolResult result = ToolResult.success("done");
        ToolResultChunk chunk = ToolResultChunk.complete("tool1", result);
        StreamEvent event = StreamEvent.toolResult(chunk);

        assertEquals(StreamEvent.EventType.TOOL_RESULT, event.getType());
        assertNotNull(event.getChunk());
        assertEquals(ToolResultChunk.ChunkType.COMPLETE, event.getChunk().getType());
    }

    @Test
    @DisplayName("toolError 创建工具错误事件")
    void shouldCreateToolErrorEvent() {
        ToolResultChunk chunk = ToolResultChunk.error("tool1", "timeout");
        StreamEvent event = StreamEvent.toolError(chunk);

        assertEquals(StreamEvent.EventType.TOOL_ERROR, event.getType());
        assertNotNull(event.getChunk());
        assertEquals("timeout", event.getChunk().getMessage());
    }

    @Test
    @DisplayName("llmComplete 创建 LLM 完成事件")
    void shouldCreateLlmCompleteEvent() {
        LLMResponse response = LLMResponse.builder()
            .message(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("Final answer")
                .build())
            .stopReason("end_turn")
            .build();
        StreamEvent event = StreamEvent.llmComplete(response);

        assertEquals(StreamEvent.EventType.LLM_COMPLETE, event.getType());
        assertNotNull(event.getResponse());
        assertEquals("end_turn", event.getResponse().getStopReason());
    }

    @Test
    @DisplayName("llmComplete 可以传 null response")
    void shouldAllowNullResponseForLlmComplete() {
        StreamEvent event = StreamEvent.llmComplete(null);

        assertEquals(StreamEvent.EventType.LLM_COMPLETE, event.getType());
        assertNull(event.getResponse());
    }

    @Test
    @DisplayName("error 创建管道错误事件")
    void shouldCreateErrorEvent() {
        RuntimeException ex = new RuntimeException("connection lost");
        StreamEvent event = StreamEvent.error(ex);

        assertEquals(StreamEvent.EventType.ERROR, event.getType());
        assertNotNull(event.getError());
        assertEquals("connection lost", event.getError().getMessage());
    }

    @Test
    @DisplayName("postProcessData 创建后处理数据事件")
    void shouldCreatePostProcessDataEvent() {
        Map<String, Object> data = Map.of("cardType", "weather", "city", "Beijing");
        StreamEvent event = StreamEvent.postProcessData("card", data);

        assertEquals(StreamEvent.EventType.POST_PROCESS_DATA, event.getType());
        assertEquals("card", event.getCategory());
        assertEquals("weather", event.getData().get("cardType"));
    }

    @Test
    @DisplayName("postProcessData null data 返回空 map")
    void shouldHandleNullPostProcessData() {
        StreamEvent event = StreamEvent.postProcessData("signal", null);

        assertEquals(StreamEvent.EventType.POST_PROCESS_DATA, event.getType());
        assertNotNull(event.getData());
        assertTrue(event.getData().isEmpty());
    }

    @Test
    @DisplayName("postProcessData data 不可变")
    void shouldMakePostProcessDataUnmodifiable() {
        Map<String, Object> data = Map.of("key", "value");
        StreamEvent event = StreamEvent.postProcessData("cat", data);

        assertThrows(UnsupportedOperationException.class,
            () -> event.getData().put("new", "entry"));
    }

    // ==================== Trace Events ====================

    @Test
    @DisplayName("trace 创建追踪事件")
    void shouldCreateTraceEvent() {
        StreamEvent event = StreamEvent.trace("llm.request", "sending to Claude");

        assertEquals(StreamEvent.EventType.TRACE, event.getType());
        assertEquals("llm.request", event.getTracePhase());
        assertEquals("sending to Claude", event.getTraceMessage());
        assertTrue(event.getTraceTimestamp() > 0);
    }

    @Test
    @DisplayName("trace 带附加数据")
    void shouldCreateTraceEventWithData() {
        Map<String, Object> data = Map.of("model", "claude-sonnet", "tokens", 1024);
        StreamEvent event = StreamEvent.trace("llm.response", "received", data);

        assertEquals(StreamEvent.EventType.TRACE, event.getType());
        assertEquals("llm.response", event.getTracePhase());
        assertNotNull(event.getData());
        assertEquals("claude-sonnet", event.getData().get("model"));
    }

    // ==================== Getters / Defaults ====================

    @Test
    @DisplayName("getMetadata 默认返回空 map")
    void shouldReturnEmptyMetadataByDefault() {
        StreamEvent event = StreamEvent.textDelta("hello");

        assertNotNull(event.getMetadata());
        assertTrue(event.getMetadata().isEmpty());
    }

    // ==================== toString ====================

    @Test
    @DisplayName("toString 包含关键信息")
    void shouldProduceReadableToString() {
        StreamEvent event = StreamEvent.textDelta("Hello");
        String str = event.toString();

        assertTrue(str.contains("TEXT_DELTA"));
        assertTrue(str.contains("Hello"));
    }

    @Test
    @DisplayName("toString 工具事件包含 chunk 信息")
    void shouldIncludeChunkInToString() {
        ToolResultChunk chunk = ToolResultChunk.progress("myTool", "working", 1, 5);
        StreamEvent event = StreamEvent.toolProgress(chunk);
        String str = event.toString();

        assertTrue(str.contains("TOOL_PROGRESS"));
        assertTrue(str.contains("chunk="));
    }

    @Test
    @DisplayName("toString postProcessData 包含 category 和 data")
    void shouldIncludeCategoryAndDataInToString() {
        StreamEvent event = StreamEvent.postProcessData("risk", Map.of("level", "high"));
        String str = event.toString();

        assertTrue(str.contains("POST_PROCESS_DATA"));
        assertTrue(str.contains("risk"));
        assertTrue(str.contains("data="));
    }
}
