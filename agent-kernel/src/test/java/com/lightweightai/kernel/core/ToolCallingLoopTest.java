package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.context.CostTracker;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.testsupport.CapturingAgentObserver;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolCallingLoop — core agent loop")
class ToolCallingLoopTest {

    private ToolRegistry toolRegistry;
    private ToolExecutor toolExecutor;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        toolRegistry.register(new EchoTool());
        toolExecutor = new ToolExecutor(toolRegistry);
    }

    private List<ConversationMessage> singleUserMessage(String text) {
        return new ArrayList<>(List.of(
            ConversationMessage.builder()
                .role(MessageRole.USER)
                .textContent(text)
                .build()
        ));
    }

    private LLMOptions emptyOptions() {
        return LLMOptions.builder().build();
    }

    // ==================== Sync path ====================

    @Nested
    @DisplayName("Sync executeWithTools")
    class SyncTests {

        @Test
        @DisplayName("no tool calls → returns LLM response directly")
        void noToolCalls_returnsDirectly() {
            CapturingLLMProvider provider = CapturingLLMProvider.endTurn("Hello!");
            ToolCallingLoop loop = new ToolCallingLoop(provider, toolExecutor, 10);

            LLMResponse result = loop.executeWithTools(singleUserMessage("hi"), emptyOptions());

            assertEquals("Hello!", result.getMessage().getTextContent());
            assertFalse(result.hasToolCalls());
            assertEquals(1, provider.callCount());
        }

        @Test
        @DisplayName("single tool call round → executes tool and returns final response")
        void singleToolCallRound() {
            CapturingLLMProvider provider = CapturingLLMProvider.toolThenEnd(
                "echo", Map.of("text", "ping"), "Got it: ping");
            ToolCallingLoop loop = new ToolCallingLoop(provider, toolExecutor, 10);

            LLMResponse result = loop.executeWithTools(singleUserMessage("echo ping"), emptyOptions());

            assertEquals("Got it: ping", result.getMessage().getTextContent());
            assertEquals(2, provider.callCount());

            // Second call should have tool result in messages
            List<ConversationMessage> secondCallMessages = provider.messagesHistory().get(1);
            boolean hasToolMessage = secondCallMessages.stream()
                .anyMatch(m -> m.getRole() == MessageRole.TOOL);
            assertTrue(hasToolMessage, "Second LLM call should contain TOOL message with result");
        }

        @Test
        @DisplayName("tool result message carries correct toolUseId and is_error metadata")
        void toolResultMessage_hasCorrectPayload() {
            CapturingLLMProvider provider = CapturingLLMProvider.toolThenEnd(
                "echo", Map.of("text", "test"), "done");
            ToolCallingLoop loop = new ToolCallingLoop(provider, toolExecutor, 10);

            loop.executeWithTools(singleUserMessage("go"), emptyOptions());

            List<ConversationMessage> secondCallMessages = provider.messagesHistory().get(1);
            ConversationMessage toolMsg = secondCallMessages.stream()
                .filter(m -> m.getRole() == MessageRole.TOOL)
                .findFirst().orElseThrow();

            assertNotNull(toolMsg.getMetadata().get("tool_use_id"),
                "tool result message must carry tool_use_id");
            assertEquals(false, toolMsg.getMetadata().get("is_error"));
            assertTrue(toolMsg.getTextContent().contains("Echo: test"));
        }

        @Test
        @DisplayName("max iterations exceeded → throws RuntimeException")
        void maxIterationsExceeded() {
            CapturingLLMProvider_AlwaysToolCall endlessProvider = new CapturingLLMProvider_AlwaysToolCall("echo", Map.of("text", "x"));
            ToolCallingLoop loop = new ToolCallingLoop(endlessProvider, toolExecutor, 3);

            RuntimeException ex = assertThrows(RuntimeException.class,
                () -> loop.executeWithTools(singleUserMessage("go"), emptyOptions()));
            assertTrue(ex.getMessage().contains("maximum iterations"));
        }

        @Test
        @DisplayName("cancellation token → stops loop with [cancelled] response")
        void cancellationToken_stopsLoop() {
            CancellationToken token = new CancellationToken();
            token.cancel();

            CapturingLLMProvider provider = CapturingLLMProvider.endTurn("should not reach");
            ToolCallingLoop loop = new ToolCallingLoop(provider, toolExecutor, 10, null, null, token);

            LLMResponse result = loop.executeWithTools(singleUserMessage("go"), emptyOptions());

            assertEquals("[cancelled]", result.getMessage().getTextContent());
            assertEquals("cancelled", result.getStopReason());
            assertEquals(0, provider.callCount(), "LLM should never be called when already cancelled");
        }

        @Test
        @DisplayName("cost tracker over budget → reactive loop returns empty")
        void costTracker_overBudget_stopsReactiveLoop() {
            CostTracker tracker = new CostTracker(100);
            tracker.record(80, 30); // 110 > 100

            CapturingLLMProvider provider = CapturingLLMProvider.endTurn("should not reach");
            ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(provider)
                .toolExecutor(toolExecutor)
                .maxIterations(10)
                .costTracker(tracker)
                .build();

            StepVerifier.create(loop.executeWithToolsReactive(singleUserMessage("go"), emptyOptions()))
                .verifyComplete();

            assertEquals(0, provider.callCount());
        }
    }

    // ==================== Async path ====================

    @Nested
    @DisplayName("Async executeWithToolsAsync")
    class AsyncTests {

        @Test
        @DisplayName("no tool calls → completes with direct response")
        void noToolCalls_async() throws Exception {
            CapturingLLMProvider provider = CapturingLLMProvider.endTurn("Async hello");
            ToolCallingLoop loop = new ToolCallingLoop(provider, toolExecutor, 10);

            LLMResponse result = loop.executeWithToolsAsync(singleUserMessage("hi"), emptyOptions()).get();

            assertEquals("Async hello", result.getMessage().getTextContent());
        }

        @Test
        @DisplayName("single tool call round → async path works end-to-end")
        void singleToolCall_async() throws Exception {
            CapturingLLMProvider provider = CapturingLLMProvider.toolThenEnd(
                "echo", Map.of("text", "async"), "async done");
            ToolCallingLoop loop = new ToolCallingLoop(provider, toolExecutor, 10);

            LLMResponse result = loop.executeWithToolsAsync(singleUserMessage("go"), emptyOptions()).get();

            assertEquals("async done", result.getMessage().getTextContent());
            assertEquals(2, provider.callCount());
        }

        @Test
        @DisplayName("max iterations exceeded → async future completes exceptionally")
        void maxIterationsExceeded_async() {
            CapturingLLMProvider_AlwaysToolCall endlessProvider =
                new CapturingLLMProvider_AlwaysToolCall("echo", Map.of("text", "x"));
            ToolCallingLoop loop = new ToolCallingLoop(endlessProvider, toolExecutor, 2);

            CompletableFuture<LLMResponse> future = loop.executeWithToolsAsync(
                singleUserMessage("go"), emptyOptions());

            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertTrue(ex.getCause().getMessage().contains("maximum iterations"));
        }
    }

    // ==================== Reactive path ====================

    @Nested
    @DisplayName("Reactive executeWithToolsReactive")
    class ReactiveTests {

        @Test
        @DisplayName("no tool calls → emits LLM_COMPLETE only")
        void noToolCalls_reactive() {
            CapturingLLMProvider provider = CapturingLLMProvider.endTurn("Reactive hello");
            ToolCallingLoop loop = new ToolCallingLoop(provider, toolExecutor, 10);

            StepVerifier.create(loop.executeWithToolsReactive(singleUserMessage("hi"), emptyOptions()))
                .expectNextMatches(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE
                    && "Reactive hello".equals(e.getResponse().getMessage().getTextContent()))
                .verifyComplete();
        }

        @Test
        @DisplayName("tool call round → emits LLM_COMPLETE, TOOL_CALL_START, TOOL_RESULT, then final LLM_COMPLETE")
        void toolCallRound_reactive_emitsCorrectSequence() {
            CapturingLLMProvider provider = CapturingLLMProvider.toolThenEnd(
                "echo", Map.of("text", "reactive"), "reactive done");
            ToolCallingLoop loop = new ToolCallingLoop(provider, toolExecutor, 10);

            List<StreamEvent.EventType> types = new ArrayList<>();
            StepVerifier.create(
                loop.executeWithToolsReactive(singleUserMessage("go"), emptyOptions())
                    .doOnNext(e -> types.add(e.getType()))
            )
                .thenConsumeWhile(e -> true)
                .verifyComplete();

            assertTrue(types.contains(StreamEvent.EventType.LLM_COMPLETE),
                "Should contain LLM_COMPLETE event");
            assertTrue(types.contains(StreamEvent.EventType.TOOL_CALL_START),
                "Should contain TOOL_CALL_START event");
            assertTrue(types.contains(StreamEvent.EventType.TOOL_RESULT),
                "Should contain TOOL_RESULT event");
        }

        @Test
        @DisplayName("max iterations → emits error")
        void maxIterationsExceeded_reactive() {
            CapturingLLMProvider_AlwaysToolCall endlessProvider =
                new CapturingLLMProvider_AlwaysToolCall("echo", Map.of("text", "x"));
            ToolCallingLoop loop = new ToolCallingLoop(endlessProvider, toolExecutor, 2);

            StepVerifier.create(loop.executeWithToolsReactive(singleUserMessage("go"), emptyOptions()))
                .thenConsumeWhile(e -> true)
                .verifyErrorMessage("Tool calling loop exceeded maximum iterations: 2");
        }
    }

    // ==================== Observer tests ====================

    @Nested
    @DisplayName("Observer hooks")
    class ObserverTests {

        @Test
        @DisplayName("observers receive pre and post tool use callbacks with correct payload")
        void observersFired() {
            CapturingAgentObserver observer = new CapturingAgentObserver();
            CapturingLLMProvider provider = CapturingLLMProvider.toolThenEnd(
                "echo", Map.of("text", "observed"), "done");

            ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(provider)
                .toolExecutor(toolExecutor)
                .maxIterations(10)
                .observers(List.of(observer))
                .build();

            StepVerifier.create(loop.executeWithToolsReactive(singleUserMessage("go"), emptyOptions()))
                .thenConsumeWhile(e -> true)
                .verifyComplete();

            assertEquals(1, observer.preCalls().size());
            assertEquals("echo", observer.preCalls().get(0).name());
            assertEquals("observed", observer.preCalls().get(0).args().get("text"));

            assertEquals(1, observer.postCalls().size());
            assertEquals("echo", observer.postCalls().get(0).name());
            assertNotNull(observer.postCalls().get(0).result(),
                "PostToolUse must carry the actual ToolResult, not null");
            assertFalse(observer.postCalls().get(0).result().isError());
        }
    }

    // ==================== Builder & validation ====================

    @Nested
    @DisplayName("Builder and validation")
    class ValidationTests {

        @Test
        @DisplayName("null provider → IllegalArgumentException")
        void nullProvider() {
            assertThrows(IllegalArgumentException.class,
                () -> new ToolCallingLoop(null, toolExecutor, 10));
        }

        @Test
        @DisplayName("null toolExecutor → IllegalArgumentException")
        void nullToolExecutor() {
            assertThrows(IllegalArgumentException.class,
                () -> new ToolCallingLoop(CapturingLLMProvider.endTurn("x"), null, 10));
        }

        @Test
        @DisplayName("maxIterations < 1 → IllegalArgumentException")
        void invalidMaxIterations() {
            assertThrows(IllegalArgumentException.class,
                () -> new ToolCallingLoop(CapturingLLMProvider.endTurn("x"), toolExecutor, 0));
        }

        @Test
        @DisplayName("builder produces working loop")
        void builderWorks() {
            ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(CapturingLLMProvider.endTurn("built"))
                .toolExecutor(toolExecutor)
                .maxIterations(5)
                .build();

            assertEquals(5, loop.getMaxIterations());
            LLMResponse r = loop.executeWithTools(singleUserMessage("hi"), emptyOptions());
            assertEquals("built", r.getMessage().getTextContent());
        }

        @Test
        @DisplayName("toString includes provider name and tool count")
        void toStringContent() {
            ToolCallingLoop loop = new ToolCallingLoop(
                CapturingLLMProvider.endTurn("x"), toolExecutor, 10);
            String str = loop.toString();
            assertTrue(str.contains("capturing-spy"));
            assertTrue(str.contains("maxIterations=10"));
        }
    }

    // ==================== Test helpers ====================

    private static class EchoTool implements Tool {
        @Override public String getName() { return "echo"; }
        @Override public String getDescription() { return "Echoes text"; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
        @Override public ToolResult execute(Map<String, Object> args) {
            return ToolResult.success("Echo: " + args.get("text"));
        }
    }

    /**
     * LLMProvider that always returns a tool_use response — never end_turn.
     * Used to test max-iteration guard.
     */
    private static class CapturingLLMProvider_AlwaysToolCall implements com.lightweightai.kernel.llm.LLMProvider {
        private final String toolName;
        private final Map<String, Object> args;

        CapturingLLMProvider_AlwaysToolCall(String toolName, Map<String, Object> args) {
            this.toolName = toolName;
            this.args = args;
        }

        private LLMResponse buildResponse() {
            ToolCall tc = new ToolCall("call_" + UUID.randomUUID(), toolName, args);
            return LLMResponse.builder()
                .message(ConversationMessage.builder()
                    .role(MessageRole.ASSISTANT).textContent("").build())
                .toolCalls(List.of(tc))
                .stopReason("tool_use")
                .build();
        }

        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) { return buildResponse(); }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
            return CompletableFuture.completedFuture(buildResponse());
        }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
            return CompletableFuture.completedFuture(buildResponse());
        }
        @Override public reactor.core.publisher.Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return reactor.core.publisher.Flux.just(StreamEvent.llmComplete(buildResponse()));
        }
        @Override public com.lightweightai.kernel.llm.ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "always-tool"; }
    }
}
