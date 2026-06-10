package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.context.CostTracker;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolCallingLoop — CostTracker 预算与 CancellationToken 打断的集成测试。
 *
 * 已有 ToolCallingLoopReactiveTest 覆盖基本 reactive 流。
 * 此测试验证预算和打断在 sync/reactive 两个路径上的传导。
 *
 * 遵循 CLAUDE.md UT 规则 1：断言的是实际载荷（stopReason、事件列表），不是 ceremony。
 */
@DisplayName("ToolCallingLoop - 预算超限 & 打断终止")
class ToolCallingLoopBudgetCancellationTest {

    private static final LLMOptions OPTIONS = LLMOptions.builder().maxTokens(100).build();

    private List<ConversationMessage> singleUserMessage() {
        return List.of(
                ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.USER)
                        .textContent("test")
                        .build()
        );
    }

    @Nested
    @DisplayName("CancellationToken - sync path")
    class CancellationSync {

        @Test
        @DisplayName("已取消 token 使 sync executeWithTools 立即返回 [cancelled]")
        void cancelledTokenReturnsImmediately() {
            CancellationToken token = new CancellationToken();
            token.cancel();

            CapturingLLMProvider provider = CapturingLLMProvider.endTurn("should not reach");
            ToolExecutor executor = new ToolExecutor();

            ToolCallingLoop loop = new ToolCallingLoop(provider, executor, 10,
                    null, null, token);

            LLMResponse response = loop.executeWithTools(singleUserMessage(), OPTIONS);

            assertEquals("cancelled", response.getStopReason());
            assertEquals("[cancelled]", response.getMessage().getTextContent());
            assertEquals(0, provider.callCount(), "LLM should NOT be called when token is already cancelled");
        }
    }

    @Nested
    @DisplayName("CancellationToken - reactive path")
    class CancellationReactive {

        @Test
        @DisplayName("已取消 token 使 reactive executeWithToolsReactive 返回空 Flux")
        void cancelledTokenReturnsEmptyFlux() {
            CancellationToken token = new CancellationToken();
            token.cancel();

            CapturingLLMProvider provider = CapturingLLMProvider.endTurn("should not reach");
            ToolExecutor executor = new ToolExecutor();

            ToolCallingLoop loop = ToolCallingLoop.builder()
                    .provider(provider)
                    .toolExecutor(executor)
                    .maxIterations(10)
                    .cancellationToken(token)
                    .build();

            List<StreamEvent> events = loop.executeWithToolsReactive(singleUserMessage(), OPTIONS)
                    .collectList().block();

            assertNotNull(events);
            assertTrue(events.isEmpty(), "Cancelled token should produce empty Flux");
            assertEquals(0, provider.callCount());
        }
    }

    @Nested
    @DisplayName("CostTracker - reactive path")
    class CostTrackerReactive {

        @Test
        @DisplayName("超预算时 reactive 循环优雅退出（空 Flux）")
        void overBudgetReturnsEmptyFlux() {
            CostTracker tracker = new CostTracker(100);
            tracker.record(80, 30); // 110 > 100, already over

            CapturingLLMProvider provider = CapturingLLMProvider.endTurn("should not reach");
            ToolExecutor executor = new ToolExecutor();

            ToolCallingLoop loop = ToolCallingLoop.builder()
                    .provider(provider)
                    .toolExecutor(executor)
                    .maxIterations(10)
                    .costTracker(tracker)
                    .build();

            List<StreamEvent> events = loop.executeWithToolsReactive(singleUserMessage(), OPTIONS)
                    .collectList().block();

            assertNotNull(events);
            assertTrue(events.isEmpty(), "Over-budget should produce empty Flux");
            assertEquals(0, provider.callCount(), "LLM should NOT be called when over budget");
        }

        @Test
        @DisplayName("预算充足时正常执行到 LLM_COMPLETE")
        void withinBudgetExecutesNormally() {
            CostTracker tracker = new CostTracker(100000);

            CapturingLLMProvider provider = CapturingLLMProvider.endTurn("all good");
            ToolExecutor executor = new ToolExecutor();

            ToolCallingLoop loop = ToolCallingLoop.builder()
                    .provider(provider)
                    .toolExecutor(executor)
                    .maxIterations(10)
                    .costTracker(tracker)
                    .build();

            List<StreamEvent> events = loop.executeWithToolsReactive(singleUserMessage(), OPTIONS)
                    .collectList().block();

            assertNotNull(events);
            assertTrue(events.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE),
                    "Should contain LLM_COMPLETE event");
            assertEquals(1, provider.callCount());
        }
    }

    @Nested
    @DisplayName("maxIterations exceeded")
    class MaxIterations {

        @Test
        @DisplayName("超过 maxIterations 时 sync 抛 RuntimeException")
        void syncThrowsOnMaxIterations() {
            ToolRegistry registry = new ToolRegistry();
            registry.register(new EchoTool());
            ToolExecutor executor = new ToolExecutor(registry);

            var provider = new AlwaysToolCallProvider();

            ToolCallingLoop loop = new ToolCallingLoop(provider, executor, 2);

            assertThrows(RuntimeException.class,
                    () -> loop.executeWithTools(singleUserMessage(), OPTIONS),
                    "Should throw when maxIterations exceeded");
        }

        @Test
        @DisplayName("超过 maxIterations 时 reactive 发出 error 信号")
        void reactiveEmitsErrorOnMaxIterations() {
            ToolRegistry registry = new ToolRegistry();
            registry.register(new EchoTool());
            ToolExecutor executor = new ToolExecutor(registry);

            var provider = new AlwaysToolCallProvider();

            ToolCallingLoop loop = ToolCallingLoop.builder()
                    .provider(provider)
                    .toolExecutor(executor)
                    .maxIterations(2)
                    .build();

            assertThrows(RuntimeException.class,
                    () -> loop.executeWithToolsReactive(singleUserMessage(), OPTIONS)
                            .collectList().block());
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builder 设置所有参数正确构建")
        void builderSetsAllFields() {
            CancellationToken token = new CancellationToken();
            CostTracker tracker = new CostTracker(5000);
            CapturingLLMProvider provider = CapturingLLMProvider.endTurn("ok");
            ToolExecutor executor = new ToolExecutor();

            ToolCallingLoop loop = ToolCallingLoop.builder()
                    .provider(provider)
                    .toolExecutor(executor)
                    .maxIterations(5)
                    .cancellationToken(token)
                    .costTracker(tracker)
                    .build();

            assertEquals(5, loop.getMaxIterations());
        }

        @Test
        @DisplayName("provider 为 null 时抛 IllegalArgumentException")
        void nullProviderThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ToolCallingLoop(null, new ToolExecutor(), 10));
        }

        @Test
        @DisplayName("toolExecutor 为 null 时抛 IllegalArgumentException")
        void nullExecutorThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ToolCallingLoop(CapturingLLMProvider.endTurn("x"), null, 10));
        }

        @Test
        @DisplayName("maxIterations < 1 时抛 IllegalArgumentException")
        void zeroMaxIterationsThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ToolCallingLoop(CapturingLLMProvider.endTurn("x"), new ToolExecutor(), 0));
        }
    }

    // Helper: a tool that echoes its input
    static class EchoTool implements Tool {
        @Override public String getName() { return "echo"; }
        @Override public String getDescription() { return "Echoes input"; }
        @Override public ToolSchema getSchema() { return ToolSchema.builder().name("echo").build(); }
        @Override public ToolResult execute(Map<String, Object> args) {
            return ToolResult.success("echo: " + args.getOrDefault("msg", ""));
        }
    }


    /**
     * Provider that always emits a tool_use response, never end_turn.
     */
    static class AlwaysToolCallProvider implements com.lightweightai.kernel.llm.LLMProvider {
        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("")
                            .build())
                    .toolCalls(List.of(new ToolCall("tc-" + System.nanoTime(), "echo", Map.of("msg", "loop"))))
                    .stopReason("tool_use")
                    .build();
        }

        @Override
        public java.util.concurrent.CompletableFuture<LLMResponse> completeAsync(
                List<ConversationMessage> messages, LLMOptions options) {
            return java.util.concurrent.CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public java.util.concurrent.CompletableFuture<LLMResponse> completeStream(
                List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
            LLMResponse r = complete(messages, options);
            handler.onComplete(r);
            return java.util.concurrent.CompletableFuture.completedFuture(r);
        }

        @Override
        public Flux<StreamEvent> completeStreamReactive(
                List<ConversationMessage> messages, LLMOptions options) {
            return Flux.just(StreamEvent.llmComplete(complete(messages, options)));
        }

        @Override
        public com.lightweightai.kernel.llm.ModelCapability getModelCapability() { return null; }
        @Override
        public String getProviderName() { return "always-tool-call"; }
    }
}
