package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamEvent - 全链路统一流式事件")
class StreamEventTest {

    @Test
    @DisplayName("textDelta 创建文本片段事件")
    void textDelta() {
        StreamEvent event = StreamEvent.textDelta("hello");

        assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
        assertEquals("hello", event.getTextDelta());
        assertNull(event.getToolCall());
        assertNull(event.getChunk());
        assertNull(event.getResponse());
        assertNull(event.getError());
    }

    @Test
    @DisplayName("textDelta 带 metadata")
    void textDeltaWithMetadata() {
        Map<String, Object> meta = Map.of("emotion", "happy");
        StreamEvent event = StreamEvent.textDelta("hi", meta);

        assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
        assertEquals("hi", event.getTextDelta());
        assertEquals("happy", event.getMetadata().get("emotion"));
    }

    @Test
    @DisplayName("textDelta null metadata 返回空 map")
    void textDeltaNullMetadata() {
        StreamEvent event = StreamEvent.textDelta("hi", null);

        assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
        assertNotNull(event.getMetadata());
        assertTrue(event.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("toolCallStart 创建工具调用开始事件")
    void toolCallStart() {
        ToolCall call = new ToolCall("call-1", "myTool", java.util.Map.of("a", 1));
        StreamEvent event = StreamEvent.toolCallStart(call);

        assertEquals(StreamEvent.EventType.TOOL_CALL_START, event.getType());
        assertSame(call, event.getToolCall());
        assertNull(event.getTextDelta());
    }

    @Test
    @DisplayName("toolProgress 创建工具进度事件")
    void toolProgress() {
        ToolResultChunk chunk = ToolResultChunk.progress("tool1", "50%", 50, 100);
        StreamEvent event = StreamEvent.toolProgress(chunk);

        assertEquals(StreamEvent.EventType.TOOL_PROGRESS, event.getType());
        assertSame(chunk, event.getChunk());
    }

    @Test
    @DisplayName("toolLog 创建工具日志事件")
    void toolLog() {
        ToolResultChunk chunk = ToolResultChunk.log("tool1", "INFO", "msg");
        StreamEvent event = StreamEvent.toolLog(chunk);

        assertEquals(StreamEvent.EventType.TOOL_LOG, event.getType());
        assertSame(chunk, event.getChunk());
    }

    @Test
    @DisplayName("toolResult 创建工具结果事件")
    void toolResult() {
        ToolResultChunk chunk = ToolResultChunk.complete("tool1",
                com.lightweightai.kernel.llm.ToolResult.success("done"));
        StreamEvent event = StreamEvent.toolResult(chunk);

        assertEquals(StreamEvent.EventType.TOOL_RESULT, event.getType());
        assertSame(chunk, event.getChunk());
    }

    @Test
    @DisplayName("toolError 创建工具错误事件")
    void toolError() {
        ToolResultChunk chunk = ToolResultChunk.error("tool1", "boom");
        StreamEvent event = StreamEvent.toolError(chunk);

        assertEquals(StreamEvent.EventType.TOOL_ERROR, event.getType());
        assertSame(chunk, event.getChunk());
    }

    @Test
    @DisplayName("llmComplete 创建 LLM 完成事件")
    void llmComplete() {
        LLMResponse response = LLMResponse.builder()
                .stopReason("end_turn")
                .build();
        StreamEvent event = StreamEvent.llmComplete(response);

        assertEquals(StreamEvent.EventType.LLM_COMPLETE, event.getType());
        assertSame(response, event.getResponse());
        assertNull(event.getTextDelta());
    }

    @Test
    @DisplayName("error 创建错误事件")
    void error() {
        RuntimeException ex = new RuntimeException("fail");
        StreamEvent event = StreamEvent.error(ex);

        assertEquals(StreamEvent.EventType.ERROR, event.getType());
        assertSame(ex, event.getError());
    }

    @Test
    @DisplayName("postProcessData 创建后处理数据事件")
    void postProcessData() {
        Map<String, Object> data = Map.of("card", "risk-warning");
        StreamEvent event = StreamEvent.postProcessData("risk", data);

        assertEquals(StreamEvent.EventType.POST_PROCESS_DATA, event.getType());
        assertEquals("risk", event.getCategory());
        assertEquals("risk-warning", event.getData().get("card"));
    }

    @Test
    @DisplayName("postProcessData null data 返回空 map")
    void postProcessDataNullData() {
        StreamEvent event = StreamEvent.postProcessData("empty", null);

        assertEquals(StreamEvent.EventType.POST_PROCESS_DATA, event.getType());
        assertNotNull(event.getData());
        assertTrue(event.getData().isEmpty());
    }

    @Test
    @DisplayName("postProcessData 的 data 不可变")
    void postProcessDataImmutable() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("key", "val");
        StreamEvent event = StreamEvent.postProcessData("cat", data);

        assertThrows(UnsupportedOperationException.class,
                () -> event.getData().put("new", "val"));
    }

    @Test
    @DisplayName("trace 创建追踪事件")
    void trace() {
        StreamEvent event = StreamEvent.trace("llm.request", "sending request");

        assertEquals(StreamEvent.EventType.TRACE, event.getType());
        assertEquals("llm.request", event.getTracePhase());
        assertEquals("sending request", event.getTraceMessage());
        assertTrue(event.getTraceTimestamp() > 0);
    }

    @Test
    @DisplayName("trace 带附加数据")
    void traceWithData() {
        Map<String, Object> data = Map.of("model", "claude-3");
        StreamEvent event = StreamEvent.trace("llm.request", "msg", data);

        assertEquals(StreamEvent.EventType.TRACE, event.getType());
        assertEquals("claude-3", event.getData().get("model"));
    }

    @Test
    @DisplayName("getMetadata 无 metadata 返回空 map")
    void getMetadataDefault() {
        StreamEvent event = StreamEvent.textDelta("hi");
        assertNotNull(event.getMetadata());
        assertTrue(event.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("toString 包含类型和关键信息")
    void toStringOutput() {
        StreamEvent textEvent = StreamEvent.textDelta("hello");
        assertTrue(textEvent.toString().contains("TEXT_DELTA"));
        assertTrue(textEvent.toString().contains("hello"));

        StreamEvent postEvent = StreamEvent.postProcessData("risk", Map.of("k", "v"));
        assertTrue(postEvent.toString().contains("POST_PROCESS_DATA"));
        assertTrue(postEvent.toString().contains("risk"));
    }
}
