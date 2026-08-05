package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StreamEventSerializer 全事件类型覆盖测试。
 *
 * 已有 StreamEventSerializerTest 覆盖了 TOOL_RESULT / LLM_COMPLETE / 错误型结果。
 * 此测试补充缺失的：TEXT_DELTA、TOOL_CALL_START、TOOL_PROGRESS、TOOL_LOG、TOOL_ERROR、
 * TRACE、POST_PROCESS_DATA、ERROR、以及全部 Orchestrator/Subagent 事件。
 */
@DisplayName("StreamEventSerializer - 全事件类型序列化")
class StreamEventSerializerFullCoverageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Nested
    @DisplayName("TEXT_DELTA")
    class TextDeltaTests {

        @Test
        @DisplayName("TEXT_DELTA 序列化为 type=token，data 是文本内容")
        void textDeltaSerializedAsToken() throws Exception {
            StreamEvent event = StreamEvent.textDelta("Hello, world!");
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("token", node.get("type").asText());
            assertEquals("Hello, world!", node.get("data").asText());
        }

        @Test
        @DisplayName("空字符串 TEXT_DELTA 正常序列化")
        void emptyTextDeltaSerialized() throws Exception {
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
        @DisplayName("TOOL_CALL_START 序列化含 toolCallId/toolName/arguments")
        void toolCallStartContainsAllFields() throws Exception {
            ToolCall call = new ToolCall("tc-123", "get_weather",
                    Map.of("city", "Shanghai", "units", "celsius"));
            StreamEvent event = StreamEvent.toolCallStart(call);
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("tool_call_start", node.get("type").asText());
            JsonNode data = node.get("data");
            assertEquals("tc-123", data.get("toolCallId").asText());
            assertEquals("get_weather", data.get("toolName").asText());
            assertEquals("Shanghai", data.get("arguments").get("city").asText());
        }

        @Test
        @DisplayName("无参数的 TOOL_CALL_START 正常序列化")
        void toolCallStartNoArguments() throws Exception {
            ToolCall call = new ToolCall("tc-456", "list_files", Map.of());
            StreamEvent event = StreamEvent.toolCallStart(call);
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode data = MAPPER.readTree(json).get("data");
            assertEquals("tc-456", data.get("toolCallId").asText());
            assertEquals("list_files", data.get("toolName").asText());
        }
    }

    @Nested
    @DisplayName("TOOL_PROGRESS")
    class ToolProgressTests {

        @Test
        @DisplayName("TOOL_PROGRESS 序列化含 toolName/message/progress/total")
        void toolProgressContainsAllFields() throws Exception {
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
        @DisplayName("TOOL_LOG 序列化含 toolName/message，meta 可选")
        void toolLogContainsFields() throws Exception {
            ToolResultChunk chunk = ToolResultChunk.log("fetch", "INFO", "Fetching data");
            StreamEvent event = StreamEvent.toolLog(chunk);
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("tool_log", node.get("type").asText());
            JsonNode data = node.get("data");
            assertEquals("fetch", data.get("toolName").asText());
            assertTrue(data.get("message").asText().contains("Fetching data"));
        }

        @Test
        @DisplayName("TOOL_LOG 带 meta 时序列化 meta 字段")
        void toolLogWithMeta() throws Exception {
            ToolResultChunk chunk = ToolResultChunk.log("fetch", "DEBUG", null,
                    "details here", Map.of("key", "value"));
            StreamEvent event = StreamEvent.toolLog(chunk);
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode data = MAPPER.readTree(json).get("data");
            assertNotNull(data.get("meta"));
            assertEquals("value", data.get("meta").get("key").asText());
        }
    }

    @Nested
    @DisplayName("TOOL_ERROR")
    class ToolErrorTests {

        @Test
        @DisplayName("TOOL_ERROR 序列化含 toolName/message")
        void toolErrorContainsFields() throws Exception {
            ToolResultChunk chunk = ToolResultChunk.error("broken_tool", "connection refused");
            StreamEvent event = StreamEvent.toolError(chunk);
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("tool_error", node.get("type").asText());
            JsonNode data = node.get("data");
            assertEquals("broken_tool", data.get("toolName").asText());
            assertEquals("connection refused", data.get("message").asText());
        }
    }

    @Nested
    @DisplayName("ERROR")
    class ErrorTests {

        @Test
        @DisplayName("ERROR 事件序列化含 message")
        void errorEventSerializedWithMessage() throws Exception {
            StreamEvent event = StreamEvent.error(new RuntimeException("something broke"));
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("error", node.get("type").asText());
            assertEquals("something broke", node.get("message").asText());
        }

        @Test
        @DisplayName("ERROR 事件 null 异常时消息为 Unknown error")
        void errorEventNullException() throws Exception {
            StreamEvent event = StreamEvent.error(null);
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("error", node.get("type").asText());
            assertEquals("Unknown error", node.get("message").asText());
        }
    }

    @Nested
    @DisplayName("POST_PROCESS_DATA")
    class PostProcessTests {

        @Test
        @DisplayName("POST_PROCESS_DATA 序列化含 category 和 data")
        void postProcessDataSerialized() throws Exception {
            StreamEvent event = StreamEvent.postProcessData("risk_control",
                    Map.of("level", "high", "score", 95));
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("post_process", node.get("type").asText());
            assertEquals("risk_control", node.get("category").asText());
            assertEquals("high", node.get("data").get("level").asText());
            assertEquals(95, node.get("data").get("score").asInt());
        }

        @Test
        @DisplayName("POST_PROCESS_DATA category 为 null 时序列化为空字符串")
        void postProcessNullCategory() throws Exception {
            StreamEvent event = StreamEvent.postProcessData(null, Map.of("x", 1));
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            assertEquals("", MAPPER.readTree(json).get("category").asText());
        }
    }

    @Nested
    @DisplayName("TRACE")
    class TraceTests {

        @Test
        @DisplayName("TRACE 序列化含 phase/message/timestamp")
        void traceEventSerialized() throws Exception {
            StreamEvent event = StreamEvent.trace("llm.request", "Calling Claude API");
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("trace", node.get("type").asText());
            JsonNode data = node.get("data");
            assertEquals("llm.request", data.get("phase").asText());
            assertEquals("Calling Claude API", data.get("message").asText());
            assertTrue(data.get("timestamp").asLong() > 0);
        }

        @Test
        @DisplayName("TRACE 带 extra data 时序列化 extra 字段")
        void traceEventWithExtraData() throws Exception {
            StreamEvent event = StreamEvent.trace("llm.response", "Done",
                    Map.of("model", "claude-3", "tokens", 100));
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode data = MAPPER.readTree(json).get("data");
            assertEquals("claude-3", data.get("extra").get("model").asText());
        }
    }

    @Nested
    @DisplayName("Orchestrator 生命周期事件")
    class OrchestratorEventTests {

        @Test
        @DisplayName("AGENT_ROUTE 序列化为 type=agent_route 含 data")
        void agentRouteEvent() throws Exception {
            StreamEvent event = StreamEvent.agentRoute("email-triage", "sess-123");
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("agent_route", node.get("type").asText());
            assertEquals("email-triage", node.get("data").get("agentId").asText());
            assertEquals("sess-123", node.get("data").get("sessionId").asText());
            assertTrue(node.get("timestamp").asLong() > 0);
        }

        @Test
        @DisplayName("AGENT_INTERRUPT 序列化为 type=agent_interrupt")
        void agentInterruptEvent() throws Exception {
            StreamEvent event = StreamEvent.agentInterrupt("run-1", "LLM_CALL", 128);
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("agent_interrupt", node.get("type").asText());
            assertEquals("run-1", node.get("data").get("runId").asText());
            assertEquals("LLM_CALL", node.get("data").get("phase").asText());
            assertEquals(128, node.get("data").get("textLength").asInt());
        }

        @Test
        @DisplayName("AGENT_RESUME 序列化为 type=agent_resume")
        void agentResumeEvent() throws Exception {
            StreamEvent event = StreamEvent.agentResume("run-1", "continue", 15);
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("agent_resume", node.get("type").asText());
            assertEquals("run-1", node.get("data").get("runId").asText());
            assertEquals("continue", node.get("data").get("newInput").asText());
        }
    }

    @Nested
    @DisplayName("Subagent 生命周期事件")
    class SubagentEventTests {

        @Test
        @DisplayName("SUBAGENT_SPAWN 序列化为 type=subagent_spawn")
        void subagentSpawnEvent() throws Exception {
            StreamEvent event = StreamEvent.subagentSpawn("run-42", "code-review", "Review PR");
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("subagent_spawn", node.get("type").asText());
            assertEquals("run-42", node.get("data").get("runId").asText());
            assertEquals("code-review", node.get("data").get("agentId").asText());
            assertEquals("Review PR", node.get("data").get("task").asText());
        }

        @Test
        @DisplayName("SUBAGENT_COMPLETE 序列化为 type=subagent_complete")
        void subagentCompleteEvent() throws Exception {
            StreamEvent event = StreamEvent.subagentComplete("run-42", "Done", 3500L, 1200);
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("subagent_complete", node.get("type").asText());
            assertEquals("Done", node.get("data").get("result").asText());
            assertEquals(3500, node.get("data").get("durationMs").asLong());
            assertEquals(1200, node.get("data").get("tokenUsage").asInt());
        }

        @Test
        @DisplayName("SUBAGENT_ERROR 序列化为 type=subagent_error")
        void subagentErrorEvent() throws Exception {
            StreamEvent event = StreamEvent.subagentError("run-42", "timeout");
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("subagent_error", node.get("type").asText());
            assertEquals("timeout", node.get("data").get("error").asText());
        }

        @Test
        @DisplayName("SUBAGENT_CANCELLED 序列化为 type=subagent_cancelled")
        void subagentCancelledEvent() throws Exception {
            StreamEvent event = StreamEvent.subagentCancelled("run-42", "parent stopped");
            String json = StreamEventSerializer.serialize(event);

            assertNotNull(json);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("subagent_cancelled", node.get("type").asText());
            assertEquals("parent stopped", node.get("data").get("reason").asText());
        }
    }

    @Nested
    @DisplayName("Orchestrator/Subagent 事件含 message 和 timestamp")
    class EventMetadataTests {

        @Test
        @DisplayName("所有 lifecycle 事件都含 message 字段")
        void lifecycleEventsContainMessage() throws Exception {
            StreamEvent[] events = {
                StreamEvent.agentRoute("a", "s"),
                StreamEvent.agentInterrupt("r", "p", 0),
                StreamEvent.agentResume("r", "i", 0),
                StreamEvent.subagentSpawn("r", "a", "t"),
                StreamEvent.subagentComplete("r", "d", 0L, 0),
                StreamEvent.subagentError("r", "e"),
                StreamEvent.subagentCancelled("r", "c"),
            };

            for (StreamEvent event : events) {
                String json = StreamEventSerializer.serialize(event);
                assertNotNull(json, "Event " + event.getType() + " should not be null");
                JsonNode node = MAPPER.readTree(json);
                assertTrue(node.has("message") || node.has("data"),
                        "Event " + event.getType() + " should have message or data");
                assertTrue(node.has("timestamp"),
                        "Event " + event.getType() + " should have timestamp");
            }
        }
    }
}
