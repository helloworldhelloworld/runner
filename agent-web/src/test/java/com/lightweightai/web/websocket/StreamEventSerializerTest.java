package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StreamEventSerializer 测试 — WebSocket JSON 序列化
 *
 * 覆盖：
 * - TEXT_DELTA → tokenJson
 * - TOOL_CALL_START → tool_call_start JSON
 * - TOOL_PROGRESS → tool_progress JSON
 * - TOOL_LOG → tool_log JSON
 * - TOOL_ERROR → tool_error JSON
 * - ERROR → error JSON
 * - TOOL_RESULT / LLM_COMPLETE → null（不发送）
 * - Orchestrator 事件 → eventWithDataJson
 * - tokenJson / streamEndJson / errorJson 静态方法
 * - POST_PROCESS_DATA → post_process JSON
 * - TRACE → trace JSON
 */
@DisplayName("StreamEventSerializer - WebSocket JSON 序列化")
class StreamEventSerializerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("TEXT_DELTA 序列化为 token JSON")
    void textDeltaSerializesToToken() throws Exception {
        StreamEvent event = StreamEvent.textDelta("Hello");
        String json = StreamEventSerializer.serialize(event);

        assertNotNull(json);
        JsonNode node = MAPPER.readTree(json);
        assertEquals("token", node.get("type").asText());
        assertEquals("Hello", node.get("data").asText());
    }

    @Test
    @DisplayName("TOOL_CALL_START 序列化包含 toolCallId 和 toolName")
    void toolCallStartSerializesCorrectly() throws Exception {
        ToolCall tc = new ToolCall("tc-1", "search", Map.of("q", "test"));
        StreamEvent event = StreamEvent.toolCallStart(tc);
        String json = StreamEventSerializer.serialize(event);

        assertNotNull(json);
        JsonNode node = MAPPER.readTree(json);
        assertEquals("tool_call_start", node.get("type").asText());
        assertEquals("tc-1", node.get("data").get("toolCallId").asText());
        assertEquals("search", node.get("data").get("toolName").asText());
    }

    @Test
    @DisplayName("TOOL_PROGRESS 序列化包含进度信息")
    void toolProgressSerializesCorrectly() throws Exception {
        ToolResultChunk chunk = ToolResultChunk.progress("search", "Searching...", 0.5, 1.0);
        StreamEvent event = StreamEvent.toolProgress(chunk);
        String json = StreamEventSerializer.serialize(event);

        assertNotNull(json);
        JsonNode node = MAPPER.readTree(json);
        assertEquals("tool_progress", node.get("type").asText());
        assertEquals("search", node.get("data").get("toolName").asText());
        assertEquals("Searching...", node.get("data").get("message").asText());
    }

    @Test
    @DisplayName("TOOL_LOG 序列化包含日志内容")
    void toolLogSerializesCorrectly() throws Exception {
        ToolResultChunk chunk = ToolResultChunk.log("calc", "INFO", "Calculating result");
        StreamEvent event = StreamEvent.toolLog(chunk);
        String json = StreamEventSerializer.serialize(event);

        assertNotNull(json);
        JsonNode node = MAPPER.readTree(json);
        assertEquals("tool_log", node.get("type").asText());
        assertEquals("calc", node.get("data").get("toolName").asText());
    }

    @Test
    @DisplayName("TOOL_ERROR 序列化包含错误信息")
    void toolErrorSerializesCorrectly() throws Exception {
        ToolResultChunk chunk = ToolResultChunk.error("broken_tool", "Something failed");
        StreamEvent event = StreamEvent.toolError(chunk);
        String json = StreamEventSerializer.serialize(event);

        assertNotNull(json);
        JsonNode node = MAPPER.readTree(json);
        assertEquals("tool_error", node.get("type").asText());
        assertEquals("broken_tool", node.get("data").get("toolName").asText());
        assertEquals("Something failed", node.get("data").get("message").asText());
    }

    @Test
    @DisplayName("ERROR 序列化包含错误消息")
    void errorSerializesCorrectly() throws Exception {
        StreamEvent event = StreamEvent.error(new RuntimeException("Pipeline failed"));
        String json = StreamEventSerializer.serialize(event);

        assertNotNull(json);
        JsonNode node = MAPPER.readTree(json);
        assertEquals("error", node.get("type").asText());
        assertEquals("Pipeline failed", node.get("message").asText());
    }

    @Test
    @DisplayName("TOOL_RESULT 返回 null（不发送给客户端）")
    void toolResultReturnsNull() {
        ToolResultChunk chunk = ToolResultChunk.complete("tool",
                ToolResult.success("result-id", "some result"));
        StreamEvent event = StreamEvent.toolResult(chunk);
        assertNull(StreamEventSerializer.serialize(event));
    }

    @Test
    @DisplayName("LLM_COMPLETE 返回 null（不发送给客户端）")
    void llmCompleteReturnsNull() {
        StreamEvent event = StreamEvent.llmComplete(LLMResponse.builder()
                .message(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.ASSISTANT)
                        .textContent("done").build())
                .build());
        assertNull(StreamEventSerializer.serialize(event));
    }

    // ==================== Orchestrator 事件 ====================

    @Test
    @DisplayName("AGENT_ROUTE 序列化为 agent_route JSON")
    void agentRouteSerializesCorrectly() throws Exception {
        StreamEvent event = StreamEvent.agentRoute("alpha", "sess-1");
        String json = StreamEventSerializer.serialize(event);

        assertNotNull(json);
        JsonNode node = MAPPER.readTree(json);
        assertEquals("agent_route", node.get("type").asText());
        assertEquals("alpha", node.get("data").get("agentId").asText());
        assertEquals("sess-1", node.get("data").get("sessionId").asText());
    }

    @Test
    @DisplayName("AGENT_INTERRUPT 序列化为 agent_interrupt JSON")
    void agentInterruptSerializesCorrectly() throws Exception {
        StreamEvent event = StreamEvent.agentInterrupt("run-1", "STREAMING", 42);
        String json = StreamEventSerializer.serialize(event);

        assertNotNull(json);
        JsonNode node = MAPPER.readTree(json);
        assertEquals("agent_interrupt", node.get("type").asText());
        assertEquals("run-1", node.get("data").get("runId").asText());
    }

    @Test
    @DisplayName("SUBAGENT_SPAWN 序列化包含 runId/agentId/task")
    void subagentSpawnSerializesCorrectly() throws Exception {
        StreamEvent event = StreamEvent.subagentSpawn("r1", "worker", "search docs");
        String json = StreamEventSerializer.serialize(event);

        assertNotNull(json);
        JsonNode node = MAPPER.readTree(json);
        assertEquals("subagent_spawn", node.get("type").asText());
        assertEquals("r1", node.get("data").get("runId").asText());
        assertEquals("worker", node.get("data").get("agentId").asText());
    }

    @Test
    @DisplayName("SUBAGENT_COMPLETE 序列化包含 durationMs")
    void subagentCompleteSerializesCorrectly() throws Exception {
        StreamEvent event = StreamEvent.subagentComplete("r1", "result text", 1500, 200);
        String json = StreamEventSerializer.serialize(event);

        assertNotNull(json);
        JsonNode node = MAPPER.readTree(json);
        assertEquals("subagent_complete", node.get("type").asText());
        assertEquals(1500, node.get("data").get("durationMs").asInt());
    }

    @Test
    @DisplayName("SUBAGENT_CANCELLED 序列化包含 reason")
    void subagentCancelledSerializesCorrectly() throws Exception {
        StreamEvent event = StreamEvent.subagentCancelled("r1", "parent stopped");
        String json = StreamEventSerializer.serialize(event);

        assertNotNull(json);
        JsonNode node = MAPPER.readTree(json);
        assertEquals("subagent_cancelled", node.get("type").asText());
        assertEquals("parent stopped", node.get("data").get("reason").asText());
    }

    // ==================== 静态方法 ====================

    @Test
    @DisplayName("tokenJson 生成正确格式")
    void tokenJsonFormat() throws Exception {
        String json = StreamEventSerializer.tokenJson("partial");
        JsonNode node = MAPPER.readTree(json);
        assertEquals("token", node.get("type").asText());
        assertEquals("partial", node.get("data").asText());
    }

    @Test
    @DisplayName("streamEndJson 包含 emotion 元数据")
    void streamEndJsonFormat() throws Exception {
        String json = StreamEventSerializer.streamEndJson("happy");
        JsonNode node = MAPPER.readTree(json);
        assertEquals("stream_end", node.get("type").asText());
        assertEquals("happy", node.get("meta").get("emotion").asText());
    }

    @Test
    @DisplayName("streamEndJson emotion 为 null 时输出空字符串")
    void streamEndJsonNullEmotion() throws Exception {
        String json = StreamEventSerializer.streamEndJson(null);
        JsonNode node = MAPPER.readTree(json);
        assertEquals("", node.get("meta").get("emotion").asText());
    }

    @Test
    @DisplayName("errorJson 包含错误消息")
    void errorJsonFormat() throws Exception {
        String json = StreamEventSerializer.errorJson("connection lost");
        JsonNode node = MAPPER.readTree(json);
        assertEquals("error", node.get("type").asText());
        assertEquals("connection lost", node.get("message").asText());
    }

    // ==================== POST_PROCESS_DATA & TRACE ====================

    @Test
    @DisplayName("POST_PROCESS_DATA 序列化包含 category 和 data")
    void postProcessDataSerializesCorrectly() throws Exception {
        StreamEvent event = StreamEvent.postProcessData("emotion", Map.of("value", "happy"));
        String json = StreamEventSerializer.serialize(event);

        assertNotNull(json);
        JsonNode node = MAPPER.readTree(json);
        assertEquals("post_process", node.get("type").asText());
        assertEquals("emotion", node.get("category").asText());
        assertEquals("happy", node.get("data").get("value").asText());
    }

    @Test
    @DisplayName("TRACE 序列化包含 phase 和 message")
    void traceSerializesCorrectly() throws Exception {
        StreamEvent event = StreamEvent.trace("llm.request", "Starting LLM call");
        String json = StreamEventSerializer.serialize(event);

        assertNotNull(json);
        JsonNode node = MAPPER.readTree(json);
        assertEquals("trace", node.get("type").asText());
        assertEquals("llm.request", node.get("data").get("phase").asText());
        assertEquals("Starting LLM call", node.get("data").get("message").asText());
    }
}
