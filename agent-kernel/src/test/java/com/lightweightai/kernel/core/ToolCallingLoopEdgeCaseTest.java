package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.AgentObserver;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.context.CostTracker;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolCallingLoop - edge cases: cancellation reactive, observer resilience, sync cost")
class ToolCallingLoopEdgeCaseTest {

    private ToolRegistry toolRegistry;
    private ToolExecutor toolExecutor;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        toolRegistry.register(new EchoTool());
        toolExecutor = new ToolExecutor(toolRegistry);
    }

    private List<ConversationMessage> userMsg(String text) {
        return new ArrayList<>(List.of(
                ConversationMessage.builder().role(ConversationMessage.MessageRole.USER).textContent(text).build()));
    }

    private LLMOptions opts() {
        return LLMOptions.builder().build();
    }

    // ==================== Cancellation on reactive path ====================

    @Test
    @DisplayName("cancellationToken pre-cancelled → reactive path returns empty Flux")
    void cancellationTokenStopsReactivePath() {
        CancellationToken token = new CancellationToken();
        token.cancel();

        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("should not reach");
        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(provider)
                .toolExecutor(toolExecutor)
                .maxIterations(10)
                .cancellationToken(token)
                .build();

        StepVerifier.create(loop.executeWithToolsReactive(userMsg("go"), opts()))
                .verifyComplete();

        assertEquals(0, provider.callCount(), "LLM should never be called when token is pre-cancelled");
    }

    // ==================== Observer exception resilience ====================

    @Test
    @DisplayName("observer that throws in onPreToolUse does not break tool execution")
    void observerExceptionInPreToolUseDoesNotBreakLoop() {
        AgentObserver throwingObserver = new AgentObserver() {
            @Override
            public void onPreToolUse(String toolName, Map<String, Object> args) {
                throw new RuntimeException("observer boom");
            }
        };

        CapturingLLMProvider provider = CapturingLLMProvider.toolThenEnd(
                "echo", Map.of("text", "resilient"), "done");

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(provider)
                .toolExecutor(toolExecutor)
                .maxIterations(10)
                .observers(List.of(throwingObserver))
                .build();

        StepVerifier.create(loop.executeWithToolsReactive(userMsg("go"), opts()))
                .thenConsumeWhile(e -> true)
                .verifyComplete();

        assertEquals(2, provider.callCount(), "Loop should complete normally despite observer error");
    }

    @Test
    @DisplayName("observer that throws in onPostToolUse does not break tool execution")
    void observerExceptionInPostToolUseDoesNotBreakLoop() {
        AgentObserver throwingObserver = new AgentObserver() {
            @Override
            public void onPostToolUse(String toolName, Map<String, Object> args, ToolResult result) {
                throw new RuntimeException("post observer boom");
            }
        };

        CapturingLLMProvider provider = CapturingLLMProvider.toolThenEnd(
                "echo", Map.of("text", "resilient"), "done");

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(provider)
                .toolExecutor(toolExecutor)
                .maxIterations(10)
                .observers(List.of(throwingObserver))
                .build();

        StepVerifier.create(loop.executeWithToolsReactive(userMsg("go"), opts()))
                .thenConsumeWhile(e -> true)
                .verifyComplete();

        assertEquals(2, provider.callCount());
    }

    // ==================== CostTracker on sync path ====================

    @Test
    @DisplayName("costTracker has no effect on sync path (sync does not check cost)")
    void costTrackerSyncPathDoesNotCheck() {
        CostTracker tracker = new CostTracker(100);
        tracker.record(80, 30); // over budget

        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("sync response");
        ToolCallingLoop loop = new ToolCallingLoop(provider, toolExecutor, 10, null, null, null, tracker);

        LLMResponse result = loop.executeWithTools(userMsg("go"), opts());
        assertEquals("sync response", result.getMessage().getTextContent());
    }

    // ==================== Builder defaults ====================

    @Test
    @DisplayName("builder with default maxIterations (10)")
    void builderDefaultMaxIterations() {
        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(CapturingLLMProvider.endTurn("x"))
                .toolExecutor(toolExecutor)
                .build();
        assertEquals(10, loop.getMaxIterations());
    }

    @Test
    @DisplayName("builder with cancellationToken and costTracker together")
    void builderWithBothTokenAndTracker() {
        CancellationToken token = new CancellationToken();
        CostTracker tracker = new CostTracker(1000);

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(CapturingLLMProvider.endTurn("combined"))
                .toolExecutor(toolExecutor)
                .maxIterations(5)
                .cancellationToken(token)
                .costTracker(tracker)
                .build();

        LLMResponse result = loop.executeWithTools(userMsg("hi"), opts());
        assertEquals("combined", result.getMessage().getTextContent());
    }

    // ==================== Multiple observers ====================

    @Test
    @DisplayName("multiple observers all receive callbacks")
    void multipleObserversAllReceive() {
        List<String> observer1Calls = new ArrayList<>();
        List<String> observer2Calls = new ArrayList<>();

        AgentObserver obs1 = new AgentObserver() {
            @Override public void onPreToolUse(String name, Map<String, Object> args) { observer1Calls.add("pre:" + name); }
            @Override public void onPostToolUse(String name, Map<String, Object> args, ToolResult r) { observer1Calls.add("post:" + name); }
        };
        AgentObserver obs2 = new AgentObserver() {
            @Override public void onPreToolUse(String name, Map<String, Object> args) { observer2Calls.add("pre:" + name); }
            @Override public void onPostToolUse(String name, Map<String, Object> args, ToolResult r) { observer2Calls.add("post:" + name); }
        };

        CapturingLLMProvider provider = CapturingLLMProvider.toolThenEnd(
                "echo", Map.of("text", "multi"), "done");

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(provider)
                .toolExecutor(toolExecutor)
                .maxIterations(10)
                .observers(List.of(obs1, obs2))
                .build();

        StepVerifier.create(loop.executeWithToolsReactive(userMsg("go"), opts()))
                .thenConsumeWhile(e -> true)
                .verifyComplete();

        assertEquals(List.of("pre:echo", "post:echo"), observer1Calls);
        assertEquals(List.of("pre:echo", "post:echo"), observer2Calls);
    }

    // ==================== Reactive: enrichWithToolCalls carries tool_calls metadata ====================

    @Test
    @DisplayName("assistant message in conversation includes tool_calls metadata after enrichment")
    void enrichedMessageCarriesToolCallsMetadata() {
        CapturingLLMProvider provider = CapturingLLMProvider.toolThenEnd(
                "echo", Map.of("text", "enrich"), "enriched");
        ToolCallingLoop loop = new ToolCallingLoop(provider, toolExecutor, 10);

        loop.executeWithTools(userMsg("go"), opts());

        List<ConversationMessage> secondCall = provider.messagesHistory().get(1);
        ConversationMessage assistantMsg = secondCall.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.ASSISTANT)
                .findFirst().orElseThrow();

        assertNotNull(assistantMsg.getMetadata().get("tool_calls"),
                "Enriched assistant message must carry tool_calls metadata");
    }

    // ==================== Helpers ====================

    private static class EchoTool implements Tool {
        @Override public String getName() { return "echo"; }
        @Override public String getDescription() { return "Echoes text"; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
        @Override public ToolResult execute(Map<String, Object> args) {
            return ToolResult.success("Echo: " + args.get("text"));
        }
    }
}
