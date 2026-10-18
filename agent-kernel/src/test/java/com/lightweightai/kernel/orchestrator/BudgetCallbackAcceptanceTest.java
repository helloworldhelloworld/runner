package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.gateway.*;
import com.lightweightai.kernel.memory.InMemoryProvider;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class BudgetCallbackAcceptanceTest {
    @Test void agentCallbackUsesTheSameBudgetedToolLoopAsReactive() throws Exception {
        try (var remote = new MockWebServer()) {
            remote.start(); remote.enqueue(ExecutionBudgetAcceptanceTest.reply(true, 4900, 100));
            remote.enqueue(ExecutionBudgetAcceptanceTest.reply(false, 1, 1));
            var agent = new AgentFactory(ExecutionBudgetAcceptanceTest.router(remote), new InMemoryProvider(),
                    ExecutionBudgetAcceptanceTest.tools()).create(AgentProfile.builder().agentId("worker")
                            .maxTokens(900).maxBudgetTokens(5000).build());
            var deltas = new CopyOnWriteArrayList<String>();
            var actual = agent.runStream("use large", "session", deltas::add).get(30, TimeUnit.SECONDS);
            var sent = ExecutionBudgetAcceptanceTest.JSON.readTree(remote.takeRequest(5, TimeUnit.SECONDS).getBody().readUtf8());
            ((com.fasterxml.jackson.databind.node.ObjectNode) sent).remove("api_key");
            export(Map.of("modelRequest", sent, "actualStopReason", actual.getStopReason(),
                    "httpRequestCount", remote.getRequestCount(), "deltas", List.copyOf(deltas)));
            assertEquals("budget_exhausted", actual.getStopReason());
            assertEquals(1, remote.getRequestCount());
            assertTrue(deltas.isEmpty(), "No synthetic budget text may be presented as a model delta");
        }
    }

    @ParameterizedTest @ValueSource(strings={"gateway", "orchestrator", "agent_adapter"})
    void callbackFinalResponsePreservesBudgetStopAndUsage(String entry) throws Exception {
        try (var remote = new MockWebServer()) {
            remote.start(); remote.enqueue(ExecutionBudgetAcceptanceTest.reply(false, 1, 1));
            var deltas = new CopyOnWriteArrayList<String>();
            var registry = new AgentRegistry();
            var profile = AgentProfile.builder().agentId("default").maxBudgetTokens(1).build();
            registry.register(profile); registry.setDefault("default");
            var factory = new AgentFactory(ExecutionBudgetAcceptanceTest.router(remote), new InMemoryProvider(), new ToolRegistry());
            var orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));
            try {
                var result = new CompletableFuture<GatewayResponse>();
                var request = GatewayRequest.builder().sessionId("s").message("hello").build();
                if (entry.equals("orchestrator")) {
                    orchestrator.chatStream(request, new ChatHandler.StreamCallback() {
                        public void onDelta(String delta, Map<String,Object> metadata) { deltas.add(delta); }
                        public void onComplete(GatewayResponse response) { result.complete(response); }
                        public void onError(Throwable error) { result.completeExceptionally(error); }
                    });
                } else {
                    var gateway = entry.equals("agent_adapter") ? Gateway.builder().agentLoop(factory.create(profile)).build()
                            : Gateway.builder().chatHandler(orchestrator).build();
                    gateway.handleStream(request, GatewayStreamHandler.simple(deltas::add,
                            result::complete, result::completeExceptionally));
                }
                var response = result.get(30, TimeUnit.SECONDS);
                assertEquals("budget_exhausted", response.getMetadata().get("stopReason"));
                var usage = assertInstanceOf(Map.class, response.getMetadata().get("usage"));
                assertEquals(1L, usage.get("maxBudgetTokens"));
                assertEquals(0L, usage.get("knownTokens"));
                assertTrue(deltas.isEmpty());
                assertEquals(0, remote.getRequestCount());
            } finally { orchestrator.shutdown(); }
        }
    }
    private static void export(Map<String,Object> observations) throws Exception {
        String directory = System.getProperty("runner.evidence.dir");
        if (directory == null) return;
        String candidate = System.getProperty("runner.candidate.digest", "");
        String evaluation = System.getProperty("runner.evaluation.digest", "");
        assertTrue(candidate.matches("[a-f0-9]{64}"));
        assertTrue(evaluation.matches("[a-f0-9]{64}"));
        var path = java.nio.file.Path.of(directory); java.nio.file.Files.createDirectories(path);
        java.nio.file.Files.writeString(path.resolve("budget-callback.json"), ExecutionBudgetAcceptanceTest.JSON
                .writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("schemaVersion", 1,
                        "caseId", "budget-callback", "candidateDigest", candidate, "evaluationDigest", evaluation,
                        "observations", observations)), java.nio.file.StandardOpenOption.CREATE_NEW);
    }
}
