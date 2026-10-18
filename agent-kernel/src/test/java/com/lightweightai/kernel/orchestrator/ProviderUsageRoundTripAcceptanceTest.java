package com.lightweightai.kernel.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.llm.claude.ClaudeProvider;
import com.lightweightai.kernel.llm.openrouter.OpenRouterProvider;
import com.lightweightai.kernel.memory.InMemoryProvider;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP/SSE and native assembly; only the model service is controlled. */
class ProviderUsageRoundTripAcceptanceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<ConversationMessage> INPUT = List.of(ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER).textContent("actual input").build());

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void openRouterUsageAfterFinishReachesTheFinalConsumerOnce(boolean emptyChoices) throws Exception {
        try (var remote = new MockWebServer()) {
            remote.start();
            String choices = emptyChoices ? "[]" : "[{\"delta\":{},\"finish_reason\":\"stop\"}]";
            remote.enqueue(sse("data: {\"choices\":[{\"delta\":{\"content\":\"actual reply\"},\"finish_reason\":\"stop\"}]}\n\n"
                    + "data: {\"choices\":" + choices + ",\"usage\":{\"prompt_tokens\":41,\"completion_tokens\":9}}\n\n"
                    + "data: [DONE]\n\n"));
            var events = router(remote).completeStreamReactive(INPUT, LLMOptions.builder().maxTokens(70).build())
                    .collectList().block(Duration.ofSeconds(30));
            var completions = events.stream().filter(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE).toList();
            assertEquals(1, completions.size());
            var response = completions.getFirst().getResponse();
            assertEquals("actual reply", response.getMessage().getTextContent());
            assertNotNull(response.getUsage(), "The final SSE usage frame was lost");
            assertEquals(41, response.getUsage().getInputTokens());
            assertEquals(9, response.getUsage().getOutputTokens());
            var request = JSON.readTree(remote.takeRequest(5, TimeUnit.SECONDS).getBody().readUtf8());
            assertEquals(70, request.path("max_tokens").asInt());
            assertEquals("actual input", request.path("messages").get(0).path("content").asText());
        }
    }

    @Test void claudeUsageUsesFinalCumulativeOutputAndPreservesStopReason() throws Exception {
        try (var remote = new MockWebServer()) {
            remote.start();
            remote.enqueue(sse("data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":25,\"output_tokens\":1}}}\n\n"
                    + "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"actual reply\"}}\n\n"
                    + "data: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":5}}\n\n"
                    + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":15}}\n\n"
                    + "data: {\"type\":\"message_stop\"}\n\n"));
            var response = claude(remote).completeStreamReactive(INPUT, LLMOptions.builder().maxTokens(70).build())
                    .filter(event -> event.getType() == StreamEvent.EventType.LLM_COMPLETE)
                    .blockLast(Duration.ofSeconds(30)).getResponse();
            assertEquals("actual reply", response.getMessage().getTextContent());
            assertNotNull(response.getUsage(), "message_start/message_delta usage was lost");
            assertEquals(25, response.getUsage().getInputTokens());
            assertEquals(15, response.getUsage().getOutputTokens(), "Cumulative frames must not be added together");
            assertEquals("end_turn", response.getStopReason());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"openrouter", "claude"})
    void http200StreamErrorIsAConsumerFailure(String kind) throws Exception {
        try (var remote = new MockWebServer()) {
            remote.start();
            String event = kind.equals("claude")
                    ? "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"controlled-stream-error\"}}"
                    : "{\"error\":{\"code\":529,\"message\":\"controlled-stream-error\"}}";
            remote.enqueue(sse("data: " + event + "\n\n"));
            LLMProvider provider = kind.equals("claude") ? claude(remote) : router(remote);
            RuntimeException failure = assertThrows(RuntimeException.class, () -> provider
                    .completeStreamReactive(INPUT, LLMOptions.builder().build()).blockLast(Duration.ofSeconds(30)));
            assertTrue(failure.toString().contains("controlled-stream-error"), failure.toString());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"openrouter", "claude"})
    void incompleteUsageStaysUnknownAndErrorsAfterTextStillFail(String kind) throws Exception {
        try (var remote = new MockWebServer()) {
            remote.start();
            boolean anthropic = kind.equals("claude");
            String partial = anthropic
                    ? "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":7}}}\n\n"
                    : "data: {\"usage\":{\"prompt_tokens\":7},\"choices\":[]}\n\n";
            String text = anthropic
                    ? "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"partial reply\"}}\n\n"
                    : "data: {\"choices\":[{\"delta\":{\"content\":\"partial reply\"}}]}\n\n";
            String end = anthropic ? "data: {\"type\":\"message_stop\"}\n\n" : "data: [DONE]\n\n";
            remote.enqueue(sse(partial + text + end));
            var provider = anthropic ? claude(remote) : router(remote);
            var completion = provider.completeStreamReactive(INPUT, LLMOptions.builder().build())
                    .filter(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE).blockLast(Duration.ofSeconds(30));
            assertEquals("partial reply", completion.getResponse().getMessage().getTextContent());
            assertNull(completion.getResponse().getUsage(), "Partial usage must not fabricate a known zero output");
            remote.enqueue(sse(text + "data: {\"type\":\"error\",\"error\":{\"message\":\"late controlled error\"}}\n\n"));
            var received = new java.util.concurrent.CopyOnWriteArrayList<StreamEvent>();
            var failure = assertThrows(RuntimeException.class, () -> provider.completeStreamReactive(INPUT,
                    LLMOptions.builder().build()).doOnNext(received::add).blockLast(Duration.ofSeconds(30)));
            assertTrue(failure.toString().contains("late controlled error"));
            assertTrue(received.stream().anyMatch(e -> "partial reply".equals(e.getTextDelta())));
            assertTrue(received.stream().noneMatch(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void nativeNextRequestCompactsLargeToolResultsAndKeepsTheirCallIdentity(boolean reactive) throws Exception {
        try (var remote = new MockWebServer()) {
            remote.start();
            remote.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                    {"choices":[{"message":{"role":"assistant","content":"","tool_calls":[
                    {"id":"large-call","type":"function","function":{"name":"large","arguments":"{}"}}]},
                    "finish_reason":"tool_calls"}],"usage":{"prompt_tokens":25,"completion_tokens":10}}
                    """));
            remote.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                    {"choices":[{"message":{"role":"assistant","content":"done"},"finish_reason":"stop"}],
                    "usage":{"prompt_tokens":40,"completion_tokens":5}}
                    """));
            var tools = new ToolRegistry();
            tools.register(new Tool() {
                public String getName() { return "large"; }
                public String getDescription() { return "Read a large result"; }
                public ToolSchema getSchema() { return ToolSchema.empty(); }
                public ToolResult execute(Map<String, Object> args) { return ToolResult.success("x".repeat(30_000)); }
            });
            var agent = new AgentFactory(router(remote), new InMemoryProvider(), tools)
                    .create(AgentProfile.builder().agentId("worker").build());
            if (reactive) agent.runReactive("use large", "session").collectList().block(Duration.ofSeconds(30));
            else assertEquals("done", agent.run("use large", "session").getText());
            assertNotNull(remote.takeRequest(5, TimeUnit.SECONDS));
            var next = JSON.readTree(remote.takeRequest(5, TimeUnit.SECONDS).getBody().readUtf8());
            var messages = next.path("messages");
            var tool = messages.get(messages.size() - 1);
            assertEquals("tool", tool.path("role").asText());
            assertEquals("large-call", tool.path("tool_call_id").asText());
            assertTrue(tool.path("content").asText().length() <= 2000, "The actual next request still contains the whole tool result");
            assertEquals("large-call", messages.get(messages.size() - 2).path("tool_calls").get(0).path("id").asText());
        }
    }

    private static MockResponse sse(String body) {
        return new MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body);
    }
    private static LLMProvider router(MockWebServer remote) {
        return new OpenRouterProvider("controlled-key", "controlled-model", remote.url("/v1").toString());
    }
    private static LLMProvider claude(MockWebServer remote) {
        var client = new OkHttpClient.Builder().addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                .url(remote.url("/v1/messages")).build())).build();
        return new ClaudeProvider("controlled-key", "controlled-model", client);
    }
}
