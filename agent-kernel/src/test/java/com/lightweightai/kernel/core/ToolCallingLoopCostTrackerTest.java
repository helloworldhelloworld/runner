package com.lightweightai.kernel.core;

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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that CostTracker budget enforcement works in the ToolCallingLoop
 * reactive path. When the budget is exceeded, the loop should exit gracefully
 * (empty Flux) rather than continuing to burn tokens.
 */
@DisplayName("ToolCallingLoop CostTracker budget enforcement")
class ToolCallingLoopCostTrackerTest {

    private ToolRegistry toolRegistry;
    private ToolExecutor toolExecutor;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        toolRegistry.register(echoTool());
        toolExecutor = new ToolExecutor(toolRegistry);
    }

    @Test
    @DisplayName("reactive loop exits gracefully when budget is already exceeded")
    void reactiveLoop_exitsOnOverBudget() {
        CostTracker tracker = new CostTracker(100);
        tracker.record(60, 50); // 110 total > 100 budget
        assertTrue(tracker.isOverBudget());

        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("should not reach");

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(provider)
                .toolExecutor(toolExecutor)
                .maxIterations(10)
                .costTracker(tracker)
                .build();

        List<ConversationMessage> msgs = singleUserMessage("hi");

        StepVerifier.create(loop.executeWithToolsReactive(msgs, emptyOptions()))
                .verifyComplete();

        assertEquals(0, provider.callCount(), "LLM should not be called when already over budget");
    }

    @Test
    @DisplayName("sync loop does not check CostTracker (only reactive does)")
    void syncLoop_doesNotCheckCostTracker() {
        CostTracker tracker = new CostTracker(100);
        tracker.record(60, 50); // over budget

        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("sync ok");

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(provider)
                .toolExecutor(toolExecutor)
                .maxIterations(10)
                .costTracker(tracker)
                .build();

        // Sync path doesn't have cost tracker check — it proceeds
        LLMResponse result = loop.executeWithTools(singleUserMessage("hi"), emptyOptions());
        assertEquals("sync ok", result.getMessage().getTextContent());
        assertEquals(1, provider.callCount());
    }

    @Test
    @DisplayName("CancellationToken also causes graceful exit in reactive loop")
    void cancellationToken_gracefulExit() {
        CancellationToken token = new CancellationToken();
        token.cancel();

        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("should not reach");

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(provider)
                .toolExecutor(toolExecutor)
                .maxIterations(10)
                .cancellationToken(token)
                .build();

        StepVerifier.create(loop.executeWithToolsReactive(singleUserMessage("hi"), emptyOptions()))
                .verifyComplete();

        assertEquals(0, provider.callCount(), "LLM should not be called when already cancelled");
    }

    @Test
    @DisplayName("CancellationToken causes sync loop to return [cancelled]")
    void cancellationToken_syncReturns_cancelled() {
        CancellationToken token = new CancellationToken();
        token.cancel();

        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("should not reach");

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(provider)
                .toolExecutor(toolExecutor)
                .maxIterations(10)
                .cancellationToken(token)
                .build();

        LLMResponse result = loop.executeWithTools(singleUserMessage("hi"), emptyOptions());
        assertEquals("[cancelled]", result.getMessage().getTextContent());
        assertEquals("cancelled", result.getStopReason());
        assertEquals(0, provider.callCount());
    }

    @Test
    @DisplayName("zero budget means unlimited — loop proceeds normally")
    void zeroBudget_unlimited() {
        CostTracker tracker = new CostTracker(0);
        tracker.record(999999, 999999); // huge consumption
        assertFalse(tracker.isOverBudget(), "zero budget = unlimited");

        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("ok");

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(provider)
                .toolExecutor(toolExecutor)
                .maxIterations(10)
                .costTracker(tracker)
                .build();

        List<StreamEvent> events = loop.executeWithToolsReactive(singleUserMessage("hi"), emptyOptions())
                .collectList().block();

        assertNotNull(events);
        assertFalse(events.isEmpty());
        assertEquals(1, provider.callCount());
    }

    private List<ConversationMessage> singleUserMessage(String text) {
        return new ArrayList<>(List.of(
                ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.USER)
                        .textContent(text)
                        .build()
        ));
    }

    private LLMOptions emptyOptions() {
        return LLMOptions.builder().build();
    }

    private static Tool echoTool() {
        return new Tool() {
            @Override public String getName() { return "echo"; }
            @Override public String getDescription() { return "Echoes input"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("echo: " + args);
            }
        };
    }
}
