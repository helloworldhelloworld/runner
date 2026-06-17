package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StreamEvent lifecycle factory methods — especially the
 * Orchestrator and Subagent events that are newer and critical for
 * the multi-agent path.
 */
@DisplayName("StreamEvent - Lifecycle events")
class StreamEventLifecycleTest {

    @Nested
    @DisplayName("Orchestrator events")
    class OrchestratorEvents {

        @Test
        @DisplayName("agentRoute carries agentId and sessionId")
        void agentRouteCarriesData() {
            StreamEvent event = StreamEvent.agentRoute("comfort-agent", "sess-42");

            assertEquals(StreamEvent.EventType.AGENT_ROUTE, event.getType());
            assertNotNull(event.getData());
            assertEquals("comfort-agent", event.getData().get("agentId"));
            assertEquals("sess-42", event.getData().get("sessionId"));
            assertEquals("comfort-agent", event.getTraceMessage());
        }

        @Test
        @DisplayName("agentInterrupt carries runId, phase, textLength")
        void agentInterruptCarriesData() {
            StreamEvent event = StreamEvent.agentInterrupt("run-1", "streaming", 150);

            assertEquals(StreamEvent.EventType.AGENT_INTERRUPT, event.getType());
            assertEquals("run-1", event.getData().get("runId"));
            assertEquals("streaming", event.getData().get("phase"));
            assertEquals(150, event.getData().get("textLength"));
        }

        @Test
        @DisplayName("agentResume carries runId, newInput, contextSize")
        void agentResumeCarriesData() {
            StreamEvent event = StreamEvent.agentResume("run-1", "actually...", 2048);

            assertEquals(StreamEvent.EventType.AGENT_RESUME, event.getType());
            assertEquals("run-1", event.getData().get("runId"));
            assertEquals("actually...", event.getData().get("newInput"));
            assertEquals(2048, event.getData().get("contextSize"));
        }
    }

    @Nested
    @DisplayName("Subagent events")
    class SubagentEvents {

        @Test
        @DisplayName("subagentSpawn carries runId, agentId, task")
        void subagentSpawnCarriesData() {
            StreamEvent event = StreamEvent.subagentSpawn("sub-1", "research", "find papers");

            assertEquals(StreamEvent.EventType.SUBAGENT_SPAWN, event.getType());
            assertEquals("sub-1", event.getData().get("runId"));
            assertEquals("research", event.getData().get("agentId"));
            assertEquals("find papers", event.getData().get("task"));
        }

        @Test
        @DisplayName("subagentComplete carries result, duration, token usage")
        void subagentCompleteCarriesData() {
            StreamEvent event = StreamEvent.subagentComplete("sub-1", "found 5 papers", 3000L, 1500);

            assertEquals(StreamEvent.EventType.SUBAGENT_COMPLETE, event.getType());
            assertEquals("sub-1", event.getData().get("runId"));
            assertEquals("found 5 papers", event.getData().get("result"));
            assertEquals(3000L, event.getData().get("durationMs"));
            assertEquals(1500, event.getData().get("tokenUsage"));
        }

        @Test
        @DisplayName("subagentError carries runId and error message")
        void subagentErrorCarriesData() {
            StreamEvent event = StreamEvent.subagentError("sub-1", "timeout");

            assertEquals(StreamEvent.EventType.SUBAGENT_ERROR, event.getType());
            assertEquals("sub-1", event.getData().get("runId"));
            assertEquals("timeout", event.getData().get("error"));
        }

        @Test
        @DisplayName("subagentCancelled carries runId and reason")
        void subagentCancelledCarriesData() {
            StreamEvent event = StreamEvent.subagentCancelled("sub-1", "cascade-stop");

            assertEquals(StreamEvent.EventType.SUBAGENT_CANCELLED, event.getType());
            assertEquals("sub-1", event.getData().get("runId"));
            assertEquals("cascade-stop", event.getData().get("reason"));
        }
    }

    @Nested
    @DisplayName("Core events")
    class CoreEvents {

        @Test
        @DisplayName("textDelta with metadata preserves metadata")
        void textDeltaWithMetadata() {
            StreamEvent event = StreamEvent.textDelta("hi", Map.of("emotion", "happy"));
            assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
            assertEquals("hi", event.getTextDelta());
            assertEquals("happy", event.getMetadata().get("emotion"));
        }

        @Test
        @DisplayName("speakableChunk carries text, index, and emotion")
        void speakableChunkCarriesData() {
            StreamEvent event = StreamEvent.speakableChunk("Hello world.", 0, "neutral");
            assertEquals(StreamEvent.EventType.SPEAKABLE_CHUNK, event.getType());
            assertEquals("Hello world.", event.getTextDelta());
            assertEquals("speakable", event.getCategory());
            assertNotNull(event.getData());
            assertEquals("Hello world.", event.getData().get("text"));
            assertEquals(0, event.getData().get("index"));
            assertEquals("neutral", event.getData().get("emotion"));
        }

        @Test
        @DisplayName("speakableChunk default emotion is neutral")
        void speakableChunkDefaultEmotion() {
            StreamEvent event = StreamEvent.speakableChunk("Test.", 1);
            assertEquals("neutral", event.getData().get("emotion"));
        }

        @Test
        @DisplayName("speechInterrupted carries runId and reason")
        void speechInterrupted() {
            StreamEvent event = StreamEvent.speechInterrupted("run-42", "barge-in");
            assertEquals(StreamEvent.EventType.SPEECH_INTERRUPTED, event.getType());
            assertEquals("run-42", event.getData().get("runId"));
            assertEquals("barge-in", event.getData().get("reason"));
        }

        @Test
        @DisplayName("trace event carries phase and message")
        void traceCarriesPhaseAndMessage() {
            StreamEvent event = StreamEvent.trace("llm.request", "sending to claude");
            assertEquals(StreamEvent.EventType.TRACE, event.getType());
            assertEquals("llm.request", event.getTracePhase());
            assertEquals("sending to claude", event.getTraceMessage());
            assertTrue(event.getTraceTimestamp() > 0);
        }

        @Test
        @DisplayName("trace with data carries phase, message, and data")
        void traceWithDataCarriesAll() {
            Map<String, Object> data = Map.of("tokens", 500);
            StreamEvent event = StreamEvent.trace("llm.response", "received", data);
            assertEquals("llm.response", event.getTracePhase());
            assertEquals(500, event.getData().get("tokens"));
        }

        @Test
        @DisplayName("postProcessData carries category and data")
        void postProcessDataCarries() {
            Map<String, Object> data = Map.of("type", "weather_card", "temp", 25);
            StreamEvent event = StreamEvent.postProcessData("card", data);
            assertEquals(StreamEvent.EventType.POST_PROCESS_DATA, event.getType());
            assertEquals("card", event.getCategory());
            assertEquals("weather_card", event.getData().get("type"));
        }

        @Test
        @DisplayName("postProcessData with null data returns empty map")
        void postProcessNullData() {
            StreamEvent event = StreamEvent.postProcessData("signal", null);
            assertNotNull(event.getData());
            assertTrue(event.getData().isEmpty());
        }

        @Test
        @DisplayName("error event carries throwable")
        void errorCarriesThrowable() {
            RuntimeException ex = new RuntimeException("boom");
            StreamEvent event = StreamEvent.error(ex);
            assertEquals(StreamEvent.EventType.ERROR, event.getType());
            assertSame(ex, event.getError());
        }

        @Test
        @DisplayName("llmComplete event carries response")
        void llmCompleteCarriesResponse() {
            LLMResponse resp = LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("done")
                            .build())
                    .stopReason("end_turn")
                    .build();

            StreamEvent event = StreamEvent.llmComplete(resp);
            assertEquals(StreamEvent.EventType.LLM_COMPLETE, event.getType());
            assertSame(resp, event.getResponse());
            assertEquals("end_turn", event.getResponse().getStopReason());
        }
    }

    @Test
    @DisplayName("getMetadata returns empty map when null")
    void getMetadataReturnsEmptyWhenNull() {
        StreamEvent event = StreamEvent.textDelta("hi");
        assertNotNull(event.getMetadata());
        assertTrue(event.getMetadata().isEmpty());
    }
}
