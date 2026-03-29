package com.lightweightai.web.config;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DynamicLLMProvider - hot-swappable LLM delegation")
class DynamicLLMProviderTest {

    @Test
    void delegatesComplete() {
        LLMResponse expected = LLMResponse.builder().content("hello").model("test").build();
        MockLLMProvider mock = new MockLLMProvider("mock-1", expected);
        DynamicLLMProvider dynamic = new DynamicLLMProvider(mock);

        LLMResponse result = dynamic.complete(List.of(), LLMOptions.builder().build());
        assertEquals("hello", result.getMessage().getTextContent());
    }

    @Test
    void delegatesProviderName() {
        MockLLMProvider mock = new MockLLMProvider("claude-mock", null);
        DynamicLLMProvider dynamic = new DynamicLLMProvider(mock);

        assertEquals("claude-mock", dynamic.getProviderName());
    }

    @Test
    void hotSwap_changesDelegate() {
        MockLLMProvider initial = new MockLLMProvider("initial", null);
        MockLLMProvider replacement = new MockLLMProvider("replacement", null);

        DynamicLLMProvider dynamic = new DynamicLLMProvider(initial);
        assertEquals("initial", dynamic.getProviderName());

        dynamic.setDelegate(replacement);
        assertEquals("replacement", dynamic.getProviderName());
    }

    @Test
    void getDelegate_returnsCurrentProvider() {
        MockLLMProvider mock = new MockLLMProvider("test", null);
        DynamicLLMProvider dynamic = new DynamicLLMProvider(mock);

        assertSame(mock, dynamic.getDelegate());
    }

    @Test
    void delegatesCompleteAsync() {
        LLMResponse expected = LLMResponse.builder().content("async").model("test").build();
        MockLLMProvider mock = new MockLLMProvider("mock", expected);
        DynamicLLMProvider dynamic = new DynamicLLMProvider(mock);

        CompletableFuture<LLMResponse> future = dynamic.completeAsync(List.of(), LLMOptions.builder().build());
        assertNotNull(future);
        assertEquals("async", future.join().getMessage().getTextContent());
    }

    @Test
    void delegatesCompleteStreamReactive() {
        MockLLMProvider mock = new MockLLMProvider("mock", null);
        DynamicLLMProvider dynamic = new DynamicLLMProvider(mock);

        Flux<StreamEvent> flux = dynamic.completeStreamReactive(List.of(), LLMOptions.builder().build());
        assertNotNull(flux);
    }

    @Test
    void delegatesModelCapability() {
        MockLLMProvider mock = new MockLLMProvider("mock", null);
        DynamicLLMProvider dynamic = new DynamicLLMProvider(mock);

        assertNotNull(dynamic.getModelCapability());
    }

    // === Mock ===

    private static class MockLLMProvider implements LLMProvider {
        private final String name;
        private final LLMResponse response;

        MockLLMProvider(String name, LLMResponse response) {
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
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> messages, LLMOptions options) {
            return Flux.empty();
        }

        @Override
        public ModelCapability getModelCapability() {
            return new ModelCapability() {
                @Override public String getModelId() { return "mock-model"; }
                @Override public int getMaxContextTokens() { return 100000; }
                @Override public int getMaxOutputTokens() { return 4096; }
                @Override public MessageFormatter getMessageFormatter() { return null; }
                @Override public TokenCounter getTokenCounter() { return null; }
                @Override public java.util.Set<ModelFeature> getSupportedFeatures() {
                    return java.util.Set.of(ModelFeature.STREAMING, ModelFeature.TOOL_CALLING);
                }
            };
        }

        @Override
        public String getProviderName() {
            return name;
        }
    }
}
