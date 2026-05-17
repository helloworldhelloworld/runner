package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.core.StreamEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LLMProvider interface — contract and default method validation")
class LLMProviderContractTest {

    @Test
    @DisplayName("completeStreamReactive default bridges callback-based completeStream to Flux")
    void defaultCompleteStreamReactiveBridgesCallbacks() {
        LLMResponse expectedResponse = LLMResponse.builder()
                .message(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.ASSISTANT)
                        .textContent("Hello world")
                        .build())
                .stopReason("end_turn")
                .build();

        LLMProvider provider = new LLMProvider() {
            @Override
            public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                return expectedResponse;
            }
            @Override
            public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
                return CompletableFuture.completedFuture(expectedResponse);
            }
            @Override
            public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages,
                    LLMOptions options, StreamEventHandler handler) {
                handler.onTextDelta("Hello ");
                handler.onTextDelta("world");
                handler.onComplete(expectedResponse);
                return CompletableFuture.completedFuture(expectedResponse);
            }
            @Override public ModelCapability getModelCapability() { return null; }
            @Override public String getProviderName() { return "test"; }
        };

        List<ConversationMessage> messages = List.of(
                ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.USER)
                        .textContent("Hi")
                        .build()
        );

        StepVerifier.create(provider.completeStreamReactive(messages, LLMOptions.builder().build()))
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
                    assertEquals("Hello ", event.getTextDelta());
                })
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
                    assertEquals("world", event.getTextDelta());
                })
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.LLM_COMPLETE, event.getType());
                    assertNotNull(event.getResponse());
                    assertEquals("end_turn", event.getResponse().getStopReason());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("completeStreamReactive propagates errors from completeStream")
    void defaultReactivePropagatesError() {
        RuntimeException expected = new RuntimeException("LLM service unavailable");

        LLMProvider provider = new LLMProvider() {
            @Override
            public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) { return null; }
            @Override
            public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
                return CompletableFuture.failedFuture(expected);
            }
            @Override
            public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages,
                    LLMOptions options, StreamEventHandler handler) {
                handler.onTextDelta("partial...");
                handler.onError(expected);
                return CompletableFuture.failedFuture(expected);
            }
            @Override public ModelCapability getModelCapability() { return null; }
            @Override public String getProviderName() { return "error-provider"; }
        };

        StepVerifier.create(provider.completeStreamReactive(List.of(), LLMOptions.builder().build()))
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
                    assertEquals("partial...", event.getTextDelta());
                })
                .expectErrorMatches(e -> e.getMessage().contains("LLM service unavailable"))
                .verify();
    }

    @Test
    @DisplayName("completeStreamReactive bridges tool call deltas")
    void defaultReactiveBridgesToolCalls() {
        ToolCall toolCall = new ToolCall("call_123", "search", java.util.Map.of("q", "test"));

        LLMResponse response = LLMResponse.builder()
                .message(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.ASSISTANT)
                        .textContent("")
                        .build())
                .toolCalls(List.of(toolCall))
                .stopReason("tool_use")
                .build();

        LLMProvider provider = new LLMProvider() {
            @Override
            public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) { return response; }
            @Override
            public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
                return CompletableFuture.completedFuture(response);
            }
            @Override
            public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages,
                    LLMOptions options, StreamEventHandler handler) {
                handler.onToolCallDelta(toolCall);
                handler.onComplete(response);
                return CompletableFuture.completedFuture(response);
            }
            @Override public ModelCapability getModelCapability() { return null; }
            @Override public String getProviderName() { return "tool-provider"; }
        };

        StepVerifier.create(provider.completeStreamReactive(List.of(), LLMOptions.builder().build()))
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.TOOL_CALL_START, event.getType());
                })
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.LLM_COMPLETE, event.getType());
                    assertTrue(event.getResponse().hasToolCalls());
                    assertEquals("search", event.getResponse().getToolCalls().get(0).getName());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("StreamEventHandler default methods are no-ops (don't throw)")
    void streamEventHandlerDefaultsAreNoOps() {
        LLMProvider.StreamEventHandler handler = new LLMProvider.StreamEventHandler() {};

        assertDoesNotThrow(handler::onStart);
        assertDoesNotThrow(() -> handler.onTextDelta("text"));
        assertDoesNotThrow(() -> handler.onToolCallDelta(null));
        assertDoesNotThrow(() -> handler.onComplete(null));
        assertDoesNotThrow(() -> handler.onError(new RuntimeException()));
    }
}
