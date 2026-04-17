package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StreamEventSerializerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonNode parse(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    @Test
    void serializeTextDelta() throws Exception {
        StreamEvent event = StreamEvent.textDelta("hello");
        String json = StreamEventSerializer.serialize(event);
        JsonNode node = parse(json);
        assertEquals("token", node.get("type").asText());
        assertEquals("hello", node.get("data").asText());
    }

    @Test
    void serializeToolCallStart() throws Exception {
        ToolCall tc = new ToolCall("tc-1", "get_weather", Map.of("city", "Beijing"));
        StreamEvent event = StreamEvent.toolCallStart(tc);
        String json = StreamEventSerializer.serialize(event);
        JsonNode node = parse(json);

        assertEquals("tool_call_start", node.get("type").asText());
        JsonNode data = node.get("data");
        assertEquals("tc-1", data.get("toolCallId").asText());
        assertEquals("get_weather", data.get("toolName").asText());
        assertEquals("Beijing", data.get("arguments").get("city").asText());
    }

    @Test
    void serializeToolProgress() throws Exception {
        ToolResultChunk chunk = ToolResultChunk.progress("search", "searching...", 3, 10);
        StreamEvent event = StreamEvent.toolProgress(chunk);
        String json = StreamEventSerializer.serialize(event);
        JsonNode node = parse(json);

        assertEquals("tool_progress", node.get("type").asText());
        JsonNode data = node.get("data");
        assertEquals("search", data.get("toolName").asText());
        assertEquals("searching...", data.get("message").asText());
        assertEquals(3.0, data.get("progress").asDouble());
        assertEquals(10.0, data.get("total").asDouble());
    }

    @Test
    void serializeToolLog() throws Exception {
        ToolResultChunk chunk = ToolResultChunk.log("mcp-tool", "INFO", "Processing data");
        StreamEvent event = StreamEvent.toolLog(chunk);
        String json = StreamEventSerializer.serialize(event);
        JsonNode node = parse(json);

        assertEquals("tool_log", node.get("type").asText());
        assertEquals("mcp-tool", node.get("data").get("toolName").asText());
    }

    @Test
    void serializeToolError() throws Exception {
        ToolResultChunk chunk = ToolResultChunk.error("bad-tool", "Connection failed");
        StreamEvent event = StreamEvent.toolError(chunk);
        String json = StreamEventSerializer.serialize(event);
        JsonNode node = parse(json);

        assertEquals("tool_error", node.get("type").asText());
        assertEquals("bad-tool", node.get("data").get("toolName").asText());
        assertEquals("Connection failed", node.get("data").get("message").asText());
    }

    @Test
    void serializePostProcessData() throws Exception {
        StreamEvent event = StreamEvent.postProcessData("card", Map.of("title", "Weather"));
        String json = StreamEventSerializer.serialize(event);
        JsonNode node = parse(json);

        assertEquals("post_process", node.get("type").asText());
        assertEquals("card", node.get("category").asText());
        assertEquals("Weather", node.get("data").get("title").asText());
    }

    @Test
    void serializeTrace() throws Exception {
        StreamEvent event = StreamEvent.trace("llm.request", "Calling Claude");
        String json = StreamEventSerializer.serialize(event);
        JsonNode node = parse(json);

        assertEquals("trace", node.get("type").asText());
        JsonNode data = node.get("data");
        assertEquals("llm.request", data.get("phase").asText());
        assertEquals("Calling Claude", data.get("message").asText());
        assertTrue(data.get("timestamp").asLong() > 0);
    }

    @Test
    void serializeError() throws Exception {
        StreamEvent event = StreamEvent.error(new RuntimeException("boom"));
        String json = StreamEventSerializer.serialize(event);
        JsonNode node = parse(json);

        assertEquals("error", node.get("type").asText());
        assertEquals("boom", node.get("message").asText());
    }

    @Test
    void serializeErrorWithNullMessage() throws Exception {
        StreamEvent event = StreamEvent.error(new RuntimeException());
        String json = StreamEventSerializer.serialize(event);
        JsonNode node = parse(json);
        assertEquals("error", node.get("type").asText());
    }

    @Test
    void toolResultReturnsNull() {
        StreamEvent event = StreamEvent.toolResult(ToolResultChunk.complete("tool", null));
        assertNull(StreamEventSerializer.serialize(event));
    }

    @Test
    void llmCompleteReturnsNull() {
        StreamEvent event = StreamEvent.llmComplete(null);
        assertNull(StreamEventSerializer.serialize(event));
    }

    @Test
    void serializeAgentRoute() throws Exception {
        StreamEvent event = StreamEvent.agentRoute("comfort-agent", "sess-1");
        String json = StreamEventSerializer.serialize(event);
        JsonNode node = parse(json);

        assertEquals("agent_route", node.get("type").asText());
        assertEquals("comfort-agent", node.get("data").get("agentId").asText());
    }

    @Test
    void serializeAgentInterrupt() throws Exception {
        StreamEvent event = StreamEvent.agentInterrupt("run-1", "llm", 500);
        String json = StreamEventSerializer.serialize(event);
        JsonNode node = parse(json);

        assertEquals("agent_interrupt", node.get("type").asText());
        assertEquals("run-1", node.get("data").get("runId").asText());
    }

    @Test
    void serializeSubagentSpawn() throws Exception {
        StreamEvent event = StreamEvent.subagentSpawn("run-2", "helper", "analyze data");
        String json = StreamEventSerializer.serialize(event);
        JsonNode node = parse(json);

        assertEquals("subagent_spawn", node.get("type").asText());
        assertEquals("run-2", node.get("data").get("runId").asText());
    }

    @Test
    void serializeSubagentComplete() throws Exception {
        StreamEvent event = StreamEvent.subagentComplete("run-2", "done", 1500L, 200);
        String json = StreamEventSerializer.serialize(event);
        JsonNode node = parse(json);

        assertEquals("subagent_complete", node.get("type").asText());
    }

    @Test
    void serializeSubagentError() throws Exception {
        StreamEvent event = StreamEvent.subagentError("run-2", "timeout");
        String json = StreamEventSerializer.serialize(event);
        JsonNode node = parse(json);

        assertEquals("subagent_error", node.get("type").asText());
    }

    @Test
    void serializeSubagentCancelled() throws Exception {
        StreamEvent event = StreamEvent.subagentCancelled("run-2", "parent stopped");
        String json = StreamEventSerializer.serialize(event);
        JsonNode node = parse(json);

        assertEquals("subagent_cancelled", node.get("type").asText());
    }

    @Test
    void tokenJsonHelperProducesValidJson() throws Exception {
        String json = StreamEventSerializer.tokenJson("hi");
        JsonNode node = parse(json);
        assertEquals("token", node.get("type").asText());
        assertEquals("hi", node.get("data").asText());
    }

    @Test
    void streamEndJsonHelperProducesValidJson() throws Exception {
        String json = StreamEventSerializer.streamEndJson("happy");
        JsonNode node = parse(json);
        assertEquals("stream_end", node.get("type").asText());
        assertEquals("happy", node.get("meta").get("emotion").asText());
    }

    @Test
    void streamEndJsonNullEmotionDefaultsToEmpty() throws Exception {
        String json = StreamEventSerializer.streamEndJson(null);
        JsonNode node = parse(json);
        assertEquals("", node.get("meta").get("emotion").asText());
    }

    @Test
    void errorJsonHelperProducesValidJson() throws Exception {
        String json = StreamEventSerializer.errorJson("something broke");
        JsonNode node = parse(json);
        assertEquals("error", node.get("type").asText());
        assertEquals("something broke", node.get("message").asText());
    }
}
