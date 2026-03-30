package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.core.StreamEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LLMProvider - default completeStreamReactive 桥接")
class LLMProviderDefaultTest {

    @Test
    @DisplayName("桥接 StreamEventHandler 到 Flux: text + tool call + complete")
    void bridgesHandlerToFlux() {
        ToolCall toolCall = new ToolCall("tc-1", "search", java.util.Map.of("q", "test"));
        LLMResponse finalResponse = LLMResponse.builder()
                .stopReason("end_turn")
                .build();

        LLMProvider provider = new LLMProvider() {
            @Override
            public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                return null;
            }

            @Override
            public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
                return null;
            }

            @Override
            public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
                handler.onStart();
                handler.onTextDelta("hello ");
                handler.onTextDelta("world");
                handler.onToolCallDelta(toolCall);
                handler.onComplete(finalResponse);
                return CompletableFuture.completedFuture(finalResponse);
            }

            @Override
            public ModelCapability getModelCapability() { return null; }

            @Override
            public String getProviderName() { return "test"; }
        };

        List<ConversationMessage> messages = List.of(
                ConversationMessage.builder().role(ConversationMessage.MessageRole.USER).textContent("hi").build());

        StepVerifier.create(provider.completeStreamReactive(messages, LLMOptions.builder().build()))
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
                    assertEquals("hello ", event.getTextDelta());
                })
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
                    assertEquals("world", event.getTextDelta());
                })
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.TOOL_CALL_START, event.getType());
                    assertEquals("search", event.getToolCall().getName());
                })
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.LLM_COMPLETE, event.getType());
                    assertSame(finalResponse, event.getResponse());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("桥接错误到 Flux error signal")
    void bridgesErrorToFlux() {
        LLMProvider provider = new LLMProvider() {
            @Override
            public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                return null;
            }

            @Override
            public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
                return null;
            }

            @Override
            public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
                handler.onTextDelta("partial");
                handler.onError(new RuntimeException("API timeout"));
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public ModelCapability getModelCapability() { return null; }

            @Override
            public String getProviderName() { return "test"; }
        };

        StepVerifier.create(provider.completeStreamReactive(
                        List.of(ConversationMessage.builder().role(ConversationMessage.MessageRole.USER).textContent("hi").build()),
                        LLMOptions.builder().build()))
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
                })
                .expectErrorMatches(e -> e.getMessage().equals("API timeout"))
                .verify();
    }

    @Test
    @DisplayName("StreamEventHandler 默认方法为空实现不抛异常")
    void streamEventHandlerDefaults() {
        LLMProvider.StreamEventHandler handler = new LLMProvider.StreamEventHandler() {};

        // 调用所有默认方法应不抛异常
        assertDoesNotThrow(() -> {
            handler.onStart();
            handler.onTextDelta("text");
            handler.onToolCallDelta(new ToolCall("id", "name", java.util.Map.of()));
            handler.onComplete(LLMResponse.builder().build());
            handler.onError(new RuntimeException("err"));
        });
    }
}
