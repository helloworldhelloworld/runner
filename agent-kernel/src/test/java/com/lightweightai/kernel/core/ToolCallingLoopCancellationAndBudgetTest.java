package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.AgentObserver;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.context.CostTracker;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.testsupport.CapturingAgentObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolCallingLoop 同步路径的 CancellationToken / CostTracker / Observer 测试
 *
 * 补充现有 SyncTest/AsyncTest/ReactiveTest 的覆盖盲区：
 * - 同步路径的 CancellationToken 检查
 * - 同步/Reactive 路径的 CostTracker 预算超限
 * - 同步路径的 PreToolUse/PostToolUse observer hooks
 * - 多个 tool call 在一次 LLM 响应中的处理
 */
@DisplayName("ToolCallingLoop - 取消/预算/观察者")
class ToolCallingLoopCancellationAndBudgetTest {

    private ScriptedLLMProvider llmProvider;
    private ToolExecutor toolExecutor;
    private ToolRegistry toolRegistry;
    private AtomicInteger callCounter;

    @BeforeEach
    void setUp() {
        llmProvider = new ScriptedLLMProvider();
        toolRegistry = new ToolRegistry();
        toolExecutor = new ToolExecutor(toolRegistry);
        callCounter = new AtomicInteger(0);

        toolRegistry.register(new Tool() {
            @Override public String getName() { return "echo"; }
            @Override public String getDescription() { return "echo"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("echo: " + args.getOrDefault("msg", ""));
            }
        });

        toolRegistry.register(new Tool() {
            @Override public String getName() { return "add"; }
            @Override public String getDescription() { return "add"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                int a = ((Number) args.get("a")).intValue();
                int b = ((Number) args.get("b")).intValue();
                return ToolResult.success(String.valueOf(a + b));
            }
        });
    }

    // ==================== Sync CancellationToken ====================

    @Nested
    @DisplayName("同步路径 CancellationToken")
    class SyncCancellationTests {

        @Test
        @DisplayName("已取消的 token 在第一次迭代前就返回 [cancelled]")
        void shouldReturnCancelledImmediatelyWhenAlreadyCancelled() {
            CancellationToken token = new CancellationToken();
            token.cancel();

            llmProvider.add(textResponse("This should never appear"));

            ToolCallingLoop loop = ToolCallingLoop.builder()
                    .provider(llmProvider)
                    .toolExecutor(toolExecutor)
                    .maxIterations(5)
                    .cancellationToken(token)
                    .build();

            LLMResponse result = loop.executeWithTools(userMessages("hi"), LLMOptions.builder().build());

            assertEquals("[cancelled]", result.getMessage().getTextContent());
            assertEquals("cancelled", result.getStopReason());
            assertEquals(0, llmProvider.callCount, "LLM should not be called when already cancelled");
        }

        @Test
        @DisplayName("取消发生在工具调用后、下一轮 LLM 调用前")
        void shouldCancelBetweenToolCallRounds() {
            CancellationToken token = new CancellationToken();

            llmProvider.add(toolCallResponse("echo", Map.of("msg", "hello")));
            llmProvider.add(textResponse("Should not see this"));

            ToolCallingLoop loop = ToolCallingLoop.builder()
                    .provider(llmProvider)
                    .toolExecutor(toolExecutor)
                    .maxIterations(5)
                    .cancellationToken(token)
                    .build();

            // Cancel after first LLM call returns tool_use
            llmProvider.onCall(1, () -> token.cancel());

            LLMResponse result = loop.executeWithTools(userMessages("test"), LLMOptions.builder().build());

            assertEquals("[cancelled]", result.getMessage().getTextContent());
            assertEquals(1, llmProvider.callCount, "Should have made exactly 1 LLM call before cancel");
        }
    }

    // ==================== CostTracker Budget ====================

    @Nested
    @DisplayName("CostTracker 预算检查")
    class CostTrackerTests {

        @Test
        @DisplayName("Reactive: 预算超限时立即返回空 Flux")
        void reactiveShouldReturnEmptyWhenOverBudget() {
            CostTracker tracker = new CostTracker(100);
            tracker.record(60, 50); // total=110 > budget=100

            llmProvider.addStream(List.of(
                    StreamEvent.textDelta("never"),
                    StreamEvent.llmComplete(textResponse("never"))
            ));

            ToolCallingLoop loop = ToolCallingLoop.builder()
                    .provider(llmProvider)
                    .toolExecutor(toolExecutor)
                    .maxIterations(5)
                    .costTracker(tracker)
                    .build();

            StepVerifier.create(loop.executeWithToolsReactive(userMessages("hi"), LLMOptions.builder().build()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Reactive: 预算充足时正常执行")
        void reactiveShouldWorkWhenWithinBudget() {
            CostTracker tracker = new CostTracker(1000);
            tracker.record(10, 10); // total=20 < budget=1000

            llmProvider.addStream(List.of(
                    StreamEvent.textDelta("Hello"),
                    StreamEvent.llmComplete(textResponse("Hello"))
            ));

            ToolCallingLoop loop = ToolCallingLoop.builder()
                    .provider(llmProvider)
                    .toolExecutor(toolExecutor)
                    .maxIterations(5)
                    .costTracker(tracker)
                    .build();

            StepVerifier.create(loop.executeWithToolsReactive(userMessages("hi"), LLMOptions.builder().build()))
                    .assertNext(e -> assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType()))
                    .assertNext(e -> assertEquals(StreamEvent.EventType.LLM_COMPLETE, e.getType()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Reactive: 无限预算 (maxBudget=0) 永不触发超限")
        void reactiveShouldNeverLimitWhenBudgetIsZero() {
            CostTracker tracker = new CostTracker(0);
            tracker.record(999999, 999999);

            llmProvider.addStream(List.of(
                    StreamEvent.textDelta("ok"),
                    StreamEvent.llmComplete(textResponse("ok"))
            ));

            ToolCallingLoop loop = ToolCallingLoop.builder()
                    .provider(llmProvider)
                    .toolExecutor(toolExecutor)
                    .maxIterations(5)
                    .costTracker(tracker)
                    .build();

            StepVerifier.create(loop.executeWithToolsReactive(userMessages("hi"), LLMOptions.builder().build()))
                    .assertNext(e -> assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType()))
                    .assertNext(e -> assertEquals(StreamEvent.EventType.LLM_COMPLETE, e.getType()))
                    .verifyComplete();
        }
    }

    // ==================== Observer Hooks ====================

    @Nested
    @DisplayName("��步路径 Observer hooks")
    class SyncObserverTests {

        @Test
        @DisplayName("PreToolUse 和 PostToolUse 被调用，携带正确参数和结果")
        void observerHooksFireOnReactivePath() {
            Map<String, Object> toolArgs = Map.of("msg", "test-value");
            llmProvider.addStream(List.of(StreamEvent.llmComplete(toolCallResponse("echo", toolArgs))));
            llmProvider.addStream(List.of(
                    StreamEvent.textDelta("done"),
                    StreamEvent.llmComplete(textResponse("done"))));

            CapturingAgentObserver observer = new CapturingAgentObserver();

            ToolCallingLoop loop = ToolCallingLoop.builder()
                    .provider(llmProvider)
                    .toolExecutor(toolExecutor)
                    .maxIterations(5)
                    .observers(List.of(observer))
                    .build();

            // Use reactive path since observers fire in reactive executeReactiveLoop
            List<StreamEvent> events = loop.executeWithToolsReactive(
                    userMessages("call echo"), LLMOptions.builder().build())
                    .collectList()
                    .block();

            // PreToolUse must have fired with correct args
            assertEquals(1, observer.preCalls().size());
            assertEquals("echo", observer.preCalls().get(0).name());
            assertEquals(toolArgs, observer.preCalls().get(0).args());

            // PostToolUse must have fired with correct args AND result
            assertEquals(1, observer.postCalls().size());
            assertEquals("echo", observer.postCalls().get(0).name());
            assertEquals(toolArgs, observer.postCalls().get(0).args());
            assertNotNull(observer.postCalls().get(0).result());
            assertTrue(observer.postCalls().get(0).result().getContent().contains("echo: test-value"));
        }

        @Test
        @DisplayName("Observer 异常不中断工具执行循环")
        void observerExceptionShouldNotBreakLoop() {
            llmProvider.addStream(List.of(StreamEvent.llmComplete(toolCallResponse("echo", Map.of("msg", "safe")))));
            llmProvider.addStream(List.of(
                    StreamEvent.textDelta("survived"),
                    StreamEvent.llmComplete(textResponse("survived"))));

            AgentObserver throwingObserver = new AgentObserver() {
                @Override
                public void onPreToolUse(String toolName, Map<String, Object> args) {
                    throw new RuntimeException("Observer explosion");
                }
                @Override
                public void onPostToolUse(String toolName, Map<String, Object> args, ToolResult result) {
                    throw new RuntimeException("Observer explosion");
                }
            };

            ToolCallingLoop loop = ToolCallingLoop.builder()
                    .provider(llmProvider)
                    .toolExecutor(toolExecutor)
                    .maxIterations(5)
                    .observers(List.of(throwingObserver))
                    .build();

            List<StreamEvent> events = loop.executeWithToolsReactive(
                    userMessages("test"), LLMOptions.builder().build())
                    .collectList()
                    .block();

            assertNotNull(events);
            boolean hasComplete = events.stream()
                    .anyMatch(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE);
            assertTrue(hasComplete, "Loop should complete despite observer exception");
        }

        @Test
        @DisplayName("多个 tool call 在一个 LLM 响应中，每个都触发 pre/post hooks")
        void multipleToolCallsEachFireHooks() {
            // LLM returns 2 tool calls in one response
            ToolCall tc1 = new ToolCall("call_1", "echo", Map.of("msg", "a"));
            ToolCall tc2 = new ToolCall("call_2", "add", Map.of("a", 1, "b", 2));
            LLMResponse multiToolResponse = LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("")
                            .build())
                    .toolCalls(List.of(tc1, tc2))
                    .build();

            llmProvider.addStream(List.of(StreamEvent.llmComplete(multiToolResponse)));
            llmProvider.addStream(List.of(
                    StreamEvent.textDelta("done"),
                    StreamEvent.llmComplete(textResponse("done"))
            ));

            CapturingAgentObserver observer = new CapturingAgentObserver();

            ToolCallingLoop loop = ToolCallingLoop.builder()
                    .provider(llmProvider)
                    .toolExecutor(toolExecutor)
                    .maxIterations(5)
                    .observers(List.of(observer))
                    .build();

            loop.executeWithToolsReactive(userMessages("multi"), LLMOptions.builder().build())
                    .collectList()
                    .block();

            assertEquals(2, observer.preCalls().size(), "PreToolUse should fire for each tool call");
            assertEquals(2, observer.postCalls().size(), "PostToolUse should fire for each tool call");

            Set<String> preNames = new HashSet<>();
            observer.preCalls().forEach(c -> preNames.add(c.name()));
            assertTrue(preNames.contains("echo"));
            assertTrue(preNames.contains("add"));
        }
    }

    // ==================== Helpers ====================

    private List<ConversationMessage> userMessages(String text) {
        return List.of(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent(text)
                .build());
    }

    private LLMResponse textResponse(String text) {
        return LLMResponse.builder()
                .message(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.ASSISTANT)
                        .textContent(text)
                        .build())
                .build();
    }

    private LLMResponse toolCallResponse(String toolName, Map<String, Object> args) {
        ToolCall tc = new ToolCall("call_" + callCounter.incrementAndGet(), toolName, args);
        return LLMResponse.builder()
                .message(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.ASSISTANT)
                        .textContent("")
                        .build())
                .toolCalls(List.of(tc))
                .build();
    }

    /**
     * LLM provider supporting both sync and reactive scripted responses,
     * with an optional callback hook on specific call counts.
     */
    private static class ScriptedLLMProvider implements LLMProvider {
        private final Queue<LLMResponse> syncResponses = new LinkedList<>();
        private final Queue<List<StreamEvent>> streamResponses = new LinkedList<>();
        private final Map<Integer, Runnable> callHooks = new HashMap<>();
        int callCount = 0;

        void add(LLMResponse r) { syncResponses.add(r); }
        void addStream(List<StreamEvent> events) { streamResponses.add(events); }
        void onCall(int n, Runnable hook) { callHooks.put(n, hook); }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            callCount++;
            Runnable hook = callHooks.get(callCount);
            if (hook != null) hook.run();
            LLMResponse r = syncResponses.poll();
            if (r == null) throw new RuntimeException("No more sync responses");
            return r;
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public reactor.core.publisher.Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> messages, LLMOptions options) {
            callCount++;
            List<StreamEvent> events = streamResponses.poll();
            if (events == null) return reactor.core.publisher.Flux.error(new RuntimeException("No more stream responses"));
            return reactor.core.publisher.Flux.fromIterable(events);
        }

        @Override
        public ModelCapability getModelCapability() { return null; }

        @Override
        public String getProviderName() { return "scripted-mock"; }
    }
}
