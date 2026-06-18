package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamEventSerializer - all event type coverage")
class StreamEventSerializerAllEventsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Nested
    @DisplayName("TEXT_DELTA")
    class TextDeltaTests {

        @Test
        @DisplayName("serializes to type=token with data containing the delta text")
        void textDeltaSerializesToToken() throws Exception {
            StreamEvent event = StreamEvent.textDelta("Hello world");
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("token", node.get("type").asText());
            assertEquals("Hello world", node.get("data").asText());
        }

        @Test
        @DisplayName("empty delta text produces valid JSON")
        void emptyDeltaProducesValidJson() throws Exception {
            StreamEvent event = StreamEvent.textDelta("");
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("token", node.get("type").asText());
            assertEquals("", node.get("data").asText());
        }
    }

    @Nested
    @DisplayName("TOOL_CALL_START")
    class ToolCallStartTests {

        @Test
        @DisplayName("serializes tool call with id, name, and arguments")
        void toolCallStartSerializesAllFields() throws Exception {
            ToolCall call = new ToolCall("tc-1", "get_weather", Map.of("city", "Beijing"));
            StreamEvent event = StreamEvent.toolCallStart(call);
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("tool_call_start", node.get("type").asText());
            JsonNode data = node.get("data");
            assertEquals("tc-1", data.get("toolCallId").asText());
            assertEquals("get_weather", data.get("toolName").asText());
            assertEquals("Beijing", data.get("arguments").get("city").asText());
        }

        @Test
        @DisplayName("null ToolCall produces empty data object")
        void nullToolCallProducesEmptyData() throws Exception {
            StreamEvent event = StreamEvent.toolCallStart(null);
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("tool_call_start", node.get("type").asText());
            assertNotNull(node.get("data"));
        }
    }

    @Nested
    @DisplayName("TOOL_PROGRESS")
    class ToolProgressTests {

        @Test
        @DisplayName("serializes progress with toolName, message, progress, and total")
        void toolProgressSerializesAllFields() throws Exception {
            ToolResultChunk chunk = ToolResultChunk.progress("search", "Searching...", 0.5, 1.0);
            StreamEvent event = StreamEvent.toolProgress(chunk);
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("tool_progress", node.get("type").asText());
            JsonNode data = node.get("data");
            assertEquals("search", data.get("toolName").asText());
            assertEquals("Searching...", data.get("message").asText());
            assertEquals(0.5, data.get("progress").asDouble(), 0.001);
            assertEquals(1.0, data.get("total").asDouble(), 0.001);
        }
    }

    @Nested
    @DisplayName("TOOL_LOG")
    class ToolLogTests {

        @Test
        @DisplayName("serializes log with toolName, message, and meta")
        void toolLogSerializesAllFields() throws Exception {
            ToolResultChunk chunk = ToolResultChunk.log("fetch", "INFO", "Fetching data");
            StreamEvent event = StreamEvent.toolLog(chunk);
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("tool_log", node.get("type").asText());
            JsonNode data = node.get("data");
            assertEquals("fetch", data.get("toolName").asText());
            assertNotNull(data.get("message"));
        }
    }

    @Nested
    @DisplayName("TOOL_ERROR")
    class ToolErrorTests {

        @Test
        @DisplayName("serializes error with toolName and message")
        void toolErrorSerializesFields() throws Exception {
            ToolResultChunk chunk = ToolResultChunk.error("bad_tool", "timeout after 30s");
            StreamEvent event = StreamEvent.toolError(chunk);
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("tool_error", node.get("type").asText());
            JsonNode data = node.get("data");
            assertEquals("bad_tool", data.get("toolName").asText());
            assertEquals("timeout after 30s", data.get("message").asText());
        }
    }

    @Nested
    @DisplayName("POST_PROCESS_DATA")
    class PostProcessTests {

        @Test
        @DisplayName("serializes with category and data")
        void postProcessSerializesFields() throws Exception {
            Map<String, Object> data = Map.of("risk", "high", "score", 0.9);
            StreamEvent event = StreamEvent.postProcessData("risk_control", data);
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("post_process", node.get("type").asText());
            assertEquals("risk_control", node.get("category").asText());
            assertEquals("high", node.get("data").get("risk").asText());
        }

        @Test
        @DisplayName("null category becomes empty string")
        void nullCategoryBecomesEmpty() throws Exception {
            StreamEvent event = StreamEvent.postProcessData(null, Map.of("key", "val"));
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("", node.get("category").asText());
        }
    }

    @Nested
    @DisplayName("TRACE")
    class TraceTests {

        @Test
        @DisplayName("serializes with phase, message, timestamp, and optional extra data")
        void traceSerializesAllFields() throws Exception {
            Map<String, Object> extra = Map.of("model", "claude-3");
            StreamEvent event = StreamEvent.trace("llm.request", "Calling API", extra);
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("trace", node.get("type").asText());
            JsonNode data = node.get("data");
            assertEquals("llm.request", data.get("phase").asText());
            assertEquals("Calling API", data.get("message").asText());
            assertTrue(data.get("timestamp").asLong() > 0);
            assertEquals("claude-3", data.get("extra").get("model").asText());
        }
    }

    @Nested
    @DisplayName("ERROR")
    class ErrorTests {

        @Test
        @DisplayName("serializes exception message")
        void errorSerializesMessage() throws Exception {
            StreamEvent event = StreamEvent.error(new RuntimeException("connection lost"));
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("error", node.get("type").asText());
            assertEquals("connection lost", node.get("message").asText());
        }

        @Test
        @DisplayName("null error message is serialized (error object exists but has no message)")
        void nullErrorMessageIsSerialized() throws Exception {
            StreamEvent event = StreamEvent.error(new RuntimeException());
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json, "ERROR event should still produce JSON even with null message");
            JsonNode node = MAPPER.readTree(json);
            assertEquals("error", node.get("type").asText());
            // RuntimeException() has null message; the ternary passes null to errorJson
            assertTrue(node.has("message"));
        }
    }

    @Nested
    @DisplayName("Orchestrator events")
    class OrchestratorTests {

        @Test
        @DisplayName("AGENT_ROUTE serializes as agent_route with data and timestamp")
        void agentRouteSerializes() throws Exception {
            StreamEvent event = StreamEvent.agentRoute("email-triage", "sess-1");
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("agent_route", node.get("type").asText());
            assertEquals("email-triage", node.get("data").get("agentId").asText());
            assertTrue(node.get("timestamp").asLong() > 0);
        }

        @Test
        @DisplayName("AGENT_INTERRUPT serializes as agent_interrupt")
        void agentInterruptSerializes() throws Exception {
            StreamEvent event = StreamEvent.agentInterrupt("run-1", "LLM_CALL", 128);
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("agent_interrupt", node.get("type").asText());
        }

        @Test
        @DisplayName("AGENT_RESUME serializes as agent_resume")
        void agentResumeSerializes() throws Exception {
            StreamEvent event = StreamEvent.agentResume("run-1", "new question", 15);
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("agent_resume", node.get("type").asText());
        }
    }

    @Nested
    @DisplayName("Subagent events")
    class SubagentTests {

        @Test
        @DisplayName("SUBAGENT_SPAWN serializes as subagent_spawn")
        void subagentSpawnSerializes() throws Exception {
            StreamEvent event = StreamEvent.subagentSpawn("run-42", "code-assistant", "write tests");
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("subagent_spawn", node.get("type").asText());
            assertEquals("code-assistant", node.get("data").get("agentId").asText());
        }

        @Test
        @DisplayName("SUBAGENT_COMPLETE serializes as subagent_complete")
        void subagentCompleteSerializes() throws Exception {
            StreamEvent event = StreamEvent.subagentComplete("run-42", "done", 3500L, 1200);
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("subagent_complete", node.get("type").asText());
        }

        @Test
        @DisplayName("SUBAGENT_ERROR serializes as subagent_error")
        void subagentErrorSerializes() throws Exception {
            StreamEvent event = StreamEvent.subagentError("run-42", "timeout");
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("subagent_error", node.get("type").asText());
        }

        @Test
        @DisplayName("SUBAGENT_CANCELLED serializes as subagent_cancelled")
        void subagentCancelledSerializes() throws Exception {
            StreamEvent event = StreamEvent.subagentCancelled("run-42", "parent stopped");
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("subagent_cancelled", node.get("type").asText());
        }
    }

    @Nested
    @DisplayName("Static helper methods")
    class HelperMethodTests {

        @Test
        @DisplayName("tokenJson produces valid JSON with type=token")
        void tokenJsonProducesValidOutput() throws Exception {
            String json = StreamEventSerializer.tokenJson("hello");
            JsonNode node = MAPPER.readTree(json);
            assertEquals("token", node.get("type").asText());
            assertEquals("hello", node.get("data").asText());
        }

        @Test
        @DisplayName("streamEndJson includes emotion in meta")
        void streamEndJsonIncludesEmotion() throws Exception {
            String json = StreamEventSerializer.streamEndJson("happy");
            JsonNode node = MAPPER.readTree(json);
            assertEquals("stream_end", node.get("type").asText());
            assertEquals("happy", node.get("meta").get("emotion").asText());
        }

        @Test
        @DisplayName("streamEndJson null emotion becomes empty string")
        void streamEndJsonNullEmotionBecomesEmpty() throws Exception {
            String json = StreamEventSerializer.streamEndJson(null);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("", node.get("meta").get("emotion").asText());
        }

        @Test
        @DisplayName("errorJson produces valid error message JSON")
        void errorJsonProducesValidOutput() throws Exception {
            String json = StreamEventSerializer.errorJson("something broke");
            JsonNode node = MAPPER.readTree(json);
            assertEquals("error", node.get("type").asText());
            assertEquals("something broke", node.get("message").asText());
        }
    }

    @Nested
    @DisplayName("LLM_COMPLETE suppression")
    class LlmCompleteTests {

        @Test
        @DisplayName("LLM_COMPLETE returns null (internal signal, not sent to client)")
        void llmCompleteReturnsNull() {
            StreamEvent event = StreamEvent.llmComplete(
                    com.lightweightai.kernel.llm.LLMResponse.builder()
                            .stopReason("end_turn").build());
            assertNull(StreamEventSerializer.serialize(event));
        }
    }
}
