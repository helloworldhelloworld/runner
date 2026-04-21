package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamEventSerializer - StreamEvent → WebSocket JSON 序列化")
class StreamEventSerializerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("TEXT_DELTA 序列化为 token 消息")
    void textDeltaSerializesToToken() throws Exception {
        StreamEvent event = StreamEvent.textDelta("Hello");
        String json = StreamEventSerializer.serialize(event);

        JsonNode node = MAPPER.readTree(json);
        assertEquals("token", node.get("type").asText());
        assertEquals("Hello", node.get("data").asText());
    }

    @Test
    @DisplayName("TOOL_CALL_START 序列化为 tool_call_start 消息")
    void toolCallStartSerializesToToolCallStart() throws Exception {
        ToolCall call = new ToolCall("tc-1", "search", Map.of("query", "test"));
        StreamEvent event = StreamEvent.toolCallStart(call);
        String json = StreamEventSerializer.serialize(event);

        JsonNode node = MAPPER.readTree(json);
        assertEquals("tool_call_start", node.get("type").asText());
        assertEquals("tc-1", node.get("data").get("toolCallId").asText());
        assertEquals("search", node.get("data").get("toolName").asText());
    }

    @Test
    @DisplayName("TOOL_PROGRESS 序列化为 tool_progress 消息")
    void toolProgressSerializesToToolProgress() throws Exception {
        ToolResultChunk chunk = ToolResultChunk.progress("search", "Searching...", 50, 100);
        StreamEvent event = StreamEvent.toolProgress(chunk);
        String json = StreamEventSerializer.serialize(event);

        JsonNode node = MAPPER.readTree(json);
        assertEquals("tool_progress", node.get("type").asText());
        assertEquals("search", node.get("data").get("toolName").asText());
        assertEquals("Searching...", node.get("data").get("message").asText());
    }

    @Test
    @DisplayName("TOOL_ERROR 序列化为 tool_error 消息")
    void toolErrorSerializesToToolError() throws Exception {
        ToolResultChunk chunk = ToolResultChunk.error("calc", "Division by zero");
        StreamEvent event = StreamEvent.toolError(chunk);
        String json = StreamEventSerializer.serialize(event);

        JsonNode node = MAPPER.readTree(json);
        assertEquals("tool_error", node.get("type").asText());
        assertEquals("calc", node.get("data").get("toolName").asText());
        assertEquals("Division by zero", node.get("data").get("message").asText());
    }

    @Test
    @DisplayName("ERROR 序列化为 error 消息")
    void errorSerializesToError() throws Exception {
        StreamEvent event = StreamEvent.error(new RuntimeException("something broke"));
        String json = StreamEventSerializer.serialize(event);

        JsonNode node = MAPPER.readTree(json);
        assertEquals("error", node.get("type").asText());
        assertEquals("something broke", node.get("message").asText());
    }

    @Test
    @DisplayName("TOOL_RESULT 不发送给客户端(返回 null)")
    void toolResultReturnsNull() {
        ToolResultChunk chunk = ToolResultChunk.complete("search",
                com.lightweightai.kernel.llm.ToolResult.success("found it"));
        StreamEvent event = StreamEvent.toolResult(chunk);

        assertNull(StreamEventSerializer.serialize(event));
    }

    @Test
    @DisplayName("LLM_COMPLETE 不发送给客户端(返回 null)")
    void llmCompleteReturnsNull() {
        LLMResponse response = LLMResponse.builder()
                .stopReason("end_turn")
                .build();
        StreamEvent event = StreamEvent.llmComplete(response);

        assertNull(StreamEventSerializer.serialize(event));
    }

    @Test
    @DisplayName("POST_PROCESS_DATA 序列化为 post_process 消息")
    void postProcessDataSerializesToPostProcess() throws Exception {
        StreamEvent event = StreamEvent.postProcessData("card",
                Map.of("title", "test card"));
        String json = StreamEventSerializer.serialize(event);

        JsonNode node = MAPPER.readTree(json);
        assertEquals("post_process", node.get("type").asText());
        assertEquals("card", node.get("category").asText());
        assertEquals("test card", node.get("data").get("title").asText());
    }

    @Test
    @DisplayName("TRACE 序列化为 trace 消息")
    void traceSerializesToTrace() throws Exception {
        StreamEvent event = StreamEvent.trace("llm.request", "Calling Claude");
        String json = StreamEventSerializer.serialize(event);

        JsonNode node = MAPPER.readTree(json);
        assertEquals("trace", node.get("type").asText());
        assertEquals("llm.request", node.get("data").get("phase").asText());
        assertEquals("Calling Claude", node.get("data").get("message").asText());
    }

    @Test
    @DisplayName("SUBAGENT_SPAWN 序列化为 subagent_spawn 消息")
    void subagentSpawnSerializesToSubagentSpawn() throws Exception {
        StreamEvent event = StreamEvent.subagentSpawn("run-1", "worker", "search docs");
        String json = StreamEventSerializer.serialize(event);

        JsonNode node = MAPPER.readTree(json);
        assertEquals("subagent_spawn", node.get("type").asText());
        assertEquals("run-1", node.get("data").get("runId").asText());
    }

    @Test
    @DisplayName("AGENT_ROUTE 序列化为 agent_route 消息")
    void agentRouteSerializesToAgentRoute() throws Exception {
        StreamEvent event = StreamEvent.agentRoute("comfort", "session-1");
        String json = StreamEventSerializer.serialize(event);

        JsonNode node = MAPPER.readTree(json);
        assertEquals("agent_route", node.get("type").asText());
        assertEquals("comfort", node.get("data").get("agentId").asText());
    }

    @Test
    @DisplayName("tokenJson 静态方法产生正确 JSON")
    void tokenJsonProducesCorrectJson() throws Exception {
        String json = StreamEventSerializer.tokenJson("world");
        JsonNode node = MAPPER.readTree(json);

        assertEquals("token", node.get("type").asText());
        assertEquals("world", node.get("data").asText());
    }

    @Test
    @DisplayName("streamEndJson 包含 emotion meta")
    void streamEndJsonContainsEmotion() throws Exception {
        String json = StreamEventSerializer.streamEndJson("happy");
        JsonNode node = MAPPER.readTree(json);

        assertEquals("stream_end", node.get("type").asText());
        assertEquals("happy", node.get("meta").get("emotion").asText());
    }

    @Test
    @DisplayName("streamEndJson emotion 为 null 时使用空字符串")
    void streamEndJsonNullEmotionUsesEmptyString() throws Exception {
        String json = StreamEventSerializer.streamEndJson(null);
        JsonNode node = MAPPER.readTree(json);

        assertEquals("", node.get("meta").get("emotion").asText());
    }

    @Test
    @DisplayName("errorJson 产生 error 类型消息")
    void errorJsonProducesErrorMessage() throws Exception {
        String json = StreamEventSerializer.errorJson("timeout");
        JsonNode node = MAPPER.readTree(json);

        assertEquals("error", node.get("type").asText());
        assertEquals("timeout", node.get("message").asText());
    }
}
