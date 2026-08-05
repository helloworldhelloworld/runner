package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StreamEvent 工厂方法验证
 *
 * 验证各种 factory method 的载荷构建：data map 结构、不可变性、null 安全性。
 * 这些工厂方法是整个事件管道的入口——下游代码依赖 data map 中的特定 key。
 */
@DisplayName("StreamEvent - 工厂方法载荷验证")
class StreamEventFactoryTest {

    @Test
    @DisplayName("agentRoute 事件包含 agentId 和 sessionId")
    void agentRouteContainsRequiredFields() {
        StreamEvent event = StreamEvent.agentRoute("comfort", "sess-1");
        assertEquals(StreamEvent.EventType.AGENT_ROUTE, event.getType());
        assertEquals("comfort", event.getData().get("agentId"));
        assertEquals("sess-1", event.getData().get("sessionId"));
        assertNotNull(event.getTracePhase());
    }

    @Test
    @DisplayName("agentInterrupt 事件包含 runId、phase、textLength")
    void agentInterruptContainsFields() {
        StreamEvent event = StreamEvent.agentInterrupt("run-1", "STREAMING", 42);
        assertEquals(StreamEvent.EventType.AGENT_INTERRUPT, event.getType());
        assertEquals("run-1", event.getData().get("runId"));
        assertEquals("STREAMING", event.getData().get("phase"));
        assertEquals(42, event.getData().get("textLength"));
    }

    @Test
    @DisplayName("agentResume 事件包含 runId、newInput、contextSize")
    void agentResumeContainsFields() {
        StreamEvent event = StreamEvent.agentResume("run-1", "new question", 5);
        assertEquals(StreamEvent.EventType.AGENT_RESUME, event.getType());
        assertEquals("run-1", event.getData().get("runId"));
        assertEquals("new question", event.getData().get("newInput"));
        assertEquals(5, event.getData().get("contextSize"));
    }

    @Test
    @DisplayName("speechInterrupted 事件包含 runId 和 reason")
    void speechInterruptedContainsFields() {
        StreamEvent event = StreamEvent.speechInterrupted("run-1", "barge-in");
        assertEquals(StreamEvent.EventType.SPEECH_INTERRUPTED, event.getType());
        assertEquals("run-1", event.getData().get("runId"));
        assertEquals("barge-in", event.getData().get("reason"));
    }

    @Test
    @DisplayName("subagentSpawn 事件包含 runId、agentId、task")
    void subagentSpawnContainsFields() {
        StreamEvent event = StreamEvent.subagentSpawn("run-1", "tech", "solve problem");
        assertEquals(StreamEvent.EventType.SUBAGENT_SPAWN, event.getType());
        assertEquals("run-1", event.getData().get("runId"));
        assertEquals("tech", event.getData().get("agentId"));
        assertEquals("solve problem", event.getData().get("task"));
    }

    @Test
    @DisplayName("subagentComplete 事件包含 runId、result、durationMs、tokenUsage")
    void subagentCompleteContainsFields() {
        StreamEvent event = StreamEvent.subagentComplete("run-1", "answer", 1500L, 200);
        assertEquals(StreamEvent.EventType.SUBAGENT_COMPLETE, event.getType());
        assertEquals("run-1", event.getData().get("runId"));
        assertEquals("answer", event.getData().get("result"));
        assertEquals(1500L, event.getData().get("durationMs"));
        assertEquals(200, event.getData().get("tokenUsage"));
    }

    @Test
    @DisplayName("subagentError 事件包含 runId 和 error")
    void subagentErrorContainsFields() {
        StreamEvent event = StreamEvent.subagentError("run-1", "timeout");
        assertEquals(StreamEvent.EventType.SUBAGENT_ERROR, event.getType());
        assertEquals("run-1", event.getData().get("runId"));
        assertEquals("timeout", event.getData().get("error"));
    }

    @Test
    @DisplayName("subagentCancelled 事件包含 runId 和 reason")
    void subagentCancelledContainsFields() {
        StreamEvent event = StreamEvent.subagentCancelled("run-1", "parent stopped");
        assertEquals(StreamEvent.EventType.SUBAGENT_CANCELLED, event.getType());
        assertEquals("run-1", event.getData().get("runId"));
        assertEquals("parent stopped", event.getData().get("reason"));
    }

    @Test
    @DisplayName("textDelta 事件带 metadata")
    void textDeltaWithMetadata() {
        Map<String, Object> meta = Map.of("emotion", "happy");
        StreamEvent event = StreamEvent.textDelta("hi", meta);
        assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
        assertEquals("hi", event.getTextDelta());
        assertEquals("happy", event.getMetadata().get("emotion"));
    }

    @Test
    @DisplayName("speakableChunk 事件带 emotion（存储在 data map 中）")
    void speakableChunkWithEmotion() {
        StreamEvent event = StreamEvent.speakableChunk("Hello world", 0, "excited");
        assertEquals(StreamEvent.EventType.SPEAKABLE_CHUNK, event.getType());
        assertEquals("Hello world", event.getTextDelta());
        assertEquals("excited", event.getData().get("emotion"));
        assertEquals(0, event.getData().get("index"));
        assertEquals("speakable", event.getCategory());
    }

    @Test
    @DisplayName("speakableChunk 默认 emotion 为 neutral（存储在 data map 中）")
    void speakableChunkDefaultEmotion() {
        StreamEvent event = StreamEvent.speakableChunk("text", 1);
        assertEquals("neutral", event.getData().get("emotion"));
        assertEquals(1, event.getData().get("index"));
    }

    @Test
    @DisplayName("postProcessData 的 data map 不可修改")
    void postProcessDataImmutable() {
        StreamEvent event = StreamEvent.postProcessData("risk", Map.of("level", "high"));
        assertThrows(UnsupportedOperationException.class,
                () -> event.getData().put("new_key", "value"));
    }

    @Test
    @DisplayName("error 事件包含 Throwable")
    void errorEventContainsThrowable() {
        RuntimeException ex = new RuntimeException("test");
        StreamEvent event = StreamEvent.error(ex);
        assertEquals(StreamEvent.EventType.ERROR, event.getType());
        assertSame(ex, event.getError());
    }

    @Test
    @DisplayName("trace 事件包含 phase 和 message")
    void traceEventContainsPhaseAndMessage() {
        StreamEvent event = StreamEvent.trace("user.message", "hello");
        assertEquals(StreamEvent.EventType.TRACE, event.getType());
        assertEquals("user.message", event.getTracePhase());
        assertEquals("hello", event.getTraceMessage());
        assertTrue(event.getTraceTimestamp() > 0);
    }

    @Test
    @DisplayName("getData() 对无 data 的事件返回 null（与 getMetadata 不同）")
    void getDataReturnsNullWhenNotSet() {
        StreamEvent event = StreamEvent.textDelta("hi");
        assertNull(event.getData());
    }

    @Test
    @DisplayName("getMetadata() 对 null metadata 返回空 map")
    void getMetadataReturnsEmptyMapForNull() {
        StreamEvent event = StreamEvent.trace("phase", "msg");
        Map<String, Object> meta = event.getMetadata();
        assertNotNull(meta);
    }

    @Test
    @DisplayName("llmComplete 事件包含 LLMResponse")
    void llmCompleteContainsResponse() {
        LLMResponse resp = LLMResponse.builder()
                .message(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.ASSISTANT)
                        .textContent("done")
                        .build())
                .stopReason("end_turn")
                .build();
        StreamEvent event = StreamEvent.llmComplete(resp);
        assertEquals(StreamEvent.EventType.LLM_COMPLETE, event.getType());
        assertNotNull(event.getResponse());
        assertEquals("end_turn", event.getResponse().getStopReason());
    }

    @Test
    @DisplayName("toolCallStart 事件包含 ToolCall")
    void toolCallStartContainsToolCall() {
        ToolCall tc = new ToolCall("id-1", "search", Map.of("q", "test"));
        StreamEvent event = StreamEvent.toolCallStart(tc);
        assertEquals(StreamEvent.EventType.TOOL_CALL_START, event.getType());
        assertSame(tc, event.getToolCall());
    }
}
