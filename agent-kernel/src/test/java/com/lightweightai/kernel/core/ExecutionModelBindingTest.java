package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionModelBindingTest {
    private static final List<ConversationMessage> MESSAGES = List.of(ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER).textContent("perform action").build());
    private static final LLMOptions OPTIONS = LLMOptions.builder().build();

    @ParameterizedTest
    @ValueSource(strings = {"sync", "async", "reactive"})
    void standaloneToolLoopBindsOnceAndRebindsForTheNextExecution(String mode) throws Exception {
        var first = CapturingLLMProvider.toolThenEnd("switch", Map.of("value", "actual argument"), "old result");
        var second = CapturingLLMProvider.endTurn("new result");
        var source = new SwitchingProvider(first);
        ToolRegistry tools = new ToolRegistry();
        tools.register(new Tool() {
            public String getName() { return "switch"; }
            public String getDescription() { return "Change configuration during a tool call"; }
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            public ToolResult execute(Map<String, Object> args) {
                assertEquals(Map.of("value", "actual argument"), args);
                source.current.set(second);
                return ToolResult.success("actual tool result");
            }
        });
        ToolCallingLoop loop = ToolCallingLoop.builder().provider(source)
                .toolExecutor(new ToolExecutor(tools)).build();
        assertEquals("old result", execute(loop, mode).getMessage().getTextContent());
        assertEquals(2, first.callCount());
        assertTrue(first.lastMessages().stream().anyMatch(m -> m.getRole() == ConversationMessage.MessageRole.TOOL
                && m.getTextContent().contains("actual tool result")));
        assertEquals(0, second.callCount());
        assertEquals("new result", execute(loop, mode).getMessage().getTextContent());
        assertEquals(MESSAGES, second.lastMessages());
        assertEquals(2, source.bindings.get());
    }

    @Test void eachSubscriptionGetsItsOwnConversationAndModel() {
        var first = CapturingLLMProvider.endTurn("old result");
        var second = CapturingLLMProvider.endTurn("new result");
        var source = new SwitchingProvider(first);
        var loop = ToolCallingLoop.builder().provider(source).toolExecutor(new ToolExecutor(new ToolRegistry())).build();
        Flux<StreamEvent> stream = loop.executeWithToolsReactive(MESSAGES, OPTIONS);
        assertEquals(0, source.bindings.get());
        assertEquals("old result", stream.blockLast(Duration.ofSeconds(5)).getResponse().getMessage().getTextContent());
        source.current.set(second);
        assertEquals("new result", stream.blockLast(Duration.ofSeconds(5)).getResponse().getMessage().getTextContent());
        assertEquals(MESSAGES, first.lastMessages());
        assertEquals(MESSAGES, second.lastMessages());
    }

    @Test void concurrentChildrenResolveOnceAndDifferentSourcesRemainIndependent() throws Exception {
        var first = CapturingLLMProvider.endTurn("one");
        var second = CapturingLLMProvider.endTurn("two");
        var source = new SwitchingProvider(first);
        var distinctSource = new SwitchingProvider(second);
        // These objects deliberately compare equal; only identity defines the configured source.
        assertEquals(source, distinctSource);
        var scope = new ExecutionModelScope();
        var resolutions = IntStream.range(0, 16).mapToObj(i ->
                CompletableFuture.supplyAsync(() -> scope.resolve(source))).toList();
        for (var resolution : resolutions) assertSame(first, resolution.get(5, TimeUnit.SECONDS));
        source.current.set(second);
        assertSame(first, scope.resolve(source));
        assertSame(second, scope.resolve(distinctSource));
        assertSame(second, new ExecutionModelScope().resolve(source));
        assertEquals(2, source.bindings.get());
        assertEquals(1, distinctSource.bindings.get());
    }

    @Test void contextCopiesKeepTheModelScopeAndCancellationIndependent() {
        var source = CapturingLLMProvider.endTurn("stable");
        var token = new CancellationToken();
        var scope = new ExecutionModelScope();
        var original = new ToolExecutionContext(null, 1234);
        var context = original.withModelScope(scope).withCancellationToken(token);
        assertSame(scope, context.getModelScope());
        assertSame(token, context.getCancellationToken());
        assertSame(original.getDeviceContext(), context.getDeviceContext());
        assertEquals(1234, context.getClientToolTimeoutMs());
        assertNull(original.getModelScope());
        assertNull(original.getCancellationToken());
        assertSame(source, scope.resolve(source));
        token.cancel();
        assertTrue(context.getCancellationToken().isCancelled());
        assertSame(source, scope.resolve(source));
        assertSame(token, original.withCancellationToken(token).withModelScope(scope).getCancellationToken());
    }

    @Test void failedBindingIsNotCached() {
        var source = new SwitchingProvider(null);
        var scope = new ExecutionModelScope();
        assertThrows(NullPointerException.class, () -> scope.resolve(source));
        var working = CapturingLLMProvider.endTurn("working");
        source.current.set(working);
        assertSame(working, scope.resolve(source));
        assertEquals(2, source.bindings.get());
    }

    @Test void resilienceAroundDynamicSourceKeepsRetriesOnTheSelectedProvider() {
        var attempts = new AtomicInteger();
        var first = CapturingLLMProvider.responding((messages, options) -> {
            if (attempts.incrementAndGet() == 1) throw new RuntimeException("HTTP: 503");
            return LLMResponse.builder().message(ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.ASSISTANT).textContent("retried old model").build()).build();
        });
        var second = CapturingLLMProvider.endTurn("new model");
        var source = new SwitchingProvider(first);
        var wrapper = new ResilientLLMProvider(source, 1, 0);
        var selected = new ExecutionModelScope().resolve(wrapper);
        source.current.set(second);
        assertEquals("retried old model", selected.complete(MESSAGES, OPTIONS).getMessage().getTextContent());
        assertEquals(2, first.callCount());
        assertEquals(MESSAGES, first.lastMessages());
        assertEquals(0, second.callCount());
        assertEquals("new model", new ExecutionModelScope().resolve(wrapper)
                .complete(MESSAGES, OPTIONS).getMessage().getTextContent());
    }

    @Test void fixedResilienceWrapperRetainsItsHealthState() {
        var delegate = CapturingLLMProvider.endTurn("healthy");
        var wrapper = new ResilientLLMProvider(delegate, 0, 0);
        assertSame(wrapper, new ExecutionModelScope().resolve(wrapper));
    }

    private static LLMResponse execute(ToolCallingLoop loop, String mode) throws Exception {
        return switch (mode) {
            case "sync" -> loop.executeWithTools(MESSAGES, OPTIONS);
            case "async" -> loop.executeWithToolsAsync(MESSAGES, OPTIONS).get(5, TimeUnit.SECONDS);
            default -> loop.executeWithToolsReactive(MESSAGES, OPTIONS).filter(e -> e.getResponse() != null)
                    .blockLast(Duration.ofSeconds(5)).getResponse();
        };
    }

    /** Dynamic configuration source; downstream providers capture every actual request. */
    private static class SwitchingProvider implements ExecutionScopedLLMProvider {
        final AtomicReference<LLMProvider> current;
        final AtomicInteger bindings = new AtomicInteger();
        SwitchingProvider(LLMProvider initial) { current = new AtomicReference<>(initial); }
        public LLMProvider bindToExecution() { bindings.incrementAndGet(); return current.get(); }
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            return current.get().complete(messages, options);
        }
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            return current.get().completeAsync(messages, options);
        }
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options,
                                                            StreamEventHandler handler) {
            return current.get().completeStream(messages, options, handler);
        }
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> messages, LLMOptions options) {
            return current.get().completeStreamReactive(messages, options);
        }
        public ModelCapability getModelCapability() { return current.get().getModelCapability(); }
        public String getProviderName() { return "same-name"; }
        public boolean equals(Object other) { return other instanceof SwitchingProvider; }
        public int hashCode() { return 1; }
    }
}
