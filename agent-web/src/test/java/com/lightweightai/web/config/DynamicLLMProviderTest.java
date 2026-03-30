package com.lightweightai.web.config;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.llm.claude.ClaudeModelCapability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DynamicLLMProvider - 运行时热切换 LLM 提供者")
class DynamicLLMProviderTest {

    @Test
    @DisplayName("委托 complete 到底层 provider")
    void delegatesComplete() {
        LLMResponse expected = LLMResponse.builder().build();
        LLMProvider mock = stubProvider("mock1", expected);

        DynamicLLMProvider dynamic = new DynamicLLMProvider(mock);
        LLMResponse result = dynamic.complete(List.of(ConversationMessage.builder().role(ConversationMessage.MessageRole.USER).textContent("hi").build()), LLMOptions.builder().build());

        assertSame(expected, result);
    }

    @Test
    @DisplayName("委托 completeAsync 到底层 provider")
    void delegatesCompleteAsync() throws Exception {
        LLMResponse expected = LLMResponse.builder().build();
        LLMProvider mock = stubProvider("mock1", expected);

        DynamicLLMProvider dynamic = new DynamicLLMProvider(mock);
        LLMResponse result = dynamic.completeAsync(
                List.of(ConversationMessage.builder().role(ConversationMessage.MessageRole.USER).textContent("hi").build()), LLMOptions.builder().build()).get();

        assertSame(expected, result);
    }

    @Test
    @DisplayName("委托 completeStream 到底层 provider")
    void delegatesCompleteStream() throws Exception {
        LLMResponse expected = LLMResponse.builder().build();
        LLMProvider mock = stubProvider("mock1", expected);

        DynamicLLMProvider dynamic = new DynamicLLMProvider(mock);
        LLMProvider.StreamEventHandler handler = new LLMProvider.StreamEventHandler() {};
        LLMResponse result = dynamic.completeStream(
                List.of(ConversationMessage.builder().role(ConversationMessage.MessageRole.USER).textContent("hi").build()), LLMOptions.builder().build(), handler).get();

        assertSame(expected, result);
    }

    @Test
    @DisplayName("委托 completeStreamReactive 到底层 provider")
    void delegatesCompleteStreamReactive() {
        LLMProvider mock = new StubLLMProvider("mock1",
                LLMResponse.builder().build()) {
            @Override
            public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> messages, LLMOptions options) {
                return Flux.just(StreamEvent.textDelta("hello"));
            }
        };

        DynamicLLMProvider dynamic = new DynamicLLMProvider(mock);
        List<StreamEvent> events = dynamic.completeStreamReactive(
                List.of(ConversationMessage.builder().role(ConversationMessage.MessageRole.USER).textContent("hi").build()), LLMOptions.builder().build())
                .collectList().block();

        assertEquals(1, events.size());
        assertEquals("hello", events.get(0).getTextDelta());
    }

    @Test
    @DisplayName("委托 getModelCapability 和 getProviderName")
    void delegatesMetadata() {
        LLMProvider mock = stubProvider("testProvider", null);

        DynamicLLMProvider dynamic = new DynamicLLMProvider(mock);

        assertEquals("testProvider", dynamic.getProviderName());
        assertNotNull(dynamic.getModelCapability());
    }

    @Test
    @DisplayName("热切换 provider 后委托到新 provider")
    void hotSwap() {
        LLMResponse resp1 = LLMResponse.builder().stopReason("old").build();
        LLMResponse resp2 = LLMResponse.builder().stopReason("new").build();
        LLMProvider old = stubProvider("old", resp1);
        LLMProvider newer = stubProvider("new", resp2);

        DynamicLLMProvider dynamic = new DynamicLLMProvider(old);
        assertSame(resp1, dynamic.complete(List.of(ConversationMessage.builder().role(ConversationMessage.MessageRole.USER).textContent("hi").build()), LLMOptions.builder().build()));

        dynamic.setDelegate(newer);
        assertSame(resp2, dynamic.complete(List.of(ConversationMessage.builder().role(ConversationMessage.MessageRole.USER).textContent("hi").build()), LLMOptions.builder().build()));
        assertEquals("new", dynamic.getProviderName());
    }

    @Test
    @DisplayName("getDelegate 返回当前底层 provider")
    void getDelegate() {
        LLMProvider mock = stubProvider("p1", null);
        DynamicLLMProvider dynamic = new DynamicLLMProvider(mock);

        assertSame(mock, dynamic.getDelegate());
    }

    // ---- helpers ----

    private LLMProvider stubProvider(String name, LLMResponse response) {
        return new StubLLMProvider(name, response);
    }

    private static class StubLLMProvider implements LLMProvider {
        private final String name;
        private final LLMResponse response;

        StubLLMProvider(String name, LLMResponse response) {
            this.name = name;
            this.response = response;
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            return response;
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public ModelCapability getModelCapability() {
            return new ClaudeModelCapability(name);
        }

        @Override
        public String getProviderName() {
            return name;
        }
    }
}
