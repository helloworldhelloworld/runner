package com.lightweightai.kernel.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.*;
import com.lightweightai.kernel.context.ExecutionUsageScope;
import com.lightweightai.kernel.context.MicroCompactor;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.llm.openrouter.OpenRouterProvider;
import com.lightweightai.kernel.memory.InMemoryProvider;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual native assembly and HTTP peer; limits must affect the request and the final consumer. */
class ExecutionBudgetAcceptanceTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final List<ConversationMessage> INPUT = List.of(ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER).textContent("use large").build());

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void profileBudgetStopsAnotherModelCallAndReachesConsumer(boolean reactive) throws Exception {
        try (var remote = new MockWebServer()) {
            remote.start();
            remote.enqueue(reply(true, 4900, 100));
            remote.enqueue(reply(false, 1, 1));
            var agent = new AgentFactory(router(remote), new InMemoryProvider(), tools())
                    .create(AgentProfile.builder().agentId("worker").maxTokens(900).maxBudgetTokens(5000).build());
            if (reactive) {
                var events = agent.runReactive("use large", "session").collectList().block(Duration.ofSeconds(30));
                var stopped = events.stream().filter(e -> e.getType() == StreamEvent.EventType.BUDGET_STOPPED).toList();
                assertEquals(1, stopped.size());
                assertEquals("budget_exhausted", stopped.getFirst().getData().get("reason"));
                assertEquals(5000L, stopped.getFirst().getData().get("knownTokens"));
            } else {
                assertEquals("budget_exhausted", agent.run("use large", "session").getStopReason());
            }
            var request = JSON.readTree(remote.takeRequest(5, TimeUnit.SECONDS).getBody().readUtf8());
            assertEquals(900, request.path("max_tokens").asInt());
            assertEquals(1, remote.getRequestCount(), "The root budget did not stop the actual next HTTP call");
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void remainingBudgetCapsActualOutputAndUnknownUsageIsNotRefunded(boolean known) throws Exception {
        try (var remote = new MockWebServer()) {
            remote.start();
            remote.enqueue(reply(!known, known ? 40 : null, known ? 5 : null));
            remote.enqueue(reply(false, 1, 1));
            var scope = new ExecutionUsageScope(900);
            var agent = new AgentFactory(router(remote), new InMemoryProvider(), tools())
                    .create(AgentProfile.builder().agentId("worker").maxTokens(2000).build());
            var result = agent.run("use large", "session", ToolExecutionContext.serverOnly().withUsageScope(scope));
            var request = JSON.readTree(remote.takeRequest(5, TimeUnit.SECONDS).getBody().readUtf8());
            assertTrue(request.path("max_tokens").asInt() > 0);
            assertTrue(request.path("max_tokens").asInt() < 900);
            assertEquals(1, remote.getRequestCount());
            assertEquals(known ? 45L : 0L, scope.snapshot().get("knownTokens"));
            assertEquals(known ? 0L : 1L, scope.snapshot().get("unknownAttempts"));
            assertEquals(known ? "stop" : "budget_exhausted", result.getStopReason());
            assertEquals(known ? 45L : 900L, scope.snapshot().get("chargedTokens"));
        }
    }

    @Test void asyncLoopAccountsEveryRequestAndCompactsTheActualToolPayload() throws Exception {
        try (var remote = new MockWebServer()) {
            remote.start();
            remote.enqueue(reply(true, 30, 10)); remote.enqueue(reply(false, 40, 5));
            var scope = new ExecutionUsageScope(5000);
            var loop = ToolCallingLoop.builder().provider(router(remote)).toolExecutor(new ToolExecutor(tools()))
                    .executionContext(ToolExecutionContext.serverOnly().withUsageScope(scope))
                    .contextCompactor(new MicroCompactor(2000)).build();
            assertEquals("done", loop.executeWithToolsAsync(INPUT, LLMOptions.builder().maxTokens(400).build())
                    .get(30, TimeUnit.SECONDS).getMessage().getTextContent());
            assertEquals(85L, scope.snapshot().get("knownTokens"));
            assertEquals(2, scope.attempts().size());
            assertNotNull(remote.takeRequest(5, TimeUnit.SECONDS));
            var sent = JSON.readTree(remote.takeRequest(5, TimeUnit.SECONDS).getBody().readUtf8()).path("messages");
            assertTrue(sent.get(sent.size()-1).path("content").asText().length() <= 2000);
            assertEquals("large-call", sent.get(sent.size()-1).path("tool_call_id").asText());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void resilientRetriesHaveSeparateAttemptRecords(boolean reactive) throws Exception {
        try (var remote = new MockWebServer()) {
            remote.start();
            remote.enqueue(new MockResponse().setResponseCode(503).setBody("controlled unavailable"));
            remote.enqueue(reply(false, 30, 4));
            var scope = new ExecutionUsageScope(5000);
            var resilient = new ResilientLLMProvider(router(remote), 1, 1);
            var loop = ToolCallingLoop.builder().provider(resilient).toolExecutor(new ToolExecutor(new ToolRegistry()))
                    .executionContext(ToolExecutionContext.serverOnly().withUsageScope(scope)).build();
            var options = LLMOptions.builder().maxTokens(100).build();
            if (reactive) loop.executeWithToolsReactive(INPUT, options).blockLast(Duration.ofSeconds(30));
            else loop.executeWithTools(INPUT, options);
            assertEquals(2, remote.getRequestCount());
            assertEquals(2, scope.attempts().size());
            assertEquals(34L, scope.snapshot().get("knownTokens"));
            assertEquals(1L, scope.snapshot().get("unknownAttempts"));
            assertTrue((long) scope.snapshot().get("chargedTokens") > 34);
            assertEquals(2, resilient.getHealthInfo().totalRequests());
        }
    }

    @Test void nativeSpawnInheritsTheRootBalanceEvenWhenChildProfileIsUnlimited() throws Exception {
        try (var remote = new MockWebServer()) {
            remote.start();
            remote.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                    {"choices":[{"message":{"role":"assistant","content":"","tool_calls":[
                    {"id":"spawn-child","type":"function","function":{"name":"spawn_subagent",
                    "arguments":"{\\"task\\":\\"child task\\",\\"agentId\\":\\"child\\"}"}}]},"finish_reason":"tool_calls"}],
                    "usage":{"prompt_tokens":9980,"completion_tokens":10}}
                    """));
            var registry = new AgentRegistry();
            var parent = AgentProfile.builder().agentId("parent").maxSpawnDepth(1).maxTokens(900).maxBudgetTokens(10000).build();
            registry.register(parent);
            registry.register(AgentProfile.builder().agentId("child").maxTokens(900).maxBudgetTokens(0).build());
            registry.setDefault("parent");
            var factory = new AgentFactory(router(remote), new InMemoryProvider(), new ToolRegistry());
            var runtime = new SubagentRuntime(factory, registry, 2);
            try {
                var scope = new ExecutionUsageScope(10000);
                var events = runtime.createRootAgent(factory, parent, "root")
                        .runReactive("delegate", "root", new CancellationToken(),
                                ToolExecutionContext.serverOnly().withUsageScope(scope))
                        .collectList().block(Duration.ofSeconds(30));
                var toolResult = events.stream().filter(e -> e.getType() == StreamEvent.EventType.TOOL_RESULT)
                        .findFirst().orElseThrow().getChunk().getResult().getContent();
                String id = toolResult.split("Run ID: ")[1].split(" ")[0];
                assertTrue(runtime.waitForCompletion(id, 5, TimeUnit.SECONDS));
                var child = runtime.getRunOrCompleted(id);
                assertSame(scope, child.getRequest().executionContext().getUsageScope());
                assertEquals(SubagentRun.Status.FAILED, child.getStatus());
                assertTrue(child.getErrorMessage().contains("budget_exhausted"));
                assertTrue(events.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.BUDGET_STOPPED));
                assertEquals(1, remote.getRequestCount(), "The child reset the exhausted root balance");
            } finally { runtime.shutdown(); }
        }
    }

    @Test void concurrentReservationsAndCancellationCannotReleaseUnknownRemoteConsumption() throws Exception {
        try (var remote = new MockWebServer()) {
            var release = new CompletableFuture<Void>();
            remote.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
                @Override public MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest request) throws InterruptedException {
                    try { release.get(30, TimeUnit.SECONDS); return reply(false, 1, 1); }
                    catch (InterruptedException e) { throw e; }
                    catch (Exception e) { throw new IllegalStateException(e); }
                }
            });
            remote.start();
            try {
                var scope = new ExecutionUsageScope(900);
                var loop = ToolCallingLoop.builder().provider(router(remote)).toolExecutor(new ToolExecutor(new ToolRegistry()))
                        .executionContext(ToolExecutionContext.serverOnly().withUsageScope(scope)).build();
                var options = LLMOptions.builder().maxTokens(2000).build();
                var first = loop.executeWithToolsAsync(INPUT, options);
                assertNotNull(remote.takeRequest(5, TimeUnit.SECONDS));
                assertEquals("budget_exhausted", loop.executeWithToolsAsync(INPUT, options)
                        .get(5, TimeUnit.SECONDS).getStopReason());
                assertEquals(1, remote.getRequestCount());
                assertEquals(900L, scope.snapshot().get("inFlightTokens"));
                assertTrue(first.cancel(true));
                assertEquals(1L, scope.snapshot().get("unknownAttempts"));
                assertEquals(900L, scope.snapshot().get("chargedTokens"));
                assertEquals("CANCELLED", scope.attempts().getFirst().get("status"));
            } finally { release.complete(null); }
        }
    }

    static ToolRegistry tools() {
        var tools = new ToolRegistry();
        tools.register(new Tool() {
            public String getName() { return "large"; }
            public String getDescription() { return "Read a result"; }
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            public ToolResult execute(Map<String,Object> args) { return ToolResult.success("x".repeat(30_000)); }
        });
        return tools;
    }
    static LLMProvider router(MockWebServer remote) {
        return new OpenRouterProvider("controlled-key", "controlled-model", remote.url("/v1").toString());
    }
    static MockResponse reply(boolean tool, Integer input, Integer output) throws Exception {
        var message = new LinkedHashMap<String,Object>();
        message.put("role", "assistant"); message.put("content", tool ? "" : "done");
        if (tool) message.put("tool_calls", List.of(Map.of("id", "large-call", "type", "function",
                "function", Map.of("name", "large", "arguments", "{}"))));
        var payload = new LinkedHashMap<String,Object>();
        payload.put("choices", List.of(Map.of("message", message, "finish_reason", tool ? "tool_calls" : "stop")));
        if (input != null) payload.put("usage", Map.of("prompt_tokens", input, "completion_tokens", output));
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(JSON.writeValueAsString(payload));
    }
}
