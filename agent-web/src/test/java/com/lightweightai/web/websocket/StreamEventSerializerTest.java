package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamEventSerializer - WebSocket JSON 序列化")
class StreamEventSerializerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonNode parse(String json) throws Exception {
        assertNotNull(json);
        return MAPPER.readTree(json);
    }

    @Nested
    @DisplayName("TEXT_DELTA 事件")
    class TextDeltaTests {

        @Test
        @DisplayName("序列化文本片段为 token 类型")
        void shouldSerializeTextDelta() throws Exception {
            StreamEvent event = StreamEvent.textDelta("Hello world");
            JsonNode json = parse(StreamEventSerializer.serialize(event));

            assertEquals("token", json.get("type").asText());
            assertEquals("Hello world", json.get("data").asText());
        }

        @Test
        @DisplayName("空字符串文本片段")
        void shouldHandleEmptyText() throws Exception {
            StreamEvent event = StreamEvent.textDelta("");
            JsonNode json = parse(StreamEventSerializer.serialize(event));

            assertEquals("token", json.get("type").asText());
            assertEquals("", json.get("data").asText());
        }
    }

    @Nested
    @DisplayName("TOOL_CALL_START 事件")
    class ToolCallStartTests {

        @Test
        @DisplayName("序列化工具调用开始事件")
        void shouldSerializeToolCallStart() throws Exception {
            ToolCall call = new ToolCall("tc_1", "web_search", Map.of("query", "test"));
            StreamEvent event = StreamEvent.toolCallStart(call);
            JsonNode json = parse(StreamEventSerializer.serialize(event));

            assertEquals("tool_call_start", json.get("type").asText());
            JsonNode data = json.get("data");
            assertEquals("tc_1", data.get("toolCallId").asText());
            assertEquals("web_search", data.get("toolName").asText());
            assertEquals("test", data.get("arguments").get("query").asText());
        }

        @Test
        @DisplayName("ToolCall 为 null 时序列化空 data")
        void shouldHandleNullToolCall() throws Exception {
            StreamEvent event = StreamEvent.toolCallStart(null);
            JsonNode json = parse(StreamEventSerializer.serialize(event));

            assertEquals("tool_call_start", json.get("type").asText());
            assertNotNull(json.get("data"));
        }
    }

    @Nested
    @DisplayName("TOOL_PROGRESS 事件")
    class ToolProgressTests {

        @Test
        @DisplayName("序列化工具进度事件")
        void shouldSerializeToolProgress() throws Exception {
            ToolResultChunk chunk = ToolResultChunk.progress("web_search", "Fetching...", 0.5, 1.0);
            StreamEvent event = StreamEvent.toolProgress(chunk);
            JsonNode json = parse(StreamEventSerializer.serialize(event));

            assertEquals("tool_progress", json.get("type").asText());
            JsonNode data = json.get("data");
            assertEquals("web_search", data.get("toolName").asText());
            assertEquals("Fetching...", data.get("message").asText());
            assertEquals(0.5, data.get("progress").asDouble(), 0.001);
            assertEquals(1.0, data.get("total").asDouble(), 0.001);
        }
    }

    @Nested
    @DisplayName("TOOL_LOG 事件")
    class ToolLogTests {

        @Test
        @DisplayName("序列化工具日志事件")
        void shouldSerializeToolLog() throws Exception {
            ToolResultChunk chunk = ToolResultChunk.log("calc", "INFO", "Computing...");
            StreamEvent event = StreamEvent.toolLog(chunk);
            JsonNode json = parse(StreamEventSerializer.serialize(event));

            assertEquals("tool_log", json.get("type").asText());
            JsonNode data = json.get("data");
            assertEquals("calc", data.get("toolName").asText());
            assertTrue(data.get("message").asText().contains("Computing..."));
        }

        @Test
        @DisplayName("带 meta 的工具日志事件")
        void shouldSerializeToolLogWithMeta() throws Exception {
            ToolResultChunk chunk = ToolResultChunk.log("calc", "DEBUG", "logger1",
                    "step done", Map.of("step", 3));
            StreamEvent event = StreamEvent.toolLog(chunk);
            JsonNode json = parse(StreamEventSerializer.serialize(event));

            assertEquals("tool_log", json.get("type").asText());
            JsonNode data = json.get("data");
            assertNotNull(data.get("meta"));
            assertEquals(3, data.get("meta").get("step").asInt());
        }
    }

    @Nested
    @DisplayName("TOOL_ERROR 事件")
    class ToolErrorTests {

        @Test
        @DisplayName("序列化工具错误事件")
        void shouldSerializeToolError() throws Exception {
            ToolResultChunk chunk = ToolResultChunk.error("web_search", "Connection timeout");
            StreamEvent event = StreamEvent.toolError(chunk);
            JsonNode json = parse(StreamEventSerializer.serialize(event));

            assertEquals("tool_error", json.get("type").asText());
            JsonNode data = json.get("data");
            assertEquals("web_search", data.get("toolName").asText());
            assertEquals("Connection timeout", data.get("message").asText());
        }
    }

    @Nested
    @DisplayName("POST_PROCESS_DATA 事件")
    class PostProcessTests {

        @Test
        @DisplayName("序列化后处理数据事件")
        void shouldSerializePostProcessData() throws Exception {
            StreamEvent event = StreamEvent.postProcessData("card", Map.of("title", "Weather", "temp", 25));
            JsonNode json = parse(StreamEventSerializer.serialize(event));

            assertEquals("post_process", json.get("type").asText());
            assertEquals("card", json.get("category").asText());
            assertEquals("Weather", json.get("data").get("title").asText());
            assertEquals(25, json.get("data").get("temp").asInt());
        }

        @Test
        @DisplayName("category 为 null 时使用空字符串")
        void shouldHandleNullCategory() throws Exception {
            StreamEvent event = StreamEvent.postProcessData(null, Map.of("k", "v"));
            JsonNode json = parse(StreamEventSerializer.serialize(event));

            assertEquals("post_process", json.get("type").asText());
            assertEquals("", json.get("category").asText());
        }
    }

    @Nested
    @DisplayName("TRACE 事件")
    class TraceTests {

        @Test
        @DisplayName("序列化调用链追踪事件")
        void shouldSerializeTrace() throws Exception {
            StreamEvent event = StreamEvent.trace("llm.request", "Calling Claude API");
            JsonNode json = parse(StreamEventSerializer.serialize(event));

            assertEquals("trace", json.get("type").asText());
            JsonNode data = json.get("data");
            assertEquals("llm.request", data.get("phase").asText());
            assertEquals("Calling Claude API", data.get("message").asText());
            assertTrue(data.get("timestamp").asLong() > 0);
        }

        @Test
        @DisplayName("带附加数据的 trace")
        void shouldSerializeTraceWithExtraData() throws Exception {
            StreamEvent event = StreamEvent.trace("tool.execute", "Running", Map.of("toolName", "calc"));
            JsonNode json = parse(StreamEventSerializer.serialize(event));

            assertEquals("trace", json.get("type").asText());
            JsonNode data = json.get("data");
            assertNotNull(data.get("extra"));
            assertEquals("calc", data.get("extra").get("toolName").asText());
        }
    }

    @Nested
    @DisplayName("ERROR 事件")
    class ErrorTests {

        @Test
        @DisplayName("序列化错误事件")
        void shouldSerializeError() throws Exception {
            StreamEvent event = StreamEvent.error(new RuntimeException("Something broke"));
            JsonNode json = parse(StreamEventSerializer.serialize(event));

            assertEquals("error", json.get("type").asText());
            assertEquals("Something broke", json.get("message").asText());
        }

        @Test
        @DisplayName("error 为 null 时使用 Unknown error")
        void shouldHandleNullError() throws Exception {
            StreamEvent event = StreamEvent.error(null);
            JsonNode json = parse(StreamEventSerializer.serialize(event));

            assertEquals("error", json.get("type").asText());
            assertEquals("Unknown error", json.get("message").asText());
        }
    }

    @Nested
    @DisplayName("Orchestrator 生命周期事件")
    class OrchestratorTests {

        @Test
        @DisplayName("AGENT_ROUTE 事件")
        void shouldSerializeAgentRoute() throws Exception {
            StreamEvent event = StreamEvent.agentRoute("default", "sess-123");
            JsonNode json = parse(StreamEventSerializer.serialize(event));

            assertEquals("agent_route", json.get("type").asText());
            assertEquals("default", json.get("data").get("agentId").asText());
            assertEquals("sess-123", json.get("data").get("sessionId").asText());
        }

        @Test
        @DisplayName("AGENT_INTERRUPT 事件")
        void shouldSerializeAgentInterrupt() throws Exception {
            StreamEvent event = StreamEvent.agentInterrupt("run-1", "RUNNING", 150);
            JsonNode json = parse(StreamEventSerializer.serialize(event));

            assertEquals("agent_interrupt", json.get("type").asText());
            assertEquals("run-1", json.get("data").get("runId").asText());
            assertEquals("RUNNING", json.get("data").get("phase").asText());
            assertEquals(150, json.get("data").get("textLength").asInt());
        }

        @Test
        @DisplayName("AGENT_RESUME 事件")
        void shouldSerializeAgentResume() throws Exception {
            StreamEvent event = StreamEvent.agentResume("run-1", "continue please", 500);
            JsonNode json = parse(StreamEventSerializer.serialize(event));

            assertEquals("agent_resume", json.get("type").asText());
            assertEquals("run-1", json.get("data").get("runId").asText());
        }
    }

    @Nested
    @DisplayName("Subagent 生命周期事件")
    class SubagentTests {

        @Test
        @DisplayName("SUBAGENT_SPAWN 事件")
        void shouldSerializeSubagentSpawn() throws Exception {
            StreamEvent event = StreamEvent.subagentSpawn("run-sub-1", "worker", "Analyze data");
            JsonNode json = parse(StreamEventSerializer.serialize(event));

            assertEquals("subagent_spawn", json.get("type").asText());
            assertEquals("run-sub-1", json.get("data").get("runId").asText());
            assertEquals("worker", json.get("data").get("agentId").asText());
            assertEquals("Analyze data", json.get("data").get("task").asText());
        }

        @Test
        @DisplayName("SUBAGENT_COMPLETE 事件")
        void shouldSerializeSubagentComplete() throws Exception {
            StreamEvent event = StreamEvent.subagentComplete("run-sub-1", "Done", 1500L, 200);
            JsonNode json = parse(StreamEventSerializer.serialize(event));

            assertEquals("subagent_complete", json.get("type").asText());
            assertEquals("run-sub-1", json.get("data").get("runId").asText());
        }

        @Test
        @DisplayName("SUBAGENT_ERROR 事件")
        void shouldSerializeSubagentError() throws Exception {
            StreamEvent event = StreamEvent.subagentError("run-sub-1", "OOM");
            JsonNode json = parse(StreamEventSerializer.serialize(event));

            assertEquals("subagent_error", json.get("type").asText());
            assertEquals("run-sub-1", json.get("data").get("runId").asText());
            assertEquals("OOM", json.get("data").get("error").asText());
        }

        @Test
        @DisplayName("SUBAGENT_CANCELLED 事件")
        void shouldSerializeSubagentCancelled() throws Exception {
            StreamEvent event = StreamEvent.subagentCancelled("run-sub-1", "parent stopped");
            JsonNode json = parse(StreamEventSerializer.serialize(event));

            assertEquals("subagent_cancelled", json.get("type").asText());
            assertEquals("run-sub-1", json.get("data").get("runId").asText());
            assertEquals("parent stopped", json.get("data").get("reason").asText());
        }
    }

    @Nested
    @DisplayName("过滤规则")
    class FilterTests {

        @Test
        @DisplayName("TOOL_RESULT 不推送给客户端")
        void shouldFilterToolResult() {
            ToolResultChunk chunk = ToolResultChunk.complete("calc",
                    com.lightweightai.kernel.llm.ToolResult.success("tc-1", "42"));
            StreamEvent event = StreamEvent.toolResult(chunk);
            assertNull(StreamEventSerializer.serialize(event));
        }

        @Test
        @DisplayName("LLM_COMPLETE 不推送给客户端")
        void shouldFilterLlmComplete() {
            LLMResponse response = LLMResponse.builder()
                    .message(com.lightweightai.kernel.llm.ConversationMessage.builder()
                            .role(com.lightweightai.kernel.llm.ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("done")
                            .build())
                    .build();
            StreamEvent event = StreamEvent.llmComplete(response);
            assertNull(StreamEventSerializer.serialize(event));
        }
    }

    @Nested
    @DisplayName("辅助方法")
    class HelperTests {

        @Test
        @DisplayName("tokenJson 独立调用")
        void shouldCreateTokenJson() throws Exception {
            String json = StreamEventSerializer.tokenJson("hi");
            JsonNode node = MAPPER.readTree(json);
            assertEquals("token", node.get("type").asText());
            assertEquals("hi", node.get("data").asText());
        }

        @Test
        @DisplayName("streamEndJson 包含 emotion")
        void shouldCreateStreamEndJson() throws Exception {
            String json = StreamEventSerializer.streamEndJson("happy");
            JsonNode node = MAPPER.readTree(json);
            assertEquals("stream_end", node.get("type").asText());
            assertEquals("happy", node.get("meta").get("emotion").asText());
        }

        @Test
        @DisplayName("streamEndJson emotion 为 null 时用空字符串")
        void shouldHandleNullEmotion() throws Exception {
            String json = StreamEventSerializer.streamEndJson(null);
            JsonNode node = MAPPER.readTree(json);
            assertEquals("", node.get("meta").get("emotion").asText());
        }

        @Test
        @DisplayName("errorJson 独立调用")
        void shouldCreateErrorJson() throws Exception {
            String json = StreamEventSerializer.errorJson("oops");
            JsonNode node = MAPPER.readTree(json);
            assertEquals("error", node.get("type").asText());
            assertEquals("oops", node.get("message").asText());
        }
    }
}
